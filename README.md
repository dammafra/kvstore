## Simple Replicated Key-Value store

A key-value store is a very simple form of a database. Its entries are key-value pairs, the key part acting as a unique identifier, and the value <g class="gr_ gr_528 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar multiReplace" id="528" data-gr-id="528">being</g> arbitrary data. In recent years, distributed versions of key-value stores have become very popular. Your task in this assignment is to implement a distributed, replicated storage of key-value pairs. Each node (the replicas) in this distributed system will be represented by one actor. You will also have to define some helper actors.

In the simplified version that we will examine, your system will include a primary node (the primary replica), which will be responsible for replicating all changes to a set of secondary nodes (the secondary replicas). The primary and the secondary replica nodes will form a distributed database, where potential replica nodes might join and leave at arbitrary times.

The primary replica will be the only one accepting modification events (insertions and removals) and replicate its current state to the secondaries. Both the primary and the secondary replicas will accept lookup (read) events, although the secondary nodes will be allowed to give results that are "out-of-date" since it takes time for the replicas to keep up with the changes on the primary replica.

Compared to a full real-world solution several restricting assumptions are made about the system to make the problem easier to solve:

*   Updates are only possible on a dedicated node, the primary replica.
*   The primary (leader) node does not fail during the uptime of the system.
*   Membership is handled reliably by a provided subsystem (see section "The Arbiter").
*   The update rate is low, meaning that no incoming requests need to be rejected due to system overload.
*   In case of rejecting an <g class="gr_ gr_475 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-ins replaceWithoutSep" id="475" data-gr-id="475">update</g> the store is left in a possibly inconsistent state which may require a subsequent succeeding write to the same key to repair it.
*   Clients are expected to not reuse request IDs before the request has been fully processed and responded to.

For a more detailed discussion of these restrictions and on how lifting them would affect the solution please refer to the last section below.

### Overview of the system components

The key-value store and its environment consists of the following components:

*   **The clustered key-value store:** A set of nodes that store <g class="gr_ gr_420 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="420" data-gr-id="420">key value</g> pairs in a distributed fashion, cooperating to maintain a certain set of guarantees (specified in section _“System Behavior - Consistency guarantees”_). This cluster of nodes consists of replicas and the provided `Arbiter` and `Persistence` modules:
    *   **Primary replica** A distinguished node in the cluster that accepts updates to keys and propagates the changes to secondary replicas.
    *   **Secondary replicas** Nodes that are in contact with the primary replica, accepting updates from it and serving clients for read-only operations.
    *   **Arbiter:** A subsystem that is provided in this exercise and which assigns the primary or secondary roles to your nodes.
    *   **Persistence:** A subsystem that is provided in this exercise and which offers to persist updates to stable storage, but might fail spuriously.
*   **Clients:** Entities that communicate with one of the replicas to update or read key-value pairs.

You are encouraged to use additional actors in your solution as you see fit.

### Clients and The <g class="gr_ gr_411 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="411" data-gr-id="411">KV</g> Protocol

Clients are external entities contacting the replica set (your cluster) for reading and writing key-value pairs. It is your responsibility to maintain the consistency guarantees for clients that are described in the next section. Each node participating in the key-value store (primary and secondaries) provides an interface for clients. This interface is a set of messages and corresponding answers that together form the <g class="gr_ gr_412 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="412" data-gr-id="412">KV</g> Protocol.

The <g class="gr_ gr_324 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="324" data-gr-id="324">KV</g> Protocol itself contains two subsets of operations:

1.  Update operations (insertion and removal).
2.  Lookup operation.

Clients contacting the primary node directly can use all operations on the key-value store, while clients contacting the secondaries can only use lookups.

The two sets of operations in detail are:

**Update Commands**

