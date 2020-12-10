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
  def initialize(): Unit = {}
  def beforeRound(): Unit = {}
  def afterRound(): Unit = {}
}

abstract class BehaviorBuilder[State, Output](ctx: NodeContext[State, Output]) extends NodeBehavior[State, Output](ctx) {
  private val beforeActions = mutable.Map[Int, mutable.Buffer[Int => ()]]()
  private val afterActions = mutable.Map[Int, mutable.Buffer[Int => ()]]()
  private val initActions = mutable.Buffer[Unit => ()]()
  private val messageHandlers = mutable.Buffer[Receive]()

  class OperationBuilder(target: mutable.Map[Int, mutable.Buffer[Int => ()]], round: Int) {
    def perform(op: Int => Unit): Unit = {
      target.getOrElseUpdate(round, mutable.Buffer()) += op
    }
  }

  class RoundBuilder(target: mutable.Map[Int, mutable.Buffer[Int => ()]]) {
    def round(num: Int): OperationBuilder = new OperationBuilder(target, num)
  }
  def before: RoundBuilder = new RoundBuilder(beforeActions)
  def after: RoundBuilder = new RoundBuilder(afterActions)
  def recv(receive: Receive): Unit = messageHandlers += receive
  def init(fx: Unit => ()): Unit = initActions += fx

  override def beforeRound(): Unit = beforeActions.getOrElse(round, Seq.empty).foreach(_(round))
  override def afterRound(): Unit = afterActions.getOrElse(round, Seq.empty).foreach(_(round))
  override def initialize(): Unit = initActions.foreach(_(()))
  override def receive: Receive = { case (msg, sender) =>
    messageHandlers.filter(_.isDefinedAt(msg, sender)).foreach(_(msg, sender))
  }
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
