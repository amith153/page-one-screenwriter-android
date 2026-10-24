package com.amit.pageone.collab

import android.content.Context
import com.amit.pageone.data.Invite
import com.amit.pageone.data.ServerConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

/** Shared Firebase plumbing: one app instance per server, anonymous sign-in, join codes. */
object Hub {

    fun app(context: Context, config: ServerConfig): FirebaseApp {
        val name = "pageone-" + config.projectId
        return FirebaseApp.getApps(context).firstOrNull { it.name == name }
            ?: FirebaseApp.initializeApp(
                context.applicationContext,
                FirebaseOptions.Builder()
                    .setProjectId(config.projectId)
                    .setApiKey(config.apiKey)
                    .setApplicationId(config.appId)
                    .build(),
                name,
            )
    }

    fun firestore(context: Context, config: ServerConfig): FirebaseFirestore = FirebaseFirestore.getInstance(app(context, config))

    /** Signs in anonymously if needed and returns this phone's user id. */
    suspend fun uid(context: Context, config: ServerConfig): String {
        val auth = FirebaseAuth.getInstance(app(context, config))
        auth.currentUser?.let { return it.uid }
        val res = auth.signInAnonymously().await()
        return res.user?.uid ?: error("Sign-in failed")
    }

    // ------------------------------------------------------------------ join codes

    private const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ" // no 0/O, 1/I/L
    private val CODE_RE = Regex("\\b([2-9A-HJ-NP-Z]{4})-?([2-9A-HJ-NP-Z]{4})\\b")

    fun newCode(): String {
        val r = SecureRandom()
        return (1..8).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    /** "k7qp x2ma", "K7QP-X2MA", a pageone:// link or a whole message → "K7QPX2MA". */
    fun findCode(text: String): String? {
        val up = text.uppercase()
        val m = CODE_RE.find(up) ?: return null
        return m.groupValues[1] + m.groupValues[2]
    }

    fun pretty(code: String) = code.take(4) + "-" + code.drop(4)

    /** How long a join code works for. */
    private const val CODE_LIFETIME_MS = 7L * 24 * 60 * 60 * 1000

    /** A brand-new random join code for a shared script — every invite gets its own. */
    suspend fun codeFor(context: Context, config: ServerConfig, scriptId: String, title: String, from: String): String {
        val db = firestore(context, config)
        uid(context, config)
        repeat(5) {
            val code = newCode()
            val ok = runCatching {
                db.collection("invites").document(code)
                    .set(mapOf("s" to scriptId, "t" to title, "n" to from, "at" to FieldValue.serverTimestamp())).await()
            }.isSuccess
            if (ok) return code // (a clash with an existing code is refused by the rules — try another)
        }
        error("Couldn't reach the live-sync server")
    }

    /** Look a code up on the server. */
    suspend fun resolve(context: Context, config: ServerConfig, code: String): Invite? {
        val db = firestore(context, config)
        uid(context, config)
        val d = db.collection("invites").document(code).get().await()
        if (!d.exists()) return null
        val at = d.getTimestamp("at")?.toDate()?.time
        if (at != null && System.currentTimeMillis() - at > CODE_LIFETIME_MS) return null // expired
        val sid = d.getString("s") ?: return null
        return Invite(config, sid, d.getString("t") ?: "Shared script", d.getString("n") ?: "your co-writer")
    }
}
