package com.tablodecori.app.data

import org.junit.Assert.*
import org.junit.Test

class PomodoroCycleTest {
    @Test fun focusMovesToFiveMinuteBreakAndThenReady() {
        val start = PomodoroCycle.start(PomodoroCycle.State(), 1000L, "task", "طراحی", 1000L)
        assertEquals(PomodoroCycle.FOCUS, start.phase)
        assertEquals(PomodoroCycle.FOCUS_MILLIS, start.left(1000L))
        assertEquals(PomodoroCycle.FOCUS_MILLIS-1000L, start.left(2000L))
        val focusDone = PomodoroCycle.advance(start, start.endsAtMillis)
        assertEquals(listOf("FOCUS_DONE"), focusDone.events)
        assertEquals(PomodoroCycle.SHORT_BREAK, focusDone.state.phase)
        assertEquals(PomodoroCycle.SHORT_BREAK_MILLIS, focusDone.state.left(start.endsAtMillis))
        val breakDone = PomodoroCycle.advance(focusDone.state, focusDone.state.endsAtMillis)
        assertEquals(listOf("BREAK_DONE"), breakDone.events)
        assertEquals(PomodoroCycle.READY, breakDone.state.phase)
        assertFalse(breakDone.state.running)
        assertEquals(1, breakDone.state.completed)
    }

    @Test fun pauseKeepsRemainingTimeAndResumeUsesIt() {
        val running = PomodoroCycle.start(PomodoroCycle.State(), 1000L)
        val paused = PomodoroCycle.pause(running, 61_000L)
        assertFalse(paused.running)
        assertEquals(24L * 60_000L, paused.remainingMillis)
        val resumed = PomodoroCycle.start(paused, 100_000L)
        assertEquals(paused.remainingMillis, resumed.left(100_000L))
    }

    @Test fun fourthFocusGetsLongBreakAndLateRecoveryDoesNotStartAnotherFocus() {
        val fourth = PomodoroCycle.start(PomodoroCycle.State(completed = 3), 1000L)
        val ended = PomodoroCycle.advance(fourth, fourth.endsAtMillis)
        assertEquals(PomodoroCycle.LONG_BREAK, ended.state.phase)
        assertEquals(PomodoroCycle.LONG_BREAK_MILLIS, ended.state.left(fourth.endsAtMillis))
        val late = PomodoroCycle.advance(fourth, fourth.endsAtMillis + PomodoroCycle.LONG_BREAK_MILLIS + 5000L)
        assertEquals(listOf("FOCUS_DONE", "BREAK_DONE"), late.events)
        assertEquals(PomodoroCycle.READY, late.state.phase)
        assertEquals(4, late.state.completed)
    }

    @Test fun skipBreakDoesNotCreditAnotherFocus() {
        val inBreak = PomodoroCycle.State(PomodoroCycle.SHORT_BREAK, PomodoroCycle.SHORT_BREAK_MILLIS,
            350_000L, 1)
        val skipped = PomodoroCycle.skipBreak(inBreak)
        assertEquals(PomodoroCycle.READY, skipped.phase)
        assertEquals(1, skipped.completed)
    }
}
