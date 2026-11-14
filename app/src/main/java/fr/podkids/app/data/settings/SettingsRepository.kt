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

data class UiSettings(
    val childName: String = "",
    val themeColor: ThemeColor = ThemeColor.OCEAN,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    val tileSize: TileSize = TileSize.LARGE,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<UiSettings> = context.settingsDataStore.data.map { prefs ->
        UiSettings(
            childName = prefs[KEY_CHILD_NAME].orEmpty(),
            themeColor = enumOrDefault(prefs[KEY_THEME_COLOR], ThemeColor.OCEAN),
            darkMode = enumOrDefault(prefs[KEY_DARK_MODE], DarkMode.SYSTEM),
            textSize = enumOrDefault(prefs[KEY_TEXT_SIZE], TextSize.NORMAL),
            tileSize = enumOrDefault(prefs[KEY_TILE_SIZE], TileSize.LARGE),
        )
    }

    suspend fun setChildName(name: String) = set(KEY_CHILD_NAME, name.take(MAX_NAME_LENGTH))
    suspend fun setThemeColor(value: ThemeColor) = set(KEY_THEME_COLOR, value.name)
    suspend fun setDarkMode(value: DarkMode) = set(KEY_DARK_MODE, value.name)
    suspend fun setTextSize(value: TextSize) = set(KEY_TEXT_SIZE, value.name)
    suspend fun setTileSize(value: TileSize) = set(KEY_TILE_SIZE, value.name)

    private suspend fun set(key: Preferences.Key<String>, value: String) {
        context.settingsDataStore.edit { it[key] = value }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    companion object {
        const val MAX_NAME_LENGTH = 20
        private val KEY_CHILD_NAME = stringPreferencesKey("child_name")
        private val KEY_THEME_COLOR = stringPreferencesKey("theme_color")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode")
        private val KEY_TEXT_SIZE = stringPreferencesKey("text_size")
        private val KEY_TILE_SIZE = stringPreferencesKey("tile_size")
    }
}
