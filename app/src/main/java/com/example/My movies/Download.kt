package com.mymovie.app.downloads

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.mymovie.app.data.local.AppDao
import com.mymovie.app.data.local.DownloadEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

data class StorageInfo(val usedByDownloads: Long, val free: Long, val total: Long)

/** Resumable HTTP downloader (Range requests). Only called for sources that explicitly permit downloading. */
class Downloader(private val context: Context, private val dao: AppDao, base: OkHttpClient, private val scope: CoroutineScope) {
    private val client = base.newBuilder().cache(null).readTimeout(60, TimeUnit.SECONDS).build()
    private val jobs = ConcurrentHashMap<Long, Job>()

    fun dir(): File = (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "movies")).also { it.mkdirs() }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9 ._-]"), "_").trim().take(60).ifBlank { "video" }

    suspend fun enqueue(title: String, url: String, mediaKey: String?, poster: String?): Long {
        dao.downloadByUrl(url)?.let { resume(it.id); return it.id }
        val file = File(dir(), "${safe(title)}_${System.currentTimeMillis() % 100000}.mp4")
        val id = dao.insertDownload(DownloadEntity(0, title, url, file.absolutePath, poster, mediaKey, "QUEUED", 0, 0, System.currentTimeMillis()))
        start(id)
        return id
    }

    fun resume(id: Long) = start(id)

    fun pause(id: Long) { jobs.remove(id)?.cancel() }

    suspend fun delete(id: Long) {
        jobs.remove(id)?.cancel()
        dao.getDownload(id)?.let { runCatching { File(it.filePath).delete() } }
        dao.deleteDownload(id)
    }

    private fun start(id: Long) {
        if (jobs[id]?.isActive == true) return
        jobs[id] = scope.launch(Dispatchers.IO) {
            try {
                dao.getDownload(id)?.let { dao.setProgress(id, it.downloadedBytes, it.totalBytes, "QUEUED") }
                DownloadService.start(context)
                run(id)
            } finally { jobs.remove(id) }
        }
    }

    private suspend fun run(id: Long) {
        val e = dao.getDownload(id) ?: return
        val file = File(e.filePath)
        var have = if (file.exists()) file.length() else 0L
        var total = e.totalBytes
        try {
            dao.setProgress(id, have, total, "RUNNING")
            val rb = Request.Builder().url(e.url)
            if (have > 0) rb.header("Range", "bytes=$have-")
            client.newCall(rb.build()).execute().use { resp ->
                if (resp.code == 416) { dao.setProgress(id, have, have, "COMPLETED"); return }
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val partial = resp.code == 206
                if (!partial && have > 0) { file.delete(); have = 0 }
                val body = resp.body ?: throw IOException("Empty response")
                total = if (body.contentLength() > 0) (if (partial) have + body.contentLength() else body.contentLength()) else total
                FileOutputStream(file, partial).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var last = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); have += n
                            val now = System.nanoTime()
                            if (now - last > 600_000_000L) { dao.setProgress(id, have, total, "RUNNING"); last = now }
                        }
                    }
                }
                dao.setProgress(id, have, if (total > 0) total else have, "COMPLETED")
            }
        } catch (ce: CancellationException) {
            withContext(NonCancellable) { dao.setProgress(id, if (file.exists()) file.length() else have, total, "PAUSED") }
            throw ce
        } catch (t: Throwable) {
            withContext(NonCancellable) { dao.setProgress(id, if (file.exists()) file.length() else have, total, "FAILED") }
        }
    }

    fun storage(): StorageInfo {
        val d = dir()
        val stat = StatFs(d.absolutePath)
        return StorageInfo(d.listFiles()?.sumOf { it.length() } ?: 0L, stat.availableBytes, stat.totalBytes)
    }
}
