package com.hourlog.app.export

import com.hourlog.app.domain.*
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class Backup(val version: Int = 1, val createdAt: String = Instant.now().toString(),
    val entries: List<WorkEntry>, val preferences: Preferences) {
    fun validate() {
        require(version == 1) { "UNSUPPORTED_VERSION" }
        Instant.parse(createdAt)
        require(entries.size <= 100000)
        require(entries.map { it.id }.distinct().size == entries.size)
        val breakIds = entries.flatMap { it.breaks }.map { it.id }
        require(breakIds.distinct().size == breakIds.size)
        entries.forEach { it.validate() }
        preferences.validate()
    }
}

object BackupCodec {
    const val MAX_BYTES = 10 * 1024 * 1024
    private val json = Json { prettyPrint = true }
    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content
    private fun JsonObject.long(key: String) = getValue(key).jsonPrimitive.long
    private fun JsonObject.bool(key: String) = getValue(key).jsonPrimitive.boolean

    private fun prefs(p: Preferences) = buildJsonObject {
        put("hourlyRate", p.hourlyRate.toPlainString()); put("currency", p.currency)
        put("overtimeThresholdMinutes", p.overtimeThresholdMinutes); put("overtimeMultiplier", p.overtimeMultiplier.toPlainString())
        put("hourFormat", p.hourFormat.name); put("appearance", p.appearance.name); put("defaultBreak", p.defaultBreak.name)
        put("reminderEnabled", p.reminderEnabled); put("reminderDay", p.reminderDay)
        put("reminderHour", p.reminderHour); put("reminderMinute", p.reminderMinute)
    }
    private fun readPrefs(o: JsonObject) = Preferences(
        BigDecimal(o.text("hourlyRate")), o.text("currency"), o.long("overtimeThresholdMinutes"),
        BigDecimal(o.text("overtimeMultiplier")), HourFormat.valueOf(o.text("hourFormat")),
        Appearance.valueOf(o.text("appearance")), DefaultBreak.valueOf(o.text("defaultBreak")),
        o.bool("reminderEnabled"), o.long("reminderDay").toIntExact(), o.long("reminderHour").toIntExact(), o.long("reminderMinute").toIntExact(),
    ).also { it.validate() }
    private fun Long.toIntExact(): Int = Math.toIntExact(this)
    fun encodePreferences(p: Preferences): String = prefs(p).toString()
    fun decodePreferences(s: String): Preferences = readPrefs(json.parseToJsonElement(s).jsonObject)

    fun encode(backup: Backup): String {
        backup.validate()
        val encoded = json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("application", "HourLog"); put("version", backup.version); put("createdAt", backup.createdAt)
            put("preferences", prefs(backup.preferences))
            putJsonArray("entries") { backup.entries.forEach { e -> add(buildJsonObject {
                put("id", e.id); put("date", e.date.toString()); put("start", e.start.epochSecond); put("end", e.end.epochSecond)
                put("zoneId", e.zoneId); put("note", e.note); put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
                put("overlapConfirmed", e.overlapConfirmed)
                putJsonArray("breaks") { e.breaks.forEach { b -> add(buildJsonObject {
                    put("id", b.id); put("minutes", b.minutes); put("paid", b.paid); put("createdAt", b.createdAt); put("updatedAt", b.updatedAt)
                }) } }
            }) } }
        })
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "BACKUP_TOO_LARGE" }
        return encoded
    }

    fun decode(s: String): Backup {
        require(s.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        val root = json.parseToJsonElement(s).jsonObject
        require(root.text("application") == "HourLog")
        val version = root.long("version").toIntExact()
        require(version == 1) { "UNSUPPORTED_VERSION" }
        val entries = root.getValue("entries").jsonArray.map { raw ->
            val o = raw.jsonObject
            WorkEntry(o.text("id"), LocalDate.parse(o.text("date")), Instant.ofEpochSecond(o.long("start")),
                Instant.ofEpochSecond(o.long("end")), o.text("zoneId"),
                o.getValue("breaks").jsonArray.map { b -> b.jsonObject.let {
                    WorkBreak(it.text("id"), it.long("minutes"), it.bool("paid"), it.long("createdAt"), it.long("updatedAt"))
                } }, o.text("note"), o.long("createdAt"), o.long("updatedAt"), o.bool("overlapConfirmed"))
        }
        return Backup(version, root.text("createdAt"), entries, readPrefs(root.getValue("preferences").jsonObject)).also { it.validate() }
    }
}
