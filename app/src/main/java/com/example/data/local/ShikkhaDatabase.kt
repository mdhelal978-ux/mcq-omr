package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ExamEntity::class, SubmissionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class ShikkhaDatabase : RoomDatabase() {
    abstract fun examDao(): ExamDao

    companion object {
        @Volatile
        private var instance: ShikkhaDatabase? = null

        fun getInstance(context: Context): ShikkhaDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ShikkhaDatabase::class.java,
                    "shikkhascan_room.db"
                ).build().also { instance = it }
            }
        }
    }
}
