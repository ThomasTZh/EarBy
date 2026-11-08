package com.thomas.passiveeartraining.training.provider

import com.thomas.passiveeartraining.training.VoiceAssetId

/** One problem with a candidate configuration, attached to a field the UI can point at. */
data class FieldIssue(
    /** Stable field id, for example `range` or `qualities`. */
    val field: String,
    /** Text the UI can show next to the field. */
    val message: String
)

/** One answer category the provider can generate for a validated configuration. */
data class AnswerCategory(
    /** Stable semantic id, for example `interval.perfect_fifth`. */
    val id: String
)

/**
 * The verdict on one provider configuration.
 *
 * [Valid] carries the normalized configuration, every reachable answer category, the exact or
 * conservative reachable *content* bound (the longest example window and the longest composed
 * answer under the default catalogue) and the required semantic voice tokens. The shared recipe
 * evaluator re-times those content bounds for the user's timing settings; a provider never reports
 * an invalid draft as valid (SC2/SC4).
 */
sealed interface ProviderValidation {
    data class Valid(
        val config: ProviderConfig,
        val categories: List<AnswerCategory>,
        /** Longest context+target example window, measured with the injected catalogue. */
        val maximumExampleDuration: kotlin.time.Duration,
        /** Longest composed answer, including the exactly-80 ms token gaps. */
        val maximumAnswerDuration: kotlin.time.Duration,
        val requiredAssets: Set<VoiceAssetId>
    ) : ProviderValidation

    data class Invalid(val issues: List<FieldIssue>) : ProviderValidation {
        init {
            require(issues.isNotEmpty()) { "An invalid validation must name at least one issue" }
        }
    }
}