*   `Insert(key, value, id)` - This message instructs the primary to insert the (key, value) pair into the storage and replicate it to the secondaries: `id` is a client-chosen unique identifier for this request.
*   `Remove(key, id)` - This message instructs the primary to remove the key (and its corresponding value) from the storage and then remove it from the secondaries.
*   A <g class="gr_ gr_482 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="482" data-gr-id="482">successful</g> `Insert`<g class="gr_ gr_497 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="497" data-gr-id="497"><g class="gr_ gr_482 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="482" data-gr-id="482">or</g></g> `Remove` <g class="gr_ gr_497 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="497" data-gr-id="497">results</g> in a reply to the client (precisely: to <g class="gr_ gr_517 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="517" data-gr-id="517">the</g> `sender` <g class="gr_ gr_517 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="517" data-gr-id="517">of</g> the update command) in the form of <g class="gr_ gr_467 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar multiReplace" id="467" data-gr-id="467"><g class="gr_ gr_532 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="532" data-gr-id="532">an</g> </g>`OperationAck(id)`<g class="gr_ gr_467 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Grammar multiReplace" id="467" data-gr-id="467"> <g class="gr_ gr_532 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="532" data-gr-id="532">message</g></g> where <g class="gr_ gr_542 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="542" data-gr-id="542">the</g> `id` <g class="gr_ gr_542 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="542" data-gr-id="542">field</g> matches the corresponding id field of the operation that has been acknowledged.
*   A <g class="gr_ gr_468 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="468" data-gr-id="468">failed</g> `Insert`<g class="gr_ gr_477 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="477" data-gr-id="477"><g class="gr_ gr_468 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="468" data-gr-id="468">or</g></g> `Remove` <g class="gr_ gr_477 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="477" data-gr-id="477">command</g> results in <g class="gr_ gr_453 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar multiReplace" id="453" data-gr-id="453"><g class="gr_ gr_488 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="488" data-gr-id="488">an</g> </g>`OperationFailed(id)`<g class="gr_ gr_453 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Grammar multiReplace" id="453" data-gr-id="453"> <g class="gr_ gr_488 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="488" data-gr-id="488">reply</g></g>. A failure is defined as the inability to confirm the operation within 1 second. See the sections on replication and persistence below for more details.

**Lookup**

*   `Get(key, id)` - Instructs the replica to look up the "current" (what current means is described in detail in the next section) value assigned with the key in the storage and reply with the stored value.
*   A `Get` operation results in a `GetResult(key, valueOption, id)` message to be sent back to the `sender` of the lookup request where the `id` field matches the value in the `id` field of the corresponding `Get` message. The `valueOption` field should contain `None` if the key is not present in the replica or `Some(value)` if a value is currently assigned to the given key in that replica.

**All replies sent by the Replica shall have that Replica as <g class="gr_ gr_481 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="481" data-gr-id="481"><g class="gr_ gr_473 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Grammar multiReplace" id="473" data-gr-id="473">their</g></g> `sender`<g class="gr_ gr_481 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="481" data-gr-id="481">.</g>**

### System Behavior - Consistency guarantees

Let's assume the scenario that one client issues the following commands to the primary replica (starting from empty storage), **waiting for successful acknowledgement of each operation before proceeding with the next** (see further below for the case of not awaiting confirmation):

    Insert("key1", "a") 
    Insert("key2", "1")
    Insert("key1", "b") 
    Insert("key2", "2") 

#### Ordering guarantees for clients contacting the primary replica

A second client reading directly from the primary is not allowed to see:

*   `key1` containing `b` and then containing `a` _(since `a` was written before `b` for `key1`)_
*   `key2` containing `2` and then containing `1` _(since `1` was written before `2` for `key2`)_

In other words, this second client sees the updates in order (although it might miss some updates, so it might see the value `2` for `key2` immediately without seeing the intermediate value `1`).

In contrast, the second client may observe

*   `key1` containing `b` and then `key2` <g class="gr_ gr_500 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling only-del replaceWithoutSep" id="500" data-gr-id="500">containing</g> `1`
*   `key2` <g class="gr_ gr_500 gr-alert gr_spell gr_inline_cards gr_disable_anim_appear ContextualSpelling only-del replaceWithoutSep" id="500" data-gr-id="500">containing</g> `2` and then `key1` containing `a`

This means that the ordering guarantee only applies between reads and write _to the same key_, not across keys. The store may choose to provide stronger semantics to respect ordering across different keys, but clients will not be able to rely on this; the reason is that lifting the restriction of having only one non-failing primary replica would require breaking these stronger guarantees.

#### Ordering guarantees for clients contacting a secondary replica

For a second client reading from one of the secondary replicas (during a conversation, the replica does not change) the exact same requirements apply as if that client was reading from the primary, with the following addition:

*   it must be guaranteed that a client reading from a secondary replica will eventually see the following (at some point in the future):

    *   `key1` containing `b`
    *   `key2` containing `2`

#### Ordering guarantees for clients contacting different replicas

If a second client asks different replicas for the same key, it may observe different values during the time window when an update is disseminated. The client asking for `key1` might see

*   answer `b` from one replica
*   and subsequently answer `a` from a different replica

As per the rule stated in the previous section, and assuming that the client keeps asking repeatedly, eventually all reads will result in the value `b` if no other updates are done on `key1`.

