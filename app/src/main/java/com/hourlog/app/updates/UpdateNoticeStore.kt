package com.hourlog.app.updates

import android.content.Context

/** Device-local UI choice, independent of exported work-data preferences. */
class UpdateNoticeStore(context: Context) {
    private val preferences = context.getSharedPreferences("hourlog_update_notice",Context.MODE_PRIVATE)
    val hidden: Boolean get() = preferences.getBoolean("hidden",false)
    fun setHidden(hidden: Boolean) { check(preferences.edit().putBoolean("hidden",hidden).commit()) }
}
