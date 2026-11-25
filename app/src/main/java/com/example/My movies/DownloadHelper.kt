
package com.mymovie.app.downloads

import com.mymovie.app.AppContainer
import com.mymovie.app.data.FreeItem
import com.mymovie.app.data.local.DownloadEntity
import java.io.File

data class DownloadStatus(
    val status: Int,
    val downloaded: Long,
    val total: Long
)

object DownloadHelper {

    private fun safeName(s: String): String =
        s.replace(Regex("[^A-Za-z0-9 ._-]"), "_")
            .trim()
            .take(60)
            .ifBlank { "video" }

    /**
     * Downloads an Internet Archive item only after its source
     * has been verified by the repository.
     */
    suspend fun downloadFree(
        c: AppContainer,
        item: FreeItem,
        title: String? = null
    ): Boolean {
        val source = runCatching {
            c.repo.verifyFree(item)
        }.getOrNull() ?: return false

        return runCatching {
            c.downloader.enqueue(
                title = safeName(title ?: item.title),
                url = source.url,
                poster = item.thumb,
                mediaKey = "ia:${item.identifier}"
            )
            true
        }.getOrDefault(false)
    }

    fun removeFile(entity: DownloadEntity): Boolean {
        return runCatching {
            File(entity.filePath).delete()
        }.getOrDefault(false)
    }
}
