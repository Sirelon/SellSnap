package com.sirelon.sellsnap.seed

import com.google.auth.oauth2.AccessToken
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.FirestoreOptions
import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.content.PostCode
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.FirestoreSocialStore
import com.sirelon.sellsnap.store.SocialStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Clock
import java.time.DateTimeException
import java.time.ZoneId
import kotlin.system.exitProcess

/** One entry of the accounts file. */
@Serializable
data class SeedAccount(
    val id: String,
    val handle: String,
    val language: String,
    val country: String,
    val timezone: String,
    val codePrefix: String,
)

/** One entry of a tips list: [key] identifies the tip for idempotent loads. */
@Serializable
data class SeedTip(val key: String, val text: String)

sealed interface TipsResult {
    /** [created] holds the codes of the new posts in file order; [skipped] the keys that already existed. */
    data class Loaded(val created: List<String>, val skipped: List<String>) : TipsResult

    /** Nothing was written. [problems] maps each bad tip key (or the file) to what is wrong. */
    data class Rejected(val problems: Map<String, String>) : TipsResult
}

class SeedException(message: String) : Exception(message)

/**
 * Loads the seed data. Accounts are upserted without touching their token, mode, streak or
 * counters. Tips become `drafted` posts, keyed by `seedKey` so a second load adds nothing.
 */
class SeedLoader(private val store: SocialStore, private val clock: Clock) {
    private val json = Json

    /** Creates or updates the accounts in [accountsJson]; returns the ids written. */
    suspend fun upsertAccounts(accountsJson: String): List<String> {
        val accounts = parse<List<SeedAccount>>(accountsJson)
        validateAccounts(accounts)
        for (seed in accounts) {
            store.transaction { tx ->
                val existing = tx.account(seed.id)
                tx.putAccount(
                    existing?.copy(
                        handle = seed.handle,
                        language = seed.language,
                        country = seed.country,
                        timezone = seed.timezone,
                        codePrefix = seed.codePrefix,
                    ) ?: SocialAccount(
                        id = seed.id,
                        handle = seed.handle,
                        language = seed.language,
                        country = seed.country,
                        timezone = seed.timezone,
                        codePrefix = seed.codePrefix,
                    ),
                )
            }
        }
        return accounts.map { it.id }
    }

    /** Loads `tips[language]` from [tipsJson] as `drafted` Tip posts of [accountId]. */
    suspend fun loadTips(accountId: String, language: String, tipsJson: String): TipsResult {
        val account = store.account(accountId) ?: throw SeedException("There is no account named $accountId. Load the accounts first.")
        if (account.language != language) {
            throw SeedException("Account $accountId has language ${account.language}, not $language.")
        }
        val tips = parse<Map<String, List<SeedTip>>>(tipsJson)[language]
            ?: throw SeedException("The tips file has no list for language $language.")

        val existingKeys = store.postsOf(accountId).mapNotNull { it.seedKey }.toSet()
        val problems = findProblems(account, tips)
        if (problems.isNotEmpty()) return TipsResult.Rejected(problems)

        val fresh = tips.filter { it.key !in existingKeys }
        val skipped = tips.filter { it.key in existingKeys }.map { it.key }
        if (fresh.isEmpty()) return TipsResult.Loaded(emptyList(), skipped)

        val now = clock.instant()
        val created = store.transaction { tx ->
            val current = tx.account(accountId)
            if (current == null || current.postSeq != account.postSeq) {
                throw SeedException("Account $accountId changed while loading. Run the load again.")
            }
            var seq = current.postSeq
            val posts = fresh.map { tip ->
                seq++
                val code = PostCode.format(current.codePrefix, Pillar.Tip, seq)
                SocialPost(
                    id = code,
                    accountId = accountId,
                    code = code,
                    pillar = Pillar.Tip,
                    kind = PostKind.Text,
                    text = tip.text,
                    source = PostSource.Seed,
                    seedKey = tip.key,
                    status = PostStatus.Drafted,
                    createdAt = now,
                )
            }
            tx.putAccount(current.copy(postSeq = seq))
            posts.forEach(tx::putPost)
            posts.map { it.code }
        }
        return TipsResult.Loaded(created, skipped)
    }

    private fun findProblems(account: SocialAccount, tips: List<SeedTip>): Map<String, String> {
        val problems = linkedMapOf<String, String>()
        val seen = mutableSetOf<String>()
        for (tip in tips) {
            when {
                tip.key.isBlank() -> problems["(blank key)"] = "the key is empty"
                !seen.add(tip.key) -> problems[tip.key] = "the key appears twice"
                else -> {
                    val violations = ContentRules.check(
                        ContentRules.Candidate(
                            text = tip.text,
                            kind = PostKind.Text,
                            pillar = Pillar.Tip,
                            source = PostSource.Seed,
                            pollOptions = emptyList(),
                            language = account.language,
                            publishedCount = account.publishedCount,
                            linkUrl = "https://example.invalid/go/${account.codePrefix}",
                        ),
                    )
                    if (violations.isNotEmpty()) problems[tip.key] = violations.joinToString(", ") { it.name }
                }
            }
        }
        return problems
    }

