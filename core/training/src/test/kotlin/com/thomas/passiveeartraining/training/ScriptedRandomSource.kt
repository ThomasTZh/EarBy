package com.thomas.passiveeartraining.training

/** Scripted random source: returns recorded values in order and fails loudly when they run out. */
class ScriptedRandomSource(private val values: List<Int>, private val fallback: Int? = null) :
    RandomSource {
    val calls = mutableListOf<Int>()

    constructor(vararg values: Int) : this(values.toList())

    override fun nextInt(bound: Int): Int {
        if (bound <=
            0
        ) {
            throw InvalidRandomBoundException("Random bound must be positive but was $bound")
        }
        calls += bound
        if (calls.size > values.size) {
            return fallback
                ?: throw AssertionError(
                    "Scripted random source exhausted after ${values.size} draws (bound=$bound)"
                )
        }
        val value = values[calls.size - 1]
        if (value !in 0 until bound) {
            throw AssertionError("Scripted value $value is outside 0..${bound - 1}")
        }
        return value
    }
}
