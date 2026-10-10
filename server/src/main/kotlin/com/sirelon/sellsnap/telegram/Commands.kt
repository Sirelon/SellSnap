package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.content.PostCode
import com.sirelon.sellsnap.content.occupiedSlots
import com.sirelon.sellsnap.content.rollOverdue
import com.sirelon.sellsnap.content.SlotPlanner
import com.sirelon.sellsnap.media.ImageStore
import com.sirelon.sellsnap.model.EventType
import com.sirelon.sellsnap.model.Pillar
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostSource
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialEvent
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import com.sirelon.sellsnap.threads.IntakeResult
import com.sirelon.sellsnap.threads.PublishOutcome
import com.sirelon.sellsnap.threads.Publisher
import com.sirelon.sellsnap.threads.RefreshResult
import com.sirelon.sellsnap.threads.ReplyState
import com.sirelon.sellsnap.threads.TokenLifecycle
import com.sirelon.sellsnap.threads.redact
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Instant

/** A `/command` split from its arguments: [name] is lower case without `/` or `@bot`. */
class ParsedCommand(val name: String, val args: String)

fun parseCommand(text: String): ParsedCommand? {
    val match = COMMAND.matchEntire(text.trim()) ?: return null
    return ParsedCommand(match.groupValues[1].lowercase(), match.groupValues[2].trim())
}

private val COMMAND = Regex("^/([A-Za-z]+)(?:@\\w+)?(?:\\s+(.*))?$", RegexOption.DOT_MATCHES_ALL)

/**
 * The arguments of `/post`: `<account> [now] <text>`, with `? ` lines as poll options. A line that is
 * only `---` ends the text; everything after the first such line is the reply ([replyText], blank
 * when nothing follows the line, null when there is no such line).
 */
class PostRequest(
    val accountId: String,
    val now: Boolean,
    val text: String,
    val pollOptions: List<String>,
    val replyText: String? = null,
)

fun parsePost(args: String): PostRequest? {
    val parts = args.trim().split(Regex("\\s+"), limit = 2)
    val accountId = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
    var rest = parts.getOrElse(1) { "" }
    val now = NOW_WORD.find(rest)?.also { rest = rest.substring(it.range.last + 1) } != null
    val options = mutableListOf<String>()
    val textLines = mutableListOf<String>()
    var replyLines: MutableList<String>? = null
    for (line in rest.trim().lines()) {
        when {
            replyLines != null -> replyLines.add(line)
            line.trim() == REPLY_SEPARATOR -> replyLines = mutableListOf()
            line.startsWith("? ") -> options += line.removePrefix("? ").trim()
            else -> textLines += line
        }
    }
    return PostRequest(accountId, now, textLines.joinToString("\n").trim(), options, replyLines?.joinToString("\n")?.trim())
}

private const val REPLY_SEPARATOR = "---"

private val NOW_WORD = Regex("^now(?=\\s|$)")

