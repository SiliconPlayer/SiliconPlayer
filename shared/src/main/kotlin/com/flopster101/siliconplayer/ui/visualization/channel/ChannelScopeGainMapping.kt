package com.flopster101.siliconplayer.ui.visualization.channel

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

object ChannelScopeGainMapping {
    const val MIN_GAIN_PERCENT = 10
    const val MAX_GAIN_PERCENT = 5000

    private val DB_MIN = 20.0 * log10(MIN_GAIN_PERCENT.toDouble() / 100.0)
    private val DB_MAX = 20.0 * log10(MAX_GAIN_PERCENT.toDouble() / 100.0)
    private val DB_RANGE = DB_MAX - DB_MIN

    fun gainPercentToFraction(gainPercent: Int): Float {
        val clamped = gainPercent.coerceIn(MIN_GAIN_PERCENT, MAX_GAIN_PERCENT)
        val db = 20.0 * log10(clamped.toDouble() / 100.0)
        return ((db - DB_MIN) / DB_RANGE).toFloat().coerceIn(0f, 1f)
    }

    fun fractionToGainPercent(fraction: Float): Int {
        val f = fraction.coerceIn(0f, 1f).toDouble()
        val db = DB_MIN + f * DB_RANGE
        val rawGain = 100.0 * 10.0.pow(db / 20.0)
        return snapGainPercent(rawGain.roundToInt())
    }

    fun snapGainPercent(raw: Int): Int {
        val clamped = raw.coerceIn(MIN_GAIN_PERCENT, MAX_GAIN_PERCENT)
        return when {
            clamped < 50 -> {
                val step = 5
                val rounded = ((clamped + step / 2) / step) * step
                rounded.coerceIn(MIN_GAIN_PERCENT, 50)
            }
            clamped < 300 -> {
                val step = 10
                val rounded = ((clamped + step / 2) / step) * step
                rounded.coerceIn(50, 300)
            }
            clamped < 600 -> {
                val step = 20
                val rounded = ((clamped + step / 2) / step) * step
                rounded.coerceIn(300, 600)
            }
            clamped < 1500 -> {
                val step = 50
                val rounded = ((clamped + step / 2) / step) * step
                rounded.coerceIn(600, 1500)
            }
            else -> {
                val step = 100
                val rounded = ((clamped + step / 2) / step) * step
                rounded.coerceIn(1500, MAX_GAIN_PERCENT)
            }
        }
    }

    fun nudgeGainPercent(current: Int, increase: Boolean): Int {
        val step = when {
            if (increase) current < 50 else current <= 50 -> 5
            if (increase) current < 300 else current <= 300 -> 10
            if (increase) current < 600 else current <= 600 -> 20
            if (increase) current < 1500 else current <= 1500 -> 50
            else -> 100
        }
        val next = if (increase) current + step else current - step
        return snapGainPercent(next)
    }
}
