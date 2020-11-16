import AuthenticatedMPI._

import scala.collection.mutable
import scala.util.Random

trait Network {
  val mpi = new MPI
}

trait AuthenticatedNetwork {
  self: Network =>
  override val mpi = new AuthenticatedMPI
}

class MPI {
  def write(msg: Any, sender: Int): Any = msg
  def read(msg: Any, sender: Int): Any = msg
}

object AuthenticatedMPI {
  case class SignedMessage(payload: Any, signer: Int, secret: String)
  case class SignedPayload(value: Any, sigChain: Seq[Int], wrapper: SignedMessage)
}

class AuthenticatedMPI extends MPI {
  private val storedMessages = mutable.Map[String, (Any, Int)]()

  /**
   * Extracts the payload from the message and returns a signature chain
   *
   * @param msg the signed message
   * @return Tuple2 of the payload and message signers if verified, None otherwise
   */
  def verify(msg: SignedMessage, sender: Int): Option[(Any, Seq[Int])] = {
    storedMessages.get(msg.secret) match {
      case Some(msg.payload -> msg.signer) if msg.signer == sender =>
        msg.payload match {
          case sg: SignedMessage => verify(sg, sg.signer) match {
            case Some(wrapped -> chain) => Some(wrapped, chain :+ msg.signer)
            case _ => None
          }
          case _ => Some(msg.payload, Seq(msg.signer))
        }
      case _ => None
    }
  }

  def sign(msg: Any, sender: Int): SignedMessage = {
    val message = SignedMessage(msg, sender, Random.nextString(16))
    storedMessages(message.secret) = msg -> sender
    message
  }

  override def write(msg: Any, sender: Int): Any = super.write(sign(msg, sender), sender)
  override def read(msg: Any, sender: Int): Any = msg match {
    case sg: SignedMessage =>
      verify(sg, sender) match {
        case Some(payload -> chain) =>
          super.read(SignedPayload(payload, chain, sg), sender)
        case _ =>
        // Could not validate signature
      }
    case _ =>
    // Authenticated protocol received unsigned message
  }
}