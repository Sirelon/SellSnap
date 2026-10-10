package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.threads.DeferReason
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Every word the bot says to the owner. The bot speaks English only (D14); the owner is its only
 * reader. Keep each reply to one or two short sentences. No secret or token may enter any of these.
 */
object BotText {
    // Preview header and body
    const val NEEDS_ATTENTION = "needs attention"
    const val NO_SLOT = "no slot yet"
    const val POLL_OPTIONS_TITLE = "Poll options:"
    const val REPLY_TITLE = "↳ reply:"
    const val BACK_TRANSLATION_PREFIX = "EN: "
    const val CHECK_NOTES_PREFIX = "Check: "

    fun header(handle: String, code: String, slot: String, needsAttention: Boolean): String =
        listOfNotNull("@$handle", code, slot, NEEDS_ATTENTION.takeIf { needsAttention }).joinToString(" | ")

    // Buttons
    const val APPROVE = "Approve"
    const val EDIT = "Edit"
    const val SKIP = "Skip"
    const val REGENERATE = "Regenerate"
    const val DELETE = "Delete"
    const val DELETE_BEFORE_SLOT = "Delete before slot"
    const val CANCEL = "Cancel"

    // Answers to a button tap
    const val APPROVED = "Approved."
    const val SKIPPED = "Skipped."
    const val CANCELLED = "Cancelled. It will not publish."
    const val EDIT_PROMPT_SENT = "Reply to the prompt below."
    const val DELETED = "Deleted from Threads."
    const val DELETED_BEFORE_SLOT = "Deleted before its slot."
    const val POST_NOT_FOUND = "That post no longer exists."
    const val UNKNOWN_BUTTON = "I do not know that button."
    const val NO_MEDIA_ID = "No Threads id is recorded for this post. Delete it in the Threads app."
    const val NO_TOKEN = "This account has no token."

    fun alreadyStatus(status: PostStatus): String = "Already ${status.name.lowercase()}."

    fun notDeletable(status: PostStatus): String = "A ${status.name.lowercase()} post cannot be deleted."

    fun threadsDeleteFailed(reason: String): String = "Threads did not delete it: $reason."

    // Regenerate
    const val REGENERATING = "Writing a new version…"
    const val REGENERATE_RUNNING = "A new version is already being written."
    const val NO_MORE_VERSIONS = "No more new versions for this post"
    const val CANNOT_REGENERATE = "This post cannot be regenerated."
    const val USAGE_LIMIT_REACHED = "Daily AI limit reached, try tomorrow"
    const val REGENERATE_BUSY = "The AI is busy right now, so the post is unchanged."
    const val REGENERATE_FAILED = "The AI did not return a usable version, so the post is unchanged."
    const val REGENERATE_NO_RELEASE = "The release for this post is no longer listed, so I cannot write a new version."
    const val REGENERATE_POST_CHANGED = "The post changed while I was writing, so I dropped the new version."

    // Edit flow
    fun editPrompt(code: String, hasReply: Boolean = false): String =
        "Reply to this message with the new text for $code." + if (hasReply) " Its reply stays as it is." else ""

    fun editSaved(code: String): String = "Saved and approved $code."

    fun editRejected(code: String, violations: String): String =
        "$code was not changed: $violations. Reply to the prompt again to try another text."

    const val PLAIN_TEXT_HINT = "To change a post, tap Edit under it and reply to my prompt. To write a new post, send /post."

    // Mode changes
    fun graduated(accountId: String, handle: String, streak: Int): String =
        "@$handle has $streak approved posts in a row, so it now posts on its own. Send /approval $accountId to undo."

    fun deletedNotice(handle: String, reverted: Int): String =
        "@$handle is back in approval mode." + revertedSuffix(reverted)

    fun approvalDone(handle: String, reverted: Int): String =
        "@$handle is in approval mode." + revertedSuffix(reverted)

    private fun revertedSuffix(reverted: Int): String =
        if (reverted > 0) " $reverted upcoming ${if (reverted == 1) "post needs" else "posts need"} your approval again." else ""

    // Commands
    const val USAGE =
        "Commands: /post <account> [now] <text>, /token <account> <token>, /approval <account>, /resume <account>, /refresh <account>."
    const val POST_USAGE =
        "Usage: /post <account> [now] <text>. Start a line with \"? \" to add a poll option. A line with only --- ends the text; what follows becomes a reply under the post."
    const val TOKEN_USAGE = "Usage: /token <account> <token>."
    const val POLL_WITH_PHOTO = "A poll cannot have a photo."
    const val NO_PHOTO_FILE = "I could not get that photo from Telegram."
    const val UNSUPPORTED_FILE = "Send the image as a JPEG or PNG."
    const val PHOTO_NEEDS_CAPTION = "Send the photo with /post <account> [now] <text> as its caption, in one message."
    const val NO_FREE_SLOT = "There is no free slot in the next 14 days."
    const val POST_RACE = "Another post was created at the same moment. Send it again."
    const val TOKEN_MESSAGE_NOT_DELETED = "I could not delete your message. Please delete it yourself."
    const val TOKEN_CHECK_FAILED = "I could not check that token."

    fun unknownAccount(id: String): String = "There is no account named $id."

    fun postRejected(violations: String): String = "The post was not created: $violations."

    fun publishedNow(code: String): String = "Published $code."

    fun publishedNowWithReply(code: String): String = "Published $code and its reply."

    fun publishedNowReplyPending(code: String): String = "Published $code. Its reply will follow on the next tick."

    fun publishedNowReplyFailed(code: String): String = "Published $code, but its reply failed. Check it in the Threads app."

    fun publishedNowNeedsAttention(code: String): String =
        "Published $code, but its Threads id was not recorded. Check it in the Threads app."

