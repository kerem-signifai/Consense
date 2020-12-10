package protocol.impl

import mpi.AuthenticatedMPI.SignedPayload
import mpi.AuthenticatedNetwork
import protocol.{BehaviorBuilder, Protocol}

case class PBFTState(votes: Map[Boolean, Set[Int]], commits: Map[Boolean, Set[Int]])

case class PrePrepare(value: Boolean)
case class Prepare(value: Boolean)
case class Commit(value: Boolean)

object PBFT extends Protocol[PBFTState, Boolean] with AuthenticatedNetwork {
  override def initialState: PBFTState = PBFTState(Map.empty, Map.empty)

  override def behavior: BehaviorGen = new BehaviorBuilder(_) {
    init { _ =>
      broadcast(PrePrepare(config.initValue.asInstanceOf[Boolean]))
    }

    recv { case (SignedPayload(msg, _, _), sender) =>
      msg match {
        case PrePrepare(value) =>
          broadcast(Prepare(value))

        case Prepare(value) =>
          goto(state.copy(votes = state.votes.updatedWith(value) {
            case Some(existing) => Some(existing + sender)
            case _ => Some(Set(sender))
          }))
          if (state.votes(value).size + 1 > (2 * config.numMaxCorruptNodes)) {
            broadcast(Commit(value))
          }
        case Commit(value) =>
          goto(state.copy(commits = state.commits.updatedWith(value) {
            case Some(existing) => Some(existing + sender)
            case _ => Some(Set(sender))
          }))
          if (state.commits(value).size + 1 > (2 * config.numMaxCorruptNodes)) {
            output(value)
          }
      }
    }
  }
}
