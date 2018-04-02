package reactive2

import akka.actor._
import akka.event.LoggingReceive
import reactive2.Cart.{ItemAdded, ItemRemoved}

import scala.concurrent.Await
import scala.concurrent.duration._

////////////////////////////////////////
// More complex example: Bank account //
////////////////////////////////////////

// Good practice: defining messages as case classes in an Actor's companion object
object BankAccount {
  case class Deposit(amount: BigInt) {
    require(amount > 0)
  }
  case class Withdraw(amount: BigInt) {
    require(amount > 0)
  }
  case object Done
  case object Failed
  case object Init
}

class BankAccount extends Actor {
  import BankAccount._

  var balance = BigInt(0)

  def receive = LoggingReceive {
    case Deposit(amount) =>
      balance += amount
      sender ! Done
    case Withdraw(amount) if amount <= balance =>
      balance -= amount
      sender ! Done
    case _ => sender ! Failed
  }
}

// Wire transfers are handled by a separate actor

object WireTransfer {
  case class Transfer(from: ActorRef, to: ActorRef, amount: BigInt)
  case object Done
  case object Failed
}

class WireTransfer extends Actor {
  import WireTransfer._

  // 1st step: we await transfer requests
  def receive = LoggingReceive {
    case Transfer(from, to, amount) =>
      from ! BankAccount.Withdraw(amount)
      context become awaitWithdraw(to, amount, sender)
  }

  // 2nd step: we await withdraw acknowledgment
  // we need to know the target account of the transfer, the amount to be transferred, and
  // the original sender of the transfer request (to send the final acknowledgment)
  def awaitWithdraw(to: ActorRef, amount: BigInt, client: ActorRef): Receive = LoggingReceive {
    case BankAccount.Done =>
      to ! BankAccount.Deposit(amount)
      context become awaitDeposit(client)

    case BankAccount.Failed =>
      client ! Failed
      context.stop(self)
  }

  // 3rd step: we await the deposit acknowledgment and notify the sender of the original request 
  def awaitDeposit(customer: ActorRef): Receive = LoggingReceive {
    case BankAccount.Done =>
      customer ! Done
      context.stop(self)

  }
}

class Bank extends Actor {
  val account1 = context.actorOf(Props[BankAccount], "account1")
  val account2 = context.actorOf(Props[BankAccount], "account2")

  def receive = LoggingReceive {
    case BankAccount.Init => {
      account1 ! BankAccount.Deposit(100)
    }

    case BankAccount.Done => transfer(150)
  }

  def transfer(amount: BigInt): Unit = {
    val transaction = context.actorOf(Props[WireTransfer], "transfer")
    transaction ! WireTransfer.Transfer(account1, account2, amount)
    context.become(LoggingReceive {
      case WireTransfer.Done =>
        println("success")
        context.system.terminate
    })
  }
}

object Cart {
  case object Init
  case object ItemAdded
  case object ItemRemoved
}

class Cart extends Actor {
  import Cart._

  def receive = LoggingReceive {
    case Init =>
      context become empty
  }

  def empty: Receive = LoggingReceive {
    case ItemAdded =>
      print("lol")
    case ItemRemoved =>
      print("zulul")
  }
}

object BankApp extends App {
  val system = ActorSystem("eShop")
  val cartActor = system.actorOf(Props[Cart], "cartActor")

  cartActor ! Cart.Init
  cartActor ! Cart.ItemRemoved

  Await.result(system.whenTerminated, Duration.Inf)
}
