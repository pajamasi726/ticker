package io.stevelabs.ticker.server.history

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.GZIPInputStream

class HistoryArchiverTest {

    @TempDir
    lateinit var tempDir: Path

    private val stamp = 1_700_000_000_000L

    private val sampleRows = listOf(
        MetricSampleRow("svc-a", "heap-used", 1_000L, 512.0),
        MetricSampleRow("svc-a", "cpu-process", 2_000L, 0.42),
        MetricSampleRow("svc-b", "heap-used", 3_000L, 256.0),
    )

    @Test
    fun `archive creates gzip CSV file with correct name`() {
        val archiver = HistoryArchiver(tempDir)
        val file = archiver.archive(sampleRows, stamp)

        assertThat(file).exists()
        assertThat(file.fileName.toString()).isEqualTo("metric_sample-$stamp.csv.gz")
        assertThat(file.parent).isEqualTo(tempDir)
    }

    @Test
    fun `archive round-trips rows correctly — order and values match`() {
        val archiver = HistoryArchiver(tempDir)
        val file = archiver.archive(sampleRows, stamp)

        val lines = GZIPInputStream(Files.newInputStream(file)).bufferedReader().readLines()
        assertThat(lines).hasSize(3)
        assertThat(lines[0]).isEqualTo("svc-a,heap-used,1000,512.0")
        assertThat(lines[1]).isEqualTo("svc-a,cpu-process,2000,0.42")
        assertThat(lines[2]).isEqualTo("svc-b,heap-used,3000,256.0")
    }

    @Test
    fun `archive creates parent directory when it does not exist`() {
        val nested = tempDir.resolve("deep/nested/dir")
        val archiver = HistoryArchiver(nested)
        val file = archiver.archive(sampleRows, stamp)

        assertThat(nested).isDirectory()
        assertThat(file).exists()
    }

    @Test
    fun `verify returns true when row count matches`() {
        val archiver = HistoryArchiver(tempDir)
        val file = archiver.archive(sampleRows, stamp)

        assertThat(archiver.verify(file, sampleRows.size)).isTrue()
    }

    @Test
    fun `verify returns false when expected count does not match actual`() {
        val archiver = HistoryArchiver(tempDir)
        val file = archiver.archive(sampleRows, stamp)

        assertThat(archiver.verify(file, sampleRows.size + 1)).isFalse()
        assertThat(archiver.verify(file, 0)).isFalse()
    }

    @Test
    fun `verify returns false for a non-existent file`() {
        val archiver = HistoryArchiver(tempDir)
        val missing = tempDir.resolve("metric_sample-does-not-exist.csv.gz")

        assertThat(archiver.verify(missing, 0)).isFalse()
        assertThat(archiver.verify(missing, 3)).isFalse()
    }

    @Test
    fun `archive with empty row list produces a zero-line file that verifies at 0`() {
        val archiver = HistoryArchiver(tempDir)
        val file = archiver.archive(emptyList(), stamp)

        assertThat(file).exists()
        assertThat(archiver.verify(file, 0)).isTrue()
        assertThat(archiver.verify(file, 1)).isFalse()
    }

    @Test
    fun `enforceRetention deletes archive files older than maxAge`() {
        val archiver = HistoryArchiver(tempDir)
        val now = 2_000_000_000_000L
        val day = 86_400_000L
        val old = archiver.archive(sampleRows, now - 100 * day)
        val recent = archiver.archive(sampleRows, now - 10 * day)

        archiver.enforceRetention(maxAgeMillis = 90 * day, maxTotalBytes = 0, now = now)

        assertThat(old).doesNotExist()
        assertThat(recent).exists()
    }

    @Test
    fun `enforceRetention with a size cap deletes oldest first`() {
        val archiver = HistoryArchiver(tempDir)
        val f1 = archiver.archive(sampleRows, 1_000L)
        val f2 = archiver.archive(sampleRows, 2_000L)
        val f3 = archiver.archive(sampleRows, 3_000L)
        // Identical rows → identical gzip size; cap = two files → the oldest must be evicted.
        val cap = Files.size(f2) + Files.size(f3)

        archiver.enforceRetention(maxAgeMillis = Long.MAX_VALUE / 2, maxTotalBytes = cap, now = 10_000L)

        assertThat(f1).doesNotExist()
        assertThat(f2).exists()
        assertThat(f3).exists()
    }

    @Test
    fun `enforceRetention leaves non-archive files untouched`() {
        val archiver = HistoryArchiver(tempDir)
        val unrelated = tempDir.resolve("readme.txt")
        Files.writeString(unrelated, "keep me")
        archiver.archive(sampleRows, 1_000L)

        archiver.enforceRetention(maxAgeMillis = 0, maxTotalBytes = 1, now = 10_000_000L)

        assertThat(unrelated).exists() // only metric_sample-*.csv.gz are managed
    }
}
