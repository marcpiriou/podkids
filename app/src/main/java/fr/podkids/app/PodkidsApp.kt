package fr.podkids.app

import android.app.Application
import android.content.Context
import fr.podkids.app.data.PodcastRepository
import fr.podkids.app.data.catalog.CatalogApi
import fr.podkids.app.data.db.PodkidsDatabase
import fr.podkids.app.data.download.EpisodeDownloader
import fr.podkids.app.data.settings.SettingsRepository
import fr.podkids.app.playback.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Dépendances partagées de l'application (injection manuelle, sans framework). */
class AppContainer(context: Context) {
    /** Portée liée au processus : écritures en base qui doivent survivre aux écrans et au service. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val database: PodkidsDatabase = PodkidsDatabase.create(context)
    val settings = SettingsRepository(context)
    val catalog = CatalogApi(httpClient)
    private val downloader = EpisodeDownloader(context, database.episodeDao())
    val repository = PodcastRepository(database, httpClient, downloader)
    val player = PlayerConnection(context)
}

class PodkidsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch { container.repository.cleanUpPreviews() }
    }
}
