package protocol.impl

import mpi.AuthenticatedMPI.SignedPayload
import mpi.AuthenticatedNetwork
import protocol.{NodeBehavior, Protocol}

object DolevStrong extends Protocol[Seq[Boolean], Boolean] with AuthenticatedNetwork {
  override def initialState: Seq[Boolean] = Seq.empty

  override def behavior: BehaviorGen = ctx => new NodeBehavior(ctx) {
    override def init(): Unit = broadcast(config.initValue)

    override def receive: Receive = {
      case (SignedPayload(value: Boolean, chain, wrapper), _) =>
        if (chain.distinct.length == round && !state.contains(value)) {
          broadcast(wrapper)
          goto(state :+ value.asInstanceOf[Boolean])
        }
    }

    override def afterRound(): Unit = {
      if (round == config.numRounds) {
        if (state.size == 1) {
          output(state.head)
        } else {
          output(false)
        }
      }
    }
  }
}
