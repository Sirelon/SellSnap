package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.threads.ThreadsApi
import com.sirelon.sellsnap.threads.ThreadsFailure
import com.sirelon.sellsnap.threads.redact
import kotlinx.coroutines.CancellationException
import java.time.Clock

/**
 * The owner's buttons and edit replies. Each transition is a conditional transaction from the
 * status the button expects, so a double tap or a stale button changes nothing and gets an
 * "already ..." answer. Telegram and Threads calls happen outside the transactions.
 */
class ApprovalActions(
    private val store: SocialStore,
    private val telegram: TelegramApi,
    private val threads: ThreadsApi,
    private val previews: Previews,
    private val sender: PreviewSender,
    private val clock: Clock,
    private val ownerChatId: Long,
) {
    /** Handles a button tap. Always answers the callback query. */
    suspend fun onCallback(query: CallbackQuery) {
        val data = query.data.orEmpty()
        val action = data.substringBefore(':', "")
        val postId = data.substringAfter(':', "")
        val answer = when {
            postId.isEmpty() -> BotText.UNKNOWN_BUTTON
            else -> when (action) {
                CallbackAction.APPROVE -> approve(postId, query)
                CallbackAction.EDIT -> askForEdit(postId)
                CallbackAction.SKIP -> skip(postId, query)
                CallbackAction.DELETE -> delete(postId, query)
                else -> BotText.UNKNOWN_BUTTON
            }
        }
        telegram.answerCallbackQuery(query.id, answer)
    }

    /**
     * Handles a plain-text [message] that replies to something. Returns true when it replied to an
     * edit prompt, whether or not the new text was accepted; false means it is not an edit reply.
     */
    suspend fun onReply(message: Message): Boolean {
        val promptId = message.replyToMessage?.messageId ?: return false
        val text = message.text ?: return false
        val post = store.postsWithStatus(PostStatus.Pending).firstOrNull { it.editPromptMessageId == promptId }
            ?: return false
        val account = store.account(post.accountId) ?: return false

        val violations = ContentRules.check(
            ContentRules.Candidate(
                text = text,
                kind = post.kind,
                pillar = post.pillar,
                source = post.source,
                pollOptions = post.pollOptions,
                language = account.language,
                publishedCount = account.publishedCount,
                linkUrl = previews.linkUrl(post.code),
            ),
        )
        if (violations.isNotEmpty()) {
            telegram.sendMessage(
                message.chat.id,
                BotText.editRejected(post.code, BotText.violations(violations)),
                replyToMessageId = message.messageId,
            )
            return true
        }

        val saved = store.transaction { tx ->
            val current = tx.post(post.id)
            val owner = tx.account(post.accountId)
            if (current == null || owner == null || current.status != PostStatus.Pending || current.editPromptMessageId != promptId) {
                null
            } else {
                val edited = current.copy(
                    text = text,
                    link = ContentRules.LINK_PLACEHOLDER in text,
                    status = PostStatus.Approved,
                    editedByOwner = true,
                    editPromptMessageId = null,
                )
                tx.putPost(edited)
                tx.putAccount(owner.copy(approvalStreak = 0))
                edited
            }
        }
        if (saved == null) {
            telegram.sendMessage(message.chat.id, BotText.alreadyStatus(currentStatus(post.id)), replyToMessageId = message.messageId)
            return true
        }
        sender.refresh(saved)
        telegram.sendMessage(message.chat.id, BotText.editSaved(saved.code), replyToMessageId = message.messageId)
        return true
    }

    /**
     * Puts [accountId] in approval mode with a zero streak and writes a mode-change event, then
     * sends every upcoming auto-approved post back to the owner. Returns how many went back.
     */
    suspend fun returnToApproval(accountId: String): Int {
        val now = clock.instant()
        store.transaction { tx ->
            val account = tx.account(accountId)
            if (account != null) {
                tx.putAccount(account.copy(mode = AccountMode.Approval, approvalStreak = 0))
                tx.putEvent(modeChange(accountId, now.toEpochMilli(), "Returned to approval mode"))
            }
        }
        return revertAutoApproved(accountId)
    }

    /**
     * Moves every upcoming post that auto mode approved back to `pending` and re-sends its preview
     * with Approve, Edit and Skip. The old preview loses its buttons. Returns the number moved.
     */
    suspend fun revertAutoApproved(accountId: String): Int {
        val now = clock.instant()
        val candidates = store.postsOf(accountId).filter {
            it.status == PostStatus.Approved && it.autoApproved && it.scheduledAt?.isAfter(now) == true
        }
        var reverted = 0
        for (candidate in candidates) {
            val moved = store.transaction { tx ->
                val current = tx.post(candidate.id)
                if (current == null || current.status != PostStatus.Approved || !current.autoApproved) {
                    null
                } else {
                    // The stored message id is cleared so a failed re-send is found again later.
                    current.copy(status = PostStatus.Pending, autoApproved = false, telegramMessageId = null)
                        .also(tx::putPost)
                }
            } ?: continue
            candidate.telegramMessageId?.let { bestEffort { telegram.editMessageReplyMarkup(ownerChatId, it, null) } }
            sender.send(moved)
            reverted++
        }
        return reverted
    }

    private sealed interface Move {
        data object Missing : Move

        class Wrong(val status: PostStatus) : Move

        class Done(val post: SocialPost, val account: SocialAccount, val graduated: Boolean) : Move
    }

    private suspend fun approve(postId: String, query: CallbackQuery): String {
        val move = store.transaction { tx ->
            val post = tx.post(postId)
            val account = post?.let { tx.account(it.accountId) }
            when {
                post == null || account == null -> Move.Missing
                post.status != PostStatus.Pending -> Move.Wrong(post.status)
                else -> {
                    val counts = account.mode == AccountMode.Approval && post.source != PostSource.Owner && !post.editedByOwner
                    val streak = if (counts) account.approvalStreak + 1 else account.approvalStreak
                    val graduated = counts && streak >= GRADUATION_STREAK
                    val updated = if (graduated) {
                        account.copy(approvalStreak = streak, mode = AccountMode.Auto)
                    } else {
                        account.copy(approvalStreak = streak)
                    }
                    val approved = post.copy(status = PostStatus.Approved, editPromptMessageId = null)
                    tx.putPost(approved)
                    if (updated != account) tx.putAccount(updated)
                    if (graduated) {
                        val now = clock.instant()
                        tx.putEvent(modeChange(account.id, now.toEpochMilli(), "Graduated to auto mode after $streak approvals"))
                    }
                    Move.Done(approved, updated, graduated)
                }
            }
        }
        return when (move) {
            Move.Missing -> BotText.POST_NOT_FOUND
            is Move.Wrong -> BotText.alreadyStatus(move.status)
            is Move.Done -> {
                updateKeyboard(query, move.post, move.account)
                if (move.graduated) {
                    telegram.sendMessage(ownerChatId, BotText.graduated(move.account.id, move.account.handle, move.account.approvalStreak))
                }
                BotText.APPROVED
            }
        }
    }

    private suspend fun askForEdit(postId: String): String {
        val post = store.post(postId) ?: return BotText.POST_NOT_FOUND
        if (post.status != PostStatus.Pending) return BotText.alreadyStatus(post.status)
        val prompt = telegram.sendMessage(
            ownerChatId,
            BotText.editPrompt(post.code),
            replyMarkup = ForceReply(true),
            replyToMessageId = post.telegramMessageId,
        )
        val stored = store.transaction { tx ->
            val current = tx.post(postId)
            if (current == null || current.status != PostStatus.Pending) {
                false
            } else {
                tx.putPost(current.copy(editPromptMessageId = prompt.messageId))
                true
            }
        }
        if (!stored) {
            bestEffort { telegram.deleteMessage(ownerChatId, prompt.messageId) }
            return BotText.alreadyStatus(currentStatus(postId))
        }
        return BotText.EDIT_PROMPT_SENT
    }

    private suspend fun skip(postId: String, query: CallbackQuery): String {
        val move = store.transaction { tx ->
            val post = tx.post(postId)
            val account = post?.let { tx.account(it.accountId) }
            when {
                post == null || account == null -> Move.Missing
                post.status != PostStatus.Pending -> Move.Wrong(post.status)
                else -> {
                    val skipped = post.copy(status = PostStatus.Skipped, skipReason = SKIP_REASON, editPromptMessageId = null)
                    tx.putPost(skipped)
                    Move.Done(skipped, account, graduated = false)
                }
            }
        }
        return when (move) {
            Move.Missing -> BotText.POST_NOT_FOUND
            is Move.Wrong -> BotText.alreadyStatus(move.status)
            is Move.Done -> {
                updateKeyboard(query, move.post, move.account)
                BotText.SKIPPED
            }
        }
    }

    private suspend fun delete(postId: String, query: CallbackQuery): String {
        val post = store.post(postId) ?: return BotText.POST_NOT_FOUND
        val account = store.account(post.accountId) ?: return BotText.POST_NOT_FOUND
        val wasPublished = when (post.status) {
            PostStatus.Published -> true
            PostStatus.Approved -> false
            else -> return BotText.notDeletable(post.status)
        }
        if (wasPublished) {
            val mediaId = post.mediaId ?: return BotText.NO_MEDIA_ID
            val token = account.token ?: return BotText.NO_TOKEN
            try {
                threads.delete(mediaId, token)
            } catch (failure: ThreadsFailure) {
                return BotText.threadsDeleteFailed(redact(failure.message.orEmpty()))
            }
        }

        val from = if (wasPublished) PostStatus.Published else PostStatus.Approved
        val move = store.transaction { tx ->
            val current = tx.post(postId)
            val owner = tx.account(post.accountId)
            when {
                current == null || owner == null -> Move.Missing
                current.status != from -> Move.Wrong(current.status)
                else -> {
                    val now = clock.instant()
                    val changed = if (wasPublished) {
                        current.copy(status = PostStatus.Deleted)
                    } else {
                        current.copy(status = PostStatus.Skipped, skipReason = DELETED_BEFORE_SLOT)
                    }
                    val reset = owner.copy(mode = AccountMode.Approval, approvalStreak = 0)
                    tx.putPost(changed)
                    tx.putAccount(reset)
                    tx.putEvent(
                        SocialEvent(
                            id = "delete_${postId}_${now.toEpochMilli()}",
                            accountId = owner.id,
                            type = EventType.Delete,
                            at = now,
                            postId = postId,
                            detail = if (wasPublished) "Deleted after publishing" else "Deleted before its slot",
                        ),
                    )
                    Move.Done(changed, reset, graduated = false)
                }
            }
        }
        return when (move) {
            Move.Missing -> BotText.POST_NOT_FOUND
            is Move.Wrong -> BotText.alreadyStatus(move.status)
            is Move.Done -> {
                updateKeyboard(query, move.post, move.account)
                val reverted = revertAutoApproved(move.account.id)
                telegram.sendMessage(ownerChatId, BotText.deletedNotice(move.account.handle, reverted))
                if (wasPublished) BotText.DELETED else BotText.DELETED_BEFORE_SLOT
            }
        }
    }

    private suspend fun currentStatus(postId: String): PostStatus =
        store.post(postId)?.status ?: PostStatus.Deleted

    /** Swaps the tapped message's buttons for those of the post's new status. */
    private suspend fun updateKeyboard(query: CallbackQuery, post: SocialPost, account: SocialAccount) {
        val messageId = query.message?.messageId ?: post.telegramMessageId ?: return
        val chatId = query.message?.chat?.id ?: ownerChatId
        bestEffort { telegram.editMessageReplyMarkup(chatId, messageId, previews.keyboard(post, account)) }
    }

    private suspend fun bestEffort(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: TelegramFailure) {
            // A stale or missing message is not worth failing the owner's tap.
        }
    }

    private fun modeChange(accountId: String, epochMillis: Long, detail: String) =
        SocialEvent("mode_${accountId}_$epochMillis", accountId, EventType.ModeChange, clock.instant(), detail = detail)

    companion object {
        /** Consecutive approvals without an edit after which an account publishes on its own (D1). */
        const val GRADUATION_STREAK = 20
        const val SKIP_REASON = "skipped_by_owner"
        const val DELETED_BEFORE_SLOT = "deleted_before_slot"
    }
}
