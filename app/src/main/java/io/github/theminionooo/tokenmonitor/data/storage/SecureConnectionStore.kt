package io.github.theminionooo.tokenmonitor.data.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.theminionooo.tokenmonitor.domain.HubConnection
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the pairing record with a non-exportable Android Keystore AES key.
 * The ciphertext in SharedPreferences is useless outside this app and device.
 */
internal class SecureConnectionStore(context: Context) {
    private val preferences = context.getSharedPreferences("secure_connection", Context.MODE_PRIVATE)

    fun read(): HubConnection? = synchronized(storeLock) { runCatching {
        val encodedIv = preferences.getString(ivKey, null) ?: return null
        val encodedCiphertext = preferences.getString(ciphertextKey, null) ?: return null
        val cleartext = decrypt(encodedIv, encodedCiphertext)
        decode(cleartext)
    }.getOrNull() }

    fun save(connection: HubConnection): Unit = synchronized(storeLock) {
        val encrypted = encrypt(encode(connection))
        check(preferences.edit()
            .putString(ivKey, encrypted.iv)
            .putString(ciphertextKey, encrypted.ciphertext)
            .commit()) { "Could not securely save the Hub connection." }
    }

    fun clear(): Unit = synchronized(storeLock) {
        check(preferences.edit().clear().commit()) { "Could not remove the saved Hub connection." }
    }

    private fun encrypt(value: String): EncryptedValue {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return EncryptedValue(
            iv = Base64.encodeToString(cipher.iv, base64Flags),
            ciphertext = Base64.encodeToString(
                cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8)),
                base64Flags,
            ),
        )
    }

    private fun decrypt(encodedIv: String, encodedCiphertext: String): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, Base64.decode(encodedIv, base64Flags)),
        )
        return cipher.doFinal(Base64.decode(encodedCiphertext, base64Flags)).toString(StandardCharsets.UTF_8)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(keyStoreName).apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreName).apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    // Layout: url.secret.allowLocal[.fallbackUrl]. The fourth part was added for the
    // home Wi-Fi fallback; records written before it still decode.
    private fun encode(connection: HubConnection): String = listOfNotNull(
        encodePart(connection.baseUrl),
        encodePart(connection.secret),
        connection.allowLocalNetwork.toString(),
        connection.fallbackUrl?.takeIf { it.isNotBlank() }?.let(::encodePart),
    ).joinToString(".")

    private fun decode(value: String): HubConnection? {
        val parts = value.split('.', limit = 4)
        if (parts.size !in 3..4) return null
        val url = decodePart(parts[0]) ?: return null
        val secret = decodePart(parts[1]) ?: return null
        val allowLocalNetwork = parts[2].toBooleanStrictOrNull() ?: return null
        val fallbackUrl = parts.getOrNull(3)?.let { decodePart(it) ?: return null }
        return HubConnection(url, secret, allowLocalNetwork, fallbackUrl)
    }

    private fun encodePart(value: String): String = Base64.encodeToString(
        value.toByteArray(StandardCharsets.UTF_8),
        base64Flags,
    )

    private fun decodePart(value: String): String? = runCatching {
        Base64.decode(value, base64Flags).toString(StandardCharsets.UTF_8)
    }.getOrNull()

    private data class EncryptedValue(val iv: String, val ciphertext: String)

    private companion object {
        // Repository/widget repositories may own separate stores in the same process.
        // Serialize key creation and complete IV/ciphertext reads/writes across them.
        val storeLock = Any()
        const val keyStoreName = "AndroidKeyStore"
        const val keyAlias = "token_monitor_hub_connection_v1"
        const val transformation = "AES/GCM/NoPadding"
        const val ivKey = "iv"
        const val ciphertextKey = "ciphertext"
        const val base64Flags = Base64.NO_WRAP or Base64.URL_SAFE
    }
}
