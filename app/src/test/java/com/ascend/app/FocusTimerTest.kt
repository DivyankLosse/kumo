package com.ascend.app

import com.ascend.app.domain.ActiveFocus
import com.ascend.app.domain.Clock
import com.ascend.app.domain.FinishGuard
import com.ascend.app.domain.FocusTimer
import org.junit.Assert.*
import org.junit.Test

class FocusTimerTest {
    private class FakeClock(var now: Long = 1_000_000L) : Clock {
        override fun nowMs() = now
        fun advance(ms: Long) { now += ms }
    }

    private val clock = FakeClock()
    private fun session(minutes: Int = 25) = ActiveFocus("k1", "CAREER", null, minutes, clock.nowMs(), null, 0)

    @Test fun elapsedAndRemainingFollowClock() {
        val s = session()
        clock.advance(90_000)
        assertEquals(90_000, FocusTimer.elapsedMs(s, clock.nowMs()))
        assertEquals(25 * 60_000L - 90_000, FocusTimer.remainingMs(s, clock.nowMs()))
    }

    @Test fun pausedTimeDoesNotCount() {
        var s = session()
        clock.advance(60_000)
        s = FocusTimer.pause(s, clock.nowMs())
        clock.advance(600_000)
        assertEquals(60_000, FocusTimer.elapsedMs(s, clock.nowMs()))
    }

    @Test fun multiplePauseResumeCycles() {
        var s = session()
        repeat(3) {
            clock.advance(60_000)
            s = FocusTimer.pause(s, clock.nowMs())
            clock.advance(30_000)
            s = FocusTimer.resume(s, clock.nowMs())
        }
        clock.advance(10_000)
        assertEquals(190_000, FocusTimer.elapsedMs(s, clock.nowMs()))
        assertEquals(90_000, s.pausedTotalMs)
        assertFalse(s.paused)
    }

    @Test fun pauseAndResumeAreIdempotent() {
        val s = session()
        assertSame(s, FocusTimer.resume(s, clock.nowMs()))
        clock.advance(1000)
        val p = FocusTimer.pause(s, clock.nowMs())
        clock.advance(1000)
        assertSame(p, FocusTimer.pause(p, clock.nowMs()))
    }

    @Test fun elapsedClampedToPlanned() {
        val s = session(15)
        clock.advance(10 * 3_600_000L)
        assertEquals(15 * 60_000L, FocusTimer.elapsedMs(s, clock.nowMs()))
        assertEquals(0, FocusTimer.remainingMs(s, clock.nowMs()))
    }

    @Test fun elapsedNeverNegativeIfClockMovesBack() {
        val s = session()
        clock.advance(-5_000)
        assertEquals(0, FocusTimer.elapsedMs(s, clock.nowMs()))
    }

    @Test fun completionDetection() {
        val s = session(15)
        clock.advance(15 * 60_000L - 1)
        assertFalse(FocusTimer.isComplete(s, clock.nowMs()))
        clock.advance(1)
        assertTrue(FocusTimer.isComplete(s, clock.nowMs()))
    }

    @Test fun pausedSessionDoesNotComplete() {
        var s = session(15)
        clock.advance(5 * 60_000L)
        s = FocusTimer.pause(s, clock.nowMs())
        clock.advance(3_600_000L)
        assertFalse(FocusTimer.isComplete(s, clock.nowMs()))
    }

    @Test fun restoreAfterDeadlineIsCompleteWithFullDuration() {
        val s = session(25)
        clock.advance(2 * 3_600_000L)
        assertTrue(FocusTimer.isComplete(s, clock.nowMs()))
        assertEquals(25 * 60, (FocusTimer.elapsedMs(s, clock.nowMs()) / 1000).toInt())
    }

    @Test fun restoreWithPauseAcrossDeathKeepsDeadlineAbsolute() {
        var s = session(25)
        clock.advance(10 * 60_000L)
        s = FocusTimer.pause(s, clock.nowMs())
        clock.advance(30 * 60_000L)
        s = FocusTimer.resume(s, clock.nowMs())
        clock.advance(14 * 60_000L)
        assertFalse(FocusTimer.isComplete(s, clock.nowMs()))
        clock.advance(60_000L)
        assertTrue(FocusTimer.isComplete(s, clock.nowMs()))
    }

    @Test fun finishGuardRefusesSameKeyTwice() {
        var lastFinished: String? = null
        var recorded = 0
        fun tryFinish(key: String) {
            if (FinishGuard.canFinish(lastFinished, key)) { lastFinished = key; recorded++ }
        }
        tryFinish("a")
        tryFinish("a")
        assertEquals(1, recorded)
        tryFinish("b")
        assertEquals(2, recorded)
    }
}
