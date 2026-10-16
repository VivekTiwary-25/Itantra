package com.chmod777.itantra

import com.chmod777.itantra.crypto.Domains
import com.chmod777.itantra.crypto.NodeIds
import com.chmod777.itantra.dtn.DtnNode
import com.chmod777.itantra.identity.ContactQrCodec
import com.chmod777.itantra.identity.IdentityCapsuleV1
import com.chmod777.itantra.identity.LocalIdentity
import com.chmod777.itantra.identity.SignedCapsule
import com.chmod777.itantra.identity.TrustMethod
import com.chmod777.itantra.identity.TrustedContact
import com.chmod777.itantra.identity.TrustedContactStore
import com.chmod777.itantra.protocol.ProtocolConfig
import com.chmod777.itantra.protocol.cbor.MalformedInputException
import com.chmod777.itantra.testing.FakeClock
import com.chmod777.itantra.testing.InMemoryDtnStore
import com.chmod777.itantra.testing.InMemoryTrustedContactStore
import com.chmod777.itantra.ui.app.mayPersistScannedContact
import com.chmod777.itantra.ui.app.safeContactName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The trust path: what actually has to be true before a stranger's keys are written to the
 * contacts table.
 *
 * WHAT THESE TESTS CANNOT DO, STATED UP FRONT.
 *
 * The confirmation gate lives in one place only: `AppState.saveTrustedContact`, which refuses
 * unless `pendingCapsule != null && fingerprintConfirmed`. `AppState` is constructed with a
 * `Context`, a `CoroutineScope` and seven Compose `MutableState`s, reads SharedPreferences in
 * its constructor and calls `refreshContacts()` from its `init` block, so a JVM unit test
 * cannot build one. The guard is therefore NOT covered here, and no test in this file should be
 * read as covering it.
 *
 * What is proved instead is the shape of the hole around it: every step between a scanned code
 * and a persisted row is reachable from the JVM, none of them requires or records a fingerprint
 * comparison, and the only thing standing between the two is that in-memory boolean. The
 * smallest honest production change that would make the guard testable is to lift the predicate
 * out of the state holder as a pure, package-internal function, e.g. in AppState.kt:
 *
 *     internal fun mayPersistScannedContact(capsule: IdentityCapsuleV1?, confirmed: Boolean) =
 *         capsule != null && confirmed
 *
 * and have `saveTrustedContact` call it. That is one function and one call site, changes no
 * behaviour, and would let this file assert the refusal against the real code instead of
 * describing it in a comment.
 *
 * The integrator took that up: mayPersistScannedContact exists, saveTrustedContact calls it,
 * and the last group of tests in this file asserts it. The rest of the caveat still stands --
 * the navigation and recreation behaviour is Compose-level and is not covered here.
 */
class ContactTrustTest {

    private val config = ProtocolConfig.DEFAULT
    private val me = LocalIdentity.generate("This phone")
    private val them = LocalIdentity.generate("Rahul")
    private val mallory = LocalIdentity.generate("Mallory")

    /** A real ITANTRA1: code for [identity], optionally carrying a display name of our choosing. */
    private fun code(identity: LocalIdentity, name: String = identity.displayNameHint): String {
        val capsule = IdentityCapsuleV1(
            identity.signingPublicKey,
            identity.encryptionPublicKey,
            name,
            identity.keyEpoch,
        )
        val bytes = capsule.encode()
        return ContactQrCodec.encode(SignedCapsule(bytes, identity.sign(Domains.CAPSULE, bytes)))
    }

    /** A store that fails the test if anything writes to it. */
    private class NoWritesStore : TrustedContactStore {
        override fun all(): List<TrustedContact> = emptyList()
        override fun byNodeId(nodeId: ByteArray): TrustedContact? = null
        override fun upsert(contact: TrustedContact): Boolean =
            throw AssertionError("a contact was written: ${contact.localName}")
        override fun rename(nodeId: ByteArray, localName: String): Boolean =
            throw AssertionError("a contact was renamed")
        override fun remove(nodeId: ByteArray): Boolean =
            throw AssertionError("a contact was removed")
    }

    // ---- what a scan on its own does, and does not, do --------------------------------

