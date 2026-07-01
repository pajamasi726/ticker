package io.stevelabs.ticker.server.history

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

enum class HistoryDb { H2, MYSQL, POSTGRESQL }

@ConfigurationProperties("ticker.history")
data class HistoryProperties(
    /** Enable opt-in persisted metric history (off by default; fully in-memory when false). */
    val enabled: Boolean = false,
    /** Which database backs history. H2 = embedded file (default, zero-setup). MYSQL / POSTGRESQL need `url` + credentials. */
    val db: HistoryDb = HistoryDb.H2,
    /** How often the recorder samples whitelisted metrics. */
    val sampleInterval: Duration = Duration.ofSeconds(15),
    /** How long samples are kept before the hourly prune deletes them. */
    val retention: Duration = Duration.ofDays(7),
    /** H2 file path (db=H2 only). A durable path/volume in production — not /tmp. */
    val h2Path: String = "./data/ticker-history",
    /** JDBC URL. Required when db=MYSQL or POSTGRESQL (e.g. jdbc:mysql://host:3306/ticker). Ignored for H2 unless set. */
    val url: String? = null,
    /** DB username — supply via env, never commit (guardrail #5). */
    val username: String? = null,
    /** DB password — supply via env, never commit (guardrail #5). */
    val password: String? = null,
    /** Auto-create the schema at startup (CREATE TABLE IF NOT EXISTS). Set false if a DBA pre-provisions the table on a restricted account. */
    val initSchema: Boolean = true,
    /** Max downsample buckets returned by a range query. */
    val maxBuckets: Int = 240,
    /** Archive cold-storage settings (guardrail #5 — write+verify before prune). */
    val archive: ArchiveProperties = ArchiveProperties(),
) {
    /** Configuration for the archive-before-prune guardrail (#5). */
    data class ArchiveProperties(
        /** Archive rows to cold storage BEFORE the retention prune deletes them (guardrail #5). */
        val enabled: Boolean = false,
        /** Directory for archive files (gzip CSV, one per prune batch). Use a durable path/volume. */
        val dir: String = "./data/ticker-history-archive",
        /** Rolling cap (Logback-style): delete archive files older than this. Default 90 days. */
        val fileRetention: Duration = Duration.ofDays(90),
        /** Rolling cap (Logback-style totalSizeCap): keep the archive dir under this many MB, deleting
         *  oldest files first. 0 = unlimited (only fileRetention applies). */
        val maxTotalSizeMb: Long = 0,
    )
}
