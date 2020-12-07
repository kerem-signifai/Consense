package controllers

import codec.Codec
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import javax.inject._
import model.SimulationTraceResponse
import play.api.{Environment, Play}
import play.api.mvc._
import simulation.Simulation

import scala.collection.mutable

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

	def simulate(): Action[AnyContent] = Action {
		new Simulation(null, null).start()
		Ok("")
	}
}
