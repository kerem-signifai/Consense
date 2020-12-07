package simulation

import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import protocol._

import scala.collection.mutable
import scala.util.Random

case class SimulationResult(
  honestSenderProposition: Boolean,
  honestAgree: Boolean,
  trace: SimulationTrace
)

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "action"
)
@JsonSubTypes(Array(
  new Type(value = classOf[MessageSentAction], name = "message_sent"),
  new Type(value = classOf[StateChangedAction], name = "state_changed"),
  new Type(value = classOf[TerminateAction], name = "terminated"),
  new Type(value = classOf[OutputAction], name = "output")
))
sealed trait SimulationAction
case class MessageSentAction(from: Int, to: Int, payload: Any) extends SimulationAction
case class StateChangedAction(node: Int, newState: Any) extends SimulationAction
case class TerminateAction(node: Int) extends SimulationAction
case class OutputAction(node: Int, output: Any) extends SimulationAction

case class RoundTrace(
  before: Seq[SimulationAction],
  during: Seq[SimulationAction],
  after: Seq[SimulationAction]
)

case class SimulationTrace(
  numNodes: Int,
  corruptNodes: Seq[Int],
  actions: Map[Int, RoundTrace]
)

case class NodeRef(nodeId: Int, honest: Boolean)

case class NodeDiscovery(nodes: Seq[NodeRef]) {
  val honestNodes: Seq[Int] = nodes.filter(_.honest).map(_.nodeId).sorted
  val corruptNodes: Seq[Int] = nodes.filter(!_.honest).map(_.nodeId).sorted

  def isHonest(id: Int): Boolean = nodes.find(_.nodeId == id).exists(_.honest)

  def randomNodes(num: Int): Seq[Int] = Random.shuffle(honestNodes ++ corruptNodes).take(num)
  def randomHonestNodes(num: Int): Seq[Int] = Random.shuffle(honestNodes).take(num)
  def randomCorruptNodes(num: Int): Seq[Int] = Random.shuffle(corruptNodes).take(num)
}

case class AdversarialContext(
  discovery: NodeDiscovery
)

class Simulation[State, Output](config: (ProtocolConfig, SimulatorConfig), protocol: Protocol[State, Output]) {
  val (protConfig, simCfg) = config

  private val honestNodeRefs = 1 to simCfg.numHonestNodes map { i => NodeRef(i, honest = true) }
  private val corruptNodeRefs = 1 to simCfg.corruptNodes.size map { i => NodeRef(i + simCfg.numHonestNodes, honest = false) }
  private val nodeDiscovery = NodeDiscovery(honestNodeRefs ++ corruptNodeRefs)

  private val advCtx = AdversarialContext(nodeDiscovery)

  private val honestBehaviors = nodeDiscovery.honestNodes map { i => i -> protocol.behavior(new NodeContext[State, Output](protConfig, i, protocol.initialState)) }
  private val corruptBehaviors = nodeDiscovery.corruptNodes zip simCfg.corruptNodes map { case (i, gen) => i -> gen(advCtx, new NodeContext[Any, Any](protConfig, i, null))}
  private val nodes = (honestBehaviors ++ corruptBehaviors).toMap

  def enqueueMessages(node: NodeBehavior[_, _]): Seq[MessageSentAction] = {
    node.ctx.egress.removeAll() flatMap { case (dest, msg) =>
      val payload = protocol.mpi.write(msg, node.ctx.nodeId)
      dest match {
        case Some(target) => nodes.get(target) match {
          case Some(targetNode) =>
            targetNode.ctx.ingress += (node.ctx.nodeId -> payload)
            Some(MessageSentAction(node.ctx.nodeId, target, payload))
          case _ => None
        }
        case _ => nodes.filter(_._1 != node.ctx.nodeId).values map { n =>
          n.ctx.ingress += (node.ctx.nodeId -> payload)
          MessageSentAction(node.ctx.nodeId, n.ctx.nodeId, payload)
        }
      }
    }
  }

