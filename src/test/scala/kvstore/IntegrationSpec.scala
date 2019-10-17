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

  //  test("Integration-case1: Primary and secondaries must work in concert when persistence is unreliable") {
  //
  //  }

  //  test("Integration-case2: Primary and secondaries must work in concert when communication to secondaries is unreliable") {
  //
  //  }

  //  test("Integration-case3: Primary and secondaries must work in concert when persistence and communication to secondaries is unreliable") {
  //
  //  }
}
