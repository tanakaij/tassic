package tassic.data

/**
 * The privacy screen.
 *
 * The journal holds relapse logs and prayer points; the people list holds notes
 * about family. All of it was one tap away for anyone who picked up an unlocked
 * phone. A PIN over those specific sections — rather than over the whole app,
 * which would make the app annoying enough to stop being used — closes the
 * realistic threat, which is a person in the room, not an attacker.
 *
 * **This is not encryption and the UI says so.** The rows remain plain JSON in
 * localStorage; anyone with devtools or file access can read them regardless of
 * the PIN. Storing a salted, iterated hash means the PIN itself is never
 * written down, but it protects the PIN, not the data. Claiming otherwise would
 * be the more comfortable lie and the more dangerous one, because it would
 * change what someone is willing to write in here.
 */
object Lock {

    private const val ROUNDS = 20_000

    /**
     * Salted, iterated FNV-1a.
     *
     * Deliberately not a real KDF: Kotlin/Wasm has no bundled crypto primitive,
     * and pulling one in for a four-digit PIN — a keyspace of ten thousand,
     * brute-forceable in any case — would be theatre. The iteration count makes
     * a casual guess-and-check loop slow rather than instant, and that is the
     * whole of the claim.
     */
    fun hash(pin: String, salt: String): String {
        var h = 0xcbf29ce484222325UL
        val prime = 0x100000001b3UL
        val material = salt + "::" + pin
        repeat(ROUNDS) { round ->
            material.forEach { ch ->
                h = h xor ch.code.toULong()
                h *= prime
            }
            h = h xor round.toULong()
            h *= prime
        }
        return h.toString(16)
    }

    /** A per-install salt so two people using the same PIN don't share a hash. */
    fun newSalt(): String {
        val seed = T.now()
        return (seed.toULong() * 0x9E3779B97F4A7C15UL).toString(16)
    }

    fun verify(pin: String, settings: Settings): Boolean {
        if (settings.lockPinHash.isEmpty()) return true
        return hash(pin, settings.lockSalt) == settings.lockPinHash
    }

    // ---------------------------------------------------------------- session

    /**
     * How long the current unlock lasts.
     *
     * Held in memory rather than persisted on purpose: closing the app should
     * re-lock, and a timestamp in localStorage would survive that.
     */
    private var unlockedUntilMs: Long = 0

    fun unlock(minutes: Int) {
        unlockedUntilMs = T.now() + minutes.coerceAtLeast(1) * 60_000L
    }

    fun lockNow() {
        unlockedUntilMs = 0
    }

    fun isUnlocked(): Boolean = T.now() < unlockedUntilMs

    /** Whether a given section should currently be sitting behind the gate. */
    fun isLocked(settings: Settings, section: String): Boolean {
        if (!settings.lockReady) return false
        if (isUnlocked()) return false
        return when (section.uppercase()) {
            "JOURNAL" -> settings.lockJournal
            "RECOVERY" -> settings.lockRecovery
            "PEOPLE" -> settings.lockPeople
            else -> false
        }
    }
}
