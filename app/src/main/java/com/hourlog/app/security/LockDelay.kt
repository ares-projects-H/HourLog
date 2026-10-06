package com.hourlog.app.security

enum class DelayUnit(val seconds: Int) { SECONDS(1), MINUTES(60), HOURS(3600) }

object LockDelay {
    const val MAX_SECONDS = 86400
    fun parse(amount: String, unit: DelayUnit): Int {
        require(amount.matches(Regex("[0-9]{1,6}")))
        val seconds = amount.toLong() * unit.seconds
        require(seconds in 0..MAX_SECONDS.toLong())
        return seconds.toInt()
    }
    fun displayUnit(seconds: Int) = when {
        seconds > 0 && seconds % 3600 == 0 -> DelayUnit.HOURS
        seconds > 0 && seconds % 60 == 0 -> DelayUnit.MINUTES
        else -> DelayUnit.SECONDS
    }
}
