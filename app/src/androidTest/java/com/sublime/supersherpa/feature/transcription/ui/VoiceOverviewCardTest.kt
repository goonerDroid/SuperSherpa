package com.sublime.supersherpa.feature.transcription.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.sublime.supersherpa.core.ai.modeldelivery.model.ModelSource
import com.sublime.supersherpa.feature.transcription.presentation.VoiceState
import com.sublime.supersherpa.ui.theme.SuperSherpaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceOverviewCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun listeningTranscriptLongerThanLegacyCutoffTypesOneCharacterAtATime() {
        val longTranscript = longTranscript(wordCount = 36)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SuperSherpaTheme {
                VoiceOverviewCard(
                    voiceState = VoiceState.Listening(partialTranscript = longTranscript),
                    modelSource = ModelSource.Ota,
                    onCopyText = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText(longTranscript).assertDoesNotExist()
        composeRule.onNodeWithText(longTranscript.take(1)).assertExists()

        composeRule.mainClock.advanceTimeBy(14L * longTranscript.length + 100L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText(longTranscript).assertExists()
    }

    @Test
    fun listeningTranscriptAutoScrollsToNewestLiveText() {
        val longTranscript = longTranscript(wordCount = 90)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SuperSherpaTheme {
                VoiceOverviewCard(
                    voiceState = VoiceState.Listening(partialTranscript = longTranscript),
                    modelSource = ModelSource.Ota,
                    onCopyText = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(14L * longTranscript.length + 100L)
        composeRule.waitForIdle()

        val scrollNode = composeRule.onNodeWithTag("live_transcript_scroll")
            .fetchSemanticsNode()
        val scrollRange = scrollNode.config[SemanticsProperties.VerticalScrollAxisRange]

        assertEquals(scrollRange.maxValue(), scrollRange.value(), 1f)
    }

    @Test
    fun correctedListeningTranscriptKeepsCommonPrefixThenTypesForward() {
        var transcript by mutableStateOf("hello wrld")

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SuperSherpaTheme {
                VoiceOverviewCard(
                    voiceState = VoiceState.Listening(partialTranscript = transcript),
                    modelSource = ModelSource.Ota,
                    onCopyText = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(14L * transcript.length + 50L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("hello wrld").assertExists()

        composeRule.runOnIdle {
            transcript = "hello world today"
        }
        composeRule.mainClock.advanceTimeBy(14L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("hello w").assertExists()

        composeRule.mainClock.advanceTimeBy(14L * transcript.length + 50L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("hello world today").assertExists()
    }

    @Test
    fun incomingPartialDoesNotGetMergedIntoSyntheticTranscript() {
        var transcript by mutableStateOf("hello world")

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SuperSherpaTheme {
                VoiceOverviewCard(
                    voiceState = VoiceState.Listening(partialTranscript = transcript),
                    modelSource = ModelSource.Ota,
                    onCopyText = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(14L * transcript.length + 50L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("hello world").assertExists()

        composeRule.runOnIdle {
            transcript = "from android"
        }
        composeRule.mainClock.advanceTimeBy(28L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("hello world f").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(14L * transcript.length + 50L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("from android").assertExists()
    }

    @Test
    fun newListeningSessionDoesNotReusePreviousTranscriptTarget() {
        var voiceState by mutableStateOf<VoiceState>(
            VoiceState.Result(transcript = "first recording old text"),
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SuperSherpaTheme {
                VoiceOverviewCard(
                    voiceState = voiceState,
                    modelSource = ModelSource.Ota,
                    onCopyText = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("first recording old text").assertExists()

        composeRule.runOnIdle {
            voiceState = VoiceState.Listening(partialTranscript = "second")
        }
        composeRule.mainClock.advanceTimeBy(14L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("first recording old text second").assertDoesNotExist()
        composeRule.onNodeWithText("s").assertExists()

        composeRule.mainClock.advanceTimeBy(14L * "econd".length + 50L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("second").assertExists()
    }

    @Test
    fun repeatedPartialWindowDoesNotCreateSyntheticDuplicate() {
        var transcript by mutableStateOf("please write this sentence")

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SuperSherpaTheme {
                VoiceOverviewCard(
                    voiceState = VoiceState.Listening(partialTranscript = transcript),
                    modelSource = ModelSource.Ota,
                    onCopyText = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(14L * transcript.length + 50L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("please write this sentence").assertExists()

        composeRule.runOnIdle {
            transcript = "write this sentence"
        }
        composeRule.mainClock.advanceTimeBy(14L * transcript.length + 50L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("please write this sentence write this sentence")
            .assertDoesNotExist()
        composeRule.onNodeWithText("write this sentence").assertExists()
    }

    @Test
    fun processingKeepsRenderedDraftUntilFinalTranscriptReplacesIt() {
        var voiceState by mutableStateOf<VoiceState>(
            VoiceState.Listening(partialTranscript = "draft transcript from live audio"),
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            SuperSherpaTheme {
                VoiceOverviewCard(
                    voiceState = voiceState,
                    modelSource = ModelSource.Ota,
                    onCopyText = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(14L * "draft transcript from live audio".length + 50L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("draft transcript from live audio").assertExists()

        composeRule.runOnIdle {
            voiceState = VoiceState.Processing()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Your transcription will appear here.").assertDoesNotExist()
        composeRule.onNodeWithText("draft transcript from live audio").assertExists()

        composeRule.runOnIdle {
            voiceState = VoiceState.Result(transcript = "final transcript from full audio")
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("draft transcript from live audio").assertExists()

        composeRule.mainClock.advanceTimeBy(32L * "final transcript from full audio".length + 50L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("draft transcript from live audio").assertDoesNotExist()
        composeRule.onNodeWithText("final transcript from full audio").assertExists()
    }

    private fun longTranscript(wordCount: Int): String =
        (1..wordCount).joinToString(separator = " ") { index ->
            "word$index"
        }
}
