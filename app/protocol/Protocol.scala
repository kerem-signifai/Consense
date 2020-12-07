package protocol

import mpi.Network
import simulation._

import scala.collection.mutable

class NodeContext[State, Output](
  final val protConfig: ProtocolConfig,
  final val nodeId: Int,
  final var state: State,
  final var round: Int = 0,
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

  final val config: ProtocolConfig = ctx.protConfig

  final val numNodes: Int = config.numNodes
  final def round: Int = ctx.round
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
  final type Context = NodeContext[State, Output]
  final type Behavior = NodeBehavior[State, Output]
  final type BehaviorGen = Context => Behavior

  def senderValidityProp(initValue: Any, o1: Output): Boolean = initValue == o1
  def consistent(o1: Output, o2: Output): Boolean = o1 == o2
  def initialState: State
  def behavior: BehaviorGen
}
