package endpoints.play.client

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import endpoints.algebra.client
import endpoints.algebra
import endpoints.algebra.circe
import play.api.libs.ws.WSClient
import play.api.test.WsTestClient

import scala.concurrent.{ExecutionContext, Future}


class TestClient(address: String, wsClient: WSClient)
  (implicit EC: ExecutionContext)
  extends Endpoints(address, wsClient)
    with BasicAuthentication
    with JsonEntitiesFromCodecs
    with ChunkedJsonEntities
    with algebra.BasicAuthenticationTestApi
    with algebra.EndpointsTestApi
    with algebra.JsonFromCodecTestApi
    with algebra.ChunkedJsonEntitiesTestApi
    with circe.JsonFromCirceCodecTestApi
    with circe.JsonEntitiesFromCodecs
    with circe.ChunkedJsonEntitiesTestApi

class EndpointsTest
  extends client.EndpointsTestSuite[TestClient]
    with client.BasicAuthTestSuite[TestClient]
    with client.JsonFromCodecTestSuite[TestClient]
    with client.JsonStreamingTestSuite[TestClient]
{

  import ExecutionContext.Implicits.global
  implicit val system = ActorSystem()

  val wsClient = new WsTestClient.InternalWSClient("http", wiremockPort)
  val client: TestClient = new TestClient(s"http://localhost:$wiremockPort", wsClient)
  val wsStreamingClient = new WsTestClient.InternalWSClient("http", streamingPort)
  val streamingClient: TestClient = new TestClient(s"http://localhost:$streamingPort", wsStreamingClient)

  def call[Req, Resp](endpoint: client.Endpoint[Req, Resp], args: Req): Future[Resp] = endpoint(args)

  def callStreamedEndpoint[A, B](endpoint: streamingClient.Endpoint[A, streamingClient.Chunks[B]], req: A): Future[Seq[B]] =
    Source.futureSource(endpoint(req)).runWith(Sink.seq)

  def callStreamedEndpoint[A, B](endpoint: streamingClient.Endpoint[streamingClient.Chunks[A], B], req: Source[A, _]): Future[B] =
    endpoint(req)

  def encodeUrl[A](url: client.Url[A])(a: A): String = url.encode(a)

  clientTestSuite()
  basicAuthSuite()
  jsonFromCodecTestSuite()

  override def afterAll(): Unit = {
    wsClient.close()
    wsStreamingClient.close()
    Thread.sleep(6000) // Unfortunate hack to let WSTestClient terminate its ActorSystem. See https://github.com/playframework/playframework/blob/8b0d5afb8c353dd8cd8d9e8057136e1858ad0173/transport/client/play-ahc-ws/src/main/scala/play/api/test/WSTestClient.scala#L142
    super.afterAll()
  }

}
