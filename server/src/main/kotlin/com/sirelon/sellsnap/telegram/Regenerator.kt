package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.brand.ReleaseNotes
import com.sirelon.sellsnap.brand.ReleaseNotesSource
import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.draft.DRAFTABLE_PILLARS
import com.sirelon.sellsnap.draft.DraftOutcome
import com.sirelon.sellsnap.draft.DraftPipeline
import com.sirelon.sellsnap.draft.Prompts
import com.sirelon.sellsnap.draft.SlotBrief
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The Regenerate button (D14): a new draft for one pending post that the drafter wrote, made by the same
 * [DraftPipeline] as the batch. The tap is claimed in a transaction first, so a double tap or a stale button
 * changes nothing; the callback is then answered at once and the pipeline runs inside the webhook request.
 * The new version replaces the old one in a conditional `pending -> pending` write; any other outcome
 * leaves the post as it was.
 */
class Regenerator(
    private val store: SocialStore,
    private val telegram: TelegramApi,
    private val pipeline: DraftPipeline,
    private val releases: ReleaseNotesSource,
    private val previews: Previews,
    private val sender: PreviewSender,
    private val clock: Clock,
    private val ownerChatId: Long,
) {
    private sealed interface Claim {
        data object Missing : Claim

        data object NotRegenerable : Claim

        class Wrong(val status: PostStatus) : Claim

        data object LimitReached : Claim

        data object Running : Claim

        class Claimed(val post: SocialPost, val account: SocialAccount, val marker: Instant) : Claim
    }

    /** Handles a tap on Regenerate for [postId]. Answers the callback query [queryId] exactly once. */
    suspend fun regenerate(postId: String, queryId: String) {
        val marker = clock.instant()
        val claim = store.transaction { tx ->
            val post = tx.post(postId)
            val account = post?.let { tx.account(it.accountId) }
            val since = post?.regeneratingSince
            when {
                post == null || account == null -> Claim.Missing
                post.source != PostSource.Drafter || post.pillar !in DRAFTABLE_PILLARS -> Claim.NotRegenerable
                post.status != PostStatus.Pending -> Claim.Wrong(post.status)
                post.regenerations >= ApprovalActions.MAX_REGENERATIONS -> Claim.LimitReached
                since != null && since.plus(MARKER_LIFETIME).isAfter(marker) -> Claim.Running
                else -> Claim.Claimed(post, account, marker).also { tx.putPost(post.copy(regeneratingSince = marker)) }
            }
        }
        when (claim) {
            Claim.Missing -> telegram.answerCallbackQuery(queryId, BotText.POST_NOT_FOUND)
            Claim.NotRegenerable -> telegram.answerCallbackQuery(queryId, BotText.CANNOT_REGENERATE)
            is Claim.Wrong -> telegram.answerCallbackQuery(queryId, BotText.alreadyStatus(claim.status))
            Claim.LimitReached -> telegram.answerCallbackQuery(queryId, BotText.NO_MORE_VERSIONS)
            Claim.Running -> telegram.answerCallbackQuery(queryId, BotText.REGENERATE_RUNNING)
            is Claim.Claimed -> {
                try {
                    telegram.answerCallbackQuery(queryId, BotText.REGENERATING)
                    rewrite(claim.post, claim.account)
                } finally {
                    release(claim.post.id, claim.marker)
                }
            }
        }
    }

    private suspend fun rewrite(post: SocialPost, account: SocialAccount) {
        val brief = brief(post, account)
        if (brief == null) {
            say(post, BotText.REGENERATE_NO_RELEASE)
            return
        }
        when (val outcome = pipeline.run(brief, account.publishedCount, previews.linkUrl(post.code, account.language))) {
            is DraftOutcome.Draft -> replace(post, outcome)
            is DraftOutcome.DraftFailed -> say(post, BotText.REGENERATE_FAILED)
            is DraftOutcome.Deferred -> say(post, BotText.REGENERATE_BUSY)
            is DraftOutcome.UsageStop -> say(post, BotText.USAGE_LIMIT_REACHED)
        }
    }

    /** The slot as the drafter sees it: the post's pillar and the account's language, latest texts and, for an update, its release. */
    private suspend fun brief(post: SocialPost, account: SocialAccount): SlotBrief? {
        val release = if (post.pillar == Pillar.Update) releaseOf(post) ?: return null else null
        val recent = store.postsOf(account.id)
            .sortedByDescending { it.scheduledAt ?: it.createdAt }
            .take(Prompts.RECENT_POSTS)
            .map { it.text }
        return SlotBrief(
            pillar = post.pillar,
            language = account.language,
            recentTexts = recent,
            release = release,
            linkReplyAllowed = post.pillar in ContentRules.LINK_PILLARS,
        )
    }

    /** The release that `sourceRef` (`release:<version>`) names, or null when the ref is missing or the release is no longer active. */
    private suspend fun releaseOf(post: SocialPost): ReleaseNotes? {
        val ref = post.sourceRef ?: return null
        if (!ref.startsWith(RELEASE_REF)) return null
        val version = ref.removePrefix(RELEASE_REF)
        return releases.activeReleases().firstOrNull { it.version == version }
    }

    private suspend fun replace(old: SocialPost, draft: DraftOutcome.Draft) {
        val updated = store.transaction { tx ->
            val current = tx.post(old.id)
            if (current == null || current.status != PostStatus.Pending || current.text != old.text || current.regenerations != old.regenerations) {
                null
            } else {
                current.copy(
                    text = draft.text,
                    link = ContentRules.LINK_PLACEHOLDER in draft.text,
                    replyText = draft.replyText,
                    backTranslation = draft.backTranslation,
                    checkNotes = draft.checkNotes,
                    needsAttention = draft.needsAttention,
                    regenerations = current.regenerations + 1,
                    editedByOwner = false,
                    regeneratingSince = null,
                ).also(tx::putPost)
            }
        }
        if (updated == null) say(old, BotText.REGENERATE_POST_CHANGED) else sender.refresh(updated)
    }

    /** Clears this tap's marker; a no-op when the post was rewritten (which cleared it) or another tap owns it. */
    private suspend fun release(postId: String, marker: Instant) {
        withContext(NonCancellable) {
            store.transaction { tx ->
                val current = tx.post(postId)
                if (current != null && current.regeneratingSince == marker) tx.putPost(current.copy(regeneratingSince = null))
            }
        }
    }

    private suspend fun say(post: SocialPost, text: String) {
        telegram.sendMessage(ownerChatId, text, replyToMessageId = post.telegramMessageId)
    }

    private companion object {
        /** A marker older than this is a crashed run's leftover and no longer blocks a tap. */
        val MARKER_LIFETIME: Duration = Duration.ofMinutes(5)
        const val RELEASE_REF = "release:"
    }
}