_Eventual consistency_ means that given enough time, all replicas settle on the same view. This also means that when you design your system **clients contacting multiple replicas at the same time are not required to see any particular ordering.** You do not need to design your solution for such scenarios and the grading system will not test these either.

#### Durability guarantees of updates for clients contacting the primary replica

The previous two sections prescribed possible transitions that the clients are allowed to experience on key updates. In this section, we will see what guarantees <g class="gr_ gr_445 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="445" data-gr-id="445">acknowledgement</g> messages must obey (on the primary replica).

Whenever the primary replica receives an update operation (<g class="gr_ gr_520 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="520" data-gr-id="520">either</g> `Insert` <g class="gr_ gr_520 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="520" data-gr-id="520">or</g> `Remove`) it **must** reply with <g class="gr_ gr_536 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="536" data-gr-id="536">an</g> `OperationAck(id)`<g class="gr_ gr_554 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="554" data-gr-id="554"><g class="gr_ gr_536 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="536" data-gr-id="536">or</g></g> `OperationFailed(id)` <g class="gr_ gr_554 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="554" data-gr-id="554">message</g>, to be sent at most 1 second after the update command was processed (the ActorSystem’s timer resolution is deemed to be sufficiently precise for this).

A positive `OperationAck` reply must be sent as soon as

*   the change in question has been handed down to the `Persistence` module (provided) **and** a corresponding <g class="gr_ gr_613 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="613" data-gr-id="613">acknowledgement</g> has been received from it _(the persistence module is "flaky"—it fails randomly from time to time—and it is your task to keep it alive while retrying unacknowledged persistence operations until they succeed, see the persistence section for details)_

*   replication of the change in question has been initiated **and** all of the secondary replicas have acknowledged the replication of the update.

    If replicas leave the cluster, which is <g class="gr_ gr_509 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="509" data-gr-id="509">signalled</g> by sending a <g class="gr_ gr_552 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="552" data-gr-id="552">new</g> `Replicas` <g class="gr_ gr_552 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="552" data-gr-id="552">message</g> to the primary, then outstanding <g class="gr_ gr_531 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="531" data-gr-id="531">acknowledgements</g> of these replicas must be waived. This can lead to the generation of <g class="gr_ gr_494 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar multiReplace" id="494" data-gr-id="494"><g class="gr_ gr_569 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="569" data-gr-id="569">an</g> </g>`OperationAck`<g class="gr_ gr_494 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Grammar multiReplace" id="494" data-gr-id="494"> <g class="gr_ gr_569 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="569" data-gr-id="569">triggered</g></g> indirectly by <g class="gr_ gr_577 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="577" data-gr-id="577">the</g> `Replicas` <g class="gr_ gr_577 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="577" data-gr-id="577">message</g>.

A <g class="gr_ gr_470 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="470" data-gr-id="470">negative</g> `OperationFailed` <g class="gr_ gr_470 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="470" data-gr-id="470">reply</g> must be sent if the conditions for sending <g class="gr_ gr_483 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="483" data-gr-id="483">an</g> `OperationAck` <g class="gr_ gr_483 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="483" data-gr-id="483">are</g> not met within the 1 second maximum response time.

#### Consistency in the case of failed replication or persistence

Assuming in the above scenario that the last write fails (i.e. an `OperationFailed` is returned), replication to some replicas may have been successful while it failed on others. Therefore in this case the property that eventually all replicas converge on the same value for `key2` is not provided by this simplified <g class="gr_ gr_404 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="404" data-gr-id="404">key–value</g> store. In order to restore consistency a later write to `key2` would have to succeed. Lifting this restriction is an interesting exercise on its own, but it is outside of the scope of this course.

One consequence of this restriction is that each replica uses this freedom to immediately hand out the updated value to subsequently reading clients, even before <g class="gr_ gr_489 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling replaceWithoutSep" id="489" data-gr-id="489">the the new</g> value has been persisted locally, and no rollback is attempted in case of failure.

#### Which value to expect while an update is outstanding?

Sending an update request for a key followed by a `Get` request for the same key without waiting for the <g class="gr_ gr_534 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="534" data-gr-id="534">acknowledgement</g> of the update is allowed to return either the old or the new value (or a third value if another client concurrently updates the same key). An example, assuming only this one client at this time:

    Insert("key1", "a") 
    <await confirmation> 
    Insert("key1", "b")
    Get("key1") 

