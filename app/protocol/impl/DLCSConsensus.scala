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
    override def initialize(): Unit = {
      val bit = config.initValue match {
        case cfg: DLCSConfig => cfg.initValue
        case b: Boolean => b
      }
      val msg = DLCSMessage(bit, 0)
      broadcast(msg)
      goto(state.copy(hasSent = true, votes = Seq(msg)))
    }

    override def receive: Receive = {
      case (SignedPayload(msg @ DLCSMessage(_, originRound), _, _), _) if originRound < round =>
        goto(state.copy(votes = state.votes :+ msg))
    }

    private def mostPopularBit = state.votes.map(v => (v.value, v.round)).toSet[(Boolean, Int)].groupMapReduce(_._1)(_ => 1)(_ + _).maxByOption(v => (v._2, v._1)).exists(_._1)

    override def afterRound(): Unit = {
      val curLeader = config.initValue match {
        case cfg: DLCSConfig => cfg.leaderOrder(round)
        case Boolean => round
      }
      if (curLeader == ctx.nodeId && !state.hasSent) {
        broadcast(DLCSMessage(mostPopularBit, round))
        goto(state.copy(hasSent = true))
      }
      if (round == config.numRounds) {
        output(mostPopularBit)
        terminate()
      }
    }
  }
}
