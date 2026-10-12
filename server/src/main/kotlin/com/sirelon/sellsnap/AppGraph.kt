package com.sirelon.sellsnap

import com.google.cloud.firestore.FirestoreOptions
import com.google.cloud.storage.StorageOptions
import com.sirelon.sellsnap.batch.BatchRunner
import com.sirelon.sellsnap.brand.BrandContext
import com.sirelon.sellsnap.brand.FirestoreReleaseNotesSource
import com.sirelon.sellsnap.brand.ReleaseNotesSource
import com.sirelon.sellsnap.digest.DigestBuilder
import com.sirelon.sellsnap.draft.DraftChecker
import com.sirelon.sellsnap.draft.DraftPipeline
import com.sirelon.sellsnap.draft.Drafter
import com.sirelon.sellsnap.http.GoogleOidcVerifier
import com.sirelon.sellsnap.http.OidcVerifier
import com.sirelon.sellsnap.http.TelegramSecret
import com.sirelon.sellsnap.insights.BigQueryInstallSource
import com.sirelon.sellsnap.insights.InsightsCollector
import com.sirelon.sellsnap.insights.InstallSource
import com.sirelon.sellsnap.insights.InstallSync
import com.sirelon.sellsnap.insights.bigQueryHttpClient
import com.sirelon.sellsnap.llm.GeminiLlmClient
import com.sirelon.sellsnap.llm.LlmClient
import com.sirelon.sellsnap.llm.geminiHttpClient
import com.sirelon.sellsnap.media.GcsImageStore
import com.sirelon.sellsnap.media.ImageStore
import com.sirelon.sellsnap.redirect.RedirectService
import com.sirelon.sellsnap.redirect.StoreLinks
import com.sirelon.sellsnap.spend.UsageTracker
import com.sirelon.sellsnap.store.FirestoreSocialStore
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.telegram.ApprovalActions
import com.sirelon.sellsnap.telegram.BotUpdateHandler
import com.sirelon.sellsnap.telegram.Commands
import com.sirelon.sellsnap.telegram.PreviewSender
import com.sirelon.sellsnap.telegram.Previews
import com.sirelon.sellsnap.telegram.Regenerator
import com.sirelon.sellsnap.telegram.TelegramApi
import com.sirelon.sellsnap.telegram.TelegramHttpClient
import com.sirelon.sellsnap.telegram.telegramHttpClient
import com.sirelon.sellsnap.threads.Publisher
import com.sirelon.sellsnap.threads.ThreadsApi
import com.sirelon.sellsnap.threads.ThreadsHttpClient
import com.sirelon.sellsnap.threads.TokenLifecycle
import com.sirelon.sellsnap.threads.threadsHttpClient
import com.sirelon.sellsnap.tick.Alerts
import com.sirelon.sellsnap.tick.QueueFiller
import com.sirelon.sellsnap.tick.TickRunner
import java.time.Clock

/** The services the HTTP routes call. */
class AppGraph(
    val oidc: OidcVerifier,
    val telegramSecret: TelegramSecret,
    val ticks: TickRunner,
    val batch: BatchRunner,
    val updates: BotUpdateHandler,
    val redirects: RedirectService,
    val llm: LlmClient,
    val usage: UsageTracker,
) {
    companion object {
        /** The real adapters: Firestore and Storage with default credentials, Threads, Telegram and Gemini over HTTP. */
        fun create(config: Config, clock: Clock = Clock.systemUTC()): AppGraph {
            val firestore = FirestoreOptions.getDefaultInstance().service
            return assemble(
                config = config,
                store = FirestoreSocialStore(firestore),
                telegram = TelegramHttpClient(telegramHttpClient(), config.telegramBotToken),
                threads = ThreadsHttpClient(threadsHttpClient(), config.threadsAppSecret),
                images = GcsImageStore(StorageOptions.getDefaultInstance().service, config.storageBucket),
                llm = GeminiLlmClient(geminiHttpClient(), config.geminiApiKey),
                releases = FirestoreReleaseNotesSource(firestore),
                installs = BigQueryInstallSource(bigQueryHttpClient()),
                oidc = GoogleOidcVerifier(config.oidcAudience, config.schedulerSaEmail),
                clock = clock,
            )
        }

        /** Wires the services over the given ports; tests call this with fakes. */
        fun assemble(
            config: Config,
            store: SocialStore,
            telegram: TelegramApi,
            threads: ThreadsApi,
            images: ImageStore,
            llm: LlmClient,
            releases: ReleaseNotesSource,
            installs: InstallSource,
            oidc: OidcVerifier,
            clock: Clock,
        ): AppGraph {
            val owner = config.ownerTelegramId
            val previews = Previews(images, config.postLinkTemplate, regenerateAvailable = true)
            val sender = PreviewSender(telegram, previews, store, owner)
            val usage = UsageTracker(store, clock, config.dailyCallLimit)
            val brand = BrandContext.load()
            val pipeline = DraftPipeline(
                Drafter(llm, usage, brand, config.draftModel),
                DraftChecker(llm, usage, brand, config.checkModel),
            )
            val regenerator = Regenerator(store, telegram, pipeline, releases, previews, sender, clock, owner)
            val approvals = ApprovalActions(store, telegram, threads, previews, sender, clock, owner, regenerator)
            val publisher = Publisher(store, threads, images, clock, config.postLinkTemplate)
            val tokens = TokenLifecycle(store, threads, clock)
            val alerts = Alerts(store, telegram, clock, owner)
            val commands = Commands(store, telegram, images, publisher, tokens, previews, sender, approvals, clock, owner)
            val insights = InsightsCollector(store, threads, clock)
            val links = StoreLinks(config.playPackage, config.appStoreId, config.appleProviderToken, config.landingUrl)
            return AppGraph(
                oidc = oidc,
                telegramSecret = TelegramSecret(config.telegramWebhookSecret),
                ticks = TickRunner(
                    store = store,
                    tokens = tokens,
                    publisher = publisher,
                    filler = QueueFiller(store, sender, previews, clock),
                    previewSender = sender,
                    alerts = alerts,
                    insights = insights,
                    clock = clock,
                ),
                batch = BatchRunner(
                    store, pipeline, releases, previews, sender, telegram, alerts, usage,
                    insights, InstallSync(store, installs, alerts, clock), DigestBuilder(store, clock), clock, owner,
                ),
                updates = BotUpdateHandler(store, telegram, approvals, commands, clock, owner),
                redirects = RedirectService(store, links, clock),
                llm = llm,
                usage = usage,
            )
        }
    }
}
