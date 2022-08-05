package shopping.cart.actor

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SupervisorStrategy}
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import shopping.cart.CborSerializable
import scala.concurrent.duration._

object ShoppingCartActor {

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey[Command]("ShoppingCart")

  sealed trait Command extends CborSerializable
  object Command {
    final case class AddItem(itemId: String, quantity: Int, replyTo: ActorRef[StatusReply[Reply.Summary]]) extends Command
  }
  object Reply {
    final case class Summary(items: Map[String, Int]) extends CborSerializable
  }

  sealed trait Event extends CborSerializable {
    def cartId: String
  }
  object Event {
    final case class ItemAdded(cartId: String, itemId: String, quantity: Int) extends Event
  }

  final case class State(items: Map[String, Int]) extends CborSerializable {
    def hasItem(itemId: String): Boolean =
      items.contains(itemId)

    def isEmpty: Boolean =
      items.isEmpty

    def updateItem(itemId: String, quantity: Int): State = {
      quantity match {
        case 0 => copy(items = items - itemId)
        case _ => copy(items = items + (itemId -> quantity))
      }
    }
  }

  object State {
    val empty: State = State(items = Map.empty)
  }

  def apply(cartId: String): Behavior[Command] =
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      persistenceId = PersistenceId(EntityKey.name, cartId),
      emptyState = State.empty,
      commandHandler = (state, command) => handleCommand(cartId, state, command),
      eventHandler = (state, event) => handleEvent(state, event)
    )
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(
          minBackoff = 200.millis,
          maxBackoff = 5.seconds,
          randomFactor = 0.1)
      )


  def init(system: ActorSystem[_]): Unit = {
    ClusterSharding(system).init(Entity(EntityKey) { entityContext =>
      ShoppingCartActor(entityContext.entityId)
    })
  }

  private def handleCommand(cartId: String, state: State, command: Command): ReplyEffect[Event, State] =
    command match {
      case Command.AddItem(itemId, quantity, replyTo) =>
        if (state.hasItem(itemId))
          Effect.reply(replyTo)(
            StatusReply.Error(
              s"Item '$itemId' was already added to this shopping cart"))
        else if (quantity <= 0)
          Effect.reply(replyTo)(
            StatusReply.Error("Quantity must be greater than zero"))
        else
          Effect
            .persist(Event.ItemAdded(cartId, itemId, quantity))
            .thenReply(replyTo) { updatedCart =>
              StatusReply.Success(Reply.Summary(updatedCart.items))
            }
    }

  private def handleEvent(state: State, event: Event): State =
    event match {
      case Event.ItemAdded(_, itemId, quantity) => state.updateItem(itemId, quantity)
    }

}
