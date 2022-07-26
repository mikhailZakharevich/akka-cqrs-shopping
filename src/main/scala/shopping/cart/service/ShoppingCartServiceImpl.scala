package shopping.cart.service

import org.slf4j.LoggerFactory
import shopping.cart.proto.{AddItemRequest, Cart, Item, ShoppingCartService}

import scala.concurrent.Future

class ShoppingCartServiceImpl extends ShoppingCartService {

  private val logger = LoggerFactory.getLogger(getClass)

  override def addItem(in: AddItemRequest): Future[Cart] = {
    logger.info(s"added item [${in.itemId}] to cart [${in.cartId}]")
    Future.successful(Cart(items = List(Item(in.itemId, in.quantity))))
  }
}
