package com.lcdcode.moodcairns.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import com.lcdcode.moodcairns.BuildConfig
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** A Keystore cipher for a BiometricPrompt CryptoObject, or why there is none. */
sealed interface BioCipher {
    /** Initialised; usable only after the user authenticates with a strong biometric. */
    data class Ready(val cipher: Cipher) : BioCipher

    /** Biometrics were added or the screen lock was removed; the key is permanently dead. */
    data object Invalidated : BioCipher

    /** No key under the alias. */
    data object Missing : BioCipher

    /** The Keystore refused for another, possibly transient, reason. */
    data class Failed(val reason: String) : BioCipher
}

/**
 * The hardware-bound AES-256-GCM key that wraps the biometric copy of the DB key. The
 * TEE/StrongBox only allows an operation after a fresh BIOMETRIC_STRONG authentication tied to
 * that one operation, so code running as the app cannot use it silently (audit #4).
 */
@Singleton
class BiometricKeystore @Inject constructor() {

    /** Replaces any existing key with a new one and returns an encrypt cipher for enrollment. */
    fun newEncryptCipher(): BioCipher {
        deleteKey()
        val key = try {
            generateKey()
        } catch (e: GeneralSecurityException) {
            return failed("generate key", e)
        } catch (e: ProviderException) {
            return failed("generate key", e)
        } catch (e: IllegalStateException) {
            // For example, no strong biometric is enrolled on the device.
            return failed("generate key", e)
        }
        return initCipher { it.init(Cipher.ENCRYPT_MODE, key) }
    }

    /** A decrypt cipher for a blob encrypted with [iv]. */
    fun decryptCipher(iv: ByteArray): BioCipher {
        val key = try {
            keyStore().getKey(ALIAS, null) as? SecretKey
        } catch (e: GeneralSecurityException) {
            return failed("load key", e)
        } catch (e: IOException) {
            return failed("load key", e)
        }
        if (key == null) return BioCipher.Missing
        return initCipher {
            it.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(BiometricBlob.TAG_BITS, iv))
        }
    }

    /** Deletes the key. Losing it only disables biometric unlock; the PIN is authoritative. */
    fun deleteKey() {
        try {
            keyStore().deleteEntry(ALIAS)
        } catch (e: GeneralSecurityException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "deleting the biometric key failed", e)
        } catch (e: IOException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "deleting the biometric key failed", e)
        }
    }

    private inline fun initCipher(init: (Cipher) -> Unit): BioCipher = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        init(cipher)
        BioCipher.Ready(cipher)
    } catch (_: KeyPermanentlyInvalidatedException) {
        BioCipher.Invalidated
    } catch (e: GeneralSecurityException) {
        failed("init cipher", e)
    }

    private fun failed(step: String, e: Exception): BioCipher.Failed {
        if (BuildConfig.DEBUG) Log.w(TAG, "biometric keystore: $step failed", e)
        return BioCipher.Failed("$step: ${e.javaClass.simpleName}")
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** StrongBox when the device has one; any StrongBox refusal falls back to the TEE. */
    private fun generateKey(): SecretKey = try {
        generateKey(strongBox = true)
    } catch (_: ProviderException) {
        generateKey(strongBox = false)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(strongBox)
            .also(::requireStrongBiometricForEveryUse)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    /**
     * API 30+ states it explicitly. On API 29 the default (validity -1) already means
     * authentication for every use, and per-use keys accept only biometrics there.
     */
    private fun requireStrongBiometricForEveryUse(builder: KeyGenParameterSpec.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        }
    }

    private companion object {
        const val TAG = "BiometricKeystore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "mood_cairns_bio_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
    }
}
