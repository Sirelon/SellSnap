package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.threads.DeferReason
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
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
    const val EXPIRED = "expired"

    fun header(handle: String, code: String, slot: String, needsAttention: Boolean, expired: Boolean = false): String =
        listOfNotNull("@$handle", code, slot, NEEDS_ATTENTION.takeIf { needsAttention }, EXPIRED.takeIf { expired }).joinToString(" | ")

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

    // Weekly batch
    fun batchHeader(handle: String, week: String, drafts: Int): String =
        "@$handle, week $week: $drafts ${if (drafts == 1) "draft" else "drafts"}"

    fun batchClosing(failedSlots: List<String>, aiCalls: Long): String =
        "Failed slots: ${failedSlots.map(::failedSlot).joinToString(", ").ifEmpty { "none" }}; AI calls today: $aiCalls."

    /** [slot] is `<day of week 1-7>-<slot 1 or 2>`, the form `failedSlots` stores. */
    private fun failedSlot(slot: String): String {
        val day = slot.substringBefore('-').toIntOrNull()?.takeIf { it in 1..7 } ?: return slot
        return "${DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} slot ${slot.substringAfter('-')}"
    }

    // Sunday digest. A plain-text report of up to a few dozen short lines, not a one-line reply.
    const val DIGEST_NO_USAGE = "Model use: no calls this week."
    const val DIGEST_NO_FOLLOWER_SNAPSHOT = "Follower change: unavailable, there is no follower snapshot yet."
    const val DIGEST_FOLLOWERS_UNAVAILABLE = "Follower change: unavailable, Threads returned no follower count today."

    fun digestHeader(handle: String, from: LocalDate, to: LocalDate): String =
        "@$handle, digest for ${day(from)} to ${day(to)}"

    fun digestFollowerChange(change: Long, since: LocalDate, now: Long): String =
        "Follower change: ${if (change > 0) "+" else ""}$change since ${day(since)} (now $now)."

    fun digestFollowerFirstSnapshot(first: LocalDate, now: Long): String =
        "Follower change: n/a (first snapshot on ${day(first)}). Followers now: $now."

    fun digestPosts(published: Int, views: Long, viewsMissing: Int, replies: Long): String =
        "Posts published: $published. Views: $views" +
            (if (viewsMissing > 0) " ($viewsMissing without a views figure)" else "") +
            ". Replies: $replies."

    fun digestRankingTitle(top: Boolean, count: Int, byViews: Boolean): String =
        "${if (top) "Top" else "Bottom"} $count by ${if (byViews) "views" else "likes + replies (views unavailable)"}:"

    fun digestPostLine(rank: Int, code: String, start: String, figures: String, landing: Int, play: Int, appStore: Int): String =
        "$rank. $code \"$start\" - $figures; clicks: landing $landing, play $play, appstore $appStore."

    fun digestFigures(byViews: Boolean, views: Long?, likes: Long, replies: Long): String =
        if (byViews) "${count(views ?: 0, "view", "views")}, ${count(replies, "reply", "replies")}"
        else "${count(likes, "like", "likes")}, ${count(replies, "reply", "replies")}"

    private fun count(n: Long, one: String, many: String): String = "$n ${if (n == 1L) one else many}"

    fun digestClicks(landing: Int, play: Int, appStore: Int): String =
        "Clicks on all posts: landing $landing, play $play, appstore $appStore."

    fun digestInstalls(total: Long, byCode: List<Pair<String, Long>>): String =
        "Android installs from Threads: $total" + if (byCode.isEmpty()) "." else " (${byCode.joinToString(", ") { "${it.first}: ${it.second}" }})."

    fun digestEvents(errors: Int, pauses: Int, resumes: Int, modeChanges: Int, deletes: Int): String =
        "Events: errors $errors, pauses $pauses, resumes $resumes, mode changes $modeChanges, deletes $deletes."

    fun digestModel(model: String, calls: Long, tokens: Long, peakCalls: Long, dailyLimit: Int): String =
        "$model: $calls calls, $tokens tokens, busiest day $peakCalls of $dailyLimit calls"

    fun digestUsage(models: List<String>): String = "Model use, last 7 UTC days, all accounts: ${models.joinToString("; ")}."

    fun batchDrafting(accountId: String): String = "A batch is drafting for $accountId, try again in a few minutes."

    // Regenerate
    const val REGENERATING = "Writing a new version…"
    const val REGENERATE_RUNNING = "A new version is already being written."
    const val NO_MORE_VERSIONS = "No more new versions for this post."
    const val CANNOT_REGENERATE = "This post cannot be regenerated."
    const val USAGE_LIMIT_REACHED = "Daily AI limit reached, try tomorrow."
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

    fun alertBatchStalled(accountId: String, week: String): String =
        "The batch for $accountId, week $week, did not finish, so run the sellsnap-batch job again to continue it."

    fun alertBatchDeferred(accountId: String, week: String, reason: String): String =
        "Drafting for $accountId, week $week, stopped: $reason, and the next batch run continues where it left off."

    fun alertInsightsFailed(accountId: String, detail: String): String =
        "Reading Threads insights for $accountId failed: $detail. The account keeps publishing, and the next tick tries again."

    fun alertInstallSyncFailed(detail: String): String =
        "Reading Android install attribution from BigQuery failed: $detail. The digest shows the installs synced so far, and the next batch run tries again."

    // Formatting
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneOffset.UTC)
    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    fun day(date: LocalDate): String = DAY.format(date)
    private val SLOT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH)

    fun date(at: Instant): String = DATE.format(at)

    fun slot(at: Instant, zone: ZoneId): String = SLOT.withZone(zone).format(at)
}
