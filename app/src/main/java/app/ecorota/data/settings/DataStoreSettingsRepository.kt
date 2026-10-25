package app.ecorota.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.ecorota.core.model.AppSettings
import app.ecorota.core.model.ConsumptionUnit
import app.ecorota.core.model.SpeedUnit
import app.ecorota.core.model.ThemeMode
import app.ecorota.core.scoring.ScoringConfigCodec
import app.ecorota.data.SettingsRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore throws when a second instance is created for the same file, so the
 * delegate stays at file level and every repository instance shares it.
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val dataStore = context.applicationContext.settingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toAppSettings() }
        .distinctUntilChanged()

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            preferences.writeSettings(transform(preferences.toAppSettings()))
        }
    }
}

private object SettingsKeys {
    val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
    val AutoDetectionEnabled = booleanPreferencesKey("auto_detection_enabled")
    val ThemeMode = stringPreferencesKey("theme_mode")
    val SpeedUnit = stringPreferencesKey("speed_unit")
    val ConsumptionUnit = stringPreferencesKey("consumption_unit")
    val Currency = stringPreferencesKey("currency")
    val AllowMapTileDownloads = booleanPreferencesKey("allow_map_tile_downloads")
    val KeepScreenOnDuringTrip = booleanPreferencesKey("keep_screen_on_during_trip")
    val DataRetentionDays = intPreferencesKey("data_retention_days")
    val ScoringConfig = stringPreferencesKey("scoring_config")
}

/**
 * Unknown enum names fall back to the default rather than throwing, so a
 * hand-edited or downgraded preferences file cannot brick the settings screen.
 */
private fun Preferences.toAppSettings(): AppSettings {
    val defaults = AppSettings()
    return AppSettings(
        onboardingCompleted = this[SettingsKeys.OnboardingCompleted] ?: defaults.onboardingCompleted,
        autoDetectionEnabled = this[SettingsKeys.AutoDetectionEnabled]
            ?: defaults.autoDetectionEnabled,
        themeMode = enumOrNull<ThemeMode>(this[SettingsKeys.ThemeMode]) ?: defaults.themeMode,
        speedUnit = enumOrNull<SpeedUnit>(this[SettingsKeys.SpeedUnit]) ?: defaults.speedUnit,
        consumptionUnit = enumOrNull<ConsumptionUnit>(this[SettingsKeys.ConsumptionUnit])
            ?: defaults.consumptionUnit,
        currency = this[SettingsKeys.Currency] ?: defaults.currency,
        allowMapTileDownloads = this[SettingsKeys.AllowMapTileDownloads]
            ?: defaults.allowMapTileDownloads,
        keepScreenOnDuringTrip = this[SettingsKeys.KeepScreenOnDuringTrip]
            ?: defaults.keepScreenOnDuringTrip,
        dataRetentionDays = this[SettingsKeys.DataRetentionDays] ?: defaults.dataRetentionDays,
        scoringConfig = ScoringConfigCodec.decode(this[SettingsKeys.ScoringConfig]),
    )
}

private fun MutablePreferences.writeSettings(settings: AppSettings) {
    this[SettingsKeys.OnboardingCompleted] = settings.onboardingCompleted
    this[SettingsKeys.AutoDetectionEnabled] = settings.autoDetectionEnabled
    this[SettingsKeys.ThemeMode] = settings.themeMode.name
    this[SettingsKeys.SpeedUnit] = settings.speedUnit.name
    this[SettingsKeys.ConsumptionUnit] = settings.consumptionUnit.name
    this[SettingsKeys.Currency] = settings.currency
    this[SettingsKeys.AllowMapTileDownloads] = settings.allowMapTileDownloads
    this[SettingsKeys.KeepScreenOnDuringTrip] = settings.keepScreenOnDuringTrip
    this[SettingsKeys.DataRetentionDays] = settings.dataRetentionDays
    this[SettingsKeys.ScoringConfig] = ScoringConfigCodec.encode(settings.scoringConfig)
}

private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
    raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
