package hw01

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}
import scala.io.StdIn

object Cart {

  var checkoutActor: ActorRef = _

  case object Init
  case object ItemAdded
  case object ItemRemoved
  case object CheckoutStarted
  case object CheckoutCancelled
  case object CartTimerExpired
  case object CheckoutClosed

}

class Cart extends Actor {

  import Cart._
  import akka.actor.Cancellable

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.duration._

  var itemCount = 0
  var cartTimer: Cancellable = _


  def receive = LoggingReceive {
    case Init =>
      context become empty
      println("cart: . -> empty, item count " + itemCount)
  }

  def empty: Receive = LoggingReceive {
    case ItemAdded =>
      itemCount += 1
      cartTimer = context.system.scheduler.scheduleOnce(5 seconds, self, CartTimerExpired)
      context become nonEmpty
      println("cart: empty -> nonempty, item count " + itemCount)
  }

  def nonEmpty: Receive = LoggingReceive {
    case ItemAdded =>
      itemCount += 1
      println("cart: item count " + itemCount)
    case ItemRemoved if itemCount > 1 =>
      itemCount -= 1
      println("cart: item count " + itemCount)
    case ItemRemoved if itemCount == 1 =>
      itemCount -= 1
      context become empty
      cartTimer.cancel()
      println("nonempty -> empty, cart: item count " + itemCount)
    case CheckoutStarted =>
      context become inCheckout
      cartTimer.cancel()
      checkoutActor = context.actorOf(Props[Checkout], "checkoutActor")
      checkoutActor ! Checkout.Init
      println("nonempty -> incheckout, cart: into checkout")
    case CartTimerExpired =>
      itemCount = 0
      context become empty
      println("nonempty -> empty, cart: cart timer expired")
  }

  def inCheckout: Receive = LoggingReceive {
    case CheckoutCancelled =>
      context become nonEmpty
      cartTimer = context.system.scheduler.scheduleOnce(5 seconds, self, CartTimerExpired)
      println("incheckout -> nonempty, cart: cancel checkout")
    case CheckoutClosed =>
      itemCount = 0
      context become empty
      println("incheckout -> empty, cart: exit checkout")
  }
}

object Checkout {

  case object Init
  case object Cancelled
  case object CheckoutTimerExpired
  case object PaymentTimerExpired
  case object DeliveryMethodSelected
  case object PaymentSelected
  case object PaymentReceived

}

class Checkout extends Actor {

  import Checkout._
  import akka.actor.Cancellable

  import scala.concurrent.ExecutionContext.Implicits.global

  var paymentTimer: Cancellable = _
  var checkoutTimer: Cancellable = _


  def receive = LoggingReceive {
    case Init =>
      context become selectingDelivery
      checkoutTimer = context.system.scheduler.scheduleOnce(5 seconds, self, CheckoutTimerExpired)
      println(". -> selectingdelivery, checkout: created checkout")
  }

  def selectingDelivery: Receive = LoggingReceive {
    case DeliveryMethodSelected =>
      println("selectingdelivery -> selectingpaymentmethod, checkout: delivery method selected")
      context become selectingPaymentMethod
    case Cancelled =>
      println("selectingdelivery -> ., checkout: cancel")
      checkoutTimer.cancel()
      cancel()
    case CheckoutTimerExpired =>
      println("selectingdelivery -> ., checkout: checkout timer expired")
      checkoutTimer.cancel()
      cancel()
  }

  def selectingPaymentMethod: Receive = LoggingReceive {
    case PaymentSelected =>
      println("selectingpaymentmethod -> processingpayment, checkout: payment is selected")
      checkoutTimer.cancel()
      paymentTimer = context.system.scheduler.scheduleOnce(5 seconds, self, PaymentTimerExpired)
      context become processingPayment
    case Cancelled =>
      println("selectingpaymentmethod -> ., checkout: cancel")
      checkoutTimer.cancel()
      cancel()
    case CheckoutTimerExpired =>
      println("selectingpaymentmethod -> ., checkout: checkout timer expired")
      checkoutTimer.cancel()
      cancel()


  }

  def processingPayment: Receive = LoggingReceive {
    case PaymentReceived =>
      println("processingpayment -> ., checkout: close")
      paymentTimer.cancel()
      close()
    case Cancelled =>
      println("processingpayment -> ., checkout: cancel")
      paymentTimer.cancel()
      cancel()
    case PaymentTimerExpired =>
      println("processingpayment -> ., checkout: payment timer expired")
      paymentTimer.cancel()
      cancel()
  }

  def close(): Unit = {
    context.parent ! Cart.CheckoutClosed
    context stop self
  }

  def cancel(): Unit = {
    context.parent ! Cart.CheckoutCancelled
    context stop self
  }


}

object EShop extends App {
  val system = ActorSystem("eShop")
  val cartActor = system.actorOf(Props[Cart], "cartActor")

  cartActor ! Cart.Init
  cartActor ! Cart.ItemAdded
  cartActor ! Cart.ItemAdded
  cartActor ! Cart.ItemRemoved
  cartActor ! Cart.CheckoutStarted
  Thread.sleep(300)
  Cart.checkoutActor ! Checkout.DeliveryMethodSelected
  Cart.checkoutActor ! Checkout.PaymentSelected
  Cart.checkoutActor ! Checkout.PaymentReceived
  Thread.sleep(300)
  cartActor ! Cart.ItemAdded
  cartActor ! Cart.ItemAdded
  cartActor ! Cart.ItemRemoved
  cartActor ! Cart.CheckoutStarted
  Thread.sleep(300)
  Cart.checkoutActor ! Checkout.DeliveryMethodSelected
  Cart.checkoutActor ! Checkout.PaymentSelected

  var done = false
  while (!done) {
    val input = StdIn.readLine()
    input match {
      case "a" => cartActor ! Cart.ItemAdded
      case "r" => cartActor ! Cart.ItemRemoved
      case "s" =>
        cartActor ! Cart.CheckoutStarted
      case "c" =>
        if (Cart.checkoutActor != null) Cart.checkoutActor ! Checkout.Cancelled
      case "p" =>
        if (Cart.checkoutActor != null) Cart.checkoutActor ! Checkout.PaymentSelected
      case "d" =>
        if (Cart.checkoutActor != null) Cart.checkoutActor ! Checkout.DeliveryMethodSelected
      case "v" =>
        if (Cart.checkoutActor != null) Cart.checkoutActor ! Checkout.PaymentReceived
      case "" =>
        done = true
        system.terminate()
    }

  }

  Await.result(system.whenTerminated, Duration.Inf)
}
