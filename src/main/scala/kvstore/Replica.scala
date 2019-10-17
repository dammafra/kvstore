package kvstore

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorRef, Cancellable, OneForOneStrategy, Props, ReceiveTimeout}
import kvstore.Arbiter._

import scala.concurrent.duration._

object Replica {

  sealed trait Operation {
    def key: String

    def id: Long
  }

  case class Insert(key: String, value: String, id: Long) extends Operation

  case class Remove(key: String, id: Long) extends Operation

  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply

  case class OperationAck(id: Long) extends OperationReply

  case class OperationFailed(id: Long) extends OperationReply

  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {

  import Persistence._
  import Replica._
  import Replicator._
  import context.dispatcher

  arbiter ! Join
  context.setReceiveTimeout(1.second)

  var kv: Map[String, String] = Map.empty
  var persistence: ActorRef = context.actorOf(persistenceProps)
  var replicasToReplicators: Map[ActorRef, ActorRef] = Map.empty
  var pendingRequests: Map[Long, (Set[ActorRef], ActorRef, Any)] = Map.empty
  var cancellable: Cancellable = Cancellable.alreadyCancelled
  var expectedSeq: Long = 0L

  override val supervisorStrategy: OneForOneStrategy = OneForOneStrategy() {
    case _: PersistenceException => Restart
  }

  def receive: Receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  // COMMON
  def receivingGet: Receive = {
    case Get(key, id) =>
      sender ! GetResult(key, kv.get(key), id)
  }

  def persisting: Receive = receivingGet orElse {
    case Persisted(_, id) =>
      cancellable.cancel()
      resolvePending(id)
  }

  // PRIMARY
  def leader: Receive =
    receivingGet orElse
      persisting orElse
      replicating orElse
      receivingReplicas orElse
      receivingTimeout orElse {

      case Insert(key, value, id) =>
        kv = kv + (key -> value)
        val tuple = (replicasToReplicators.values.toSet, sender, OperationAck(id))
        pendingRequests = pendingRequests + (id -> tuple)
        persist(key, Some(value), id)
        replicate(key, Some(value), id)

      case Remove(key, id) =>
        kv = kv - key
        val tuple = (replicasToReplicators.values.toSet, sender, OperationAck(id))
        pendingRequests = pendingRequests + (id -> tuple)
        persist(key, None, id)
        replicate(key, None, id)
    }

  def replicating: Receive = {
    case Replicated(_, id) =>
      updatePending(sender)
      resolvePending(id)
  }

  def receivingReplicas: Receive = {
    case Replicas(replicas) =>
      val secondaries = replicas - self

      replicasToReplicators.keys.toSet diff secondaries foreach (r => {
        context.stop(replicasToReplicators(r))
        updatePending(replicasToReplicators(r))
        pendingRequests foreach {
          case (id, _) => resolvePending(id)
        }
      })

      replicasToReplicators = replicasToReplicators -- (replicasToReplicators.keys.toSet diff secondaries)
      secondaries diff replicasToReplicators.keys.toSet foreach (r => {
        val replicator = context.actorOf(Replicator.props(r))
        replicasToReplicators = replicasToReplicators + (r -> replicator)

        kv foreach {
          case (key, value) =>
            replicator ! Replicate(key, Some(value), scala.util.Random.nextLong)
        }
      })
  }

  def receivingTimeout: Receive = {
    case ReceiveTimeout =>
      pendingRequests foreach {
        case (id, (_, requester, _)) =>
          requester ! OperationFailed(id)
          pendingRequests = pendingRequests - id
      }
  }

  // REPLICA
  def replica: Receive =
    receivingGet orElse
      persisting orElse {
      case Snapshot(key, valueOption, seq) =>
        if (seq < expectedSeq) sender ! SnapshotAck(key, seq)
        else if (seq == expectedSeq) {
          val tuple = (Set.empty[ActorRef], sender, SnapshotAck(key, seq))
          pendingRequests = pendingRequests + (seq -> tuple)
          persist(key, valueOption, seq)
          valueOption match {
            case Some(value) => kv = kv + (key -> value)
            case None => kv = kv - key
          }
          expectedSeq = Math.max(expectedSeq, seq + 1)
        }
    }

  // UTILS
  private def persist(key: String, valueOption: Option[String], id: Long): Unit = {
    cancellable = context.system.scheduler.schedule(0.milliseconds, 100.milliseconds, persistence, Persist(key, valueOption, id))
  }

  private def replicate(key: String, valueOption: Option[String], id: Long): Unit = {
    val tuple = (replicasToReplicators.values.toSet, sender, OperationAck(id))
    pendingRequests = pendingRequests + (id -> tuple)
    replicasToReplicators.values foreach (_ ! Replicate(key, valueOption, id))
  }

  def updatePending(toRemove: ActorRef): Unit = {
    pendingRequests = pendingRequests map {
      case (id, value) =>
        (id, value match {
          case (waiting, requester, ack) => (waiting - toRemove, requester, ack)
        })
    }
  }

  def resolvePending(id: Long): Unit = {
    pendingRequests.get(id) match {
      case Some((waiting, requester, ack)) =>
        if (waiting.isEmpty) {
          requester ! ack
          pendingRequests = pendingRequests - id
        }
      case None =>
    }
  }
}