  def start(): SimulationResult = {
    val initiators = simCfg.isp match {
      case DeterministicInitializer(ids) => ids.map(nodes)
      case RandomInitializer(num) => nodeDiscovery.randomNodes(num).map(nodes)
      case RandomCorruptInitializer(num) => nodeDiscovery.randomCorruptNodes(num).map(nodes)
      case RandomHonestInitializer(num) => nodeDiscovery.randomHonestNodes(num).map(nodes)
    }

    val trace = mutable.Map[Int, RoundTrace]()
    val before = mutable.Buffer[SimulationAction]()
    val during = mutable.Buffer[SimulationAction]()
    val after = mutable.Buffer[SimulationAction]()

    initiators foreach { init =>
      val hasTerminated = init.ctx.terminated
      val hasOutput = init.ctx.output.isDefined
      val oldState = init.ctx.state
      init.init()
      before ++= enqueueMessages(init)
      if (init.ctx.state != oldState) {
        after += StateChangedAction(init.ctx.nodeId, init.ctx.state)
      }
      if (!hasTerminated && init.ctx.terminated) {
        before += TerminateAction(init.ctx.nodeId)
      }
      if (!hasOutput && init.ctx.output.isDefined) {
        before += OutputAction(init.ctx.nodeId, init.ctx.output.get)
      }
    }

    1 to protConfig.numRounds foreach { round =>
      nodes.values foreach { node =>
        val hasTerminated = node.ctx.terminated
        val hasOutput = node.ctx.output.isDefined
        val oldState = node.ctx.state
        node.ctx.round = round
        node.beforeRound()
        if (node.ctx.state != oldState || round == 1) {
          before += StateChangedAction(node.ctx.nodeId, node.ctx.state)
        }
        if (!hasTerminated && node.ctx.terminated) {
          before += TerminateAction(node.ctx.nodeId)
        }
        if (!hasOutput && node.ctx.output.isDefined) {
          before += OutputAction(node.ctx.nodeId, node.ctx.output.get)
        }
        before ++= enqueueMessages(node)
      }

      val hasTerminated = collection.mutable.Map[Int, Boolean]()
      val hasOutput = collection.mutable.Map[Int, Boolean]()
      val oldState = collection.mutable.Map[Int, Any]()

      nodes.values foreach { node =>
        val ingress = node.ctx.ingress
        hasTerminated(node.ctx.nodeId) = node.ctx.terminated
        hasOutput(node.ctx.nodeId) = node.ctx.output.isDefined
        oldState(node.ctx.nodeId) = node.ctx.state
        ingress.removeAll() foreach { case (sender, msg) =>
          node.receive(protocol.mpi.read(msg, sender), sender)
        }
      }

      nodes.values foreach { node =>
        during ++= enqueueMessages(node)
        node.afterRound()
        after ++= enqueueMessages(node)
        if (node.ctx.state != oldState(node.ctx.nodeId)) {
          after += StateChangedAction(node.ctx.nodeId, node.ctx.state)
        }
        if (!hasTerminated(node.ctx.nodeId) && node.ctx.terminated) {
          after += TerminateAction(node.ctx.nodeId)
        }
        if (!hasOutput(node.ctx.nodeId) && node.ctx.output.isDefined) {
          after += OutputAction(node.ctx.nodeId, node.ctx.output.get)
        }
      }
      val roundTrace = RoundTrace(before.toSeq, during.toSeq, after.toSeq)
      before.clear()
      during.clear()
      after.clear()
      trace += round -> roundTrace
    }

    val honestNodes = honestBehaviors.map(_._2)
    val honestOutput = honestNodes.forall(_.ctx.output.isDefined)
    val honestAgree = honestOutput && honestNodes.map(_.ctx.output.get).combinations(2).forall(c => protocol.consistent(c.head, c.last))
    val senderProp = if (initiators.forall(n => nodeDiscovery.isHonest(n.ctx.nodeId))) honestAgree && honestNodes.forall(n => protocol.senderValidityProp(config._1.initValue, n.ctx.output.get)) else true

    println(s"If sender is honest, all honest nodes agree with sender's input: $senderProp")
    println(s"All honest nodes agree with each other: $honestAgree")
    SimulationResult(senderProp, honestAgree, SimulationTrace(nodes.size, corruptNodeRefs.map(_.nodeId), trace.toMap))
  }

}