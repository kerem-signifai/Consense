import akka.util.ByteString
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import play.api.http.{ContentTypes, Writeable}
import play.api.mvc.{BodyParser, InjectedController}

import scala.concurrent.ExecutionContext

package object controllers {

  trait PlayJacksonController extends InjectedController with PlayJacksonSerializer {

    implicit def json[A](implicit m: Manifest[A], ec: ExecutionContext): BodyParser[A] = parse.tolerantText.validate(str => {
      try {
        Right(objectMapper.readValue[A](str))
      } catch {
        case ex: Throwable => Left(BadRequest(ex.getMessage))
      }
    })
  }

  trait PlayJacksonSerializer {
    val objectMapper: ObjectMapper with ScalaObjectMapper

    implicit def toWriteable[A]: Writeable[A] = Writeable[A](
      (obj: A) => ByteString(objectMapper.writeValueAsBytes(obj)), Some(ContentTypes.JSON)
    )
  }
}
