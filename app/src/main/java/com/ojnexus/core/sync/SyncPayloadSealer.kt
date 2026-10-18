package com.ojnexus.core.sync

/**
 * Phase 119 — the crypto seam from RFC 0001 §4. The production primitive (XChaCha20-Poly1305
 * via a maintained library) is a Phase 120/transport decision; until then the only
 * implementations are the test double and the explicit stub below. Hand-rolled crypto is
 * forbidden by the RFC — which is exactly why this interface exists: the ledger code depends on
 * the seam, never on a cipher.
 */
interface SyncPayloadSealer {
    fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray

    /** Returns null on tamper/AAD mismatch — never throws for verification failures. */
    fun open(sealed: ByteArray, aad: ByteArray): ByteArray?
}

/**
 * Test double ONLY — XOR against a fixed pad. Never wire this into a debug build that syncs
 * real data; it exists so the repository lifecycle can be tested without a crypto library.
 */
class XorSealer(private val pad: ByteArray) : SyncPayloadSealer {

    init {
        require(pad.isNotEmpty()) { "XorSealer pad must not be empty (test double only)" }
    }

    override fun seal(plaintext: ByteArray, aad: ByteArray): ByteArray {
        val mixed = pad + aad
        return ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor mixed[i % mixed.size].toInt()).toByte() }
    }

    override fun open(sealed: ByteArray, aad: ByteArray): ByteArray? {
        // The double has no auth tag; a "wrong AAD" only differs if the caller passes one —
        // real tamper detection is the production AEAD's job. Opening is symmetric.
        return seal(sealed, aad)
    }
}

/**
 * Production stub: fails loudly and early. Selecting and wiring the real AEAD primitive
 * (Tink or a libsodium binding, per RFC §4) is the transport phase's first task.
 */
class ProductionSealerNotSelectedException : IllegalStateException(
    "Production AEAD primitive not selected yet (RFC 0001 §4: Tink or libsodium binding).",
)
