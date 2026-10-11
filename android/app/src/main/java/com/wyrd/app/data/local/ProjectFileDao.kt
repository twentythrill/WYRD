package com.wyrd.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectFileDao {
    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun getFilesByProject(projectId: Long): Flow<List<ProjectFileEntity>>

    @Query("SELECT * FROM project_files WHERE projectId = :projectId ORDER BY createdAt ASC")
    suspend fun getFilesByProjectList(projectId: Long): List<ProjectFileEntity>

    @Query("SELECT * FROM project_files WHERE id = :id")
    suspend fun getFileById(id: Long): ProjectFileEntity?

    @Query("SELECT * FROM project_files WHERE syncId = :syncId LIMIT 1")
    suspend fun getFileBySyncId(syncId: String): ProjectFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: ProjectFileEntity): Long

    @Query("DELETE FROM project_files WHERE id = :id")
    suspend fun deleteFileById(id: Long)

    @Query("DELETE FROM project_files WHERE syncId = :syncId")
    suspend fun deleteFileBySyncId(syncId: String)

    @Query("DELETE FROM project_files WHERE projectId = :projectId")
    suspend fun deleteFilesByProjectId(projectId: Long)
}