    @Test
    fun `scanning and verifying a code writes nothing by itself`() {
        // The whole of what the engine does when a code is scanned: parse, bound, check the
        // self-signature, compute a fingerprint to put on screen. Reading the fingerprint the
        // user is asked to compare must not be what saves the contact.
        val store: TrustedContactStore = NoWritesStore()
        val capsule = ContactQrCodec.decodeAndVerify(code(them))
        assertEquals(them.fingerprint, capsule.fingerprint)
        assertEquals(them.displayNameHint, capsule.displayNameHint)
        // Still nothing in the store, and still nothing that says anyone compared anything.
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `a verified capsule carries no record that a fingerprint was ever compared`() {
        // This is the finding, as a test. `fromVerifiedCapsule` takes a capsule and a name and
        // nothing else: there is no confirmation token, no "compared by a human" flag, and
        // TrustMethod.QR_VERIFIED is stamped unconditionally. Any caller that holds a verified
        // capsule can mint a fully trusted contact. The gate is outside this type, which is why
        // a second caller (ui/lab/NetworkLabActivity.kt:249) could skip it without the data
        // model noticing.
        val capsule = ContactQrCodec.decodeAndVerify(code(them))
        val contact = TrustedContact.fromVerifiedCapsule(capsule, "Rahul", 0)
        assertEquals(TrustMethod.QR_VERIFIED, contact.trustMethod)
        assertEquals(1, TrustMethod.entries.size)

        val store = InMemoryTrustedContactStore()
        assertTrue(store.upsert(contact))
        assertEquals(1, store.all().size)
    }

    // ---- the code the user compares against is the code that gets saved ---------------

    @Test
    fun `the fingerprint shown is derived from exactly the keys that are saved`() {
        val capsule = ContactQrCodec.decodeAndVerify(code(them))
        val contact = TrustedContact.fromVerifiedCapsule(capsule, "Rahul", 0)
        // FingerprintCheckScreen is handed capsule.fingerprint; the row stores contact.fingerprint.
        // Both must be the hash of the two public keys actually written to the row.
        assertEquals(capsule.fingerprint, contact.fingerprint)
        assertEquals(
            NodeIds.fingerprint(contact.signingPublicKey, contact.encryptionPublicKey),
            contact.fingerprint,
        )
        // Eight groups of four hex digits: 128 bits, all of it on screen.
        val groups = contact.fingerprint.split(' ')
        assertEquals(8, groups.size)
        assertTrue(groups.all { it.length == 4 && it.all(Char::isLetterOrDigit) })
    }

    @Test
    fun `no crafted code can show one fingerprint and save different keys`() {
        val shown = ContactQrCodec.decodeAndVerify(code(them)).fingerprint
        // Swap either key and the fingerprint moves, so a code cannot display a fingerprint
        // belonging to one key pair while carrying another.
        assertNotEquals(
            shown,
            NodeIds.fingerprint(mallory.signingPublicKey, them.encryptionPublicKey),
        )
        assertNotEquals(
            shown,
            NodeIds.fingerprint(them.signingPublicKey, mallory.encryptionPublicKey),
        )
        // And a capsule whose bytes were replaced under a kept signature does not verify at all.
        val genuine = them.signedCapsule()
        val forged = SignedCapsule(mallory.capsule().encode(), genuine.signature)
        assertThrows(MalformedInputException::class.java) {
            ContactQrCodec.decodeAndVerify(ContactQrCodec.encode(forged))
        }
    }

    @Test
    fun `the compared fingerprint does not cover the display name or the key epoch`() {
        // The gap: two codes that differ in name and epoch are indistinguishable on the
        // comparison screen and collapse to the same row. Whatever the user checked out loud,
        // they did not check either of these fields.
        val honest = ContactQrCodec.decodeAndVerify(code(them, "Rahul"))
        val renamed = IdentityCapsuleV1(
            them.signingPublicKey,
            them.encryptionPublicKey,
            "Rahul (verified)",
            them.keyEpoch + 7,
        )
        assertEquals(honest.fingerprint, renamed.fingerprint)
        assertEquals(honest.nodeId.toList(), renamed.nodeId.toList())
    }

    // ---- the own-code check -----------------------------------------------------------

    @Test
    fun `a node id comparison against an absent local identity is false`() {
        // The fact AppState.onContactScanned null-checks `selfIdentity?.nodeId` before using it,
        // and the fact NetworkLabActivity.parse (line 228) does not: with a null local identity
        // `contentEquals(null)` is false, so that screen would accept this phone's own code.
        val nodeId: ByteArray? = null
        assertFalse(me.nodeId.contentEquals(nodeId))
    }

    @Test
    fun `this phone's own code is recognisable only by node id, which covers both keys`() {
        val own = ContactQrCodec.decodeAndVerify(code(me))
        assertTrue(own.nodeId.contentEquals(me.nodeId))

        // A code that reuses this phone's encryption key but signs with a key of its own is a
        // different node id, so the own-code check lets it through. It cannot forge our signing
        // key, so this is a curiosity rather than a break -- but the check is on the composite,
        // not on either key, and nothing downstream objects to a contact sharing our epk.
        val reuse = IdentityCapsuleV1(
            mallory.signingPublicKey,
            me.encryptionPublicKey,
            "Me",
            1,
        )
        val bytes = reuse.encode()
        val scanned = ContactQrCodec.decodeAndVerify(
            ContactQrCodec.encode(SignedCapsule(bytes, mallory.sign(Domains.CAPSULE, bytes))),
        )
        assertFalse(scanned.nodeId.contentEquals(me.nodeId))
        assertTrue(scanned.encryptionPublicKey.contentEquals(me.encryptionPublicKey))
    }

    // ---- the name inside a scanned code ------------------------------------------------

    @Test
    fun `a hostile display name survives the whole flow into the saved row`() {
        // AddContactNameScreen pre-fills the field with the scanned hint and Save is one tap, so
        // this is what gets stored unless the user retypes it. `.take(32)` only runs on keystrokes,
        // and fromVerifiedCapsule caps nothing -- while renameContact/renameSelf both cap at 32.
        val hostile = "Vivek‮\nVERIFIED \u0000‫"
        val capsule = ContactQrCodec.decodeAndVerify(code(them, hostile))
        assertEquals(hostile, capsule.displayNameHint)

        val saved = TrustedContact.fromVerifiedCapsule(capsule, capsule.displayNameHint, 0)
        assertTrue(saved.localName.contains('\n'))
        assertTrue(saved.localName.contains('‮'))
        assertTrue(saved.localName.contains('\u0000'))

        // Length is bounded only by the capsule's 64-byte field, which is twice the 32 the
        // rename dialog enforces.
        val long = "x".repeat(IdentityCapsuleV1.MAX_DISPLAY_NAME_BYTES)
        val longSaved = TrustedContact.fromVerifiedCapsule(
            ContactQrCodec.decodeAndVerify(code(them, long)),
            long,
            0,
        )
        assertEquals(64, longSaved.localName.length)
        assertTrue(longSaved.localName.length > 32)
    }

    @Test
    fun `a whitespace display name saves a contact with no visible name`() {
        // fromVerifiedCapsule trims the typed name but not the hint it falls back to, so a code
        // whose name is three spaces produces a row that renders as an empty label in Settings
        // and in the recipient picker. "Contact" is only reached when the hint is exactly empty.
        val blank = ContactQrCodec.decodeAndVerify(code(them, "   "))
        assertEquals("   ", TrustedContact.fromVerifiedCapsule(blank, "", 0).localName)
        val empty = ContactQrCodec.decodeAndVerify(code(them, ""))
        assertEquals("Contact", TrustedContact.fromVerifiedCapsule(empty, "", 0).localName)
    }

    @Test
    fun `a code that is not an iTantra code, or is too long, is refused before anything else`() {
        val store: TrustedContactStore = NoWritesStore()
        listOf(
            "",
            "https://example.test/itantra",
            "ITANTRA1:not base64 at all!!",
            "ITANTRA1:" + "A".repeat(SignedCapsule.MAX_ENCODED_BYTES * 2 + 2),
        ).forEach { payload ->
            assertThrows(MalformedInputException::class.java) {
                ContactQrCodec.decodeAndVerify(payload)
            }
        }
        assertTrue(store.all().isEmpty())
    }

    // ---- replacement, rename, removal --------------------------------------------------

    @Test
    fun `re-adding the same person replaces their row instead of adding a second`() {
        val store = InMemoryTrustedContactStore()
        val capsule = ContactQrCodec.decodeAndVerify(code(them))
        store.upsert(TrustedContact.fromVerifiedCapsule(capsule, "Rahul", 1_000))
        store.upsert(TrustedContact.fromVerifiedCapsule(capsule, "Rahul (work)", 2_000))
        assertEquals(1, store.all().size)
        assertEquals("Rahul (work)", store.all().single().localName)
        // The replace is silent: the earlier name and the original createdAt are gone with no
        // warning anywhere in the flow that this contact already existed.
        assertEquals(2_000L, store.all().single().createdAtWallMs)

        // Two different people can never collide onto one row: node_id is a hash of both keys.
        store.upsert(TrustedContact.fromVerifiedCapsule(ContactQrCodec.decodeAndVerify(code(mallory)), "Rahul (work)", 3_000))
        assertEquals(2, store.all().size)
    }

    @Test
    fun `removing a contact does not stop a message already queued for them`() {
        // The remove dialog (SettingsScreen.kt:271) promises "messages already queued for them
        // will never be delivered". Nothing in the removal path touches the bundle: it is already
        // sealed to their keys, sitting in the store, and the relay path never consults contacts.
        val contacts = InMemoryTrustedContactStore()
        val store = InMemoryDtnStore()
        val node = DtnNode(me, contacts, store, FakeClock(), config)
        contacts.upsert(
            TrustedContact.fromVerifiedCapsule(ContactQrCodec.decodeAndVerify(code(them)), "Rahul", 0),
        )

        val queued = node.createMessage(them.nodeId, "meet at the south gate", "en")
        assertTrue(contacts.remove(them.nodeId))

        assertFalse(store.bundlesById(queued.bundleId).isEmpty())
        assertNotNull(store.outgoing(queued.bundleId))
        // Only a *new* message is blocked by the removal.
        assertThrows(IllegalArgumentException::class.java) {
            node.createMessage(them.nodeId, "and again", "en")
        }
    }

    @Test
    fun `renaming an unknown contact is refused, so a stale row cannot be revived by rename`() {
        val store = InMemoryTrustedContactStore()
        assertFalse(store.rename(them.nodeId, "Rahul"))
        assertTrue(store.all().isEmpty())
    }

    // ---- the guard itself, now that it can be reached from a JVM test --------------------
    //
    // The audit that produced this file could not test AppState.saveTrustedContact: AppState
    // wants a Context, a scope and seven Compose states. It proposed pulling the predicate out
    // instead of restating it in a test, which is what mayPersistScannedContact is. These are
    // the tests that proposal was for.

    @Test
    fun `a scanned contact may not be persisted without a confirmed fingerprint`() {
        val capsule = them.capsule()
        assertFalse(mayPersistScannedContact(capsule, fingerprintConfirmed = false))
    }

    @Test
    fun `a confirmation with no capsule persists nothing`() {
        assertFalse(mayPersistScannedContact(null, fingerprintConfirmed = true))
        assertFalse(mayPersistScannedContact(null, fingerprintConfirmed = false))
    }

    @Test
    fun `both halves together are the only way through`() {
        val capsule = them.capsule()
        assertTrue(mayPersistScannedContact(capsule, fingerprintConfirmed = true))
    }

    // ---- the name that arrives inside someone else's code --------------------------------

    @Test
    fun `a scanned name cannot be longer than every other name in the app`() {
        assertEquals(32, safeContactName("x".repeat(64)).length)
    }

    @Test
    fun `a scanned name cannot fake a second line of interface`() {
        val forged = "Yash\n4f2a 9c71 b0d3 6e88"
        val cleaned = safeContactName(forged)
        assertFalse(cleaned.contains("\n"))
        assertEquals("Yash 4f2a 9c71 b0d3 6e88", cleaned)
    }

    @Test
    fun `a scanned name cannot reverse how the row reads`() {
        // U+202E flips everything after it; the row would read as a different name entirely.
        val cleaned = safeContactName("Yash\u202Ehsay")
        assertFalse(cleaned.any { it == '\u202E' })
        assertEquals("Yashhsay", cleaned)
    }

    @Test
    fun `a whitespace-only scanned name does not save an invisible contact`() {
        assertEquals("", safeContactName("   \u0000 "))
    }

    @Test
    fun `an ordinary name survives untouched`() {
        assertEquals("Trisha", safeContactName("  Trisha  "))
        assertEquals("\u0935\u093F\u0935\u0947\u0915", safeContactName("\u0935\u093F\u0935\u0947\u0915"))
    }
}
