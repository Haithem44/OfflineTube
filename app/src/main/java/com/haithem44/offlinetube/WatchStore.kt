package com.haithem44.offlinetube

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Entity(tableName = "watch_state")
data class WatchState(
    @PrimaryKey val videoId: Long,
    val position: Long,
    val watched: Boolean
)

@Dao
interface WatchDao {
    @Query("SELECT * FROM watch_state")
    fun getAll(): List<WatchState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(s: WatchState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(list: List<WatchState>)

    @Query("DELETE FROM watch_state WHERE videoId = :id")
    fun delete(id: Long)
}

@Database(entities = [WatchState::class], version = 1, exportSchema = false)
abstract class WatchDatabase : RoomDatabase() {
    abstract fun dao(): WatchDao

    companion object {
        @Volatile private var instance: WatchDatabase? = null
        fun get(ctx: Context): WatchDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, WatchDatabase::class.java, "watch.db")
                .build().also { instance = it }
        }
    }
}

/**
 * واجهة متزامنة سريعة (للقراءة من الواجهة) فوق Room:
 * القراءة من الذاكرة، والكتابة في خيط خلفي واحد.
 * يجب استدعاء load() مرة واحدة على خيط خلفي قبل أول استخدام.
 */
class PositionStore(private val ctx: Context) {
    private val dao = WatchDatabase.get(ctx).dao()
    private val writer = Executors.newSingleThreadExecutor()
    private val map = ConcurrentHashMap<Long, WatchState>()

    /** على خيط خلفي فقط. يستورد بيانات SharedPreferences القديمة مرة واحدة. */
    fun load() {
        migrateOldPrefs()
        map.clear()
        dao.getAll().forEach { map[it.videoId] = it }
    }

    private fun migrateOldPrefs() {
        val sp = ctx.getSharedPreferences("tube_pro", Context.MODE_PRIVATE)
        if (sp.getBoolean("room_migrated", false)) return
        val rows = HashMap<Long, WatchState>()
        val oldKeys = ArrayList<String>()
        for ((k, v) in sp.all) {
            if (k.startsWith("pos_") && v is Long) {
                val id = k.removePrefix("pos_").toLongOrNull() ?: continue
                rows[id] = WatchState(id, v, rows[id]?.watched ?: false)
                oldKeys.add(k)
            } else if (k.startsWith("watched_") && v == true) {
                val id = k.removePrefix("watched_").toLongOrNull() ?: continue
                rows[id] = (rows[id] ?: WatchState(id, 0L, false)).copy(watched = true)
                oldKeys.add(k)
            }
        }
        if (rows.isNotEmpty()) dao.upsertAll(rows.values.toList())
        val e = sp.edit()
        oldKeys.forEach { e.remove(it) }
        e.putBoolean("room_migrated", true).apply()
    }

    fun getPosition(id: Long): Long = map[id]?.position ?: 0L
    fun isWatched(id: Long): Boolean = map[id]?.watched ?: false

    fun save(id: Long, pos: Long, watched: Boolean) {
        val s = WatchState(id, pos, watched || isWatched(id))
        map[id] = s
        writer.execute { try { dao.upsert(s) } catch (_: Exception) {} }
    }

    fun remove(id: Long) {
        map.remove(id)
        writer.execute { try { dao.delete(id) } catch (_: Exception) {} }
    }
}
