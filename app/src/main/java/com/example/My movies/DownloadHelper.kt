package com.mymovie.app.downloads

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.mymovie.app.AppContainer
import com.mymovie.app.data.FreeItem
import com.mymovie.app.data.local.DownloadEntity
import java.io.File

data class DownloadStatus(val status: Int, val downloaded: Long, val total: Long)

/** Uses the system DownloadManager; files live in the app's private movie folder. */
object DownloadHelper {
    private fun safeName(s: String) = s.replace(Regex("[^A-Za-z0-9 ._-]"), "_").trim().take(60).ifBlank { "video" }

    fun enqueue(context: Context, title: String, url: String): DownloadEntity {
        val dm = context.getSystemService(DownloadManager::class.java)
        val name = "${safeName(title)}_${System.currentTimeMillis() % 100000}.mp4"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDescription("My Movie")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, name)
        val id = dm.enqueue(req)
        val path = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), name).absolutePath
        return DownloadEntity(id, title, url, path, System.currentTimeMillis())
    }

    fun query(context: Context, ids: List<Long>): Map<Long, DownloadStatus> {
        if (ids.isEmpty()) return emptyMap()
        val dm = context.getSystemService(DownloadManager::class.java)
        val out = HashMap<Long, DownloadStatus>()
        dm.query(DownloadManager.Query().setFilterById(*ids.toLongArray()))?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                out[id] = DownloadStatus(
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                )
            }
        }
        return out
    }

    fun remove(context: Context, e: DownloadEntity) {
        context.getSystemService(DownloadManager::class.java).remove(e.dmId)
        runCatching { File(e.filePath).delete() }
    }

    /** Downloads an Internet Archive (public-domain) item. Returns false if no file is available. */
    suspend fun downloadFree(c: AppContainer, item: FreeItem, title: String? = null): Boolean {
        val src = runCatching { c.repo.resolveFree(item) }.getOrNull() ?: return false
        val entity = enqueue(c.context, title ?: item.title, src.url)
        c.db.dao().upsertDownload(entity)
        return true
    }
}
