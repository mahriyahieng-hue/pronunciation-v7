package com.bamah.reelstudio

import org.json.JSONArray
import org.json.JSONObject

data class WordItem(
    val word: String = "",
    val definition: String = "",
    val pronunciation: String = "",
    val emoji: String = ""
)

data class ChallengeConfig(
    val title: String = "10-WORD CHALLENGE",
    val ctaText: String = "CAN YOU GET 10/10?",
    val answerSeconds: Int = 2,
    val revealSeconds: Int = 2,
    val theme: String = "reference",
    val audioMode: String = "both",
    val mainColorHex: String = "#191817",
    val accentColorHex: String = "#E9A17B",
    val cardBackgroundHex: String = "#191817",
    val cardTextHex: String = "#F2F0ED",
    val showChallengeLabel: Boolean = true,
    val showCta: Boolean = true,
    val showTopTab: Boolean = true,
    val showIcon: Boolean = true,
    val showTimer: Boolean = true,
    val showBottomProgress: Boolean = true,
    val labelBackgroundOpacity: Int = 14
)

object ChallengeJson {
    fun encode(config: ChallengeConfig, words: List<WordItem>): String {
        val root = JSONObject()
        root.put("title", config.title)
        root.put("ctaText", config.ctaText)
        root.put("answerTime", config.answerSeconds)
        root.put("revealTime", config.revealSeconds)
        root.put("theme", config.theme)
        root.put("audioMode", config.audioMode)
        root.put("mainColor", config.mainColorHex)
        root.put("accentColor", config.accentColorHex)
        root.put("cardBackgroundColor", config.cardBackgroundHex)
        root.put("cardTextColor", config.cardTextHex)
        root.put("showChallengeLabel", config.showChallengeLabel)
        root.put("showCta", config.showCta)
        root.put("showTopTab", config.showTopTab)
        root.put("showIcon", config.showIcon)
        root.put("showTimer", config.showTimer)
        root.put("showBottomProgress", config.showBottomProgress)
        root.put("labelBackgroundOpacity", config.labelBackgroundOpacity)
        val array = JSONArray()
        words.forEach { w ->
            array.put(JSONObject().put("word", w.word).put("definition", w.definition)
                .put("pronunciation", w.pronunciation).put("emoji", w.emoji))
        }
        root.put("words", array)
        return root.toString(2)
    }

    fun decode(raw: String): Pair<ChallengeConfig?, List<WordItem>> {
        val trimmed = raw.trim()
        val root = if (trimmed.startsWith("{")) JSONObject(trimmed) else null
        val array = root?.optJSONArray("words") ?: JSONArray(trimmed)
        val words = buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(WordItem(
                    item.optString("word", ""),
                    item.optString("definition", ""),
                    item.optString("pronunciation", ""),
                    item.optString("emoji", "")
                ))
            }
        }
        val config = if (root?.has("words") == true) {
            val oldBg = safeHexColor(root.optString("cardBackgroundColor", "#191817"), "#191817")
            ChallengeConfig(
                title = root.optString("title", "10-WORD CHALLENGE"),
                ctaText = root.optString("ctaText", "CAN YOU GET 10/10?"),
                answerSeconds = root.optInt("answerTime", 2).coerceIn(0, 60),
                revealSeconds = root.optInt("revealTime", 2).coerceIn(0, 60),
                theme = root.optString("theme", "reference"),
                audioMode = root.optString("audioMode", "both"),
                mainColorHex = safeHexColor(root.optString("mainColor", oldBg), "#191817"),
                accentColorHex = safeHexColor(root.optString("accentColor", "#E9A17B"), "#E9A17B"),
                cardBackgroundHex = safeHexColor(root.optString("cardBackgroundColor", oldBg), "#191817"),
                cardTextHex = safeHexColor(root.optString("cardTextColor", "#F2F0ED"), "#F2F0ED"),
                showChallengeLabel = root.optBoolean("showChallengeLabel", true),
                showCta = root.optBoolean("showCta", true),
                showTopTab = root.optBoolean("showTopTab", true),
                showIcon = root.optBoolean("showIcon", true),
                showTimer = root.optBoolean("showTimer", true),
                showBottomProgress = root.optBoolean("showBottomProgress", true),
                labelBackgroundOpacity = root.optInt("labelBackgroundOpacity", 14).coerceIn(0, 40)
            )
        } else null
        return config to words
    }
}

private fun safeHexColor(value: String, fallback: String): String =
    if (Regex("^#[0-9A-Fa-f]{6}$").matches(value)) value else fallback

val sampleWords = listOf(
    WordItem("Specific", "Clearly defined or exact.", "spuh-SIF-ik", "🎯"),
    WordItem("Comfortable", "Making you feel relaxed and at ease.", "KUMF-tuh-buhl", "🛋️"),
    WordItem("Wednesday", "The day after Tuesday.", "WENZ-day", "📅"),
    WordItem("Vegetable", "An edible plant or part of a plant.", "VEJ-tuh-buhl", "🥦"),
    WordItem("Entrepreneur", "Someone who starts a business.", "ahn-truh-pruh-NUR", "💡")
)

data class TimelineFrame(
    val index: Int,
    val answerPhase: Boolean,
    val elapsedInPhaseMs: Long,
    val phaseDurationMs: Long,
    val complete: Boolean = false
)

object ChallengeTimeline {
    const val COMPLETE_HOLD_MS = 1600L

    /** Question line fills continuously; the reveal face holds it at 100%. */
    fun timerProgress(frame: TimelineFrame): Float = if (frame.complete || frame.answerPhase) 1f else
        (frame.elapsedInPhaseMs.toFloat() / frame.phaseDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)

    fun at(timeMs: Long, wordsCount: Int, answerSeconds: Int, revealSeconds: Int): TimelineFrame? {
        if (wordsCount <= 0 || timeMs < 0) return null
        val questionMs = answerSeconds.coerceIn(0, 60) * 1000L
        val revealMs = revealSeconds.coerceIn(0, 60) * 1000L
        val itemMs = questionMs + revealMs
        if (itemMs == 0L) {
            return if (timeMs < COMPLETE_HOLD_MS) TimelineFrame(wordsCount - 1, true, 0, COMPLETE_HOLD_MS, true) else null
        }
        val challengeMs = wordsCount * itemMs
        if (timeMs >= challengeMs) {
            return if (timeMs < challengeMs + COMPLETE_HOLD_MS) {
                TimelineFrame(wordsCount - 1, answerPhase = false, elapsedInPhaseMs = 0,
                    phaseDurationMs = COMPLETE_HOLD_MS, complete = true)
            } else null
        }
        val index = (timeMs / itemMs).toInt()
        val withinItem = timeMs % itemMs
        return if (withinItem < questionMs) {
            TimelineFrame(index, false, withinItem, questionMs)
        } else {
            TimelineFrame(index, true, withinItem - questionMs, revealMs)
        }
    }
}