    fun pausedBeforeNow(handle: String, accountId: String): String = "@$handle is paused. Send /resume $accountId first."

    fun notPublishedNow(code: String, reason: DeferReason): String = "$code was not published now: ${deferText(reason)}."

    fun failedNow(code: String, reason: String): String = "$code failed and the account is paused: $reason."

    fun claimLost(code: String): String = "Another publisher took over $code. Check its status."

    fun tokenSaved(accountId: String, expiresAt: Instant, permissionsExpireAt: Instant?, missingScopes: Set<String>): String =
        buildString {
            append("Token saved for $accountId. It expires ${date(expiresAt)}.")
            if (permissionsExpireAt != null) append(" Private-profile access ends ${date(permissionsExpireAt)}.")
            if (missingScopes.isNotEmpty()) append(" Missing optional scopes: ${missingScopes.sorted().joinToString(", ")}.")
        }

    fun tokenRejected(summary: String): String = "The token was not saved: $summary."

    fun resumed(handle: String, moved: Int): String =
        "@$handle is resumed." + if (moved > 0) " $moved overdue ${if (moved == 1) "post moved" else "posts moved"} to new slots." else ""

    fun refreshed(accountId: String, expiresAt: Instant): String =
        "Token for $accountId refreshed. It expires ${date(expiresAt)}."

    fun refreshFailed(summary: String): String = "The refresh failed: $summary."

    fun error(detail: String): String = "Something went wrong: $detail"

    // Content rule violations
    fun violation(violation: ContentRules.Violation): String = when (violation) {
        ContentRules.Violation.Empty -> "the text is empty"
        ContentRules.Violation.TooLong -> "the text is too long (limit ${ContentRules.MAX_LENGTH})"
        ContentRules.Violation.PollOptionCount ->
            "a poll needs ${ContentRules.POLL_MIN_OPTIONS} to ${ContentRules.POLL_MAX_OPTIONS} options"
        ContentRules.Violation.PollOptionLength ->
            "a poll option must have 1 to ${ContentRules.POLL_OPTION_MAX_LENGTH} characters"
        ContentRules.Violation.RussianLetter -> "it has a letter used only in russian"
        ContentRules.Violation.LinkNotAllowed -> "a link is not allowed in this post"
        ContentRules.Violation.LinkTooEarly -> "no link until ${ContentRules.MIN_PUBLISHED_BEFORE_LINK} posts are published"
        ContentRules.Violation.RawUrl -> "write {link} instead of a raw link"
        ContentRules.Violation.ReplyEmpty -> "the reply is empty"
        ContentRules.Violation.ReplyTooLong -> "the reply is too long (limit ${ContentRules.MAX_LENGTH})"
        ContentRules.Violation.ReplyRussianLetter -> "the reply has a letter used only in russian"
        ContentRules.Violation.ReplyLinkNotAllowed -> "a link is not allowed in this reply"
        ContentRules.Violation.ReplyRawUrl -> "write {link} in the reply instead of a raw link"
    }

    fun violations(violations: Collection<ContentRules.Violation>): String =
        violations.joinToString("; ") { violation(it) }

    private fun deferText(reason: DeferReason): String = when (reason) {
        DeferReason.AccountPaused -> "the account is paused"
        DeferReason.NoToken -> "the account has no token"
        DeferReason.LimitExhausted -> "the daily Threads limit is used up"
        DeferReason.NotPublishable -> "the post cannot be published now"
        DeferReason.ClaimedElsewhere -> "another publish is running"
        DeferReason.ContainerInProgress -> "Threads is still processing it, and the next tick will finish it"
        DeferReason.RateLimited -> "Threads is rate limiting, and the next tick will retry"
        DeferReason.TransientFailure -> "Threads had a temporary error, and the next tick will retry"
    }

    // Owner alerts
    const val NO_DETAIL = "no detail"

    fun alertPublishFailed(accountId: String, postCode: String?, detail: String): String =
        "Publishing ${postCode ?: "a post"} failed and $accountId is paused: $detail. Fix the cause, then send /resume $accountId."

    fun alertPublishStalled(accountId: String, postCode: String?, detail: String): String =
        "${postCode ?: "A post"} on $accountId has failed on 3 ticks in a row: $detail."

    fun alertPostNeedsAttention(accountId: String, postCode: String?): String =
        "${postCode ?: "A post"} is live on $accountId, but its Threads id was not recorded. Check it in the Threads app."

    fun alertReplyFailed(accountId: String, postCode: String?, detail: String): String =
        "The reply under ${postCode ?: "a post"} on $accountId failed: $detail. The post is live and $accountId keeps publishing. Check the reply in the Threads app."

    fun alertTokenExpiry(accountId: String, expiresAt: Instant?): String =
        "The Threads token for $accountId expires ${expiresAt?.let(::date) ?: "soon"}. Send /refresh $accountId, or /token $accountId <token> with a new one."

    fun alertPermissionsExpiry(accountId: String, expiresAt: Instant?): String =
        "Private-profile access for $accountId ends ${expiresAt?.let(::date) ?: "soon"}. Authorize the account again, then send /token $accountId <token>."

    fun alertTokenRefreshFailed(accountId: String, detail: String): String =
        "The token refresh for $accountId failed: $detail. Send /refresh $accountId to try again."

    fun alertTickFailed(accountId: String, detail: String): String =
        "The tick failed for $accountId: $detail. The other accounts ran."

    fun alertInsightsFailed(accountId: String, detail: String): String =
        "Reading Threads insights for $accountId failed: $detail. The account keeps publishing, and the next tick tries again."

    // Formatting
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC)
    private val SLOT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH)

    fun date(at: Instant): String = DATE.format(at)

    fun slot(at: Instant, zone: ZoneId): String = SLOT.withZone(zone).format(at)
}
