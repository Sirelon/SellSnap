package com.sirelon.sellsnap.telegram

import com.sirelon.sellsnap.content.ContentRules
import com.sirelon.sellsnap.media.ImageStore
import com.sirelon.sellsnap.model.AccountMode
import com.sirelon.sellsnap.model.PostKind
import com.sirelon.sellsnap.model.PostStatus
import com.sirelon.sellsnap.model.SocialAccount
import com.sirelon.sellsnap.model.SocialPost
import com.sirelon.sellsnap.store.SocialStore
import kotlinx.coroutines.CancellationException

/** Callback prefixes; `callback_data` is `<prefix>:<postId>` and stays within Telegram's 64 bytes. */
object CallbackAction {
    const val APPROVE = "a"
    const val EDIT = "e"
    const val SKIP = "s"
    const val DELETE = "d"
    const val REGENERATE = "r"

    fun data(action: String, postId: String): String = "$action:$postId"
}

/** What goes to Telegram for one post: a text message, or a photo with [text] as its caption. */
class Preview(val text: String, val photoUrl: String?, val keyboard: InlineKeyboardMarkup?)

/**
 * Builds the owner's preview of a post. [regenerateAvailable] is the one switch for the
 * Regenerate button; the drafter that would serve it does not exist yet.
 */
class Previews(
    private val images: ImageStore,
    publicBaseUrl: String,
    private val regenerateAvailable: Boolean = false,
) {
    private val baseUrl = publicBaseUrl.trimEnd('/')

    /** The redirect URL that replaces `{link}` in the post with [code]. */
    fun linkUrl(code: String): String = "$baseUrl/go/$code"

    fun build(post: SocialPost, account: SocialAccount): Preview {
        val photo = post.imagePaths.firstOrNull()?.takeIf { post.kind == PostKind.Image }?.let(images::publicUrl)
        val limit = if (photo != null) CAPTION_LIMIT else MESSAGE_LIMIT
        return Preview(fit(body(post, account), limit), photo, keyboard(post, account))
    }

    /** The buttons for the post's status, or null when it has none. */
    fun keyboard(post: SocialPost, account: SocialAccount): InlineKeyboardMarkup? {
        val rows = when {
            post.status == PostStatus.Pending -> buildList {
                add(
                    listOf(
                        button(BotText.APPROVE, CallbackAction.APPROVE, post),
                        button(BotText.EDIT, CallbackAction.EDIT, post),
                        button(BotText.SKIP, CallbackAction.SKIP, post),
                    ),
                )
                if (regenerateAvailable) add(listOf(button(BotText.REGENERATE, CallbackAction.REGENERATE, post)))
            }
            post.status == PostStatus.Approved && account.mode == AccountMode.Auto ->
                listOf(listOf(button(BotText.DELETE_BEFORE_SLOT, CallbackAction.DELETE, post)))
            post.status == PostStatus.Published ->
                listOf(listOf(button(BotText.DELETE, CallbackAction.DELETE, post)))
            else -> emptyList()
        }
        return rows.takeIf { it.isNotEmpty() }?.let(::InlineKeyboardMarkup)
    }

    private fun button(label: String, action: String, post: SocialPost): InlineKeyboardButton {
        val data = CallbackAction.data(action, post.id)
        require(data.toByteArray().size <= CALLBACK_DATA_LIMIT) { "callback_data is longer than $CALLBACK_DATA_LIMIT bytes" }
        return InlineKeyboardButton(label, data)
    }

    private fun body(post: SocialPost, account: SocialAccount): String {
        val slot = post.scheduledAt?.let { BotText.slot(it, account.zone) } ?: BotText.NO_SLOT
        val header = BotText.header(account.handle, post.code, slot, post.needsAttention)
        val text = ContentRules.render(post.text, linkUrl(post.code), account.language)
        return buildString {
            append(header)
            if (text.isNotBlank()) append("\n\n").append(text)
            if (post.kind == PostKind.Poll) {
                append("\n\n").append(BotText.POLL_OPTIONS_TITLE)
                post.pollOptions.forEachIndexed { index, option -> append("\n${index + 1}. $option") }
            }
        }
    }

    /** Cuts [text] to [limit] characters, ending with an ellipsis, without splitting a surrogate pair. */
    private fun fit(text: String, limit: Int): String {
        if (text.length <= limit) return text
        var end = limit - 1
        if (Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end) + "…"
    }

    companion object {
        const val CAPTION_LIMIT = 1024
        const val MESSAGE_LIMIT = 4096
        const val CALLBACK_DATA_LIMIT = 64
    }
}

/** Sends and updates the owner's previews and keeps the Telegram message id on the post. */
class PreviewSender(
    private val telegram: TelegramApi,
    private val previews: Previews,
    private val store: SocialStore,
    private val ownerChatId: Long,
) {
    /** Sends the preview of [post] and records the message id on it. */
    suspend fun send(post: SocialPost): Message {
        val account = requireNotNull(store.account(post.accountId)) { "Account ${post.accountId} is missing" }
        val preview = previews.build(post, account)
        val message = if (preview.photoUrl != null) {
            telegram.sendPhoto(ownerChatId, preview.photoUrl, preview.text, preview.keyboard)
        } else {
            telegram.sendMessage(ownerChatId, preview.text, preview.keyboard)
        }
        recordMessage(post.id, message.messageId)
        return message
    }

    /** Sends [text] with the buttons of [post]'s status and records the message id on the post. */
    suspend fun sendWithKeyboard(post: SocialPost, text: String): Message {
        val account = requireNotNull(store.account(post.accountId)) { "Account ${post.accountId} is missing" }
        val message = telegram.sendMessage(ownerChatId, text, previews.keyboard(post, account))
        recordMessage(post.id, message.messageId)
        return message
    }

    /**
     * Rewrites the post's existing preview message (text or caption, and buttons) from its current
     * state. A Telegram refusal, such as a message that is gone or already current, is ignored.
     */
    suspend fun refresh(post: SocialPost) {
        val messageId = post.telegramMessageId ?: return
        val account = store.account(post.accountId) ?: return
        val preview = previews.build(post, account)
        try {
            if (preview.photoUrl != null) {
                telegram.editMessageCaption(ownerChatId, messageId, preview.text, preview.keyboard)
            } else {
                telegram.editMessageText(ownerChatId, messageId, preview.text, preview.keyboard)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TelegramFailure) {
            // Nothing to do: the stored state is right, only the display is stale.
        }
    }

    private suspend fun recordMessage(postId: String, messageId: Long) {
        store.transaction { tx ->
            tx.post(postId)?.let { tx.putPost(it.copy(telegramMessageId = messageId)) }
        }
    }
}
