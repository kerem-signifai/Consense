package simulation

import protocol.{NodeBehavior, NodeContext}

import scala.collection.mutable

sealed trait InitializerSelectionPolicy
case class RandomInitializer(nodes: Int) extends InitializerSelectionPolicy
case class RandomHonestInitializer(nodes: Int) extends InitializerSelectionPolicy
case class RandomCorruptInitializer(nodes: Int) extends InitializerSelectionPolicy

object Config {

  class Builder {
    var numRounds = 0
    var numHonestNodes = 0
    var numMaxCorruptNodes = 0
    var numCorruptNodes = 0
    var initValue: Any = _
    var isp: InitializerSelectionPolicy = _
    val corruptNodes: mutable.Buffer[(AdversarialContext, NodeContext[Any, Any]) => NodeBehavior[Any, Any]] = mutable.Buffer()

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

    def addCorruptNode(behaviorGen: (AdversarialContext, NodeContext[Any, Any]) => NodeBehavior[Any, Any]): Builder = {
      corruptNodes += behaviorGen
      numCorruptNodes += 1
      numMaxCorruptNodes = math.max(numMaxCorruptNodes, numCorruptNodes)
      this
    }

    def withInitValue(initValue: Any): Builder = {
      this.initValue = initValue
      this
    }

    def build: (ProtocolConfig, SimulatorConfig) = (ProtocolConfig(numRounds, initValue), SimulatorConfig(numHonestNodes, numMaxCorruptNodes, corruptNodes.toSeq, isp))
  }

  def builder = new Builder()
}

case class ProtocolConfig(
  numRounds: Int,
  initValue: Any
)

case class SimulatorConfig(
  numHonestNodes: Int,
  numMaxCorruptNodes: Int,
  corruptNodes: Seq[(AdversarialContext, NodeContext[Any, Any]) => NodeBehavior[Any, Any]],
  isp: InitializerSelectionPolicy
)