package com.tablodecori.app.data

/** A small, clock-based state machine. The deadline survives navigation and process death. */
object PomodoroCycle {
    const val FOCUS_MILLIS = 25L * 60_000L
    const val SHORT_BREAK_MILLIS = 5L * 60_000L
    const val LONG_BREAK_MILLIS = 15L * 60_000L
    const val READY = "READY"
    const val FOCUS = "FOCUS"
    const val SHORT_BREAK = "SHORT_BREAK"
    const val LONG_BREAK = "LONG_BREAK"

    data class State(
        val phase: String = READY,
        val remainingMillis: Long = FOCUS_MILLIS,
        val endsAtMillis: Long = 0L,
        val completed: Int = 0,
        val taskId: String = "",
        val taskTitle: String = "",
        val taskDayMillis: Long = 0L
    ) {
        val running: Boolean get() = endsAtMillis > 0L
        val durationMillis: Long get() = when (phase) {
            SHORT_BREAK -> SHORT_BREAK_MILLIS
            LONG_BREAK -> LONG_BREAK_MILLIS
            else -> FOCUS_MILLIS
        }
        fun left(now: Long): Long = if (running) (endsAtMillis - now).coerceAtLeast(0L) else remainingMillis
    }

    data class Result(val state: State, val events: List<String>)

    fun start(state: State, now: Long, taskId: String = "", taskTitle: String = "", dayMillis: Long = now): State {
        if (state.running) return state
        return if (state.phase == READY) State(
            phase = FOCUS, remainingMillis = FOCUS_MILLIS, endsAtMillis = now + FOCUS_MILLIS,
            completed = state.completed, taskId = taskId, taskTitle = taskTitle, taskDayMillis = dayMillis
        ) else state.copy(endsAtMillis = now + state.remainingMillis.coerceAtLeast(1L))
    }

    fun pause(state: State, now: Long): State = if (state.running)
        state.copy(remainingMillis = state.left(now), endsAtMillis = 0L) else state

    fun skipBreak(state: State): State = if (state.phase == SHORT_BREAK || state.phase == LONG_BREAK)
        State(completed = state.completed) else state

    fun reset(): State = State()

    fun advance(state: State, now: Long): Result {
        var current = state
        val events = mutableListOf<String>()
        while (current.running && now >= current.endsAtMillis) {
            if (current.phase == FOCUS) {
                val count = current.completed + 1
                val phase = if (count % 4 == 0) LONG_BREAK else SHORT_BREAK
                val breakLength = if (phase == LONG_BREAK) LONG_BREAK_MILLIS else SHORT_BREAK_MILLIS
                current = current.copy(phase = phase, remainingMillis = breakLength,
                    endsAtMillis = current.endsAtMillis + breakLength, completed = count)
                events += "FOCUS_DONE"
            } else {
                current = State(completed = current.completed)
                events += "BREAK_DONE"
            }
        }
        return Result(current, events)
    }
}
