package endpoints.play.server

import endpoints.{Invalid, algebra}
import play.api.http.{ContentTypes, Writeable}

/**
  * @group interpreters
  */
trait BuiltInErrors extends algebra.BuiltInErrors { this: EndpointsWithCustomErrors =>

  def clientErrorsResponseEntity: ResponseEntity[Invalid] = invalid => {
    val playCodec = implicitly[play.api.mvc.Codec]
    Writeable(
      (invalid: Invalid) => playCodec.encode(endpoints.ujson.codecs.invalidCodec.encode(invalid)),
      Some(ContentTypes.JSON)
    ).toEntity(invalid)
  }

  def serverErrorResponseEntity: ResponseEntity[Throwable] =
    throwable => clientErrorsResponseEntity(Invalid(throwable.getMessage))

}
