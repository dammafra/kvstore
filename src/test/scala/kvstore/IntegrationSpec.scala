/**
  * Copyright (C) 2013-2015 Typesafe Inc. <http://www.typesafe.com>
  */
package kvstore

import akka.actor.Props
import akka.testkit.TestProbe
import kvstore.Replicator.{Snapshot, SnapshotAck}
import org.scalatest.{FunSuiteLike, Matchers}
import scala.concurrent.duration._


trait IntegrationSpec
  extends FunSuiteLike
    with Matchers {
  this: KVStoreSuite =>

  import Arbiter._

  /*
   * Recommendation: write a test case that verifies proper function of the whole system,
   * then run that with flaky Persistence and/or unreliable communication (injected by
   * using an Arbiter variant that introduces randomly message-dropping forwarder Actors).
   */

  test("Integration-case: Primary and secondaries must work in concert when persistence is unreliable") {
    val arbiter = TestProbe()
    val primary = system.actorOf(Replica.props(arbiter.ref, Persistence.props(flaky = false)), "integration-case-primary")
    val user = session(primary)
    val secondary = TestProbe()

    arbiter.expectMsg(Join)
    arbiter.send(primary, JoinedPrimary)

    user.getAndVerify("k1")
    user.setAcked("k1", "v1")
    arbiter.send(primary, Replicas(Set(primary, secondary.ref)))

    expectAtLeastOneSnapshot(secondary)("k1", Some("v1"), 0L)

    val ack1 = user.set("k1", "v2")
    expectAtLeastOneSnapshot(secondary)("k1", Some("v2"), 1L)
    user.waitAck(ack1)

    val ack2 = user.remove("k1")
    expectAtLeastOneSnapshot(secondary)("k1", None, 2L)
    user.waitAck(ack2)
  }
}
