package model

import simulation.SimulationTrace

case class SimulationTraceResponse(
  name: String,
  description: String,
  createdAt: Long,
  trace: SimulationTrace
)