The replies for the last two requests may arrive in any order, and the reply for <g class="gr_ gr_547 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="547" data-gr-id="547">the</g> `Get` <g class="gr_ gr_547 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="547" data-gr-id="547">request</g> may either <g class="gr_ gr_559 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="559" data-gr-id="559">contain</g> `"a"`<g class="gr_ gr_570 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="570" data-gr-id="570"><g class="gr_ gr_559 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="559" data-gr-id="559">or</g></g> `"b"`<g class="gr_ gr_570 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="570" data-gr-id="570">.</g>

### The Arbiter

<g class="gr_ gr_462 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="462" data-gr-id="462">The</g> `Arbiter` <g class="gr_ gr_462 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="462" data-gr-id="462">is</g> an external subsystem that will be provided for use. The Arbiter follows a simple protocol:

*   New replicas must first send a `Join` message to <g class="gr_ gr_472 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="472" data-gr-id="472">the</g> `Arbiter` <g class="gr_ gr_472 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="472" data-gr-id="472">signaling</g> that they are ready to be used.
*   The `Join` message will be answered by either a `JoinedPrimary` or `JoinedSecondary` message indicating the role of the new node; the answer will be sent to the `sender` of the `Join` message. The first node to join will get the primary role, other subsequent nodes are assigned the secondary role.
*   The arbiter will send a `Replicas` message to the primary replica whenever it receives <g class="gr_ gr_440 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="440" data-gr-id="440">the</g> `Join` <g class="gr_ gr_440 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="440" data-gr-id="440">message</g>; for this <g class="gr_ gr_436 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-ins replaceWithoutSep" id="436" data-gr-id="436">reason</g> <g class="gr_ gr_443 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="443" data-gr-id="443">the</g> `sender` <g class="gr_ gr_443 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="443" data-gr-id="443">of</g> <g class="gr_ gr_447 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="447" data-gr-id="447">the</g> `Join` <g class="gr_ gr_447 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="447" data-gr-id="447">message</g> must be the replica Actor itself. This message contains the set of available replica nodes including the primary and all the secondaries.
*   All messages sent by the Arbiter will have the Arbiter as <g class="gr_ gr_334 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="334" data-gr-id="334"><g class="gr_ gr_331 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar multiReplace" id="331" data-gr-id="331">their</g></g> `sender`<g class="gr_ gr_334 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="334" data-gr-id="334">.</g>

### The Replicas

You have to provide an Actor representing a node of the system. Its declaration is:

    class Replica(val arbiter: ActorRef) extends Actor 

Please note that in this exercise the nodes (i.e. actors) are executed within the same JVM, but the problems and their solutions apply equally when distributing the system across multiple network hosts.

When your actor starts, it must send a `Join` message to the Arbiter and then choose between primary or secondary behavior according to the reply of <g class="gr_ gr_521 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="521" data-gr-id="521">the</g> `Arbiter` <g class="gr_ gr_521 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="521" data-gr-id="521">to</g> <g class="gr_ gr_533 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="533" data-gr-id="533">the</g> `Join` <g class="gr_ gr_533 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="533" data-gr-id="533">message</g> (a `JoinedPrimary` <g class="gr_ gr_546 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="546" data-gr-id="546">or</g> `JoinedSecondary` <g class="gr_ gr_546 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="546" data-gr-id="546">message</g>).

The primary replica must provide the following features:

*   The primary must accept update and lookup operations from clients following the Key-Value protocol <g class="gr_ gr_403 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="403" data-gr-id="403">like</g> `Insert`<g class="gr_ gr_403 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="403" data-gr-id="403">,</g> `Remove` <g class="gr_ gr_399 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="399" data-gr-id="399">or</g> `Get` <g class="gr_ gr_399 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="399" data-gr-id="399">as</g> it is described in the _“Clients and The <g class="gr_ gr_328 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="328" data-gr-id="328">KV</g> Protocol”_ section, respecting the consistency guarantees described in _“Guarantees for clients contacting the primary replica”_.
*   The primary must replicate changes to the secondary replicas of the system. It must also react to changes in membership (whenever it gets a `Replicas` message from the `Arbiter`) and start replicating to newly joined nodes, and stop replication to nodes that have left; the latter implies terminating the <g class="gr_ gr_454 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="454" data-gr-id="454">corresponding</g> `Replicator` <g class="gr_ gr_454 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="454" data-gr-id="454">actor</g>. More details can be found in the section _“Replication Protocol”_.

The secondary replicas must provide the following features:

*   The secondary nodes must accept the lookup operation (`Get`) from clients following the Key-Value protocol while respecting the guarantees described in _“Guarantees for clients contacting the secondary replica”_.
*   The replica nodes must accept replication events, updating their current state (see _“Replication Protocol”_).

### The Replication Protocol

Apart from providing the <g class="gr_ gr_325 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="325" data-gr-id="325">KV</g> protocol for external clients, you must implement another protocol involving the primary and secondary replicas and some newly introduced helper nodes. The <g class="gr_ gr_326 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="326" data-gr-id="326">KV</g> store will use this protocol to synchronize its state between nodes.

When a new replica joins the system, the primary receives a new `Replicas` message and must allocate a new actor of type `Replicator` for the new replica; when a replica leaves the system its corresponding `Replicator` must be terminated. The role of this `Replicator` actor is to accept **update events**, and propagate the changes to its corresponding replica (i.e. there is exactly one `Replicator` per secondary replica). **Also, notice that at creation time of the `Replicator`, the primary must forward update events for every key-value pair it currently holds to this `Replicator`.**

Your task for this protocol will be to provide an Actor representing a `Replicator`. Its declaration is:

    class Replicator(val replica: ActorRef) extends Actor 

The protocol includes two pairs of messages. The first one is used by the replica actor which requests replication of an update:

*   `Replicate(key, valueOption, id)` is sent to the `Replicator` to initiate the replication of the given update to the `key`; in case of an `Insert` operation the `valueOption` will be `Some(value)` while in case of a `Remove` operation it will be `None`. The `sender` of the `Replicate` message shall be the Replica itself.

*   `Replicated(key, id)` is sent as a reply to the corresponding `Replicate` message once replication of that update has been successfully completed (see `SnapshotAck`). The `sender` of the `Replicated` message shall be the Replicator.

The second pair is used by the replicator when communicating with its partner replica:

*   `Snapshot(key, valueOption, seq)` is sent by <g class="gr_ gr_345 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="345" data-gr-id="345">the</g> `Replicator` <g class="gr_ gr_345 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="345" data-gr-id="345">to</g> the appropriate secondary replica to indicate a new state of the given key. `valueOption` has the same meaning as <g class="gr_ gr_346 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="346" data-gr-id="346">for</g> `Replicate` <g class="gr_ gr_346 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="346" data-gr-id="346">messages</g>. <g class="gr_ gr_348 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="348" data-gr-id="348">The</g> `sender` <g class="gr_ gr_348 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="348" data-gr-id="348">of</g> <g class="gr_ gr_352 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="352" data-gr-id="352">the</g> `Snapshot` <g class="gr_ gr_352 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="352" data-gr-id="352">message</g> shall be the Replicator.

    <g class="gr_ gr_375 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="375" data-gr-id="375">The</g> `Snapshot` <g class="gr_ gr_375 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="375" data-gr-id="375">message</g> provides a sequence number (`seq`) to enforce ordering between the updates. Updates for a given secondary replica must be processed in contiguous ascending sequence number order; this ensures that updates for every single key are applied in the correct order. <g class="gr_ gr_379 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="379" data-gr-id="379">Each</g> `Replicator` <g class="gr_ gr_379 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="379" data-gr-id="379">uses</g> its own number sequence starting at zero.

    When a snapshot arrives at a `Replica` with a sequence number which is greater than the currently expected number, then that snapshot must be ignored (meaning no state change and no reaction).

    When a snapshot arrives at a `Replica` with a sequence number which is smaller than the currently expected number, then that snapshot must be ignored and immediately acknowledged as described below.

    The sender reference when sending <g class="gr_ gr_391 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="391" data-gr-id="391">the</g> `Snapshot` <g class="gr_ gr_391 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="391" data-gr-id="391">message</g> must be <g class="gr_ gr_397 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="397" data-gr-id="397">the</g> `Replicator` <g class="gr_ gr_397 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="397" data-gr-id="397">actor</g> (not the primary replica actor or any other).

*   `SnapshotAck(key, seq)` is the reply sent by the secondary replica to <g class="gr_ gr_401 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="401" data-gr-id="401">the</g> `Replicator` <g class="gr_ gr_401 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="401" data-gr-id="401">as</g> soon as the update is persisted locally by the secondary replica. The replica might never send this reply in case it is unable to persist the update. <g class="gr_ gr_405 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="405" data-gr-id="405">The</g> `sender` <g class="gr_ gr_405 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="405" data-gr-id="405">of</g> <g class="gr_ gr_409 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="409" data-gr-id="409">the</g> `SnapshotAck` <g class="gr_ gr_409 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="409" data-gr-id="409">message</g> shall be the secondary Replica.

    The <g class="gr_ gr_343 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="343" data-gr-id="343">acknowledgement</g> is sent immediately for requests whose sequence number is less than the next expected number.

    The expected number is set to the greater of

    *   the previously expected number
    *   the sequence number just acknowledged, incremented by one

