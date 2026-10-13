package com.wyrd.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ChatEntity::class, MessageEntity::class, ProjectEntity::class, ProjectFileEntity::class],
    version = 10,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun messageDao(): MessageDao
    abstract fun projectDao(): ProjectDao
    abstract fun projectFileDao(): ProjectFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = if (context.getDatabasePath("nullverb_db").exists()) {
                    "nullverb_db"
                } else {
                    "wyrd_db"
                }
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS index_messages_chatId")
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_messages_chatId_timestamp ON messages(chatId, timestamp)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN attachmentUri TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN attachmentType TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN attachmentName TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN attachmentsJson TEXT")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE chats ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE messages ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE chats SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
                database.execSQL("UPDATE messages SET syncId = lower(hex(randomblob(16))) WHERE syncId = ''")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS projects (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        systemPrompt TEXT NOT NULL DEFAULT '',
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS project_files (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        syncId TEXT NOT NULL DEFAULT '',
                        projectId INTEGER NOT NULL,
                        projectSyncId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        size INTEGER NOT NULL,
                        mimeType TEXT NOT NULL,
                        storagePath TEXT NOT NULL,
                        textContent TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                database.execSQL("ALTER TABLE chats ADD COLUMN projectId INTEGER")
                database.execSQL("ALTER TABLE chats ADD COLUMN projectSyncId TEXT")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chats_projectId ON chats(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chats_projectSyncId ON chats(projectSyncId)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE projects ADD COLUMN color TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN modelName TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chats_syncId ON chats(syncId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_syncId ON messages(syncId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_projects_syncId ON projects(syncId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_files_projectId ON project_files(projectId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_files_projectSyncId ON project_files(projectSyncId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_project_files_syncId ON project_files(syncId)")
                database.execSQL(
                    "UPDATE chats SET syncId = substr(syncId,1,8)||'-'||substr(syncId,9,4)||'-'||substr(syncId,13,4)||'-'||substr(syncId,17,4)||'-'||substr(syncId,21,12) WHERE length(syncId) = 32 AND instr(syncId, '-') = 0"
                )
                database.execSQL(
                    "UPDATE messages SET syncId = substr(syncId,1,8)||'-'||substr(syncId,9,4)||'-'||substr(syncId,13,4)||'-'||substr(syncId,17,4)||'-'||substr(syncId,21,12) WHERE length(syncId) = 32 AND instr(syncId, '-') = 0"
                )
            }
        }
    }
}

