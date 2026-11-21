package fr.podkids.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, QueueItemEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PodkidsDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao

    companion object {
        fun create(context: Context): PodkidsDatabase =
            Room.databaseBuilder(context, PodkidsDatabase::class.java, "podkids.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** Version 2 : réglages par podcast pour sauter le début et la fin des épisodes. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE podcasts ADD COLUMN skipIntroSec INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE podcasts ADD COLUMN skipOutroSec INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