You should note that the Replicator may handle multiple snapshots of a given key in parallel (i.e. their replication has been initiated but not yet completed). It is allowed—but not required— to batch changes before sending them to the secondary replica, provided that each replication request is acknowledged properly and in the right sequence when complete. An example:

    Replicate("a_key", Some("value1"), id1) 
    Replicate("a_key", Some("value2"), id2) 

might have reached <g class="gr_ gr_527 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="527" data-gr-id="527">the</g> `Replicator` <g class="gr_ gr_527 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="527" data-gr-id="527">before</g> it got around to send a `Snapshot` message <g class="gr_ gr_539 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="539" data-gr-id="539">for</g> `a_key` <g class="gr_ gr_539 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="539" data-gr-id="539">to</g> its replica. These two messages could then result in only the following replication message

    Snapshot("a_key", Some("value2"), seq) 

skipping the state <g class="gr_ gr_553 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="553" data-gr-id="553">where</g> `a_key` <g class="gr_ gr_553 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="553" data-gr-id="553">contains</g> the <g class="gr_ gr_566 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="566" data-gr-id="566">value</g> `value1`<g class="gr_ gr_566 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="566" data-gr-id="566">.</g>

Since the replication protocol is meant to symbolize remote replication you must consider the case that either a `Snapshot` message or its corresponding `SnapshotAck` message is lost on the way. Therefore the `Replicator` must make sure to periodically retransmit all unacknowledged changes. For grading purposes it is assumed that this happens roughly every 100 milliseconds. To allow for batching (see above) we will assume that a lost `Snapshot` message will lead to a resend at most 200 milliseconds after the `Replicate` request was received (again, the ActorSystem’s scheduler service is considered precise enough for this purpose).

### Persistence

Each replica will have to submit incoming updates to the local `Persistence` actor and wait for its acknowledgement before confirming the update to the requester. In case of the primary, the requester is a client which sent an `Insert` or `Remove` request and the confirmation is an `OperationAck`, whereas in the case of a secondary the requester is a `Replicator` sending a `Snapshot` and expecting a `SnapshotAck` back.

The used message types are:

*   `Persist(key, valueOption, id)` is sent to the `Persistence` actor to request the given state to be persisted (with the same field description as for the `Replicate` message above).
*   `Persisted(key, id)` is sent by the `Persistence` actor as reply in case the corresponding request was successful; no reply is sent otherwise. The reply is sent to the `sender` of the `Persist` message and the `sender` of the `Persisted` message will be the Persistence Actor.

The provided implementation of this persistence service is a mock in the true <g class="gr_ gr_510 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-del replaceWithoutSep" id="510" data-gr-id="510">sense,</g> since it is rather unreliable: every now and then it will fail with an exception and not acknowledge the current request. It is the job of <g class="gr_ gr_541 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="541" data-gr-id="541">the</g> `Replica` <g class="gr_ gr_541 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="541" data-gr-id="541">actor</g> to create and appropriately supervise <g class="gr_ gr_557 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="557" data-gr-id="557">the</g> `Persistence` <g class="gr_ gr_557 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="557" data-gr-id="557">actor</g>; for the purpose of this <g class="gr_ gr_476 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-ins replaceWithoutSep" id="476" data-gr-id="476">exercise</g> any strategy will work, which means that you can experiment with different designs based on resuming, restarting or stopping and recreating <g class="gr_ gr_567 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="567" data-gr-id="567">the</g> `Persistence` <g class="gr_ gr_567 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="567" data-gr-id="567">actor</g>. To this <g class="gr_ gr_492 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-ins replaceWithoutSep" id="492" data-gr-id="492">end</g> your `Replica` does not receive an `ActorRef` but a `Props` for this actor, implying that the `Replica` has to initially create it as well.

