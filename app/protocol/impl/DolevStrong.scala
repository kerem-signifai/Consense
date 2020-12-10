package protocol.impl

import mpi.AuthenticatedMPI.SignedPayload
import mpi.AuthenticatedNetwork
import protocol.{BehaviorBuilder, Protocol}

case class DolevStrongState(extr: Seq[Boolean])

object DolevStrong extends Protocol[DolevStrongState, Boolean] with AuthenticatedNetwork {
  override def initialState: DolevStrongState = DolevStrongState(Seq.empty)

  override def behavior: BehaviorGen = new BehaviorBuilder(_) {
    init { _ =>
      broadcast(config.initValue)
    }

    recv { case (SignedPayload(value: Boolean, chain, wrapper), _) =>
      if (chain.distinct.length == round && !state.extr.contains(value)) {
        broadcast(wrapper)
        goto(state.copy(extr = state.extr :+ value.asInstanceOf[Boolean]))
      }
    }

    after round config.numRounds perform { _ =>
      if (state.extr.size == 1) {
        output(state.extr.head)
      } else {
        output(false)
      }
    }
  }
}
