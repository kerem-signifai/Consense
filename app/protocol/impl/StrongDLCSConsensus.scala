package protocol.impl

import mpi.AuthenticatedMPI.SignedPayload
import mpi.AuthenticatedNetwork
import protocol.{NodeBehavior, Protocol}

case class StrongDLCSState(votes: Seq[SignedPayload], hasSent: Boolean, value: Boolean)

// Strongly-valid implementation of DLCS single-shot consensus. Note the similarity (equality?) to Dolev-Strong wrt relaying signed messages
object StrongDLCSConsensus extends Protocol[StrongDLCSState, Boolean] with AuthenticatedNetwork {
  override def initialState: StrongDLCSState = StrongDLCSState(Seq.empty, hasSent = false, value = false)

  override def behavior: BehaviorGen = ctx => new NodeBehavior(ctx) {
    override def initialize(): Unit = {
      goto(state.copy(value = config.initValue.asInstanceOf[Boolean]))
      if (state.value) {
        broadcast(true)
      }
    }

    override def receive: Receive = {
      case (msg @ SignedPayload(v: Boolean, _, _), sender) =>
        if (v) {
          goto(state.copy(votes = state.votes :+ msg))
        }
    }

    override def afterRound(): Unit = {
      if (state.value && !state.hasSent) {
        broadcast(state.value)
        state.votes.foreach(m => broadcast(m.wrapper))
        goto(state.copy(hasSent = true))
      }
      if (!state.value) {
        if (state.votes.flatMap(_.sigChain).toSet.size >= round) {
          goto(state.copy(value = true))
        }
      }
      if (round == config.numRounds) {
        output(state.value)
      }
    }
  }
}
