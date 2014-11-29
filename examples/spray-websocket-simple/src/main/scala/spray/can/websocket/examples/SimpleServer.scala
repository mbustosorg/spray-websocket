package spray.can.websocket.examples

import akka.actor.{ ActorSystem, Actor, Props, ActorLogging, ActorRef, ActorRefFactory}
import akka.io.IO
import scala.io.StdIn.readLine
import spray.util._
import spray.http._
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket
import spray.can.websocket.frame.{ BinaryFrame, TextFrame }
import spray.http.HttpRequest
import spray.can.websocket.FrameCommandFailed
import spray.routing.HttpServiceActor

object SimpleServer extends App with MySslConfiguration {

  final case class Push(msg: String)

  object WebSocketServer {
    def props() = Props(classOf[WebSocketServer])
  }
  class WebSocketServer extends Actor with ActorLogging {
    def receive = {
      // when a new connection comes in we register a WebSocketConnection actor as the per connection handler
      case Http.Connected(remoteAddress, localAddress) =>
        println("Connected, Remote: " + remoteAddress + ", Local: " + localAddress)
        val serverConnection = sender()
        val conn = context.actorOf(WebSocketWorker.props(serverConnection))
        serverConnection ! Http.Register(conn)
    }
  }

  object WebSocketWorker {
    def props(serverConnection: ActorRef) = Props(classOf[WebSocketWorker], serverConnection)
  }
  class WebSocketWorker(val serverConnection: ActorRef) extends HttpServiceActor with websocket.WebSocketServerWorker {
    override def receive = handshaking orElse businessLogicNoUpgrade orElse closeLogic

    def businessLogic: Receive = {
      // just bounce frames back for Autobahn testsuite
      case x @ (_: BinaryFrame | _: TextFrame) =>
        println(x)
        sender() ! x

      case Push(msg) =>
        println(msg)
        send(TextFrame(msg))

      case x: FrameCommandFailed =>
        log.error("frame command failed", x)

      case x: HttpRequest => println("BL: " + x)// do something
    }

   def businessLogicNoUpgrade: Receive = {
     case HttpRequest(_, Uri.Path("/websocket.html"), _, _, _) =>
       implicit val refFactory: ActorRefFactory = context
       println("GET WEBSOCKET")
       getFromResourceDirectory("webapp")
     case x: HttpRequest => 
       println("Header: " + x.headers)
       println("Entity: " + x.entity)
       println("Method: " + x.method)
       optionalHeaderValueByName("Upgrade") { userId =>
         Thread.sleep(2000)
         complete(s"The user is $userId")
       }
     case x => println("Fallback: " + x)
    }
  }

  def doMain() {
    implicit val system = ActorSystem()
    import system.dispatcher
    import akka.util.Timeout
    import scala.concurrent.duration._
    import akka.pattern.{ ask, pipe }
    implicit val timeout = Timeout(DurationInt(5).seconds)
    
    val server = system.actorOf(WebSocketServer.props(), "websocket")

    //IO(UHttp) ? Http.Bind(server, "localhost", 8110)
    IO(UHttp) ? Http.Bind(server, "0.0.0.0", args(0).toInt)

    //readLine("Hit ENTER to exit ...\n")
    //system.shutdown()
    //system.awaitTermination()
  }

  // because otherwise we get an ambiguous implicit if doMain is inlined
  doMain()
}
