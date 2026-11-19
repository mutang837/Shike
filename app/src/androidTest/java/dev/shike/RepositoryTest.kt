package dev.shike

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.shike.data.*
import dev.shike.domain.*
import java.time.LocalDate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test
    fun encryptedPersistenceAndTransactionalRestore() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<ShikeApplication>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val name = "verification-${newId()}.db"
        val db = ShikeDb.open(context, name)
        try {
            val repo = Repository(context, db, scope)
            repo.initialize()
            val task = Item(kind = "TASK", title = "验证加密和恢复")
            repo.save(task)
            repo.transition(task, TaskState.DONE)
            repo.transition(task.copy(state = "DONE"), TaskState.TODO)
            val note = Item(kind = "NOTE", title = "关联笔记", body = "初版")
            repo.save(note, listOf(task.id))
            repo.save(note.copy(body = "修改版"))
            val habit =
                Item(
                    kind = "HABIT",
                    title = "打卡测试",
                    rule = json.encodeToString(RepeatRule.serializer(), RepeatRule()),
                    target = 5.0,
                )
            repo.save(habit)
            repo.checkin(habit, 2.0, CheckState.PARTIAL, LocalDate.now())
            assertTrue(repo.dao.allHistory().any { it.action == "note_version" })
            assertEquals(1, repo.dao.allCheckins().size)
            val password = "Portable928".toCharArray()
            val bytes = repo.export(password)
            repo.trash(task)
            assertNotNull(repo.dao.allItems().first { it.id == task.id }.deletedAt)
            assertTrue(runCatching { repo.import(bytes, "Mismatch999".toCharArray()) }.isFailure)
            assertNotNull(repo.dao.allItems().first { it.id == task.id }.deletedAt)
            repo.import(bytes, password)
            assertNull(repo.dao.allItems().first { it.id == task.id }.deletedAt)
            assertTrue(repo.dao.allLinks().any { it.fromId == note.id && it.toId == task.id })
            val databaseFile = context.getDatabasePath(name)
            assertTrue(databaseFile.length() > 4096)
            val header = databaseFile.inputStream().use { it.readBytes().copyOf(16) }
            assertFalse(String(header).startsWith("SQLite format 3"))
        } finally {
            scope.cancel()
            db.close()
            context.deleteDatabase(name)
        }
    }
}
