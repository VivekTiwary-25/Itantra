package com.chmod777.itantra.identity

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import com.chmod777.itantra.protocol.cbor.CborCodec
import com.chmod777.itantra.protocol.cbor.CborLimits
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.protocol.cbor.asMap
import com.chmod777.itantra.protocol.cbor.bytes
import com.chmod777.itantra.protocol.cbor.bytesUpTo
import com.chmod777.itantra.protocol.cbor.cborMap
import com.chmod777.itantra.protocol.cbor.text
import com.chmod777.itantra.protocol.cbor.uint
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Identity key persistence (spec §10, audit F).
 *
 * Android Keystore cannot hold raw Ed25519 seeds or X25519 scalars for use by
 * Tink, so the private material is generated with a CSPRNG, encrypted with an
 * AES-256-GCM key that lives in Android Keystore, and written to
 * `noBackupFilesDir` (never backed up). A reinstall or device restore therefore
 * loses the identity; the UI must say so and ask for QR re-exchange.
 */
class KeyManager(context: Context) {
    private val file = File(File(context.noBackupFilesDir, "identity"), "identity_v1.bin")

    /** Keystore security level of the wrapping key, reported from KeyInfo, never assumed. */
    var wrappingKeySecurity: String = "unknown"
        private set

    fun loadOrCreate(defaultDisplayName: String): LocalIdentity {
        load()?.let { return it }
        val identity = LocalIdentity.generate(defaultDisplayName)
        save(identity)
        return identity
    }

    fun load(): LocalIdentity? {
        if (!file.exists()) return null
        val key = wrappingKey(create = false) ?: return null
        return try {
            val envelope = CborCodec.decode(file.readBytes(), CborLimits(maxInputBytes = 512, maxDepth = 1, maxContainerItems = 3)).asMap()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope.bytes(2, 12)))
            val plain = CborCodec.decode(cipher.doFinal(envelope.bytesUpTo(3, 400)), CborLimits(maxInputBytes = 400, maxDepth = 1, maxContainerItems = 4)).asMap()
            LocalIdentity(
                signingSeed = plain.bytes(1, 32),
                encryptionPrivateKey = plain.bytes(2, 32),
                keyEpoch = plain.uint(3),
                displayNameHint = plain.text(4, IdentityCapsuleV1.MAX_DISPLAY_NAME_BYTES),
            )
        } catch (e: MalformedInputException) {
            null
        } catch (e: java.security.GeneralSecurityException) {
            // The wrapping key no longer matches (e.g. restored file without its Keystore key).
            null
        }
    }

    /** Persists a new display name hint without changing keys. */
    fun rename(identity: LocalIdentity, displayName: String): LocalIdentity {
        val (seed, x) = identity.exportPrivateMaterial()
        val renamed = LocalIdentity(seed, x, identity.keyEpoch, displayName.take(32))
        save(renamed)
        seed.fill(0)
        x.fill(0)
        return renamed
    }

    private fun save(identity: LocalIdentity) {
        val (seed, x) = identity.exportPrivateMaterial()
        val plain = CborCodec.encode(
            cborMap {
                put(1, seed)
                put(2, x)
                put(3, identity.keyEpoch)
                put(4, identity.displayNameHint)
            },
        )
        seed.fill(0)
        x.fill(0)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(create = true))
        val envelope = CborCodec.encode(
            cborMap {
                put(1, 1)
                put(2, cipher.iv)
                put(3, cipher.doFinal(plain))
            },
        )
        plain.fill(0)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(envelope)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun wrappingKey(create: Boolean): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = (keyStore.getKey(ALIAS, null) as? SecretKey) ?: if (create) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
                init(
                    KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .setRandomizedEncryptionRequired(true)
                        .build(),
                )
                generateKey()
            }
        } else {
            null
        }
        key?.let { wrappingKeySecurity = describeSecurity(it) }
        return key
    }

    private fun describeSecurity(key: SecretKey): String = try {
        val info = SecretKeyFactory.getInstance(key.algorithm, KEYSTORE).getKeySpec(key, KeyInfo::class.java) as KeyInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (info.securityLevel) {
                KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox"
                KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
                KeyProperties.SECURITY_LEVEL_SOFTWARE -> "software"
                else -> "unknown"
            }
        } else {
            @Suppress("DEPRECATION")
            if (info.isInsideSecureHardware) "secure hardware (reported)" else "software"
        }
    } catch (_: Exception) {
        "unknown"
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "itantra.identity.wrap.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
