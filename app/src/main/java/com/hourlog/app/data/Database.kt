package com.hourlog.app.data

import androidx.room.*
import com.hourlog.app.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "work_entries", indices = [Index("date")])
data class EntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    val start: Long,
    val end: Long,
    val zoneId: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
    val overlapConfirmed: Boolean,
)
@Entity(tableName = "break_entries", foreignKeys = [ForeignKey(entity = EntryEntity::class,
    parentColumns = ["id"], childColumns = ["workEntryId"], onDelete = ForeignKey.CASCADE)], indices = [Index("workEntryId")])
data class BreakEntity(@PrimaryKey val id: String, val workEntryId: String, val minutes: Long,
    val paid: Boolean, val createdAt: Long, val updatedAt: Long)
/** A committed restore can recover its DataStore settings after process interruption. */
@Entity(tableName = "preference_recovery")
data class PreferenceRecovery(@PrimaryKey val id: Int = 1, val json: String)
data class EntryWithBreaks(@Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "workEntryId") val breaks: List<BreakEntity>) {
    fun domain() = WorkEntry(entry.id, LocalDate.parse(entry.date), Instant.ofEpochSecond(entry.start),
        Instant.ofEpochSecond(entry.end), entry.zoneId,
        breaks.sortedBy { it.createdAt }.map { WorkBreak(it.id, it.minutes, it.paid, it.createdAt, it.updatedAt) },
        entry.note, entry.createdAt, entry.updatedAt, entry.overlapConfirmed)
}
@Dao
interface EntryDao {
    @Transaction @Query("SELECT * FROM work_entries ORDER BY start") fun observe(): Flow<List<EntryWithBreaks>>
    @Transaction @Query("SELECT * FROM work_entries ORDER BY start") suspend fun all(): List<EntryWithBreaks>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entry: EntryEntity)
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertBreaks(breaks: List<BreakEntity>)
    @Query("DELETE FROM work_entries WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM work_entries") suspend fun clear()
    @Query("SELECT * FROM preference_recovery WHERE id = 1") fun observeRecovery(): Flow<PreferenceRecovery?>
    @Query("SELECT * FROM preference_recovery WHERE id = 1") suspend fun recovery(): PreferenceRecovery?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setRecovery(recovery: PreferenceRecovery)
    @Query("DELETE FROM preference_recovery") suspend fun clearRecovery()
}
@Database(entities = [EntryEntity::class, BreakEntity::class, PreferenceRecovery::class], version = 1, exportSchema = true)
abstract class HourLogDatabase : RoomDatabase() { abstract fun entries(): EntryDao }

fun WorkEntry.entity() = EntryEntity(id, date.toString(), start.epochSecond, end.epochSecond, zoneId, note, createdAt, updatedAt, overlapConfirmed)
fun WorkEntry.breakEntities() = breaks.map { BreakEntity(it.id, id, it.minutes, it.paid, it.createdAt, it.updatedAt) }
