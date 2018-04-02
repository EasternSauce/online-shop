package reactive2

////////////////////
// Actor context  //
////////////////////

/*
 *  trait ActorContext {
 *     def become(behavior: Receive, discardOld: Boolean = true): Unit
 *     def unbecome(): Unit
 *     def actorOf(p: Props, name: String): ActorRef    
 *     def stop(a: ActorRef): Unit 
 *     ...
 *  }
 *
 *  trait Actor {
 *     implicit val context: ActorContext
 *     ...
 *  }
 * 
 */

import akka.actor._
import akka.actor.Props
import akka.event.LoggingReceive
import scala.concurrent.Await
import scala.concurrent.duration._

class Toggle extends Actor {

  def happy: Receive = LoggingReceive {
    case "How are you?" =>
      sender ! "happy"
      context become sad

    case "Done" =>
      sender ! "Done"
      context.stop(self)
  }

  def sad: Receive = LoggingReceive {
    case "How are you?" =>
      sender ! "sad"
      context become happy

    case "Done" =>
      sender ! "Done"
      context.stop(self)
  }
  def receive = happy
}

class ToggleMain extends Actor {

  val toggle = context.actorOf(Props[Toggle], "toggle")

  def receive = LoggingReceive {
    case "Init" =>
      toggle ! "How are you?"
      toggle ! "How are you?"
      toggle ! "How are you?"
      toggle ! "Done"

    case "Done" =>
      context.system.terminate

    case msg: String =>
      println(s" received: $msg")

  }
}

object ToggleApp extends App {
  val system = ActorSystem("Reactive2")
  val mainActor = system.actorOf(Props[ToggleMain], "mainActor")

  mainActor ! "Init"

  Await.result(system.whenTerminated, Duration.Inf)
}
