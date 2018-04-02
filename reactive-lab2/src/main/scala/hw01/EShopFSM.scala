package hw01

import akka.actor.{ActorRef, ActorSystem, LoggingFSM, Props}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn


object CartFSM {
  var checkoutActor: ActorRef = _


  sealed trait CartState

  case class CartData(itemCount: Int)

  case object Empty extends CartState
  case object NonEmpty extends CartState
  case object InCheckout extends CartState

  case object ItemAdded
  case object ItemRemoved
  case object CheckoutStarted
  case object CheckoutCancelled
  case object CartTimerExpired
  case object CheckoutClosed

}


class CartFSM extends LoggingFSM[CartFSM.CartState, CartFSM.CartData] {

  import hw01.CartFSM._


  startWith(Empty, CartData(0))

  when(Empty) {
    case Event(ItemAdded, CartData(itemCount)) =>
      goto(NonEmpty) using CartData(itemCount + 1)
  }

  when(NonEmpty) {
    case Event(ItemAdded, CartData(itemCount)) =>
      stay() using CartData(itemCount + 1)
    case Event(ItemRemoved, CartData(itemCount)) if itemCount > 1 =>
      stay() using CartData(itemCount - 1)
    case Event(ItemRemoved, CartData(itemCount)) if itemCount == 1 =>
      goto(Empty) using CartData(0)
    case Event(CheckoutStarted, _) =>
      goto(InCheckout)
    case Event(CartTimerExpired, _) =>
      goto(Empty) using CartData(0)
  }
  when(InCheckout) {
    case Event(CheckoutCancelled, _) =>
      goto(NonEmpty)
    case Event(CheckoutClosed, _) =>
      goto(Empty) using CartData(0)
  }

  onTransition {
    case Empty -> NonEmpty => setTimer("cartTimer", CartTimerExpired, 5 seconds)
    case InCheckout -> NonEmpty => setTimer("cartTimer", CartTimerExpired, 5 seconds)
    case NonEmpty -> Empty => cancelTimer("cartTimer")
    case NonEmpty -> InCheckout =>
      cancelTimer("cartTimer")
      checkoutActor = context.actorOf(Props[CheckoutFSM], "checkoutActor")
      checkoutActor ! CheckoutFSM.Init
  }

}

object CheckoutFSM {

  sealed trait CheckoutState

  case class CheckoutData()

  case object SelectingDelivery extends CheckoutState
  case object CheckoutCancelled extends CheckoutState
  case object SelectingPaymentMethod extends CheckoutState
  case object ProcessingPayment extends CheckoutState
  case object CheckoutClosed extends CheckoutState
  case object CheckoutInit extends CheckoutState

  case object Init
  case object Cancelled
  case object CheckoutTimerExpired
  case object PaymentTimerExpired
  case object DeliveryMethodSelected
  case object PaymentSelected
  case object PaymentReceived

}

class CheckoutFSM extends LoggingFSM[CheckoutFSM.CheckoutState, CheckoutFSM.CheckoutData] {
  import hw01.CheckoutFSM._

  startWith(CheckoutInit, CheckoutData())

  when(CheckoutInit) {
    case Event(Init, _) => goto(SelectingDelivery)
  }

  when(SelectingDelivery) {
    case Event(DeliveryMethodSelected, _) => goto(SelectingPaymentMethod)
    case Event(Cancelled, _) => goto(CheckoutCancelled)
    case Event(CheckoutTimerExpired, _) => goto(CheckoutCancelled)
  }

  when(SelectingPaymentMethod) {
    case Event(PaymentSelected, _) => goto(ProcessingPayment)
    case Event(Cancelled, _) => goto(CheckoutCancelled)
    case Event(CheckoutTimerExpired, _) => goto(CheckoutCancelled)
  }

  when(ProcessingPayment) {
    case Event(PaymentReceived, _) => goto(CheckoutClosed)
    case Event(Cancelled, _) => goto(CheckoutCancelled)
    case Event(PaymentTimerExpired, _) => goto(CheckoutCancelled)
  }

  when(CheckoutCancelled) {
    case _ => stay()
  }

  when(CheckoutClosed) {
    case _ => stay()
  }


  def close(): Unit = {
    context.parent ! CartFSM.CheckoutClosed
    context stop self
  }

  def cancel(): Unit = {
    context.parent ! CartFSM.CheckoutCancelled
    context stop self
  }

  onTransition {
    case CheckoutInit -> SelectingDelivery => setTimer("checkoutTimer", CheckoutTimerExpired, 5 seconds)
    case SelectingDelivery -> CheckoutCancelled =>
      cancel()
      cancelTimer("checkoutTimer")
    case SelectingPaymentMethod -> CheckoutCancelled =>
      cancel()
      cancelTimer("checkoutTimer")
    case SelectingPaymentMethod -> ProcessingPayment =>
      setTimer("paymentTimer", PaymentTimerExpired, 5 seconds)
      cancelTimer("checkoutTimer")
    case ProcessingPayment -> CheckoutCancelled =>
      cancel()
      cancelTimer("paymentTimer")
    case ProcessingPayment -> CheckoutClosed =>
      close()
      cancelTimer("paymentTimer")

  }

}


object EShopFSM extends App {
  val system = ActorSystem("eShop")
  val cartActor = system.actorOf(Props[CartFSM], "cartActor")

  cartActor ! CartFSM.ItemAdded
  cartActor ! CartFSM.ItemAdded
  cartActor ! CartFSM.ItemRemoved
  cartActor ! CartFSM.CheckoutStarted
  Thread.sleep(300)
  CartFSM.checkoutActor ! CheckoutFSM.DeliveryMethodSelected
  CartFSM.checkoutActor ! CheckoutFSM.PaymentSelected
  CartFSM.checkoutActor ! CheckoutFSM.PaymentReceived
  Thread.sleep(300)
  cartActor ! CartFSM.ItemAdded
  cartActor ! CartFSM.ItemAdded
  cartActor ! CartFSM.ItemAdded
  cartActor ! CartFSM.ItemAdded
  cartActor ! CartFSM.CheckoutStarted
  Thread.sleep(300)
  CartFSM.checkoutActor ! CheckoutFSM.DeliveryMethodSelected
  CartFSM.checkoutActor ! CheckoutFSM.PaymentSelected

  var done = false
  while (!done) {
    val input = StdIn.readLine()
    input match {
      case "a" => cartActor ! CartFSM.ItemAdded
      case "r" => cartActor ! CartFSM.ItemRemoved
      case "s" =>
        cartActor ! CartFSM.CheckoutStarted
      case "c" =>
        if (CartFSM.checkoutActor != null) CartFSM.checkoutActor ! CheckoutFSM.Cancelled
      case "p" =>
        if (CartFSM.checkoutActor != null) CartFSM.checkoutActor ! CheckoutFSM.PaymentSelected
      case "d" =>
        if (CartFSM.checkoutActor != null) CartFSM.checkoutActor ! CheckoutFSM.DeliveryMethodSelected
      case "v" =>
        if (CartFSM.checkoutActor != null) CartFSM.checkoutActor ! CheckoutFSM.PaymentReceived
      case "" =>
        done = true
        system.terminate()
    }

  }

  Await.result(system.whenTerminated, Duration.Inf)

}