package codec

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper

object Codec {
  val objectMapper = new JsonMapper with ScalaObjectMapper
  objectMapper.registerModule(new Jdk8Module)
  objectMapper.registerModule(DefaultScalaModule)
}
