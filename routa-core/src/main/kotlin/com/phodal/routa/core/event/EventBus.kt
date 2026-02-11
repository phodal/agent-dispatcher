package com.phodal.routa.core.event

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Reliable event bus with replay support for agent-to-agent coordination.
 *
 * Improvements over the previous simple implementation:
 *
 * 1. **SharedFlow replay = 32**: Late subscribers receive the last 32 events,
 *    preventing lost events when `startEventListener()` races with early emissions.
 *
 * 2. **Critical event log**: State-changing events ([AgentEvent.AgentCompleted],
 *    [AgentEvent.TaskStatusChanged], [AgentEvent.TaskDelegated]) are persisted
 *    in an in-memory log. This enables crash recovery and late-joiner replay.
 *
 * 3. **Timestamp-based replay**: [replaySince] returns all critical events after
 *    a given timestamp, useful for recovering coordinator state after a restart.
 *
 * 4. **Bounded history**: Event log is capped at [maxLogSize] to prevent unbounded
 *    memory growth. Oldest events are evicted first.
 *
 * Modeled after Intent's `pendingEventQueue` pattern in `agent.service.ts`:
 * - Events queued when no handler exists
 * - Replayed when a handler registers
 * - Auto-evicted after a TTL
 *
 * Thread-safe via [Mutex] for the event log and [MutableSharedFlow] for subscriptions.
 */
class EventBus(
    private val maxLogSize: Int = 500,
    replaySize: Int = 32,
) {

    private val _events = MutableSharedFlow<AgentEvent>(
        replay = replaySize,
        extraBufferCapacity = 256,
    )

    /** Subscribe to all events. Late subscribers receive the last [replaySize] events. */
    val events: SharedFlow<AgentEvent> = _events.asSharedFlow()

    // ── Critical Event Log ───────────────────────────────────────────

    private val logMutex = Mutex()
    private val eventLog = mutableListOf<TimestampedEvent>()

    /**
     * Emit an event to all subscribers.
     *
     * Critical events are also persisted in the event log for replay.
     */
    suspend fun emit(event: AgentEvent) {
        if (event.isCritical()) {
            logMutex.withLock {
                eventLog.add(TimestampedEvent(Instant.now(), event))
                // Evict oldest if over capacity
                while (eventLog.size > maxLogSize) {
                    eventLog.removeAt(0)
                }
            }
        }
        _events.emit(event)
    }

    /**
     * Try to emit an event without suspending.
     * @return true if the event was emitted, false if the buffer is full.
     */
    fun tryEmit(event: AgentEvent): Boolean {
        if (event.isCritical()) {
            // Can't use mutex in non-suspend context, use synchronized
            synchronized(eventLog) {
                eventLog.add(TimestampedEvent(Instant.now(), event))
                while (eventLog.size > maxLogSize) {
                    eventLog.removeAt(0)
                }
            }
        }
        return _events.tryEmit(event)
    }

    // ── Replay API ───────────────────────────────────────────────────

    /**
     * Replay all critical events emitted since [since].
     *
     * Used for:
     * - Coordinator crash recovery: replay events missed while restarting
     * - Late-joining UI: catch up on state changes that already happened
     * - Debugging: inspect the event timeline
     *
     * @param since Only return events after this timestamp.
     * @return List of events in chronological order.
     */
    suspend fun replaySince(since: Instant): List<AgentEvent> {
        return logMutex.withLock {
            eventLog
                .filter { it.timestamp.isAfter(since) }
                .map { it.event }
        }
    }

    /**
     * Replay all critical events (full history within [maxLogSize] window).
     */
    suspend fun replayAll(): List<AgentEvent> {
        return logMutex.withLock {
            eventLog.map { it.event }
        }
    }

    /**
     * Get events filtered by type since a given timestamp.
     */
    suspend fun replaySince(since: Instant, filter: (AgentEvent) -> Boolean): List<AgentEvent> {
        return logMutex.withLock {
            eventLog
                .filter { it.timestamp.isAfter(since) && filter(it.event) }
                .map { it.event }
        }
    }

    /**
     * Get the number of events currently in the log.
     */
    suspend fun logSize(): Int = logMutex.withLock { eventLog.size }

    /**
     * Get the full timestamped log (for debugging/metrics).
     */
    suspend fun getTimestampedLog(): List<TimestampedEvent> {
        return logMutex.withLock { eventLog.toList() }
    }

    /**
     * Clear the event log. Useful for testing or reset.
     */
    suspend fun clearLog() {
        logMutex.withLock { eventLog.clear() }
    }
}

// ── Supporting Types ────────────────────────────────────────────────────

/**
 * An event with its emission timestamp.
 */
data class TimestampedEvent(
    val timestamp: Instant,
    val event: AgentEvent,
)

/**
 * Extension: determine if an event is "critical" (state-changing).
 *
 * Critical events are persisted in the event log for replay.
 * Non-critical events (like [AgentEvent.MessageReceived]) are ephemeral.
 */
fun AgentEvent.isCritical(): Boolean = when (this) {
    is AgentEvent.AgentCompleted -> true
    is AgentEvent.TaskStatusChanged -> true
    is AgentEvent.TaskDelegated -> true
    is AgentEvent.AgentStatusChanged -> true
    is AgentEvent.AgentCreated -> true
    // MessageReceived is ephemeral — not persisted for replay
    is AgentEvent.MessageReceived -> false
}
