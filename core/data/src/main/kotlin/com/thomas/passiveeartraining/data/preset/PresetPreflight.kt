package com.thomas.passiveeartraining.data.preset

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.StreamReadConstraints
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.core.exc.StreamConstraintsException
import java.io.StringReader

/**
 * Structural preflight for an imported document.
 *
 * Reading is bounded before any object is built: nesting depth, individual string length and
 * duplicate keys are checked with a streaming parser, so a hostile or corrupt file cannot exhaust
 * memory or smuggle a duplicate field past the decoder. This runs before the typed decode, and the
 * limits are constants so a caller cannot widen them by accident.
 */
object PresetPreflight {

    /** Deepest nesting a document may use. */
    const val MAX_NESTING_DEPTH: Int = 32

    /** Longest single string a document may contain. */
    const val MAX_STRING_LENGTH: Int = 16 * 1024

    private val factory: JsonFactory = JsonFactory.builder()
        // Duplicate keys are a document defect: one of them would silently win.
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
        .streamReadConstraints(
            StreamReadConstraints.builder()
                .maxNestingDepth(MAX_NESTING_DEPTH)
                .maxStringLength(MAX_STRING_LENGTH)
                .build()
        )
        .build()

    /**
     * Checks the structure of [text] without building any objects.
     *
     * @throws PresetValidationException describing the first structural problem found.
     */
    fun check(text: String) {
        factory.createParser(StringReader(text)).use { parser ->
            try {
                // A parser starts before its first token, so it must be advanced before the walk.
                if (parser.nextToken() == null) {
                    throw PresetValidationException("The document is empty")
                }
                checkValue(parser, depth = 0)
                if (parser.nextToken() != null) {
                    throw PresetValidationException(
                        "The document has trailing content after the root value"
                    )
                }
            } catch (failure: StreamConstraintsException) {
                // Jackson reports the specific bound it hit, which is more useful than a generic error.
                throw PresetValidationException(
                    "The document exceeds a structural limit: ${failure.message}"
                )
            } catch (failure: com.fasterxml.jackson.core.JsonProcessingException) {
                throw PresetValidationException(
                    "The document is not valid JSON: ${failure.originalMessage}"
                )
            }
        }
    }

    /** Walks one value, enforcing depth and string limits as it goes. */
    private fun checkValue(parser: JsonParser, depth: Int) {
        if (depth > MAX_NESTING_DEPTH) {
            throw PresetValidationException(
                "The document nests deeper than $MAX_NESTING_DEPTH levels"
            )
        }
        when (parser.currentToken()) {
            JsonToken.START_OBJECT -> checkObject(parser, depth)
            JsonToken.START_ARRAY -> {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() == null) {
                        throw PresetValidationException("The document ends inside an array")
                    }
                    checkValue(parser, depth + 1)
                }
            }
            JsonToken.VALUE_STRING -> {
                val length = parser.textLength
                if (length > MAX_STRING_LENGTH) {
                    throw PresetValidationException(
                        "A string is $length characters, above the $MAX_STRING_LENGTH limit"
                    )
                }
            }
            null -> throw PresetValidationException("The document is empty")
            else -> Unit
        }
    }

    /**
     * Walks an object, rejecting over-long keys.
     *
     * Duplicate detection is Jackson's own strict feature, enabled on the factory, so it is not
     * repeated here; this walk enforces the bounds Jackson does not.
     */
    private fun checkObject(parser: JsonParser, depth: Int) {
        while (true) {
            val token = parser.nextToken()
            if (token == JsonToken.END_OBJECT) return
            if (token == null) {
                throw PresetValidationException("The document ends inside an object")
            }
            if (token != JsonToken.FIELD_NAME) {
                throw PresetValidationException(
                    "The document has a value where a field name was expected"
                )
            }
            val name = parser.currentName()
            if (name.length > MAX_STRING_LENGTH) {
                throw PresetValidationException(
                    "A field name is longer than $MAX_STRING_LENGTH characters"
                )
            }
            parser.nextToken()
            checkValue(parser, depth + 1)
        }
    }
}
