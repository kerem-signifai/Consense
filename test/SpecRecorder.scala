import java.nio.file.Paths

import codec.Codec
import model.SimulationTraceResponse
import simulation.SimulationResult

object SpecRecorder {
  def record(result: SimulationResult, name: String, desc: String): Unit = {
    val src = getClass.getResource("test_data.json")
    val existing = Codec.objectMapper.readValue[Seq[SimulationTraceResponse]](src)
    val updated = existing :+ SimulationTraceResponse(name, desc, System.currentTimeMillis, result.trace)
    Codec.objectMapper.writeValue(Paths.get(src.toURI).toFile, updated)
  }
}
