import scala.collection.mutable

sealed trait InitializerSelectionPolicy
case object RandomInitializer extends InitializerSelectionPolicy
case object RandomHonestInitializer extends InitializerSelectionPolicy
case object RandomCorruptInitializer extends InitializerSelectionPolicy

object SimulatorConfig {

  class Builder {
    var numRounds = 0
    var numHonestNodes = 0
    var numMaxCorruptNodes = 0
    var numCorruptNodes = 0
    var initValue: Any = _
    var isp: InitializerSelectionPolicy = _
    val corruptNodes: mutable.Buffer[NodeContext[Any, Any] => NodeBehavior[Any, Any]] = mutable.Buffer()

    def withHonestNodes(numHonestNodes: Int): Builder = {
      this.numHonestNodes += numHonestNodes
      this
    }

    def withRounds(numRounds: Int): Builder = {
      this.numRounds = numRounds
      this
    }

    def withMaxCorruptNodes(numMaxCorruptNodes: Int): Builder = {
      this.numMaxCorruptNodes = numMaxCorruptNodes
      this
    }

    def withInitPolicy(isp: InitializerSelectionPolicy): Builder = {
      this.isp = isp
      this
    }

    def addCorruptNode(behaviorGen: NodeContext[Any, Any] => NodeBehavior[Any, Any]): Builder = {
      corruptNodes += behaviorGen
      numCorruptNodes += 1
      numMaxCorruptNodes = math.max(numMaxCorruptNodes, numCorruptNodes)
      this
    }

    def withInitValue(initValue: Any): Builder = {
      this.initValue = initValue
      this
    }

    def build: SimulatorConfig = new SimulatorConfig(numRounds, numHonestNodes, numMaxCorruptNodes, corruptNodes.toSeq, initValue, isp)
  }

  def builder = new Builder()
}

case class SimulatorConfig(
  numRounds: Int,
  numHonestNodes: Int,
  numMaxCorruptNodes: Int,
  corruptNodes: Seq[NodeContext[Any, Any] => NodeBehavior[Any, Any]],
  initValue: Any,
  isp: InitializerSelectionPolicy
)