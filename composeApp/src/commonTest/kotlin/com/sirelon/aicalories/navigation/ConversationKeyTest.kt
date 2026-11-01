package com.sirelon.sellsnap.navigation

import androidx.navigation3.runtime.NavKey
import com.sirelon.sellsnap.features.seller.ad.publish_success.AdvertStatus
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The conversation key is restored from saved state with the title and image it was opened with. */
class ConversationKeyTest {

    private val json = Json { serializersModule = appNavigationSavedStateConfiguration.serializersModule }
    private val serializer = PolymorphicSerializer(NavKey::class)

    @Test
    fun `a key survives saved state with its title, image, listing status and account, or without them`() {
        listOf(
            AppKey.Conversation(threadId = 1, advertId = 2, title = "Placeholder title", imageUrl = "https://example.invalid/a.jpg"),
            AppKey.Conversation(threadId = 3, advertId = 4),
            AppKey.Conversation(threadId = 5, advertId = 6, status = AdvertStatus.RemovedByUser),
            AppKey.Conversation(threadId = 7, advertId = 8, isListingDeleted = true),
            AppKey.Conversation(threadId = 9, advertId = 10, localIndex = 2),
        ).forEach { key ->
            assertEquals(key, json.decodeFromString(serializer, json.encodeToString(serializer, key)))
        }
    }

    @Test
    fun `a key saved before the title, image and account existed still restores with no account`() {
        val type = AppKey.Conversation.serializer().descriptor.serialName
        val restored = json.decodeFromString(serializer, """{"type":"$type","threadId":1,"advertId":2}""")

        assertEquals(AppKey.Conversation(threadId = 1, advertId = 2), restored)
        assertEquals(null, (restored as AppKey.Conversation).localIndex)
    }
}
