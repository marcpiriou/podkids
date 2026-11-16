package fr.podkids.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeColor { OCEAN, FOREST, STRAWBERRY, SUN, GALAXY }

enum class DarkMode { SYSTEM, LIGHT, DARK }

enum class TextSize(val scale: Float) { NORMAL(1f), LARGE(1.15f), XLARGE(1.3f) }

/** Taille des pochettes : largeur minimale d'une tuile dans les grilles, en dp. */
enum class TileSize(val minWidthDp: Int) { SMALL(104), LARGE(150) }

/** Ordre d'affichage des épisodes d'un podcast. */
enum class EpisodeOrder { OLDEST_FIRST, NEWEST_FIRST }

data class UiSettings(
    val themeColor: ThemeColor = ThemeColor.OCEAN,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    val tileSize: TileSize = TileSize.LARGE,
    /** Ordre des épisodes à l'ouverture d'un podcast (modifiable ensuite par l'icône de tri). */
    val episodeOrder: EpisodeOrder = EpisodeOrder.OLDEST_FIRST,
    /** Choix d'âge cochés dans le catalogue ; vide = tous les âges. */
    val catalogAges: Set<Int> = emptySet(),
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<UiSettings> = context.settingsDataStore.data.map { prefs ->
        UiSettings(
            themeColor = enumOrDefault(prefs[KEY_THEME_COLOR], ThemeColor.OCEAN),
            darkMode = enumOrDefault(prefs[KEY_DARK_MODE], DarkMode.SYSTEM),
            textSize = enumOrDefault(prefs[KEY_TEXT_SIZE], TextSize.NORMAL),
            tileSize = enumOrDefault(prefs[KEY_TILE_SIZE], TileSize.LARGE),
            episodeOrder = enumOrDefault(prefs[KEY_EPISODE_ORDER], EpisodeOrder.OLDEST_FIRST),
            catalogAges = prefs[KEY_CATALOG_AGES].orEmpty()
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .toSet(),
        )
    }

    suspend fun setThemeColor(value: ThemeColor) = set(KEY_THEME_COLOR, value.name)
    suspend fun setDarkMode(value: DarkMode) = set(KEY_DARK_MODE, value.name)
    suspend fun setTextSize(value: TextSize) = set(KEY_TEXT_SIZE, value.name)
    suspend fun setTileSize(value: TileSize) = set(KEY_TILE_SIZE, value.name)
    suspend fun setEpisodeOrder(value: EpisodeOrder) = set(KEY_EPISODE_ORDER, value.name)
    suspend fun setCatalogAges(ages: Set<Int>) = set(KEY_CATALOG_AGES, ages.sorted().joinToString(","))

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
        val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        val KEY_TEXT_SIZE = stringPreferencesKey("text_size")
        val KEY_TILE_SIZE = stringPreferencesKey("tile_size")
        val KEY_EPISODE_ORDER = stringPreferencesKey("episode_order")
        val KEY_CATALOG_AGES = stringPreferencesKey("catalog_ages")
    }
}