    private suspend fun validateAccounts(accounts: List<SeedAccount>) {
        val problems = mutableListOf<String>()
        for (account in accounts) {
            if (!ID.matches(account.id)) problems += "${account.id}: the id must be lowercase letters, digits, - or _"
            if (!PREFIX.matches(account.codePrefix)) problems += "${account.id}: the code prefix must be 1 to 8 lowercase letters or digits"
            if (account.handle.isBlank() || account.language.isBlank() || account.country.isBlank()) {
                problems += "${account.id}: handle, language and country must not be empty"
            }
            try {
                ZoneId.of(account.timezone)
            } catch (_: DateTimeException) {
                problems += "${account.id}: ${account.timezone} is not a time zone"
            }
        }
        if (accounts.map { it.id }.toSet().size != accounts.size) problems += "an account id appears twice"
        // Prefixes must stay unique, or two accounts could mint the same post code.
        val prefixOwners = store.accounts().associate { it.id to it.codePrefix } + accounts.associate { it.id to it.codePrefix }
        prefixOwners.entries.groupBy({ it.value }, { it.key }).filterValues { it.size > 1 }.forEach { (prefix, ids) ->
            problems += "the code prefix $prefix is used by ${ids.sorted().joinToString(" and ")}"
        }
        if (problems.isNotEmpty()) throw SeedException(problems.joinToString("\n"))
    }

    private inline fun <reified T> parse(text: String): T = try {
        json.decodeFromString<T>(text)
    } catch (e: IllegalArgumentException) {
        throw SeedException("The seed file is not valid: ${e.message?.lineSequence()?.firstOrNull().orEmpty()}")
    }

    private companion object {
        val ID = Regex("[a-z0-9_-]{1,32}")
        val PREFIX = Regex("[a-z0-9]{1,8}")
    }
}

private const val USAGE = """Usage (paths are relative to the server/ directory):
  ./gradlew :server:loadSeed --args="--accounts seed/accounts.json"
  ./gradlew :server:loadSeed --args="--account <id> --lang <uk|pl> --tips seed/tips.json"
Uses Application Default Credentials."""

/** CLI behind the `loadSeed` Gradle task. */
fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { it[0] to it.getOrNull(1) }
    val accountsFile = options["--accounts"]
    val tipsFile = options["--tips"]
    if ((accountsFile == null) == (tipsFile == null)) {
        System.err.println(USAGE)
        exitProcess(2)
    }
    val firestore = seedFirestore(System.getenv())
    val exitCode = try {
        runBlocking { execute(SeedLoader(FirestoreSocialStore(firestore), Clock.systemUTC()), options) }
    } catch (e: SeedException) {
        System.err.println(e.message)
        1
    } finally {
        firestore.close()
    }
    exitProcess(exitCode)
}

/**
 * Application Default Credentials, or the gcloud sign-in when `SEED_ACCESS_TOKEN` is set
 * (`SEED_ACCESS_TOKEN=$(gcloud auth print-access-token)`), for a Mac whose ADC belong to another account.
 */
private fun seedFirestore(env: Map<String, String>): Firestore {
    val token = env["SEED_ACCESS_TOKEN"]?.takeIf { it.isNotBlank() }
        ?: return FirestoreOptions.getDefaultInstance().service
    return FirestoreOptions.newBuilder()
        .setProjectId(env["GOOGLE_CLOUD_PROJECT"] ?: DEFAULT_PROJECT)
        .setCredentials(GoogleCredentials.create(AccessToken(token, null)))
        .build()
        .service
}

private const val DEFAULT_PROJECT = "sellsnap-6e85c"

private suspend fun execute(loader: SeedLoader, options: Map<String, String?>): Int {
    options["--accounts"]?.let { file ->
        val ids = loader.upsertAccounts(File(file).readText())
        println("Upserted accounts: ${ids.joinToString(", ")}")
        return 0
    }
    val account = options["--account"]
    val language = options["--lang"]
    val file = options["--tips"]
    if (account == null || language == null || file == null) {
        System.err.println(USAGE)
        return 2
    }
    return when (val result = loader.loadTips(account, language, File(file).readText())) {
        is TipsResult.Loaded -> {
            println("Created ${result.created.size} posts: ${result.created.joinToString(", ")}")
            if (result.skipped.isNotEmpty()) println("Already loaded: ${result.skipped.joinToString(", ")}")
            0
        }
        is TipsResult.Rejected -> {
            System.err.println("Nothing was written. Fix these tips:")
            result.problems.forEach { (key, problem) -> System.err.println("  $key: $problem") }
            1
        }
    }
}
