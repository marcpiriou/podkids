package fr.podkids.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PodcastEntity::class, EpisodeEntity::class, QueueItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class PodkidsDatabase : RoomDatabase() {
    abstract fun podcastDao(): PodcastDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun queueDao(): QueueDao

    companion object {
        fun create(context: Context): PodkidsDatabase =
            Room.databaseBuilder(context, PodkidsDatabase::class.java, "podkids.db").build()
    }
}
