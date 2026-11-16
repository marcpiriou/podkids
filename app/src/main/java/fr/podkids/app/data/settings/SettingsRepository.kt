package fr.podkids.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
    /** Le catalogue n'apparaît plus sur l'accueil : il ne s'ouvre que depuis les réglages. */
    val hideCatalogOnHome: Boolean = false,
    /** Seuls les épisodes téléchargés (et les podcasts qui en ont) sont affichés. */
    val downloadedOnly: Boolean = false,
    /** Un mot de passe protège l'accès aux réglages. */
    val settingsLocked: Boolean = false,
    /** Choix d'âge cochés dans le catalogue ; vide = tous les âges. */
    val catalogAges: Set<Int> = emptySet(),
    /**
     * Mode enfant (par défaut) : les réglages de la carte « Mode enfant » s'appliquent. En mode
     * adulte, tout est visible et les réglages s'ouvrent sans mot de passe.
     */
    val childMode: Boolean = true,
) {
    /** Catalogue effectivement masqué de l'accueil (mode enfant uniquement). */
    val catalogHidden: Boolean get() = childMode && hideCatalogOnHome

    /** Seuls les épisodes téléchargés sont effectivement affichés (mode enfant uniquement). */
    val onlyDownloaded: Boolean get() = childMode && downloadedOnly

    /** Le mot de passe est demandé pour ouvrir les réglages (mode enfant uniquement). */
    val passwordRequired: Boolean get() = childMode && settingsLocked
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<UiSettings> = context.settingsDataStore.data.map { prefs ->
        UiSettings(
            themeColor = enumOrDefault(prefs[KEY_THEME_COLOR], ThemeColor.OCEAN),
            darkMode = enumOrDefault(prefs[KEY_DARK_MODE], DarkMode.SYSTEM),
            textSize = enumOrDefault(prefs[KEY_TEXT_SIZE], TextSize.NORMAL),
            tileSize = enumOrDefault(prefs[KEY_TILE_SIZE], TileSize.LARGE),
            episodeOrder = enumOrDefault(prefs[KEY_EPISODE_ORDER], EpisodeOrder.OLDEST_FIRST),
            hideCatalogOnHome = prefs[KEY_HIDE_CATALOG] ?: false,
            downloadedOnly = prefs[KEY_DOWNLOADED_ONLY] ?: false,
            settingsLocked = !prefs[KEY_PASSWORD_HASH].isNullOrEmpty(),
            childMode = prefs[KEY_CHILD_MODE] ?: true,
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
    suspend fun setHideCatalogOnHome(value: Boolean) = context.settingsDataStore.edit { it[KEY_HIDE_CATALOG] = value }
    suspend fun setDownloadedOnly(value: Boolean) = context.settingsDataStore.edit { it[KEY_DOWNLOADED_ONLY] = value }
    suspend fun setChildMode(value: Boolean) = context.settingsDataStore.edit { it[KEY_CHILD_MODE] = value }

    /** Définit (ou remplace) le mot de passe des réglages. */
    suspend fun setPassword(password: String) {
        val salt = PasswordHasher.newSalt()
        val hash = withContext(Dispatchers.Default) { PasswordHasher.hash(password, salt) }
        context.settingsDataStore.edit {
            it[KEY_PASSWORD_SALT] = salt
            it[KEY_PASSWORD_HASH] = hash
        }
    }

    suspend fun clearPassword() {
        context.settingsDataStore.edit {
            it.remove(KEY_PASSWORD_SALT)
            it.remove(KEY_PASSWORD_HASH)
        }
    }

    /** true si aucun mot de passe n'est défini ou si [password] est le bon. */
    suspend fun checkPassword(password: String): Boolean {
        val prefs = context.settingsDataStore.data.first()
        val hash = prefs[KEY_PASSWORD_HASH]
        if (hash.isNullOrEmpty()) return true
        val salt = prefs[KEY_PASSWORD_SALT].orEmpty()
        return withContext(Dispatchers.Default) { PasswordHasher.matches(password, salt, hash) }
    }
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
        val KEY_HIDE_CATALOG = booleanPreferencesKey("hide_catalog_on_home")
        val KEY_DOWNLOADED_ONLY = booleanPreferencesKey("downloaded_only")
        val KEY_CHILD_MODE = booleanPreferencesKey("child_mode")
        val KEY_PASSWORD_HASH = stringPreferencesKey("settings_password_hash")
        val KEY_PASSWORD_SALT = stringPreferencesKey("settings_password_salt")
        val KEY_CATALOG_AGES = stringPreferencesKey("catalog_ages")
    }
}
