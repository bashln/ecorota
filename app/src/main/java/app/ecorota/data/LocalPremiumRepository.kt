package app.ecorota.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
private val Context.premiumDataStore: DataStore<Preferences> by preferencesDataStore(name = "premium")

/**
 * Premium entitlement backed only by local storage: Play Billing verifies a
 * purchase and calls [setPremium], nothing here talks to the network.
 */
class LocalPremiumRepository(context: Context) : PremiumRepository {

    private val dataStore = context.applicationContext.premiumDataStore

    override val entitlement: Flow<Entitlement> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> preferences.toEntitlement() }
        .distinctUntilChanged()

    override suspend fun refresh() {
        // No external authority to reconcile for a local grant; reading keeps the contract honest.
        entitlement.first()
    }

    override suspend fun current(): Entitlement = entitlement.first()

    override suspend fun setPremium(isPremium: Boolean, source: PremiumSource) {
        dataStore.edit { preferences ->
            preferences[IsPremium] = isPremium
            preferences[Source] = if (isPremium) source.name else PremiumSource.NONE.name
        }
    }

    private fun Preferences.toEntitlement(): Entitlement = Entitlement(
        isPremium = this[IsPremium] ?: false,
        source = if (this[IsPremium] == true) sourceOrNone(this[Source]) else PremiumSource.NONE,
    )

    private fun sourceOrNone(raw: String?): PremiumSource =
        raw?.let { name -> PremiumSource.entries.firstOrNull { it.name == name } }
            ?: PremiumSource.NONE

    private companion object {
        val IsPremium = booleanPreferencesKey("is_premium")
        val Source = stringPreferencesKey("source")
    }
}
