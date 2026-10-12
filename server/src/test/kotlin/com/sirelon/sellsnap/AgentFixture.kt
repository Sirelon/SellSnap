package com.sirelon.sellsnap

import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.fakes.FakeImageStore
import com.sirelon.sellsnap.fakes.FakeInstallSource
import com.sirelon.sellsnap.fakes.FakeLlmClient
import com.sirelon.sellsnap.fakes.FakeOidcVerifier
import com.sirelon.sellsnap.fakes.FakeReleaseNotesSource
import com.sirelon.sellsnap.fakes.FakeTelegramApi
import com.sirelon.sellsnap.fakes.FakeThreadsApi
import com.sirelon.sellsnap.fakes.InMemorySocialStore
import com.sirelon.sellsnap.fakes.MutableClock
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import java.time.Instant

fun testConfig(appleProviderToken: String? = null) = Config(
    port = 8080,
    telegramBotToken = "123456:bot-token-value-for-tests-only-aaaaaaaa",
    threadsAppSecret = "app-secret-value",
    telegramWebhookSecret = AgentFixture.HOOK_SECRET,
    ownerTelegramId = AgentFixture.OWNER,
    schedulerSaEmail = AgentFixture.SCHEDULER_EMAIL,
    oidcAudience = "https://agent.test",
    publicBaseUrl = "https://agent.test",
    postLinkTemplate = "https://agent.test/go/{code}",
    storageBucket = "bucket",
    playPackage = "com.sirelon.sellsnap",
    appStoreId = "6776001373",
    appleProviderToken = appleProviderToken,
    landingUrl = "https://sellsnap.test/landing",
    geminiApiKey = "gemini-key-value-for-tests-only",
)

/** The whole service on fakes. [store] is what the services see; [memory] is the data behind it. */
class AgentFixture(
    now: Instant = NOW,
    appleProviderToken: String? = null,
    wrapStore: (InMemorySocialStore) -> SocialStore = { it },
    val llm: FakeLlmClient = FakeLlmClient(),
    releases: List<ReleaseNotes> = emptyList(),
    val installs: FakeInstallSource = FakeInstallSource(),
) {
    val clock = MutableClock(now)
    val memory = InMemorySocialStore()
    val store: SocialStore = wrapStore(memory)
    val telegram = FakeTelegramApi()
    val threads = FakeThreadsApi(memory)
    val images = FakeImageStore()
    val oidc = FakeOidcVerifier()
    val config = testConfig(appleProviderToken)
    val graph = AppGraph.assemble(config, store, telegram, threads, images, llm, FakeReleaseNotesSource(releases), installs, oidc, clock)

    fun account(id: String) = memory.allAccounts.getValue(id)

    fun post(id: String) = memory.allPosts.getValue(id)

    fun status(id: String): PostStatus = post(id).status

    /** An account that can publish: it has a Threads token that was refreshed a day ago. */
    fun publishable(account: SocialAccount): SocialAccount = account.copy(
        token = "stored-token",
        threadsUserId = "threads-user",
        tokenRefreshedAt = clock.instant().minusSeconds(86_400),
        tokenExpiresAt = clock.instant().plusSeconds(86_400L * 59),
    )

    fun sentTexts(): List<String> = telegram.callsOf<FakeTelegramApi.Call.Sent>().map { it.text.orEmpty() }

    fun put(post: SocialPost) = memory.put(post)

    companion object {
        const val OWNER = 42L
        const val HOOK_SECRET = "hook-secret-value"
        const val SCHEDULER_EMAIL = "scheduler@sellsnap.iam.gserviceaccount.com"

        /** A Monday, 08:00 in Kyiv. */
        val NOW: Instant = Instant.parse("2026-10-12T05:00:00Z")
    }
}
