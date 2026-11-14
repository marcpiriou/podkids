package fr.podkids.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.podkids.app.data.settings.UiSettings
import fr.podkids.app.ui.PodkidsRoot
import fr.podkids.app.ui.theme.PodkidsTheme
import fr.podkids.app.ui.theme.isDarkTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as PodkidsApp).container

    /** Demandes d'ouverture du lecteur, émises par un appui sur la notification de lecture. */
    private val openPlayerRequests = Channel<Unit>(Channel.CONFLATED)
    private val openPlayerFlow = openPlayerRequests.receiveAsFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            // Rien n'est dessiné tant que les réglages ne sont pas lus, pour éviter un changement de thème visible.
            val loaded by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val settings: UiSettings = loaded ?: return@setContent
            val dark = isDarkTheme(settings)

            // Icônes des barres système lisibles sur le thème effectif (qui peut différer de celui du système).
            DisposableEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }

            PodkidsTheme(settings) {
                PodkidsRoot(
                    container = container,
                    settings = settings,
                    openPlayerRequests = openPlayerFlow,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.player.connect()
    }

    override fun onStop() {
        container.player.disconnect()
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            openPlayerRequests.trySend(Unit)
        }
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
