package dev.shike.data

import android.content.Context
import androidx.room.*
import dev.shike.domain.*
import dev.shike.security.KeyVault
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Serializable
@Entity(
    tableName = "items",
    indices =
        [Index("kind"), Index("parentId"), Index("seriesId"), Index("deletedAt"), Index("due")],
)
data class Item(
    @PrimaryKey val id: String = newId(),
    val kind: String,
    val title: String,
    val body: String = "",
    val state: String = "TODO",
    val created: Long = System.currentTimeMillis(),
    val updated: Long = System.currentTimeMillis(),
    val start: Long? = null,
    val due: Long? = null,
    val allDay: Boolean = true,
    val zone: String = java.time.ZoneId.systemDefault().id,
    val priority: Int = 1,
    val parentId: String? = null,
    val folderId: String? = null,
    val categoryId: String? = null,
    val tags: String = "",
    val color: Long = 0,
    val rule: String = "",
    val seriesId: String? = null,
    val occurrence: String = "",
    val target: Double = 1.0,
    val unit: String = "次",
    val habitType: String = "完成型",
    val progress: Int = 0,
    val autoProgress: Boolean = true,
    val remind: Boolean = false,
    val reminderMinutes: Int = 0,
    val deletedAt: Long? = null,
    val sourceId: String? = null,
)

@Serializable
@Entity(tableName = "links", primaryKeys = ["fromId", "toId", "type"], indices = [Index("toId")])
data class Link(val fromId: String, val toId: String, val type: String = "related")

@Serializable
@Entity(tableName = "history", indices = [Index("itemId"), Index("created")])
data class History(
    @PrimaryKey val id: String = newId(),
    val itemId: String,
    val action: String,
    val payload: String,
    val created: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "checkins", indices = [Index(value = ["habitId", "period"]), Index("created")])
data class Checkin(
    @PrimaryKey val id: String = newId(),
    val habitId: String,
    val period: String,
    val date: String,
    val state: String,
    val value: Double,
    val target: Double,
    val unit: String,
    val created: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "config")
data class Config(@PrimaryKey val key: String, val value: String)

@Dao
interface ShikeDao {
    @Query("SELECT * FROM items ORDER BY created DESC") fun items(): Flow<List<Item>>

    @Query("SELECT * FROM links") fun links(): Flow<List<Link>>

    @Query("SELECT * FROM history ORDER BY created DESC") fun history(): Flow<List<History>>

    @Query("SELECT * FROM checkins ORDER BY created DESC") fun checkins(): Flow<List<Checkin>>

    @Query("SELECT * FROM config") fun config(): Flow<List<Config>>

    @Query("SELECT * FROM items") suspend fun allItems(): List<Item>

    @Query("SELECT * FROM links") suspend fun allLinks(): List<Link>

    @Query("SELECT * FROM history") suspend fun allHistory(): List<History>

    @Query("SELECT * FROM checkins") suspend fun allCheckins(): List<Checkin>

    @Query("SELECT * FROM config") suspend fun allConfig(): List<Config>

    @Upsert suspend fun put(item: Item)

    @Upsert suspend fun putAll(items: List<Item>)

    @Upsert suspend fun putConfig(config: Config)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(link: Link)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun history(history: History)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun checkin(checkin: Checkin)

    @Query("DELETE FROM links WHERE fromId=:id AND type='parent'")
    suspend fun clearParentLink(id: String)

    @Query("DELETE FROM links WHERE (fromId=:id OR toId=:id) AND type='related'")
    suspend fun clearLinks(id: String)

    @Query(
        "DELETE FROM history WHERE id IN (SELECT id FROM history WHERE itemId=:id AND action='note_version' ORDER BY created DESC LIMIT -1 OFFSET :keep)"
    )
    suspend fun pruneVersions(id: String, keep: Int)

    @Query("DELETE FROM items WHERE id=:id") suspend fun eraseItem(id: String)

    @Query("DELETE FROM links WHERE fromId=:id OR toId=:id") suspend fun eraseLinks(id: String)

    @Query("DELETE FROM history WHERE itemId=:id") suspend fun eraseHistory(id: String)

    @Query("DELETE FROM checkins WHERE habitId=:id") suspend fun eraseCheckins(id: String)

    @Query("DELETE FROM config") suspend fun clearConfig()

    @Query("DELETE FROM items") suspend fun clearItems()

    @Query("DELETE FROM links") suspend fun clearAllLinks()

    @Query("DELETE FROM checkins") suspend fun clearCheckins()

    @Query("DELETE FROM history") suspend fun clearHistory()
}

@Database(
    entities = [Item::class, Link::class, History::class, Checkin::class, Config::class],
    version = 1,
    exportSchema = true,
)
abstract class ShikeDb : RoomDatabase() {
    abstract fun dao(): ShikeDao

    companion object {
        fun open(context: Context, name: String = "shike.db"): ShikeDb {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, ShikeDb::class.java, name)
                .openHelperFactory(SupportOpenHelperFactory(KeyVault(context).databaseKey()))
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
        }
    }
}
