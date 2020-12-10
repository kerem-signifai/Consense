package protocol.impl

import protocol.{NodeBehavior, Protocol}

import scala.language.postfixOps

case class OMReq(v: Boolean, m: Int, path: Seq[Int])
case class OMRep(v: Boolean, m: Int, path: Seq[Int])

case class OMExecution(target: Int, numSent: Int, receipts: Map[Int, Boolean], resultsExpectedRound: Int, m: Int)
case class OMState(execs: Map[Seq[Int], OMExecution], curValue: Boolean)

// Lamport-Shostak-Pease Oral Messages Algorithm
object OralMessages extends Protocol[OMState, Boolean] {
  override def initialState: OMState = OMState(Map.empty, curValue = false)
  override def behavior: BehaviorGen = ctx => new NodeBehavior(ctx) {

    override def initialize(): Unit = {
      val initValue = config.initValue.asInstanceOf[Boolean]
      val dests = 1 to config.numNodes filterNot(ctx.nodeId ==)
      val exec = OMExecution(-1, dests.size, Map(ctx.nodeId -> initValue), 2 * (config.numMaxCorruptNodes + 1), config.numMaxCorruptNodes)
      goto(state.copy(execs = state.execs.updated(Seq(ctx.nodeId), exec)))
      dests foreach { send(OMReq(initValue, config.numMaxCorruptNodes, Seq(ctx.nodeId)), _) }
    }

    override def afterRound(): Unit = {
      println()
      val expected = state.execs.filter(_._2.resultsExpectedRound == round)
      expected foreach { case (path, exec) =>
        val votes = exec.receipts.groupMap(_._2)(_._1)
        println(s"[R$round ${ctx.nodeId}] Processing $exec, replying to ${exec.target}")
        val value = votes.find(_._2.size > exec.numSent / 2).exists(_._1)

        if (exec.target != -1) {
          send(OMRep(value, exec.m, path.dropRight(1)), exec.target)
        }
        goto(state.copy(execs = state.execs - path))
        if (state.execs.isEmpty) {
          goto(state.copy(curValue = value))
        }
      }
      if (round == config.numRounds) {
        output(state.curValue)
        println(s"Outputting ${state.curValue}")
      }
    }

    override def receive: Receive = {
      case (a @ OMRep(v, m, path), sender) =>
        println(s"[R$round ${ctx.nodeId}] Received OMRep $a from $sender")
        val oldExec = state.execs(path)
        val exec = oldExec.copy(receipts = oldExec.receipts.updated(sender, v))
        goto(state.copy(execs = state.execs.updated(path, exec)))

      case (a @ OMReq(v, m, path), sender) =>
        println(s"[R$round ${ctx.nodeId}] Received OMReq $a from $sender")
        if (m == 0) {
          goto(state.copy(curValue = v))
          send(OMRep(v, m, path), sender)
        } else {
          val newPath = path :+ ctx.nodeId
          val dests = 1 to config.numNodes diff newPath
          val exec = OMExecution(sender, dests.size, Map(ctx.nodeId -> v), round + 2 * m, m - 1)
          goto(state.copy(execs = state.execs.updated(newPath, exec)))
          dests foreach { send(OMReq(v, m - 1, newPath), _) }
        }
    }
  }
}
