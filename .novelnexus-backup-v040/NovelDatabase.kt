package com.novelnexus.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.novelnexus.app.core.model.ChapterContent
import com.novelnexus.app.core.model.ChapterRef
import com.novelnexus.app.core.model.NovelDetails

class NovelDatabase(context: Context) : SQLiteOpenHelper(context, "novel_nexus.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE offline_novels(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              title TEXT NOT NULL,
              cover_url TEXT,
              author TEXT,
              updated_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, novel_url)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE offline_chapters(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              chapter_url TEXT NOT NULL,
              chapter_index INTEGER NOT NULL,
              title TEXT NOT NULL,
              html TEXT NOT NULL,
              plain_text TEXT NOT NULL,
              downloaded_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, chapter_url)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE reading_progress(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              chapter_url TEXT NOT NULL,
              chapter_index INTEGER NOT NULL,
              progress REAL NOT NULL,
              paragraph_index INTEGER NOT NULL DEFAULT 0,
              scroll_offset INTEGER NOT NULL DEFAULT 0,
              updated_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, novel_url)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE bookmarks(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              chapter_url TEXT NOT NULL,
              chapter_index INTEGER NOT NULL,
              paragraph_index INTEGER NOT NULL DEFAULT 0,
              scroll_offset INTEGER NOT NULL DEFAULT 0,
              title TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, chapter_url, paragraph_index)
            )
        """.trimIndent())

        createV2Tables(db)
    }

    private fun createV2Tables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS chapter_catalog(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              chapter_url TEXT NOT NULL,
              chapter_index INTEGER NOT NULL,
              title TEXT NOT NULL,
              fetched_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, novel_url, chapter_url)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reading_history(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              chapter_url TEXT NOT NULL,
              chapter_index INTEGER NOT NULL,
              title TEXT NOT NULL,
              opened_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, novel_url)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reading_stats(
              day TEXT PRIMARY KEY,
              seconds_read INTEGER NOT NULL DEFAULT 0,
              chapters_opened INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE reading_progress ADD COLUMN paragraph_index INTEGER NOT NULL DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE reading_progress ADD COLUMN scroll_offset INTEGER NOT NULL DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE bookmarks ADD COLUMN paragraph_index INTEGER NOT NULL DEFAULT 0") }
            runCatching { db.execSQL("ALTER TABLE bookmarks ADD COLUMN scroll_offset INTEGER NOT NULL DEFAULT 0") }
            createV2Tables(db)
        }
        if (oldVersion < 3) createV2Tables(db)
    }

    fun upsertNovel(sourceId: String, novelUrl: String, title: String, coverUrl: String?, author: String?) {
        writableDatabase.insertWithOnConflict("offline_novels", null, ContentValues().apply {
            put("source_id", sourceId)
            put("novel_url", novelUrl)
            put("title", title)
            put("cover_url", coverUrl)
            put("author", author)
            put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun saveChapter(chapter: ChapterContent) {
        writableDatabase.insertWithOnConflict("offline_chapters", null, ContentValues().apply {
            put("source_id", chapter.sourceId)
            put("novel_url", chapter.novelUrl)
            put("chapter_url", chapter.chapterUrl)
            put("chapter_index", chapter.index)
            put("title", chapter.title)
            put("html", chapter.html)
            put("plain_text", chapter.plainText)
            put("downloaded_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getChapter(sourceId: String, chapterUrl: String): ChapterContent? {
        readableDatabase.query(
            "offline_chapters",
            arrayOf("novel_url", "chapter_index", "title", "html", "plain_text"),
            "source_id=? AND chapter_url=?",
            arrayOf(sourceId, chapterUrl), null, null, null
        ).use { c ->
            if (!c.moveToFirst()) return null
            return ChapterContent(
                sourceId = sourceId,
                novelUrl = c.getString(0),
                chapterUrl = chapterUrl,
                index = c.getInt(1),
                title = c.getString(2),
                html = c.getString(3),
                plainText = c.getString(4)
            )
        }
    }

    fun isDownloaded(sourceId: String, chapterUrl: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT 1 FROM offline_chapters WHERE source_id=? AND chapter_url=? LIMIT 1",
            arrayOf(sourceId, chapterUrl)
        ).use { return it.moveToFirst() }
    }

    fun downloadedUrls(sourceId: String, novelUrl: String): Set<String> {
        readableDatabase.query(
            "offline_chapters", arrayOf("chapter_url"),
            "source_id=? AND novel_url=?", arrayOf(sourceId, novelUrl),
            null, null, null
        ).use { c ->
            val out = mutableSetOf<String>()
            while (c.moveToNext()) out += c.getString(0)
            return out
        }
    }

    fun downloadedCount(sourceId: String, novelUrl: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM offline_chapters WHERE source_id=? AND novel_url=?",
            arrayOf(sourceId, novelUrl)
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    data class OfflineNovel(
        val sourceId: String,
        val novelUrl: String,
        val title: String,
        val coverUrl: String?,
        val author: String?,
        val chapters: Int
    )

    fun offlineNovels(): List<OfflineNovel> {
        val sql = """
            SELECT n.source_id,n.novel_url,n.title,n.cover_url,n.author,COUNT(c.chapter_url)
            FROM offline_novels n
            LEFT JOIN offline_chapters c ON c.source_id=n.source_id AND c.novel_url=n.novel_url
            GROUP BY n.source_id,n.novel_url,n.title,n.cover_url,n.author
            ORDER BY n.updated_at DESC
        """.trimIndent()
        readableDatabase.rawQuery(sql, null).use { c ->
            val out = mutableListOf<OfflineNovel>()
            while (c.moveToNext()) out += OfflineNovel(
                c.getString(0), c.getString(1), c.getString(2),
                c.getString(3), c.getString(4), c.getInt(5)
            )
            return out
        }
    }

    fun downloadedChapters(sourceId: String, novelUrl: String): List<ChapterRef> {
        readableDatabase.query(
            "offline_chapters",
            arrayOf("chapter_url", "chapter_index", "title"),
            "source_id=? AND novel_url=?", arrayOf(sourceId, novelUrl),
            null, null, "chapter_index ASC"
        ).use { cursor ->
            val chapters = mutableListOf<ChapterRef>()
            while (cursor.moveToNext()) {
                chapters += ChapterRef(sourceId, novelUrl, cursor.getString(2), cursor.getString(0), cursor.getInt(1))
            }
            return chapters
        }
    }

    fun saveChapterCatalog(sourceId: String, novelUrl: String, chapters: List<ChapterRef>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("chapter_catalog", "source_id=? AND novel_url=?", arrayOf(sourceId, novelUrl))
            val now = System.currentTimeMillis()
            chapters.forEach { ch ->
                db.insertWithOnConflict("chapter_catalog", null, ContentValues().apply {
                    put("source_id", sourceId)
                    put("novel_url", novelUrl)
                    put("chapter_url", ch.url)
                    put("chapter_index", ch.index)
                    put("title", ch.title)
                    put("fetched_at", now)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun cachedChapterCatalog(sourceId: String, novelUrl: String): List<ChapterRef> {
        readableDatabase.query(
            "chapter_catalog", arrayOf("chapter_url", "chapter_index", "title"),
            "source_id=? AND novel_url=?", arrayOf(sourceId, novelUrl),
            null, null, "chapter_index ASC"
        ).use { c ->
            val out = mutableListOf<ChapterRef>()
            while (c.moveToNext()) out += ChapterRef(sourceId, novelUrl, c.getString(2), c.getString(0), c.getInt(1))
            return out
        }
    }

    fun offlineNovelDetails(sourceId: String, novelUrl: String): NovelDetails? {
        readableDatabase.query(
            "offline_novels", arrayOf("title", "cover_url", "author"),
            "source_id=? AND novel_url=?", arrayOf(sourceId, novelUrl),
            null, null, null, "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val catalogue = cachedChapterCatalog(sourceId, novelUrl)
            return NovelDetails(
                sourceId = sourceId,
                title = cursor.getString(0),
                url = novelUrl,
                coverUrl = if (cursor.isNull(1)) null else cursor.getString(1),
                author = if (cursor.isNull(2)) null else cursor.getString(2),
                description = "",
                chapters = catalogue.ifEmpty { downloadedChapters(sourceId, novelUrl) }
            )
        }
    }

    data class ReadingProgress(
        val chapterUrl: String,
        val chapterIndex: Int,
        val progress: Float,
        val paragraphIndex: Int,
        val scrollOffset: Int
    )

    fun getReadingProgress(sourceId: String, novelUrl: String): ReadingProgress? {
        readableDatabase.query(
            "reading_progress",
            arrayOf("chapter_url", "chapter_index", "progress", "paragraph_index", "scroll_offset"),
            "source_id=? AND novel_url=?", arrayOf(sourceId, novelUrl),
            null, null, null, "1"
        ).use { c ->
            if (!c.moveToFirst()) return null
            return ReadingProgress(c.getString(0), c.getInt(1), c.getFloat(2).coerceIn(0f,1f), c.getInt(3), c.getInt(4))
        }
    }

    fun saveProgress(
        sourceId: String,
        novelUrl: String,
        chapterUrl: String,
        chapterIndex: Int,
        progress: Float,
        paragraphIndex: Int = 0,
        scrollOffset: Int = 0
    ) {
        writableDatabase.insertWithOnConflict("reading_progress", null, ContentValues().apply {
            put("source_id", sourceId)
            put("novel_url", novelUrl)
            put("chapter_url", chapterUrl)
            put("chapter_index", chapterIndex)
            put("progress", progress.coerceIn(0f,1f))
            put("paragraph_index", paragraphIndex.coerceAtLeast(0))
            put("scroll_offset", scrollOffset.coerceAtLeast(0))
            put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun toggleBookmark(
        sourceId: String,
        novelUrl: String,
        chapterUrl: String,
        chapterIndex: Int,
        paragraphIndex: Int,
        scrollOffset: Int,
        title: String
    ): Boolean {
        val exists = readableDatabase.rawQuery(
            "SELECT 1 FROM bookmarks WHERE source_id=? AND chapter_url=? AND paragraph_index=? LIMIT 1",
            arrayOf(sourceId, chapterUrl, paragraphIndex.toString())
        ).use { it.moveToFirst() }

        if (exists) {
            writableDatabase.delete(
                "bookmarks", "source_id=? AND chapter_url=? AND paragraph_index=?",
                arrayOf(sourceId, chapterUrl, paragraphIndex.toString())
            )
            return false
        }

        writableDatabase.insertWithOnConflict("bookmarks", null, ContentValues().apply {
            put("source_id", sourceId)
            put("novel_url", novelUrl)
            put("chapter_url", chapterUrl)
            put("chapter_index", chapterIndex)
            put("paragraph_index", paragraphIndex)
            put("scroll_offset", scrollOffset)
            put("title", title)
            put("created_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        return true
    }

    fun isBookmarked(sourceId: String, chapterUrl: String, paragraphIndex: Int): Boolean {
        return readableDatabase.rawQuery(
            "SELECT 1 FROM bookmarks WHERE source_id=? AND chapter_url=? AND paragraph_index=? LIMIT 1",
            arrayOf(sourceId, chapterUrl, paragraphIndex.toString())
        ).use { it.moveToFirst() }
    }

    fun recordHistory(sourceId: String, novelUrl: String, chapterUrl: String, chapterIndex: Int, title: String) {
        writableDatabase.insertWithOnConflict("reading_history", null, ContentValues().apply {
            put("source_id", sourceId)
            put("novel_url", novelUrl)
            put("chapter_url", chapterUrl)
            put("chapter_index", chapterIndex)
            put("title", title)
            put("opened_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun addReadingSeconds(seconds: Long) {
        if (seconds <= 0) return
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        val db = writableDatabase
        db.insertWithOnConflict(
            "reading_stats", null, ContentValues().apply {
                put("day", day)
                put("seconds_read", 0)
                put("chapters_opened", 0)
            }, SQLiteDatabase.CONFLICT_IGNORE
        )
        db.execSQL(
            "UPDATE reading_stats SET seconds_read=seconds_read+?, chapters_opened=chapters_opened+1 WHERE day=?",
            arrayOf(seconds, day)
        )
    }
}
