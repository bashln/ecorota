package app.ecorota.tracking

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TileCachePrunerTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a cache under the budget is left alone`() {
        val directory = folder.newFolder("tiles")
        val tile = file(directory, "keep.mvt", lastModified = 1_000L)

        val freed = TileCachePruner.prune(directory, maxBytes = 1_000L)

        assertEquals(0L, freed)
        assertTrue(tile.exists())
    }

    @Test
    fun `the oldest tiles go first`() {
        val directory = folder.newFolder("tiles")
        val oldest = file(directory, "old.mvt", lastModified = 1_000L)
        val middle = file(directory, "mid.mvt", lastModified = 2_000L)
        val newest = file(directory, "new.mvt", lastModified = 3_000L)

        val freed = TileCachePruner.prune(directory, maxBytes = 250L)

        assertEquals(100L, freed)
        assertFalse(oldest.exists())
        assertTrue(middle.exists())
        assertTrue(newest.exists())
    }

    @Test
    fun `a missing directory is not an error`() {
        val missing = File(folder.root, "absent")

        assertEquals(0L, TileCachePruner.prune(missing))
    }

    private fun file(directory: File, name: String, lastModified: Long): File =
        File(directory, name).apply {
            writeBytes(ByteArray(100))
            setLastModified(lastModified)
        }
}
