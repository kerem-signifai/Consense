package protocol.impl

import mpi.AuthenticatedMPI.SignedPayload
import mpi.AuthenticatedNetwork
import protocol.{NodeBehavior, Protocol}

case class DolevStrongState(extr: Seq[Boolean])

object DolevStrong extends Protocol[DolevStrongState, Boolean] with AuthenticatedNetwork {
  override def initialState: DolevStrongState = DolevStrongState(Seq.empty)

  override def behavior: BehaviorGen = ctx => new NodeBehavior(ctx) {
    override def init(): Unit = {
      broadcast(config.initValue)
      goto(state.copy(extr = state.extr :+ config.initValue.asInstanceOf[Boolean]))
    }

    override def receive: Receive = {
      case (SignedPayload(value: Boolean, chain, wrapper), _) =>
        if (chain.distinct.length == round && !state.extr.contains(value)) {
          broadcast(wrapper)
          goto(state.copy(extr = state.extr :+ value.asInstanceOf[Boolean]))
        }
    }

    override def afterRound(): Unit = {
      if (round == config.numRounds) {
        if (state.extr.size == 1) {
          output(state.extr.head)
        } else {
          output(false)
        }
        terminate()
      }
    }
  }
}
