package jp.miyavi.android.clawdroid.core.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Gateway への接続情報（data-model 1）。`gatewayToken` は画面とログに出さない。 */
@Serializable
data class GatewayConnection(
    val url: String,
    val gatewayToken: String,
    val deviceToken: String? = null,
    val deviceTokenScopes: List<String> = emptyList(),
)

/** 端末の Ed25519 の鍵（data-model 1）。作り直さない。 */
@Serializable
data class StoredDeviceIdentity(
    val deviceId: String,
    val publicKeyBase64: String,
    val privateKeyBase64: String,
)

/** 暗号化して保存する値を読み書きする口。テストでは偽物に差し替える。 */
interface SecretStore {
    suspend fun loadConnection(): GatewayConnection?
    suspend fun saveConnection(connection: GatewayConnection)
    suspend fun clearConnection()
    suspend fun loadDeviceIdentity(): StoredDeviceIdentity?
    suspend fun saveDeviceIdentity(identity: StoredDeviceIdentity)
}

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "vault")

/**
 * Tink の AEAD（鍵は Android Keystore）で暗号化して DataStore に保存する（research R3）。
 * 平文は DataStore にもログにも残さない。
 */
class TinkSecretStore(context: Context) : SecretStore {
    private val store = context.applicationContext.vaultDataStore
    private val json = Json { ignoreUnknownKeys = true }
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override suspend fun loadConnection(): GatewayConnection? =
        read(CONNECTION)?.let { runCatching { json.decodeFromString<GatewayConnection>(it) }.getOrNull() }

    override suspend fun saveConnection(connection: GatewayConnection) =
        write(CONNECTION, json.encodeToString(connection))

    override suspend fun clearConnection() {
        store.edit { it.remove(CONNECTION) }
    }

    override suspend fun loadDeviceIdentity(): StoredDeviceIdentity? =
        read(IDENTITY)?.let { runCatching { json.decodeFromString<StoredDeviceIdentity>(it) }.getOrNull() }

    override suspend fun saveDeviceIdentity(identity: StoredDeviceIdentity) =
        write(IDENTITY, json.encodeToString(identity))

    private suspend fun read(key: Preferences.Key<String>): String? {
        val sealed = store.data.first()[key] ?: return null
        return runCatching {
            val bytes = aead.decrypt(Base64.decode(sealed, Base64.NO_WRAP), key.name.toByteArray())
            bytes.toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private suspend fun write(key: Preferences.Key<String>, plain: String) {
        val sealed = Base64.encodeToString(aead.encrypt(plain.toByteArray(Charsets.UTF_8), key.name.toByteArray()), Base64.NO_WRAP)
        store.edit { it[key] = sealed }
    }

    private companion object {
        const val KEYSET_NAME = "clawdroid_vault_keyset"
        const val KEYSET_PREFS = "clawdroid_vault_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://clawdroid_vault_master_key"
        val CONNECTION = stringPreferencesKey("connection")
        val IDENTITY = stringPreferencesKey("device_identity")
    }
}
