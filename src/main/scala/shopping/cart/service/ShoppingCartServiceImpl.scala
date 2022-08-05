package shopping.cart.service

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import org.slf4j.LoggerFactory
import shopping.cart.actor.ShoppingCartActor
import shopping.cart.proto
import io.grpc.Status

import scala.concurrent.{ExecutionContextExecutor, Future, TimeoutException}

class ShoppingCartServiceImpl(system: ActorSystem[_])
    extends proto.ShoppingCartService {

  implicit private val ex: ExecutionContextExecutor = system.executionContext

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val logger = LoggerFactory.getLogger(getClass)

  private val sharding = ClusterSharding(system)

  override def addItem(in: proto.AddItemRequest): Future[proto.Cart] = {
    logger.info(s"added item [${in.itemId}] to cart [${in.cartId}]")
    val entityRef =
      sharding.entityRefFor(ShoppingCartActor.EntityKey, in.cartId)
    val reply = entityRef.askWithStatus(
      ShoppingCartActor.Command.AddItem(in.itemId, in.quantity, _))
    convertError(reply.map(toProtoCart))
  }

  private def toProtoCart(
      summary: ShoppingCartActor.Reply.Summary): proto.Cart =
    proto.Cart(items = summary.items.iterator.map { case (itemId, quantity) =>
      proto.Item(itemId, quantity)
    }.toSeq)

  private def convertError[T](resultF: Future[T]): Future[T] =
    resultF.recoverWith {
      case _: TimeoutException => Future.failed(new GrpcServiceException(Status.UNAVAILABLE.withDescription("Operation timed out")))
      case error => Future.failed(new GrpcServiceException(Status.INVALID_ARGUMENT.withDescription(error.getMessage)))
    }

}
