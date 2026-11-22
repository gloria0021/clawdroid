package jp.miyavi.android.clawdroid.testing

import jp.miyavi.android.clawdroid.core.security.GatewayConnection
import jp.miyavi.android.clawdroid.core.security.SecretStore
import jp.miyavi.android.clawdroid.core.security.StoredDeviceIdentity

class InMemorySecretStore(var connection: GatewayConnection? = null) : SecretStore {
    var identity: StoredDeviceIdentity? = null
    override suspend fun loadConnection() = connection
    override suspend fun saveConnection(connection: GatewayConnection) { this.connection = connection }
    override suspend fun clearConnection() { connection = null }
    override suspend fun loadDeviceIdentity() = identity
    override suspend fun saveDeviceIdentity(identity: StoredDeviceIdentity) { this.identity = identity }
}