For grading <g class="gr_ gr_333 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-ins replaceWithoutSep" id="333" data-gr-id="333">purposes</g> it is expected <g class="gr_ gr_341 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="341" data-gr-id="341">that</g> `Persist` <g class="gr_ gr_341 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="341" data-gr-id="341">is</g> retried before the <g class="gr_ gr_337 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="337" data-gr-id="337">1 second</g> response timeout in case persistence failed. <g class="gr_ gr_353 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="353" data-gr-id="353">The</g> `id` <g class="gr_ gr_353 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="353" data-gr-id="353">used</g> in <g class="gr_ gr_358 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="358" data-gr-id="358">retried</g> `Persist` <g class="gr_ gr_358 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="358" data-gr-id="358">messages</g> must match the one which was used in the first request for this particular update.

### Your Task

Since this assignment is a longer one, we have prepared the test suite in a way that supports the solution step by step. In the <g class="gr_ gr_373 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-ins replaceWithoutSep" id="373" data-gr-id="373">following</g> you can find a suggestion for how to proceed with the solution, but you can <g class="gr_ gr_369 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation replaceWithoutSep" id="369" data-gr-id="369">of course</g> choose any path leading up to twenty points that you like.

1.  Implement the primary replica role so that it correctly responds to the <g class="gr_ gr_322 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="322" data-gr-id="322">KV</g> protocol messages without considering persistence or replication.
2.  Implement the secondary replica role so that it correctly responds to the read-only part of the <g class="gr_ gr_323 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="323" data-gr-id="323">KV</g> protocol and accepts the replication protocol, without considering persistence.
3.  Implement the replicator so that it correctly mediates between replication requests, snapshots and <g class="gr_ gr_360 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="360" data-gr-id="360">acknowledgements</g>.
4.  Implement the use of persistence at the secondary replicas.
5.  Implement the use of persistence and replication at the primary replica.
6.  Implement the sending of the initial state replication to newly joined replicas.

### Hints, Tips

The logic for collecting <g class="gr_ gr_344 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="344" data-gr-id="344">acknowledgements</g> of persistence and replication can be made such that it is usable both in primary and secondary replicas.

You should write (versions of) tests which exercise the behavior under unreliable persistence (i.e. when using a `Persistence` actor created with `flaky = true`) or unreliable communication between primary and secondaries. The latter can be done by having the `Arbiter` wrap the secondary nodes’ ActorRefs in small actors which normally forward messages but sometimes forget to do so. The grading process involves such a test suite as well.

Resending snapshots from <g class="gr_ gr_448 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="448" data-gr-id="448">the</g> `Replicator` <g class="gr_ gr_448 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="448" data-gr-id="448">without</g> pause is not the intended solution—the resend rate needs to be kept reasonable. This fact is exploited implicitly by the test suite in step 3.

### The Effect of the Restrictions

This section is meant to offer further insights for those who want to take the assignment as a starting point for continuing their own studies beyond what the course provides. It is not necessary to read this section in order to complete the assignment.

#### Updates are only accepted by a distinguished primary replica

Accepting writes only on one node at any given time simplifies conflict resolution, because request arrival order at that node can be used to serialize updates to a given key. The downside of this is that this node clearly limits the <g class="gr_ gr_351 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar multiReplace" id="351" data-gr-id="351">amount</g> of requests that can be handled per second, it is a single point of bottleneck.

In order to scale the possible update rate beyond what a single node can digest it would also be possible to divide the key space into shards and distribute them across the members, making each member a primary for a certain portion of the key space. The clients would then need to either be told which one the right node is for updating a certain key, or every node could include a ConsistentHashingRouter which dispatches incoming requests to the right node on the client’s behalf. Moving the primary role for a shard from one member to another would then be necessary to rebalance the allocation when nodes join or leave the cluster.

The service which determines the placement of shards does not need to be a single point of <g class="gr_ gr_342 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar only-ins doubleReplace replaceWithoutSep" id="342" data-gr-id="342">bottleneck</g>, as we can see the distribution of shards by using consistent hashing can be done without going through a central point. The only thing that needs special care is the hand-off period when shards move between nodes.

#### The primary node does not fail

A real system would have to tolerate a failure of the primary node, which otherwise would be a single point of failure, making the system not resilient. In order to support this, the primary role must be transferred by the arbiter to another node, which transitions from secondary to primary and starts accepting updates. During this <g class="gr_ gr_486 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-ins replaceWithoutSep" id="486" data-gr-id="486">transition</g> updates will be left without a reply, but since confirmations are only sent out after all replicas have acknowledged an update, the new primary node will be able to continue where the old one left off.

One problem are unconfirmed or even rejected updates which have in fact been accepted by all secondary replicas. The store will in this case be internally consistent, but its contents possibly does not match what the client assumes if it received an `OperationFailed` message. Having received that message signals a possibly inconsistent state due to other reasons as well, and it could be an option to require the client to attempt to repair it by sending another write to the same key if needed.

