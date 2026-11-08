package com.chmod777.itantra.identity

import com.chmod777.itantra.crypto.Domains
import com.chmod777.itantra.crypto.Primitives
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rename bug found on I2221 (2026-10-23): KeyManager.rename built a LocalIdentity from exported key
 * arrays and then zeroed those same arrays, because LocalIdentity kept references instead of copies.
 * One rename left the in-memory identity signing with a zero key; a second saved the zero keys to disk
 * (fingerprint 91c4 ad49 0157 6d46 7f67 fd42 3e46 5f57). KeyManager needs the Android Keystore, so these
 * tests replay its export → build → wipe sequence directly.
 */
class LocalIdentityKeyCopyTest {
    private val message = "hello".toByteArray()

    private fun signsAs(identity: LocalIdentity, reference: LocalIdentity): Boolean =
        Primitives.ed25519Verify(reference.signingPublicKey, Domains.CAPSULE, message, identity.sign(Domains.CAPSULE, message))

    /** What KeyManager.rename does: export copies, build the renamed identity, save, wipe the copies. */
    private fun renameLikeKeyManager(identity: LocalIdentity, name: String): LocalIdentity {
        val (seed, x) = identity.exportPrivateMaterial()
        val renamed = LocalIdentity(seed, x, identity.keyEpoch, name)
        seed.fill(0)
        x.fill(0)
        return renamed
    }

    @Test
    fun wipingTheConstructorArraysDoesNotTouchTheIdentity() {
        val original = LocalIdentity.generate("iTantra user")
        val renamed = renameLikeKeyManager(original, "Vimalesh")

        assertTrue(signsAs(renamed, original))
        val sealed = Primitives.sealTo(original.encryptionPublicKey, message, Domains.SEALED_MESSAGE)
        assertArrayEquals(message, renamed.openSealed(sealed, Domains.SEALED_MESSAGE))
        val (seed, x) = renamed.exportPrivateMaterial()
        assertFalse(seed.all { it == 0.toByte() })
        assertFalse(x.all { it == 0.toByte() })
    }

    @Test
    fun twoRenamesInOneSessionKeepTheSameIdentity() {
        // The I2221 sequence: Save next to "Your name", twice, without restarting the app.
        val original = LocalIdentity.generate("iTantra user")
        val second = renameLikeKeyManager(renameLikeKeyManager(original, "Vimalesh "), "Vimalesh")

        assertEquals("Vimalesh", second.displayNameHint)
        assertEquals(original.fingerprint, second.fingerprint)
        assertArrayEquals(original.nodeId, second.nodeId)
        assertTrue(signsAs(second, original))
    }
}
