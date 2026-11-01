package com.sirelon.sellsnap.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The conversation key is restored from saved state with the title and image it was opened with. */
class ConversationKeyTest {

    private val json = Json { serializersModule = appNavigationSavedStateConfiguration.serializersModule }
    private val serializer = PolymorphicSerializer(NavKey::class)

    @Test
    fun `a key survives saved state with its title and image, or without them`() {
        listOf(
            AppKey.Conversation(threadId = 1, advertId = 2, title = "Placeholder title", imageUrl = "https://example.invalid/a.jpg"),
            AppKey.Conversation(threadId = 3, advertId = 4),
        ).forEach { key ->
            assertEquals(key, json.decodeFromString(serializer, json.encodeToString(serializer, key)))
        }
    }

    @Test
    fun `a key saved before the title and image existed still restores`() {
        val type = AppKey.Conversation.serializer().descriptor.serialName
        val restored = json.decodeFromString(serializer, """{"type":"$type","threadId":1,"advertId":2}""")

        assertEquals(AppKey.Conversation(threadId = 1, advertId = 2), restored)
    }
}
