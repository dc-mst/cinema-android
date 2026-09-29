package com.devcrumbs.cinema.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where the login survives app restarts. */
interface SessionStore {
    fun load(): Session?
    fun save(session: Session?)
}

/** For tests and previews. */
class InMemorySessionStore(private var session: Session? = null) : SessionStore {
    override fun load(): Session? = session
    override fun save(session: Session?) { this.session = session }
}

/**
 * The session (token + user) encrypted with an AES-GCM key that never leaves
 * the Android Keystore, in its own preferences file (`account.xml`, excluded
 * from backups: a restored copy could not be decrypted anyway).
 *
 * Anything unexpected — no Keystore (Robolectric), a key lost with a
 * reinstall, a corrupt value — reads as "logged out"; the token is never
 * stored in the clear.
 */
class KeystoreSessionStore(context: Context) : SessionStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): Session? {
        val stored = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val (iv, data) = stored.split(':').map { Base64.decode(it, Base64.NO_WRAP) }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            Json.decodeFromString(Session.serializer(), String(cipher.doFinal(data), Charsets.UTF_8))
        }.getOrElse {
            prefs.edit().remove(KEY_SESSION).apply()
            null
        }
    }

    override fun save(session: Session?) {
        if (session == null) {
            prefs.edit().remove(KEY_SESSION).apply()
            return
        }
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val data = cipher.doFinal(Json.encodeToString(Session.serializer(), session).toByteArray(Charsets.UTF_8))
            val encoded = listOf(cipher.iv, data).joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) }
            prefs.edit().putString(KEY_SESSION, encoded).apply()
        }.onFailure {
            // Logged in for this run only.
            prefs.edit().remove(KEY_SESSION).apply()
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "account"
        const val KEY_SESSION = "session"
        const val KEY_ALIAS = "cinema_session"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
