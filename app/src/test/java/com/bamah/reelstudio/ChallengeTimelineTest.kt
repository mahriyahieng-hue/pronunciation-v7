package com.bamah.reelstudio

import org.junit.Assert.*
import org.junit.Test

class ChallengeTimelineTest {
    @Test fun questionThenAnswerThenNextWord() {
        assertEquals(0, ChallengeTimeline.at(0, 2, 2, 3)!!.index)
        assertFalse(ChallengeTimeline.at(1999, 2, 2, 3)!!.answerPhase)
        assertTrue(ChallengeTimeline.at(2000, 2, 2, 3)!!.answerPhase)
        assertEquals(1, ChallengeTimeline.at(5000, 2, 2, 3)!!.index)
        assertFalse(ChallengeTimeline.at(5000, 2, 2, 3)!!.answerPhase)
    }

    @Test fun completionCardHasDefinedHoldAndThenDisappears() {
        val challengeEnd = 2L * (2 + 3) * 1000L
        assertTrue(ChallengeTimeline.at(challengeEnd, 2, 2, 3)!!.complete)
        assertTrue(ChallengeTimeline.at(challengeEnd + 1599, 2, 2, 3)!!.complete)
        assertNull(ChallengeTimeline.at(challengeEnd + 1600, 2, 2, 3))
    }

    @Test fun clampsTimerRangesAndHandlesEmptyChallenge() {
        assertNull(ChallengeTimeline.at(0, 0, 2, 2))
        assertEquals(0, ChallengeTimeline.at(4999, 1, 99, 99)!!.index)
        assertFalse(ChallengeTimeline.at(4999, 1, 99, 99)!!.answerPhase)
    }

    @Test fun zeroAnswerTimeStartsWithRevealImmediately() {
        val frame = ChallengeTimeline.at(0, 1, 0, 3)!!
        assertTrue(frame.answerPhase)
        assertEquals(3000L, frame.phaseDurationMs)
    }

    @Test fun zeroRevealTimeMovesDirectlyToNextQuestion() {
        val frame = ChallengeTimeline.at(2000, 2, 2, 0)!!
        assertEquals(1, frame.index)
        assertFalse(frame.answerPhase)
    }

    @Test fun bothTimersZeroGoStraightToCompletionSlate() {
        assertTrue(ChallengeTimeline.at(0, 1, 0, 0)!!.complete)
    }

    @Test fun timerProgressAdvancesDuringQuestionAndStaysFullOnReveal() {
        val start = ChallengeTimeline.at(0, 1, 4, 3)!!
        val halfway = ChallengeTimeline.at(2000, 1, 4, 3)!!
        val reveal = ChallengeTimeline.at(4000, 1, 4, 3)!!
        assertEquals(0f, ChallengeTimeline.timerProgress(start), 0.001f)
        assertEquals(0.5f, ChallengeTimeline.timerProgress(halfway), 0.001f)
        assertTrue(reveal.answerPhase)
        assertEquals(1f, ChallengeTimeline.timerProgress(reveal), 0.001f)
    }

    @Test fun timerProgressHandlesZeroLengthQuestion() {
        val reveal = ChallengeTimeline.at(0, 1, 0, 3)!!
        assertEquals(1f, ChallengeTimeline.timerProgress(reveal), 0.001f)
    }

    @Test fun zeroDurationChallengeHasAStableCompletionHold() {
        assertTrue(ChallengeTimeline.at(0, 3, 0, 0)!!.complete)
        assertTrue(ChallengeTimeline.at(1599, 3, 0, 0)!!.complete)
        assertNull(ChallengeTimeline.at(1600, 3, 0, 0))
    }
}
