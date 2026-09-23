package com.novelnexus.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.novelnexus.app.core.model.ChapterContent
import com.novelnexus.app.core.model.ChapterRef
import com.novelnexus.app.core.model.NovelDetails

class NovelDatabase(context: Context) : SQLiteOpenHelper(context, "novel_nexus.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE offline_novels(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              title TEXT NOT NULL,
              cover_url TEXT,
              author TEXT,
              updated_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, novel_url)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
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
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE reading_progress(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              chapter_url TEXT NOT NULL,
              chapter_index INTEGER NOT NULL,
              progress REAL NOT NULL,
              updated_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, novel_url)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE bookmarks(
              source_id TEXT NOT NULL,
              novel_url TEXT NOT NULL,
              chapter_url TEXT NOT NULL,
              chapter_index INTEGER NOT NULL,
              title TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              PRIMARY KEY(source_id, chapter_url)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
            "source_id=? AND novel_url=?",
            arrayOf(sourceId, novelUrl),
            null,
            null,
            "chapter_index ASC"
        ).use { cursor ->
            val chapters = mutableListOf<ChapterRef>()
            while (cursor.moveToNext()) {
                chapters += ChapterRef(
                    sourceId = sourceId,
                    novelUrl = novelUrl,
                    title = cursor.getString(2),
                    url = cursor.getString(0),
                    index = cursor.getInt(1)
                )
            }
            return chapters
        }
    }

    fun offlineNovelDetails(sourceId: String, novelUrl: String): NovelDetails? {
        readableDatabase.query(
            "offline_novels",
            arrayOf("title", "cover_url", "author"),
            "source_id=? AND novel_url=?",
            arrayOf(sourceId, novelUrl),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return NovelDetails(
                sourceId = sourceId,
                title = cursor.getString(0),
                url = novelUrl,
                coverUrl = if (cursor.isNull(1)) null else cursor.getString(1),
                author = if (cursor.isNull(2)) null else cursor.getString(2),
                description = "",
                chapters = downloadedChapters(sourceId, novelUrl)
            )
        }
    }

    fun getProgress(sourceId: String, novelUrl: String): Float {
        readableDatabase.query(
            "reading_progress",
            arrayOf("progress"),
            "source_id=? AND novel_url=?",
            arrayOf(sourceId, novelUrl),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getFloat(0).coerceIn(0f, 1f) else 0f
        }
    }


    fun saveProgress(sourceId: String, novelUrl: String, chapterUrl: String, chapterIndex: Int, progress: Float) {
        writableDatabase.insertWithOnConflict("reading_progress", null, ContentValues().apply {
            put("source_id", sourceId)
            put("novel_url", novelUrl)
            put("chapter_url", chapterUrl)
            put("chapter_index", chapterIndex)
            put("progress", progress.coerceIn(0f, 1f))
            put("updated_at", System.currentTimeMillis())
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
