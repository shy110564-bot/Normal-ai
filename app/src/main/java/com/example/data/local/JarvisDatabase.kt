package com.example.data.local

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
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class MemoryCategory(val displayName: String) {
    USER_PREFERENCE("User Preferences"),
    ASSISTANT_PREFERENCE("Assistant Preferences"),
    IMPORTANT_INFO("Important Information"),
    CONVERSATION_SUMMARY("Conversation Summaries"),
    CUSTOM_COMMAND("Custom Commands"),
    PERSONAL_SETTING("Personal Settings")
}

@Entity(tableName = "jarvis_notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String = "General",
    val isVoiceCreated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "jarvis_memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String = MemoryCategory.IMPORTANT_INFO.name,
    val memoryKey: String,
    val memoryValue: String,
    val importance: Int = 3, // 1..5
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "jarvis_chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "jarvis", "tool", "system"
    val content: String,
    val toolName: String? = null,
    val isVoice: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM jarvis_notes ORDER BY updatedAt DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM jarvis_notes ORDER BY updatedAt DESC")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query(
        "SELECT * FROM jarvis_notes WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY updatedAt DESC"
    )
    suspend fun searchNotes(query: String): List<NoteEntity>

    @Query("SELECT * FROM jarvis_notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM jarvis_notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long): Int

    @Query("DELETE FROM jarvis_notes")
    suspend fun deleteAllNotes()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM jarvis_memories ORDER BY importance DESC, createdAt DESC")
    fun observeAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM jarvis_memories ORDER BY importance DESC, createdAt DESC")
    suspend fun getAllMemoriesSync(): List<MemoryEntity>

    @Query(
        "SELECT * FROM jarvis_memories WHERE memoryKey LIKE '%' || :query || '%' OR memoryValue LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY importance DESC, createdAt DESC"
    )
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM jarvis_memories WHERE memoryKey = :key LIMIT 1")
    suspend fun findByKey(key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Query("DELETE FROM jarvis_memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long): Int

    @Query("DELETE FROM jarvis_memories WHERE memoryKey LIKE '%' || :keyQuery || '%'")
    suspend fun deleteMemoryByKey(keyQuery: String): Int

    @Query("DELETE FROM jarvis_memories")
    suspend fun clearAllMemories()
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM jarvis_chat_messages ORDER BY timestamp ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM jarvis_chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesSync(limit: Int = 20): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM jarvis_chat_messages")
    suspend fun clearConversation()
}

@Database(
    entities = [NoteEntity::class, MemoryEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun memoryDao(): MemoryDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_core_database.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class JarvisDataRepository(private val database: JarvisDatabase) {
    val allNotes: Flow<List<NoteEntity>> = database.noteDao().observeAllNotes()
    val allMemories: Flow<List<MemoryEntity>> = database.memoryDao().observeAllMemories()
    val allMessages: Flow<List<ChatMessageEntity>> = database.chatDao().observeMessages()

    suspend fun createNote(
        title: String,
        content: String,
        category: String = "General",
        isVoiceCreated: Boolean = false
    ): NoteEntity {
        val now = System.currentTimeMillis()
        val entity = NoteEntity(
            title = title.ifBlank { "Untitled Note" },
            content = content,
            category = category.ifBlank { "General" },
            isVoiceCreated = isVoiceCreated,
            createdAt = now,
            updatedAt = now
        )
        val id = database.noteDao().insertNote(entity)
        return entity.copy(id = id)
    }

    suspend fun updateNote(id: Long, title: String, content: String, category: String = "General"): Boolean {
        val existing = database.noteDao().getNoteById(id) ?: return false
        database.noteDao().updateNote(
            existing.copy(
                title = title.ifBlank { existing.title },
                content = content,
                category = category.ifBlank { existing.category },
                updatedAt = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun deleteNote(id: Long): Boolean {
        return database.noteDao().deleteNoteById(id) > 0
    }

    suspend fun searchNotes(query: String): List<NoteEntity> {
        return if (query.isBlank()) {
            database.noteDao().getAllNotesSync()
        } else {
            database.noteDao().searchNotes(query.trim())
        }
    }

    suspend fun saveMemory(
        category: String,
        key: String,
        value: String,
        importance: Int = 3
    ): MemoryEntity {
        val normalizedCategory = MemoryCategory.entries.firstOrNull {
            it.name.equals(category, ignoreCase = true) ||
                it.displayName.equals(category, ignoreCase = true)
        }?.name ?: MemoryCategory.IMPORTANT_INFO.name

        val existing = database.memoryDao().findByKey(key.trim())
        val entity = if (existing != null) {
            existing.copy(
                category = normalizedCategory,
                memoryValue = value.trim(),
                importance = importance.coerceIn(1, 5),
                createdAt = System.currentTimeMillis()
            )
        } else {
            MemoryEntity(
                category = normalizedCategory,
                memoryKey = key.trim(),
                memoryValue = value.trim(),
                importance = importance.coerceIn(1, 5)
            )
        }
        val id = database.memoryDao().insertMemory(entity)
        return entity.copy(id = id)
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        return if (query.isBlank()) {
            database.memoryDao().getAllMemoriesSync()
        } else {
            database.memoryDao().searchMemories(query.trim())
        }
    }

    suspend fun deleteMemoryById(id: Long): Boolean {
        return database.memoryDao().deleteMemoryById(id) > 0
    }

    suspend fun forgetMemoryByKey(keyQuery: String): Int {
        return database.memoryDao().deleteMemoryByKey(keyQuery.trim())
    }

    suspend fun clearAllMemories() {
        database.memoryDao().clearAllMemories()
    }

    suspend fun addChatMessage(
        role: String,
        content: String,
        toolName: String? = null,
        isVoice: Boolean = false
    ): ChatMessageEntity {
        val msg = ChatMessageEntity(
            role = role,
            content = content,
            toolName = toolName,
            isVoice = isVoice
        )
        val id = database.chatDao().insertMessage(msg)
        return msg.copy(id = id)
    }

    suspend fun getRecentMessages(limit: Int = 16): List<ChatMessageEntity> {
        return database.chatDao().getRecentMessagesSync(limit).reversed()
    }

    suspend fun clearConversation() {
        database.chatDao().clearConversation()
    }
}
