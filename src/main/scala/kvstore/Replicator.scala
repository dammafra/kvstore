package kvstore

import akka.actor.{Actor, ActorRef, Cancellable, Props}

import scala.concurrent.duration._

object Replicator {

  case class Replicate(key: String, valueOption: Option[String], id: Long)

  case class Replicated(key: String, id: Long)

  case class Snapshot(key: String, valueOption: Option[String], seq: Long)

  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {

  import Replicator._
  import context.dispatcher

  var seqCounter: Long = 0L
  var pendingReplications: Map[Long, (Long, Cancellable, ActorRef)] = Map.empty

  def receive: Receive = {
    case Replicate(key, valueOption, id) =>
      replica ! Snapshot(key, valueOption, seqCounter)
      val cancellable = context.system.scheduler.schedule(200.milliseconds, 100.milliseconds, replica, Snapshot(key, valueOption, seqCounter))
      val tuple = (id, cancellable, sender)
      pendingReplications = pendingReplications + (seqCounter -> tuple)
      seqCounter = seqCounter + 1

    case SnapshotAck(key, seq) =>
      pendingReplications.get(seq) match {
        case Some((id, cancellable, requester)) =>
          cancellable.cancel()
          requester ! Replicated(key, id)
        case None =>
      }
  }
}
