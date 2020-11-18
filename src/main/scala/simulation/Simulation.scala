package simulation

import protocol._

import scala.util.Random

case class SimulationResult(
  honestSenderProposition: Boolean,
  honestAgree: Boolean
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

  def enqueueMessages(node: NodeBehavior[_, _]): Unit = {
    node.ctx.egress.removeAll() foreach { case (dest, msg) =>
      val payload = protocol.mpi.write(msg, node.ctx.nodeId)
      dest match {
        case Some(target) => nodes.get(target) match {
          case Some(targetNode) =>
            targetNode.ctx.ingress += (node.ctx.nodeId -> payload)
          case _ =>
        }
        case _ => nodes.filter(_._1 != node.ctx.nodeId).values.foreach(_.ctx.ingress += (node.ctx.nodeId -> payload))
      }
    }
  }

  def start(): SimulationResult = {
    val initiators = simCfg.isp match {
      case RandomInitializer(num) => nodeDiscovery.randomNodes(num).map(nodes)
      case RandomCorruptInitializer(num) => nodeDiscovery.randomCorruptNodes(num).map(nodes)
      case RandomHonestInitializer(num) => nodeDiscovery.randomHonestNodes(num).map(nodes)
    }
    initiators foreach { init =>
      init.init()
      enqueueMessages(init)
    }

    1 to protConfig.numRounds foreach { round =>
      nodes.values foreach { node =>
        node.ctx.round = round
        val ingress = node.ctx.ingress
        node.beforeRound()
        ingress.removeAll() foreach { case (sender, msg) =>
          node.receive(protocol.mpi.read(msg, sender), sender)
        }
        node.afterRound()
      }
      nodes.values foreach enqueueMessages
    }

    val honestNodes = nodeDiscovery.honestNodes.map(nodes)
    val honestOutput = honestNodes.forall(_.ctx.output.isDefined)
    val honestAgree = honestOutput && honestNodes.map(_.ctx.output).distinct.size == 1
    val senderProp = if (initiators.forall(n => nodeDiscovery.isHonest(n.ctx.nodeId))) honestAgree else true

    println(s"If sender is honest, all honest nodes agree with sender: $senderProp")
    println(s"All honest nodes agree with each other: $honestAgree")
    SimulationResult(senderProp, honestAgree)
  }

}