#### Membership is handled reliably by a provided subsystem

A service which reliably handles membership is available in the form of the Akka Cluster module. The arbiter of this assignment can be a [ClusterSingleton](http://doc.akka.io/docs/akka/2.2.3/scala/cluster-usage.html#Cluster_Singleton_Pattern) to which replicas register and which uses DeathWatch to monitor them, or the cluster membership list could be used directly, determining the primary role by sorting the member addresses and using the first one.

#### The update rate is low

Without this restriction the resend mechanism for example in <g class="gr_ gr_435 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="435" data-gr-id="435">the</g> `Replicator` <g class="gr_ gr_435 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="435" data-gr-id="435">would</g> have to manage the size of the buffer in which snapshots are kept while awaiting their <g class="gr_ gr_431 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling multiReplace" id="431" data-gr-id="431">acknowledgement</g>. This means that the replication mechanism could either lose updates or <g class="gr_ gr_437 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="437" data-gr-id="437">the</g> `Replica` <g class="gr_ gr_437 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="437" data-gr-id="437">would</g> need to start rejecting requests <g class="gr_ gr_439 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="439" data-gr-id="439">with</g> `OperationFailed` <g class="gr_ gr_439 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="439" data-gr-id="439">as</g> soon as a limit on the number of outstanding requests is exceeded.

Especially when considering the next restriction, <g class="gr_ gr_398 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar only-ins replaceWithoutSep" id="398" data-gr-id="398">latency</g> of confirmations is a <g class="gr_ gr_410 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace replaceWithoutSep replaceWithoutSep" id="410" data-gr-id="410">performance sensitive</g> topic. One further possible optimization is to distribute the burden of ensuring consistency between the primary and secondary nodes by requiring that only a certain number of secondaries have sent their confirmation before acknowledging an update. In order to restore consistency in case of a replication failure (e.g. if the primary stops working for whatever reason) the secondaries have to ask their peers for the key and a reply is only sent once enough have replied with the same value. If the number of write confirmations is W and the count of agreeing reads is denoted R, then the condition for consistency is R+W>N, where N is the total number of replicas. This equation must be interpreted with some care because N can change during <g class="gr_ gr_395 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar only-del replaceWithoutSep" id="395" data-gr-id="395">a replication</g> or read process.

#### Disregarding inconsistencies after OperationFailed

This is the toughest restriction to lift because it touches on the fundamental choice of which flavor of weak consistency eventually shall be reached. The restriction allows the system to stay responsive in case of failure (by providing <g class="gr_ gr_359 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar multiReplace" id="359" data-gr-id="359"><g class="gr_ gr_365 gr-alert gr_gramm gr_inline_cards gr_run_anim Style multiReplace" id="365" data-gr-id="365">an</g> </g>`OperationFailed`<g class="gr_ gr_359 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Grammar multiReplace" id="359" data-gr-id="359"> <g class="gr_ gr_365 gr-alert gr_gramm gr_inline_cards gr_disable_anim_appear Style multiReplace" id="365" data-gr-id="365">reply</g></g>), but making that reply mean that the update will eventually disappear from all nodes is not possible in general.

Assuming that the primary does not fail for a sufficiently long time period after the operation failure, the first step would be to ensure that for a given key only up to one update can be “<g class="gr_ gr_329 gr-alert gr_spell gr_inline_cards gr_run_anim ContextualSpelling ins-del multiReplace" id="329" data-gr-id="329">in flight</g>” at any given time. If the primary determines that this update has failed, it will then replicate the previous state for that key to all replicas, awaiting confirmation without a timeout. This favors consistency over <g class="gr_ gr_452 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-del replaceWithoutSep" id="452" data-gr-id="452">availability,</g> since it makes the service of updating that key unavailable while the system is experiencing a malfunction.

#### Clients are expected not to reuse request IDs

This restriction makes your solution simpler and it also removes the need for some grading tests which would have been hard to formulate in a way which does not assume too much about your solution. In a real <g class="gr_ gr_354 gr-alert gr_gramm gr_inline_cards gr_run_anim Punctuation only-ins replaceWithoutSep" id="354" data-gr-id="354">system</g> you might even find this restriction as <g class="gr_ gr_349 gr-alert gr_gramm gr_inline_cards gr_run_anim Grammar only-ins replaceWithoutSep" id="349" data-gr-id="349">specified</g> operational requirement.