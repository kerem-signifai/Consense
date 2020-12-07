package protocol.impl

import mpi.AuthenticatedMPI.SignedPayload
import mpi.AuthenticatedNetwork
import protocol.{NodeBehavior, Protocol}

case class DLCSConfig(initValue: Boolean, leaderOrder: Map[Int, Int])
case class DLCSMessage(value: Boolean, round: Int)
case class DLCSState(votes: Seq[DLCSMessage], hasSent: Boolean)

object DLCSConsensus extends Protocol[DLCSState, Boolean] with AuthenticatedNetwork {
  override def initialState: DLCSState = DLCSState(Seq.empty, hasSent = false)
  override def senderValidityProp(initValue: Any, o1: Boolean): Boolean = initValue.asInstanceOf[DLCSConfig].initValue == o1

  override def behavior: BehaviorGen = ctx => new NodeBehavior(ctx) {
    override def init(): Unit = {
      val msg = DLCSMessage(config.initValue.asInstanceOf[DLCSConfig].initValue, 0)
      broadcast(msg)
      goto(state.copy(hasSent = true, votes = Seq(msg)))
    }

    override def receive: Receive = {
      case (SignedPayload(msg @ DLCSMessage(_, originRound), _, _), _) =>
        if (originRound < round) {
          goto(state.copy(votes = state.votes :+ msg))
        }
    }

    private def mostPopularBit = state.votes.map(v => (v.value, v.round)).toSet[(Boolean, Int)].groupMapReduce(_._1)(_ => 1)(_ + _).maxByOption(v => (v._2, v._1)).exists(_._1)

    override def afterRound(): Unit = {
      if (config.initValue.asInstanceOf[DLCSConfig].leaderOrder(round) == ctx.nodeId) {
        broadcast(DLCSMessage(mostPopularBit, round))
      }
      if (round == config.numRounds) {
        output(mostPopularBit)
        terminate()
      }
    }
  }
}
