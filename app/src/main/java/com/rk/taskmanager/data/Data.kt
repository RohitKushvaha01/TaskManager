package com.rk.taskmanager.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val id: String,
    val description: String?
)

@Dao
interface AppDao {
    @Query("SELECT description FROM apps WHERE id = :packageId LIMIT 1")
    suspend fun getDescription(packageId: String): String?
}

@Database(entities = [AppEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}



