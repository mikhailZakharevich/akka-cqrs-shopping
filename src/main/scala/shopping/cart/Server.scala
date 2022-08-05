package shopping.cart

import akka.actor.typed.ActorSystem
import akka.grpc.scaladsl.{ServerReflection, ServiceHandler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object Server {

  def start(interface: String, port: Int)
           (grpcService: proto.ShoppingCartService)
           (implicit system: ActorSystem[_]): Unit = {
    implicit val ex: ExecutionContextExecutor = system.executionContext

    val service: HttpRequest => Future[HttpResponse] =
      ServiceHandler.concatOrNotFound(
        proto.ShoppingCartServiceHandler.partial(grpcService),
        ServerReflection.partial(List(proto.ShoppingCartService))
      )

    val bound = Http().newServerAt(interface, port).bind(service).map(_.addToCoordinatedShutdown(3.seconds))
    bound.onComplete {
      case Success(binding) =>
        val address = binding.localAddress
        system.log.info(
          "Shopping online at gRPC server {}:{}",
          address.getHostString,
          address.getPort)
      case Failure(ex) =>
        system.log.error("Failed to bind gRPC endpoint, terminating system", ex)
        system.terminate()
    }
  }

}
