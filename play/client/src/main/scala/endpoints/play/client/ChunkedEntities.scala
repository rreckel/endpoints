package endpoints.play.client

import akka.stream.scaladsl.Source
import akka.util.ByteString
import endpoints.algebra
import play.api.http.ContentTypes
import play.api.libs.ws.{BodyWritable, SourceBody}

/**
  * Interpreter for the [[algebra.ChunkedEntities]] algebra in the [[endpoints.play.client]] family.
  *
  * @group interpreters
  */
trait ChunkedEntities extends algebra.ChunkedEntities with EndpointsWithCustomErrors {

  type Chunks[A] = Source[A, _]

  def textChunksRequest: RequestEntity[Chunks[String]] =
    chunkedRequestEntity(ContentTypes.TEXT, ByteString.fromString)

  def textChunksResponse: ResponseEntity[Chunks[String]] =
    chunkedResponseEntity(byteString => Right(byteString.utf8String))

  def bytesChunksRequest: RequestEntity[Chunks[Array[Byte]]] =
    chunkedRequestEntity(ContentTypes.BINARY, ByteString(_))

  def bytesChunksResponse: ResponseEntity[Chunks[Array[Byte]]] =
    chunkedResponseEntity(byteString => Right(byteString.toArray))

  private[client] def chunkedRequestEntity[A](contentType: String, toByteString: A => ByteString): RequestEntity[Chunks[A]] =
    (as, httpRequest) =>
      httpRequest.withBody(as.map(toByteString))(BodyWritable(SourceBody, contentType))

  private[client] def chunkedResponseEntity[A](fromByteString: ByteString => Either[Throwable, A]): ResponseEntity[Chunks[A]] =
    httpResponse => Right(httpResponse.bodyAsSource.map(fromByteString).flatMapConcat {
      case Left(error)  => Source.failed(error)
      case Right(value) => Source.single(value)
    })

}

/**
  * Interpreter for the [[algebra.ChunkedJsonEntities]] algebra in the [[endpoints.play.client]] family.
  *
  * @group interpreters
  */
trait ChunkedJsonEntities extends algebra.ChunkedJsonEntities with ChunkedEntities with JsonEntitiesFromCodecs {

  def jsonChunksRequest[A](implicit codec: JsonCodec[A]): RequestEntity[Chunks[A]] = {
    val encoder = stringCodec(codec)
    chunkedRequestEntity(ContentTypes.JSON, a => ByteString.fromString(encoder.encode(a)))
  }

  def jsonChunksResponse[A](implicit codec: JsonCodec[A]): ResponseEntity[Chunks[A]] = {
    val decoder = stringCodec(codec)
    chunkedResponseEntity { byteString =>
      val string = byteString.utf8String
      decoder.decode(string).toEither.left.map(errors => new Throwable(errors.mkString(". ")))
    }
  }

}
