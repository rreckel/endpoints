package endpoints4s.http4s.client

import cats.implicits._
import endpoints4s.{Invalid, algebra}

trait BuiltInErrors extends algebra.BuiltInErrors {
  this: EndpointsWithCustomErrors =>

  def clientErrorsResponseEntity: ResponseEntity[Invalid] =
    _.as[String].flatMap(body =>
      endpoints4s.ujson.codecs.invalidCodec
        .decode(body)
        .fold(
          effect.pure,
          errors => effect.raiseError(new Exception(errors.mkString(". ")))
        )
    )

  def serverErrorResponseEntity: ResponseEntity[Throwable] =
    res =>
      clientErrorsResponseEntity(res).map(invalid => new Throwable(invalid.errors.mkString(", ")))

}
