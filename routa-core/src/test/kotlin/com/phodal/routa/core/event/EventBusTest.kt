package com.phodal.routa.core.event

import com.phodal.routa.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Tests for the [EventBus] with replay support.
 */
class EventBusTest {

    @Test
    fun `critical events are persisted in log`() = runBlocking {
        val bus = EventBus()

        // Emit critical events
        bus.emit(AgentEvent.AgentCreated("a1", "ws1", null))
        bus.emit(AgentEvent.TaskDelegated("t1", "a1", "a0"))
        bus.emit(AgentEvent.AgentStatusChanged("a1", AgentStatus.PENDING, AgentStatus.ACTIVE))

        assertEquals("Should log 3 critical events", 3, bus.logSize())
    }

    @Test
    fun `non-critical events are NOT persisted in log`() = runBlocking {
        val bus = EventBus()

        // MessageReceived is non-critical
        bus.emit(AgentEvent.MessageReceived("a1", "a2", "hello"))

        assertEquals("MessageReceived should not be logged", 0, bus.logSize())
    }

    @Test
    fun `replaySince returns events after timestamp`() = runBlocking {
        val bus = EventBus()

        bus.emit(AgentEvent.AgentCreated("a1", "ws1", null))
        delay(50) // Ensure different timestamps
        val midpoint = Instant.now()
        delay(50)
        bus.emit(AgentEvent.AgentCreated("a2", "ws1", null))
        bus.emit(AgentEvent.TaskDelegated("t1", "a2", "a1"))

        val replayed = bus.replaySince(midpoint)
        assertEquals("Should replay 2 events after midpoint", 2, replayed.size)
        assertTrue("First replayed should be AgentCreated for a2",
            replayed[0] is AgentEvent.AgentCreated && (replayed[0] as AgentEvent.AgentCreated).agentId == "a2")
    }

    @Test
    fun `replayAll returns all logged events`() = runBlocking {
        val bus = EventBus()

        bus.emit(AgentEvent.AgentCreated("a1", "ws1", null))
        bus.emit(AgentEvent.AgentStatusChanged("a1", AgentStatus.PENDING, AgentStatus.ACTIVE))
        bus.emit(AgentEvent.TaskDelegated("t1", "a1", "a0"))
        bus.emit(AgentEvent.MessageReceived("a1", "a0", "hi")) // non-critical, not logged

        val all = bus.replayAll()
        assertEquals("Should have 3 critical events", 3, all.size)
    }

    @Test
    fun `event log is bounded by maxLogSize`() = runBlocking {
        val bus = EventBus(maxLogSize = 5)

        // Emit 10 events — only last 5 should be retained
        for (i in 1..10) {
            bus.emit(AgentEvent.AgentCreated("a$i", "ws1", null))
        }

        assertEquals("Should cap at 5 events", 5, bus.logSize())

        val all = bus.replayAll()
        // Oldest should be a6, newest a10
        val ids = all.map { (it as AgentEvent.AgentCreated).agentId }
        assertEquals("Oldest should be a6", "a6", ids.first())
        assertEquals("Newest should be a10", "a10", ids.last())
    }

    @Test
    fun `clearLog empties the event log`() = runBlocking {
        val bus = EventBus()

        bus.emit(AgentEvent.AgentCreated("a1", "ws1", null))
        bus.emit(AgentEvent.AgentCreated("a2", "ws1", null))
        assertEquals(2, bus.logSize())

        bus.clearLog()
        assertEquals("After clear, log should be empty", 0, bus.logSize())
    }

    @Test
    fun `replaySince with filter returns matching events only`() = runBlocking {
        val bus = EventBus()

        val before = Instant.now().minusSeconds(1)

        bus.emit(AgentEvent.AgentCreated("a1", "ws1", null))
        bus.emit(AgentEvent.TaskDelegated("t1", "a1", "a0"))
        bus.emit(AgentEvent.AgentStatusChanged("a1", AgentStatus.PENDING, AgentStatus.ACTIVE))

        val delegations = bus.replaySince(before) { it is AgentEvent.TaskDelegated }
        assertEquals("Should find 1 TaskDelegated event", 1, delegations.size)
    }

    @Test
    fun `SharedFlow replay delivers events to late subscribers`() = runBlocking {
        val bus = EventBus(replaySize = 8)

        // Emit events BEFORE subscribing
        bus.emit(AgentEvent.AgentCreated("a1", "ws1", null))
        bus.emit(AgentEvent.AgentCreated("a2", "ws1", null))

        // Now subscribe and collect replayed events
        val collected = mutableListOf<AgentEvent>()
        val job = launch {
            bus.events.take(2).toList(collected)
        }
        job.join()

        assertEquals("Late subscriber should receive 2 replayed events", 2, collected.size)
    }

    @Test
    fun `tryEmit also logs critical events`() = runBlocking {
        val bus = EventBus()

        val result = bus.tryEmit(AgentEvent.AgentCreated("a1", "ws1", null))
        assertTrue("tryEmit should succeed", result)
        assertEquals("Should log the critical event", 1, bus.logSize())

        // Non-critical via tryEmit
        bus.tryEmit(AgentEvent.MessageReceived("a1", "a2", "hello"))
        assertEquals("MessageReceived should not be logged", 1, bus.logSize())
    }

    @Test
    fun `getTimestampedLog returns events with timestamps`() = runBlocking {
        val bus = EventBus()

        bus.emit(AgentEvent.AgentCreated("a1", "ws1", null))

        val log = bus.getTimestampedLog()
        assertEquals(1, log.size)
        assertNotNull("Should have timestamp", log[0].timestamp)
        assertTrue("Timestamp should be recent",
            log[0].timestamp.isAfter(Instant.now().minusSeconds(10)))
    }

    @Test
    fun `AgentCompleted is classified as critical`() {
        val report = CompletionReport(agentId = "a1", taskId = "t1", summary = "done")
        val event = AgentEvent.AgentCompleted("a1", "a0", report)
        assertTrue("AgentCompleted should be critical", event.isCritical())
    }

    @Test
    fun `TaskStatusChanged is classified as critical`() {
        val event = AgentEvent.TaskStatusChanged("t1", TaskStatus.PENDING, TaskStatus.IN_PROGRESS)
        assertTrue("TaskStatusChanged should be critical", event.isCritical())
    }

    @Test
    fun `MessageReceived is classified as non-critical`() {
        val event = AgentEvent.MessageReceived("a1", "a2", "hello")
        assertFalse("MessageReceived should not be critical", event.isCritical())
    }
}
