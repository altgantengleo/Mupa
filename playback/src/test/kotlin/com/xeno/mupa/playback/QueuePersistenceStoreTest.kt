package com.xeno.mupa.playback

import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class QueuePersistenceStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun savesAndRestoresQueueSnapshotCleanly() {
        val file = File(tempFolder.root, "mupa_queue.json")
        val store = QueuePersistenceStore(file)

        val tracks = listOf(
            Track("t1", "Evaluasi", "Hindia", durationMs = 214_000L, sourceType = AudioSourceType.STREAMING),
            Track("t2", "Bertaut", "Nadin Amizah", durationMs = 256_000L, sourceType = AudioSourceType.LOCAL),
        )
        store.save(
            tracks = tracks,
            currentIndex = 1,
            positionMs = 45_200L,
            mediaUrisByTrackId = mapOf("t2" to "content://media/external/audio/media/42"),
        )

        val loaded = store.load()!!
        assertEquals(2, loaded.items.size)
        assertEquals(1, loaded.currentIndex)
        assertEquals(45_200L, loaded.positionMs)
        assertEquals("content://media/external/audio/media/42", loaded.items[1].mediaUri)
    }

    @Test
    fun corruptJsonReturnsNullInsteadOfCrashing() {
        val file = File(tempFolder.root, "corrupt_queue.json")
        file.writeText("{not-valid-json")
        val store = QueuePersistenceStore(file)
        assertNull(store.load())
    }
}
