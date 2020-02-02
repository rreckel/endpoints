package endpoints.algebra.server

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, HttpResponse}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import endpoints.algebra.{Codec, ChunkedJsonEntitiesTestApi}

import scala.concurrent.Future
import scala.util.control.NonFatal

trait ChunkedJsonEntitiesTestSuite[T <: ChunkedJsonEntitiesTestApi] extends EndpointsTestSuite[T] {

  def serveStreamedEndpoint[Resp](
    endpoint: serverApi.Endpoint[_, serverApi.Chunks[Resp]],
    response: Source[Resp, _]
  )(
    runTests: Int => Unit
  ): Unit

  def serveStreamedEndpoint[Req, Resp](
    endpoint: serverApi.Endpoint[serverApi.Chunks[Req], Resp],
    logic: Source[Req, _] => Future[Resp]
  )(
    runTests: Int => Unit
  ): Unit

  def sendAndCollectResponseChunks(request: HttpRequest): Future[(HttpResponse, Seq[ByteString])] = {
    httpClient.singleRequest(request).flatMap { response =>
      response.entity.dataBytes.runWith(Sink.seq).map { chunks =>
        (response, chunks)
      }
    }
  }

  def sendAndDecodeJsonChunks[A](request: HttpRequest)(implicit codec: serverApi.JsonCodec[A]): Future[(HttpResponse, Seq[Either[Throwable, A]])] = {
    val jsonCodec: Codec[String, A] = serverApi.stringCodec(codec)
    httpClient.singleRequest(request).flatMap { response =>
      val chunksSource =
        response.entity.dataBytes
          .map(chunk => Right(jsonCodec.decode(decodeEntityAsText(response, chunk)).toEither.right.get))
          .recoverWithRetries(1, { case NonFatal(e) => Source.single(Left(e)) })
      chunksSource.runWith(Sink.seq).map { as =>
        (response, as)
      }
    }
  }

  "Http1Streaming" should {

    "stream response entities" in {
      val expectedItems =
        serverApi.Counter(1) :: serverApi.Counter(2) :: serverApi.Counter(3) :: Nil
      serveStreamedEndpoint(serverApi.streamedEndpointTest, Source(expectedItems)) { port =>
        val request = HttpRequest(uri = s"http://localhost:$port/notifications")
        whenReady(sendAndDecodeJsonChunks(request)(serverApi.counterCodec)) { case (response, chunks) =>
          assert(chunks.zip(expectedItems)
            .forall { case (received, expected) => received.toOption.contains(expected) })
          ()
        }
      }
    }

    "close connection in case of error" in {
      val source: Source[serverApi.Counter, _] = Source((1 to 3).map(serverApi.Counter)).flatMapConcat {
        case serverApi.Counter(3) => Source.failed(new Exception("Something went wrong"))
        case serverApi.Counter(n) => Source.single(serverApi.Counter(n))
      }
      serveStreamedEndpoint(serverApi.streamedEndpointTest, source) { port =>
        val request = HttpRequest(uri = s"http://localhost:$port/notifications")
        whenReady(sendAndDecodeJsonChunks(request)(serverApi.counterCodec)) { case (response, chunks) =>
          chunks.toList match {
            case Right(serverApi.Counter(1)) :: Right(serverApi.Counter(2)) :: Left(_) :: Nil => ()
            case chunks => fail(s"Unexpected chunks: $chunks")
          }
          ()
        }
      }
    }

    "stream request entities" in {
      val requestItems = Source(List(Array[Byte](1, 2, 3), Array[Byte](4, 5, 6)))
      serveStreamedEndpoint[Array[Byte], String](serverApi.uploadEndpointTest, source => source.runWith(Sink.seq).map(_.map(_.toList).mkString(";"))) { port =>
        val request = HttpRequest(
          method = HttpMethods.POST,
          uri = s"http://localhost:$port/upload",
          entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, requestItems.map(ByteString(_)))
        )
        whenReady(send(request)) { case (response, byteString) =>
          assert(byteString.utf8String == "List(1, 2, 3);List(4, 5, 6)")
          ()
        }
      }
    }

  }
}
