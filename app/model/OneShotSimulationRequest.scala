package model

case class OneShotSimulationRequest(
  name: String,
  desc: String,
  protocol: String,
  initValue: Boolean,
  numRounds: Int,
  numNodes: Int,
  maxNumFailures: Int,
  numFailures: Int,
  honestLeader: Boolean
)
