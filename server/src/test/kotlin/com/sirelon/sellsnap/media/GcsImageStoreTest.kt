package com.sirelon.sellsnap.media

import com.google.auth.Credentials
import com.google.cloud.NoCredentials
import com.google.cloud.storage.StorageOptions
import kotlin.test.Test
import kotlin.test.assertEquals

/** The port contract is covered with the fake; this checks only the URL the real store hands out. */
class GcsImageStoreTest {
    private val store = GcsImageStore(
        StorageOptions.newBuilder().setProjectId("test").setCredentials(NoCredentials.getInstance() as Credentials).build().service,
        bucket = "sellsnap-6e85c.firebasestorage.app",
    )

    @Test
    fun publicUrlIsTheFirebaseMediaUrlWithTheEncodedPath() {
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/sellsnap-6e85c.firebasestorage.app/o/social%2Fmanual%2Fstt0001.jpg?alt=media",
            store.publicUrl("social/manual/stt0001.jpg"),
        )
    }
}