/** The owner's slash commands. Every reply is a [BotText] string; none ever echoes a token. */
class Commands(
    private val store: SocialStore,
    private val telegram: TelegramApi,
    private val images: ImageStore,
    private val publisher: Publisher,
    private val tokens: TokenLifecycle,
    private val previews: Previews,
    private val sender: PreviewSender,
    private val approvals: ApprovalActions,
    private val clock: Clock,
    private val ownerChatId: Long,
) {
    suspend fun handle(message: Message, text: String) {
        val command = parseCommand(text) ?: return reply(BotText.USAGE)
        when (command.name) {
            "post" -> post(message, command.args)
            "token" -> token(message, command.args)
            "approval" -> withAccount(command.args) { account ->
                val reverted = approvals.returnToApproval(account.id)
                reply(BotText.approvalDone(account.handle, reverted))
            }
            "resume" -> withAccount(command.args) { resume(it) }
            "refresh" -> withAccount(command.args) { refresh(it) }
            else -> reply(BotText.USAGE)
        }
    }

    private suspend fun reply(text: String) {
        telegram.sendMessage(ownerChatId, text)
    }

    private suspend fun withAccount(args: String, block: suspend (SocialAccount) -> Unit) {
        val id = args.trim().substringBefore(' ').takeIf { it.isNotEmpty() } ?: return reply(BotText.USAGE)
        val account = store.account(id) ?: return reply(BotText.unknownAccount(id))
        block(account)
    }

    // /post

    private suspend fun post(message: Message, args: String) {
        val request = parsePost(args) ?: return reply(BotText.POST_USAGE)
        val account = store.account(request.accountId) ?: return reply(BotText.unknownAccount(request.accountId))
        if (request.now && account.paused) return reply(BotText.pausedBeforeNow(account.handle, account.id))
        if (request.now && account.token == null) return reply(BotText.NO_TOKEN)
        val photo = message.image()
        if (photo == null && message.document != null) return reply(BotText.UNSUPPORTED_FILE)
        if (photo != null && request.pollOptions.isNotEmpty()) return reply(BotText.POLL_WITH_PHOTO)
        if (request.text.isEmpty() && photo == null) return reply(BotText.POST_USAGE)

        val kind = when {
            request.pollOptions.isNotEmpty() -> PostKind.Poll
            photo != null -> PostKind.Image
            else -> PostKind.Text
        }
        val pillar = when {
            kind == PostKind.Poll -> Pillar.Poll
            ContentRules.LINK_PLACEHOLDER in request.text -> Pillar.Update
            else -> Pillar.Tip
        }
        val seq = account.postSeq + 1
        val code = PostCode.format(account.codePrefix, pillar, seq)

        val violations = ContentRules.check(
            ContentRules.Candidate(
                text = request.text,
                kind = kind,
                pillar = pillar,
                source = PostSource.Owner,
                pollOptions = request.pollOptions,
                language = account.language,
                publishedCount = account.publishedCount,
                linkUrl = previews.linkUrl(code),
                replyText = request.replyText,
            ),
        )
        if (violations.isNotEmpty()) return reply(BotText.postRejected(BotText.violations(violations)))

        val now = clock.instant()
        val scheduledAt = if (request.now) now else nextSlot(account, kind, pillar, now)
        if (scheduledAt == null) return reply(BotText.NO_FREE_SLOT)

        val imagePaths = if (photo != null) {
            val filePath = telegram.getFile(photo.fileId).filePath ?: return reply(BotText.NO_PHOTO_FILE)
            listOf(images.saveManualPhoto(code, telegram.downloadFile(filePath), photo.contentType))
        } else {
            emptyList()
        }

        val created = store.transaction { tx ->
            val current = tx.account(account.id)
            if (current == null || current.postSeq != account.postSeq) {
                null
            } else {
                val post = SocialPost(
                    id = code,
                    accountId = account.id,
                    code = code,
                    pillar = pillar,
                    kind = kind,
                    text = request.text,
                    replyText = request.replyText,
                    imagePaths = imagePaths,
                    pollOptions = request.pollOptions,
                    link = ContentRules.LINK_PLACEHOLDER in request.text,
                    source = PostSource.Owner,
                    scheduledAt = scheduledAt,
                    status = if (request.now) PostStatus.Approved else PostStatus.Pending,
                    createdAt = now,
                )
                tx.putAccount(current.copy(postSeq = seq))
                tx.putPost(post)
                post
            }
        } ?: return reply(BotText.POST_RACE)

        if (request.now) publishNow(created) else sender.send(created)
    }

    /** First free slot for the account, counting every post that holds a slot or a day (D13). */
    private suspend fun nextSlot(account: SocialAccount, kind: PostKind, pillar: Pillar, now: Instant): Instant? =
        SlotPlanner.nextFreeSlot(
            now = now,
            zone = account.zone,
            publishedCount = account.publishedCount,
            slot2Only = SlotPlanner.requiresSlot2(kind, pillar),
            taken = occupiedSlots(store.postsOf(account.id)),
        )

    private suspend fun publishNow(post: SocialPost) {
        when (val outcome = publisher.publishNow(post.id)) {
            is PublishOutcome.Published -> {
                val published = store.post(post.id) ?: post
                val text = when {
                    outcome.needsAttention -> BotText.publishedNowNeedsAttention(post.code)
                    outcome.reply == ReplyState.Published -> BotText.publishedNowWithReply(post.code)
                    outcome.reply == ReplyState.Deferred -> BotText.publishedNowReplyPending(post.code)
                    outcome.reply == ReplyState.Failed -> BotText.publishedNowReplyFailed(post.code)
                    else -> BotText.publishedNow(post.code)
                }
                sender.sendWithKeyboard(published, text)
            }
            is PublishOutcome.Deferred -> reply(BotText.notPublishedNow(post.code, outcome.reason))
            is PublishOutcome.Paused -> reply(BotText.failedNow(post.code, redact(outcome.reason)))
            is PublishOutcome.ClaimLost -> reply(BotText.claimLost(post.code))
            // Only the tick's reply retries return this; publishNow never does.
            is PublishOutcome.Replied -> Unit
        }
    }

    // /token

    private suspend fun token(message: Message, args: String) {
        // The token is in the owner's message, so that message goes first, before anything can fail.
        val deleted = try {
            telegram.deleteMessage(message.chat.id, message.messageId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: TelegramFailure) {
            false
        }
        if (!deleted) reply(BotText.TOKEN_MESSAGE_NOT_DELETED)

        val parts = args.trim().split(Regex("\\s+"))
        if (parts.size != 2) return reply(BotText.TOKEN_USAGE)
        val result = try {
            tokens.intake(parts[0], parts[1])
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return reply(BotText.TOKEN_CHECK_FAILED)
        }
        when (result) {
            is IntakeResult.Accepted -> reply(
                BotText.tokenSaved(result.accountId, result.tokenExpiresAt, result.permissionsExpireAt, result.missingOptionalScopes),
            )
            is IntakeResult.Rejected -> reply(BotText.tokenRejected(redact(result.summary)))
        }
    }

    // /resume

    private suspend fun resume(account: SocialAccount) {
        val now = clock.instant()
        store.transaction { tx ->
            val current = tx.account(account.id)
            if (current != null) {
                tx.putAccount(current.copy(paused = false, pausedReason = null))
                tx.putEvent(
                    SocialEvent("resume_${account.id}_${now.toEpochMilli()}", account.id, EventType.Resume, now, detail = "Resumed by the owner"),
                )
            }
        }
        val moved = rollOverdue(account, now)
        reply(BotText.resumed(account.handle, moved))
    }

    /** Gives every overdue approved or pending post of [account] a new slot; returns how many moved. */
    private suspend fun rollOverdue(account: SocialAccount, now: Instant): Int {
        val moved = store.rollOverdue(account.id, now)
        moved.filter { it.status == PostStatus.Pending }.forEach { sender.refresh(it) }
        return moved.size
    }

    // /refresh

    private suspend fun refresh(account: SocialAccount) {
        when (val result = tokens.forceRefresh(account.id)) {
            is RefreshResult.Refreshed -> reply(BotText.refreshed(account.id, result.tokenExpiresAt))
            is RefreshResult.Failed -> reply(BotText.refreshFailed(redact(result.summary)))
        }
    }
}

/** An image the owner attached: the largest photo size, or a JPEG or PNG sent as a file. */
private class ImageFile(val fileId: String, val contentType: String)

private val IMAGE_TYPES = setOf("image/jpeg", "image/png")

private fun Message.image(): ImageFile? =
    photo.maxByOrNull { it.width * it.height }?.let { ImageFile(it.fileId, "image/jpeg") }
        ?: document?.takeIf { it.mimeType in IMAGE_TYPES }?.let { ImageFile(it.fileId, it.mimeType!!) }
