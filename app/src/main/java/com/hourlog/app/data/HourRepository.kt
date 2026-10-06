package com.hourlog.app.data

import androidx.room.withTransaction
import com.hourlog.app.domain.*
import com.hourlog.app.export.Backup
import com.hourlog.app.export.BackupCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OverlapException(val conflicting: List<WorkEntry>) : IllegalArgumentException("OVERLAP")
class HourRepository(private val db: HourLogDatabase, private val store: PreferenceStore) {
    private val mutex = Mutex()
    val entries = db.entries().observe().map { list -> list.map { it.domain() } }
    val preferences = combine(store.flow, db.entries().observeRecovery()) { p, recovery ->
        recovery?.let { BackupCodec.decodePreferences(it.json) } ?: p
    }

    suspend fun snapshot(): Backup = mutex.withLock { Backup(entries = db.entries().all().map { it.domain() }, preferences = preferences.first()) }
    suspend fun settings(prefs: Preferences, keepAppearance: Boolean = false) = mutex.withLock {
        val updated = if (keepAppearance) preferences.first().let { prefs.copy(appearance=it.appearance, colorSeed=it.colorSeed) } else prefs
        persistPreferences(updated)
    }
    suspend fun appearance(mode: Appearance?, color: String?) = mutex.withLock {
        val current = preferences.first()
        persistPreferences(current.copy(appearance=mode ?: current.appearance, colorSeed=color ?: current.colorSeed))
    }
    private suspend fun persistPreferences(prefs: Preferences) {
        prefs.validate()
        db.entries().setRecovery(PreferenceRecovery(json = BackupCodec.encodePreferences(prefs)))
        finishPreferenceRecovery()
    }
    suspend fun recoverPreferences() = mutex.withLock { finishPreferenceRecovery() }
    private suspend fun finishPreferenceRecovery() {
        val recovery = db.entries().recovery() ?: return
        try {
            store.replace(BackupCodec.decodePreferences(recovery.json))
            db.entries().clearRecovery()
        } catch (e: CancellationException) { throw e }
        catch (e: java.io.IOException) {
            // The journal remains authoritative and durable until DataStore can be written.
        }
    }

    suspend fun save(list: List<WorkEntry>, allowOverlap: Boolean = false) = mutex.withLock {
        list.forEach { it.validate() }
        val ids = list.map { it.id }.toSet()
        val existing = db.entries().all().map { it.domain() }.filterNot { it.id in ids }
        val conflicts = list.flatMap { a -> (existing + list).filter { b -> TimeCalculator.overlaps(a, b) } }.distinctBy { it.id }
        if (conflicts.isNotEmpty() && !allowOverlap) throw OverlapException(conflicts)
        db.withTransaction {
            list.forEach {
                val entry = it.copy(overlapConfirmed = allowOverlap || it.overlapConfirmed)
                db.entries().delete(entry.id)
                db.entries().insert(entry.entity())
                db.entries().insertBreaks(entry.breakEntities())
            }
        }
    }
    suspend fun delete(id: String) = mutex.withLock { db.entries().delete(id) }

    suspend fun restore(backup: Backup) = mutex.withLock {
        backup.validate()
        db.withTransaction {
            db.entries().clear()
            backup.entries.forEach { db.entries().insert(it.entity()); db.entries().insertBreaks(it.breakEntities()) }
            db.entries().setRecovery(PreferenceRecovery(json = BackupCodec.encodePreferences(backup.preferences)))
        }
        finishPreferenceRecovery()
    }
}
