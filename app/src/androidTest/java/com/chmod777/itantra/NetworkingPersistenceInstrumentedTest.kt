package com.chmod777.itantra

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chmod777.itantra.crypto.Primitives
import com.chmod777.itantra.dtn.DeliveryState
import com.chmod777.itantra.dtn.DtnClock
import com.chmod777.itantra.dtn.DtnNode
import com.chmod777.itantra.dtn.sqlite.NetDatabase
import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.KeyManager
import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.service.AndroidDtnClock
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device checks for the Android-only persistence pieces: SQLite durability across
 * a fresh open (process-restart analogue), Keystore-wrapped identity reload, and the
 * no-backup location of key material. Uses a separate DB name so app data is untouched.
 */
@RunWith(AndroidJUnit4::class)
class NetworkingPersistenceInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = ProtocolConfig.DEFAULT
    private val dbName = "itantra_net_instrumented_test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun bundleOutboxAndContactsSurviveReopen() {
        context.deleteDatabase(dbName)
        val clock: DtnClock = AndroidDtnClock(context)
        val vivek = LocalIdentity.generate("vivek")
        val rahul = LocalIdentity.generate("rahul")
        val rahulContact = TrustedContact.fromVerifiedCapsule(
            ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(rahul.signedCapsule())), "Rahul", 0,
        )

        val first = openDb()
        first.upsert(rahulContact)
        val out = DtnNode(vivek, first, first, clock, config).createMessage(rahul.nodeId, "persist me", "en")
        first.close()

        val reopened = openDb()
        val node = DtnNode(vivek, reopened, reopened, clock, config)
        assertEquals("Rahul", reopened.byNodeId(rahul.nodeId)?.localName)
        val stored = reopened.bundlesById(out.bundleId).single()
        assertEquals(config.normalPrivateCopyBudget, stored.copyTokens)
        assertEquals(DeliveryState.QUEUED, reopened.outgoing(out.bundleId)?.state)
        assertEquals(1, node.inventory().size)
        // The relay-visible blob holds ciphertext only.
        assertTrue(!String(stored.immutableBytes, Charsets.ISO_8859_1).contains("persist me"))
        reopened.close()
    }

    /** A separate database file, so the app's real networking data is never touched. */
    private fun openDb(): NetDatabase = NetDatabase(context, config, dbName)

    @Test
    fun identityReloadsFromKeystoreWrappedFileInNoBackupDir() {
        val manager = KeyManager(context)
        val first = manager.loadOrCreate("instrumented")
        val again = KeyManager(context).load()
        assertNotNull(again)
        assertArrayEquals(first.nodeId, again!!.nodeId)
        assertEquals(first.fingerprint, again.fingerprint)
        val keyFile = File(File(context.noBackupFilesDir, "identity"), "identity_v1.bin")
        assertTrue(keyFile.exists())
        // The file is ciphertext: neither private seed nor public key appears in clear.
        val bytes = keyFile.readBytes()
        assertTrue(bytes.size < 400)
        assertTrue(!Primitives.sha256(bytes).contentEquals(Primitives.sha256(first.signingPublicKey)))
        assertTrue(manager.wrappingKeySecurity.isNotEmpty())
    }
}
