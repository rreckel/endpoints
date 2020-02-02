package endpoints.akkahttp.client

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import endpoints.algebra
import endpoints.algebra.ChunkedJsonEntitiesTestApi

import scala.concurrent.{ExecutionContext, Future}

class TestClient(settings: EndpointsSettings)
  (implicit EC: ExecutionContext, M: Materializer)
  extends Endpoints(settings)
    with BasicAuthentication
    with algebra.EndpointsTestApi
    with algebra.BasicAuthenticationTestApi
    with algebra.JsonFromCodecTestApi
    with algebra.circe.JsonFromCirceCodecTestApi
    with JsonEntitiesFromCodecs
    with algebra.circe.JsonEntitiesFromCodecs
    with ChunkedJsonEntities
    with ChunkedJsonEntitiesTestApi
    with algebra.circe.ChunkedJsonEntitiesTestApi

class AkkaHttpClientEndpointsTest
  extends algebra.client.EndpointsTestSuite[TestClient]
    with algebra.client.BasicAuthTestSuite[TestClient]
    with algebra.client.JsonFromCodecTestSuite[TestClient]
    with algebra.client.JsonStreamingTestSuite[TestClient]
{

  implicit val system = ActorSystem()
  implicit val ec = system.dispatcher

  val client: TestClient = new TestClient(EndpointsSettings(AkkaHttpRequestExecutor.cachedHostConnectionPool("localhost", wiremockPort)))

  val streamingClient: TestClient = new TestClient(EndpointsSettings(AkkaHttpRequestExecutor.cachedHostConnectionPool("localhost", streamingPort)))

  def call[Req, Resp](endpoint: client.Endpoint[Req, Resp], args: Req): Future[Resp] = endpoint(args)

  def encodeUrl[A](url: client.Url[A])(a: A): String = url.encode(a)

  def callStreamedEndpoint[A, B](endpoint: streamingClient.Endpoint[A, streamingClient.Chunks[B]], req: A): Future[Seq[B]] =
    Source.futureSource(endpoint(req)).runWith(Sink.seq)

  def callStreamedEndpoint[A, B](endpoint: streamingClient.Endpoint[streamingClient.Chunks[A], B], req: Source[A, _]): Future[B] =
    endpoint(req)

  clientTestSuite()
  basicAuthSuite()
  jsonFromCodecTestSuite()

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

}
