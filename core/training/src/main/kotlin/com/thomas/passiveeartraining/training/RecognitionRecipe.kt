package com.thomas.passiveeartraining.training

import kotlin.time.Duration

/** Semantic identifier of a bundled voice clip, for example `interval.perfect_fifth`. */
@JvmInline
value class VoiceAssetId(val value: String) {
    init {
        if (value.isBlank()) {
            throw InvalidTimelineException("Voice asset id must not be blank")
        }
    }

    override fun toString(): String = value

    companion object {
        const val ANSWER_ID_PREFIX: String = "interval."

        /** Answer clip identifier for an interval label, for example `interval.minor_third`. */
        fun forInterval(labelId: String): VoiceAssetId {
            if (labelId.isBlank()) {
                throw InvalidTimelineException("Interval label id must not be blank")
            }
            return VoiceAssetId("$ANSWER_ID_PREFIX$labelId")
        }
    }
}

/**
 * Measured length of every bundled voice clip.
 *
 * Stage 2 derives these durations from exact manifest frame counts and a documented rounding
 * rule. Hand-entered milliseconds are not acceptable input for a shipped pack, so this port is
 * the only place the domain learns how long an answer clip is.
 */
interface VoiceDurationCatalog {
    /** Duration of [voiceId]. */
    fun durationOf(voiceId: VoiceAssetId): Duration
}

/** Simple immutable catalog used by tests, fixtures and (later) the asset adapter. */
class MapVoiceDurationCatalog(private val durations: Map<VoiceAssetId, Duration>) :
    VoiceDurationCatalog {
    override fun durationOf(voiceId: VoiceAssetId): Duration =
        durations[voiceId] ?: throw MissingVoiceDurationException(voiceId)

    companion object {
        fun of(vararg entries: Pair<String, Duration>): MapVoiceDurationCatalog =
            MapVoiceDurationCatalog(
                entries.associate { (id, duration) -> VoiceAssetId(id) to duration }
            )
    }
}

/** Raised when the asset pack does not provide a required answer clip. */
class MissingVoiceDurationException(val voiceId: VoiceAssetId) :
    IllegalArgumentException("No measured duration for voice asset '$voiceId'")

/**
 * Scheduling times of one recognition exercise.
 *
 * The recipe defines only timing; the presentation belongs to the concrete exercise, so a
 * recipe and an exercise can never disagree about direction.
 */
data class RecognitionRecipe(
    val noteDuration: Duration,
    val melodicGap: Duration,
    val thinkingSilence: Duration,
    val postAnswerSilence: Duration
) {
    companion object {
        /** Roadmap default: 700 ms notes, 100 ms melodic gap, 4 s thinking, 2 s final silence. */
        val DEFAULT: RecognitionRecipe = RecognitionRecipe(
            noteDuration = Duration.parse("700ms"),
            melodicGap = Duration.parse("100ms"),
            // 4 s until the first phone listening review called it "a bit too long"; 2 s still
            // separates the example from the answer without feeling like a wait.
            thinkingSilence = Duration.parse("2s"),
            postAnswerSilence = Duration.parse("2s")
        )

        /** Maximum compiled length of one exercise, including every silence. */
        val EXERCISE_CAP: Duration = Duration.parse("60s")

        /**
         * Named timing profiles offered as user presets.
         *
         * The thinking silence in particular is a preference: the first phone listening review
         * called 4 s "a bit too long" and asked for the setting to remain user-adjustable, so the
         * values live in one table that a future settings screen can present and persist. Timing is
         * domain configuration, so a profile is stored with the configuration, never with playback
         * state.
         */
        private val profileTable: Map<String, RecognitionRecipe> by lazy {
            linkedMapOf(
                "spacious" to RecognitionRecipe(
                    noteDuration = Duration.parse("700ms"),
                    melodicGap = Duration.parse("100ms"),
                    thinkingSilence = Duration.parse("4s"),
                    postAnswerSilence = Duration.parse("2s")
                ),
                "default" to DEFAULT,
                "quick" to RecognitionRecipe(
                    noteDuration = Duration.parse("600ms"),
                    melodicGap = Duration.parse("80ms"),
                    thinkingSilence = Duration.parse("1s"),
                    postAnswerSilence = Duration.parse("1.5s")
                )
            )
        }

        /**
         * Named timing profiles, built on first use.
         *
         * The table is lazy because it names [DEFAULT] by reference: building it during companion
         * initialization would capture a not-yet-initialized value, which a test caught as the
         * "default" profile differing from the documented default.
         */
        val PROFILES: Map<String, RecognitionRecipe> get() = profileTable
    }
}
