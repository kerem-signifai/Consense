package controllers

import codec.Codec
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import javax.inject._
import model.{OneShotSimulationRequest, SimulationTraceResponse}
import play.api.Environment
import play.api.mvc._
import protocol.{NodeBehavior, Protocol}
import protocol.impl.{DLCSConsensus, DolevStrong, OralMessages, PBFT, StrongDLCSConsensus}
import simulation.{Config, RandomCorruptInitializer, RandomHonestInitializer, Simulation}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

@Singleton
class FrontendController @Inject()(
  assets: Assets,
  env: Environment
) extends PlayJacksonController {
  override val objectMapper: JsonMapper with ScalaObjectMapper = Codec.objectMapper

  private val simulations = mutable.Buffer[SimulationTraceResponse]()
  private val src = env.resource("test_data.json").get
  private val existing = objectMapper.readValue[Seq[SimulationTraceResponse]](src)
  simulations ++= existing

  def index: Action[AnyContent] = assets.at("index.html")
  def assetOrDefault(resource: String): Action[AnyContent] = assets.at(resource)

  def listSimulations(): Action[AnyContent] = Action {
    synchronized {
      Ok(simulations)
    }
  }

  def simulate(): Action[OneShotSimulationRequest] = Action(json[OneShotSimulationRequest]) { req =>
    val body = req.body
    val config = Config.builder
      .withInitValue(body.initValue)
      .withMaxCorruptNodes(body.maxNumFailures)
      .withHonestNodes(body.numNodes - body.numFailures)
      .withRounds(body.numRounds)
      .withInitPolicy(if (body.honestLeader) RandomHonestInitializer(1) else RandomCorruptInitializer(1))
    1 to body.numFailures foreach { _ =>
      config.addCorruptNode { (_, ctx) => new NodeBehavior(ctx) {} }
    }
    val prot: Protocol[_, _] = body.protocol match {
      case "DolevStrong" => DolevStrong
      case "DLCSConsensus" => DLCSConsensus
      case "StrongDLCSConsensus" => StrongDLCSConsensus
      case "OralMessages" => OralMessages
      case "PBFT" => PBFT
      case s => throw new IllegalArgumentException(s"Unknown protocol $s")
    }
    val result = new Simulation(config.build, prot).start()
    val resp = SimulationTraceResponse(body.name, body.desc, System.currentTimeMillis(), result.trace)
    simulations += resp
    Ok(resp)
  }
}
