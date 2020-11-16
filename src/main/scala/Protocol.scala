import scala.collection.mutable
import scala.util.Random

case class SimulationResult(
  honestSenderProposition: Boolean,
  honestAgree: Boolean
)

case class NodeRef(nodeId: Int, honest: Boolean)

class NodeDiscovery(nodes: Seq[NodeRef]) {
  val honestNodes: Seq[Int] = nodes.filter(_.honest).map(_.nodeId).sorted
  val corruptNodes: Seq[Int] = nodes.filter(!_.honest).map(_.nodeId).sorted

  def isHonest(id: Int): Boolean = nodes.find(_.nodeId == id).exists(_.honest)
  def randomNode: Int = Seq(randomCorruptNode, randomHonestNode)(Random.nextInt(1))
  def randomHonestNode: Int = honestNodes(Random.nextInt(honestNodes.size))
  def randomCorruptNode: Int = corruptNodes(Random.nextInt(corruptNodes.size))
}

case class SimulationContext(
  config: SimulatorConfig,
  nodeDiscovery: NodeDiscovery,
  var round: Int
)

class Simulator[State, Output](config: SimulatorConfig, protocol: Protocol[State, Output]) {
  private val honestNodeRefs = 1 to config.numHonestNodes map { i => NodeRef(i, honest = true) }
  private val corruptNodeRefs = 1 to config.corruptNodes.size map { i => NodeRef(i + config.numHonestNodes, honest = false) }
  private val nodeDiscovery = new NodeDiscovery(honestNodeRefs ++ corruptNodeRefs)

  private val ctx: SimulationContext = SimulationContext(config, nodeDiscovery, 0)

  private val honestBehaviors = nodeDiscovery.honestNodes map { i => i -> protocol.behavior(new NodeContext[State, Output](ctx, i, protocol.initialState)) }
  private val corruptBehaviors = nodeDiscovery.corruptNodes zip config.corruptNodes map { case (i, gen) => i -> gen(new NodeContext[Any, Any](ctx, i, null))}
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
    val initiator = config.isp match {
      case RandomInitializer => nodes(nodeDiscovery.randomNode)
      case RandomCorruptInitializer => nodes(nodeDiscovery.randomCorruptNode)
      case RandomHonestInitializer => nodes(nodeDiscovery.randomHonestNode)
    }
    initiator.init()
    enqueueMessages(initiator)

    1 to config.numRounds foreach { round =>
      ctx.round = round
      nodes.values foreach { node =>
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
    val senderProp = if (nodeDiscovery.isHonest(initiator.ctx.nodeId)) honestAgree else true

    println(s"If sender is honest, all honest nodes agree with sender: $senderProp")
    println(s"All honest nodes agree with each other: $honestAgree")
    SimulationResult(senderProp, honestAgree)
  }

}

class NodeContext[State, Output](
  final val simCtx: SimulationContext,
  final val nodeId: Int,
  final var state: State,
  final var output: Option[Output] = None,
  final var terminated: Boolean = false
) {
  // Messages that this node has left to process for the next round
  final val ingress: mutable.Queue[(Int, Any)] = mutable.Queue()

  // Messages that this node has produced as output for the current round
  final val egress: mutable.Queue[(Option[Int], Any)] = mutable.Queue()
}

abstract class NodeBehavior[State, Output](val ctx: NodeContext[State, Output]) {
  final type Receive = PartialFunction[(Any, Int), Unit]

  final val discovery: NodeDiscovery = ctx.simCtx.nodeDiscovery
  final val config: SimulatorConfig = ctx.simCtx.config

  final def round: Int = ctx.simCtx.round
  final def state: State = ctx.state
  final def goto(newState: State): Unit = ctx.state = newState

  final def broadcast(msg: Any): Unit = ctx.egress += (None -> msg)
  final def send(msg: Any, target: Int): Unit = ctx.egress += (Some(target) -> msg)

  final def terminate(): Unit = ctx.terminated = true
  final def output(result: Output): Unit = ctx.output = Some(result)

  def receive: Receive = _ => {}
  def init(): Unit = {}
  def beforeRound(): Unit = {}
  def afterRound(): Unit = {}
}

abstract class Protocol[State, Output] extends Network {
  final type BehaviorGen = NodeContext[State, Output] => NodeBehavior[State, Output]

  def initialState: State
  def behavior: BehaviorGen
}
