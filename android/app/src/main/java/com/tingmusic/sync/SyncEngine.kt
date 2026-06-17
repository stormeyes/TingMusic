package com.tingmusic.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 同步进度,UI 据此显示。 */
sealed interface SyncProgress {
    data object FetchingManifest : SyncProgress
    data class Downloading(val done: Int, val total: Int, val current: String) : SyncProgress
    data class Done(val downloaded: Int, val deleted: Int, val failed: Int) : SyncProgress
    data class Failed(val message: String) : SyncProgress
}

/**
 * 单向镜像同步:fetch manifest -> diff -> 下载新增/变化 -> 删除 remote 没有的 -> 存状态。
 * mirrorRoot 是 App 专属目录下的镜像根(如 getExternalFilesDir("Music"))。
 * onProgress 在 IO 线程回调。返回最终 SyncProgress(Done 或 Failed)。
 */
class SyncEngine(
    private val client: SyncClient,
    private val stateStore: SyncStateStore,
    private val mirrorRoot: File,
) {
    suspend fun sync(baseUrl: String, onProgress: (SyncProgress) -> Unit): SyncProgress =
        withContext(Dispatchers.IO) {
            try {
                onProgress(SyncProgress.FetchingManifest)
                val manifest = client.fetchManifest(baseUrl)
                val local = stateStore.load().toMutableMap()
                val plan = SyncDiff.compute(local, manifest.files)

                var failed = 0
                plan.toDownload.forEachIndexed { i, entry ->
                    onProgress(SyncProgress.Downloading(i, plan.toDownload.size, entry.path))
                    try {
                        client.downloadFile(baseUrl, entry.path, File(mirrorRoot, entry.path))
                        local[entry.path] = FileKey(entry.size, entry.mtime)
                    } catch (_: Exception) {
                        failed++  // 跳过这个文件,不更新它的本地状态,下次自然重试
                    }
                }

                for (path in plan.toDelete) {
                    File(mirrorRoot, path).delete()
                    local.remove(path)
                }
                pruneEmptyDirs(mirrorRoot)
                stateStore.save(local)

                SyncProgress.Done(
                    downloaded = plan.toDownload.size - failed,
                    deleted = plan.toDelete.size,
                    failed = failed,
                ).also(onProgress)
            } catch (e: Exception) {
                SyncProgress.Failed(e.message ?: "sync failed").also(onProgress)
            }
        }

    private fun pruneEmptyDirs(root: File) {
        root.walkBottomUp().forEach { f ->
            if (f.isDirectory && f != root && (f.listFiles()?.isEmpty() == true)) f.delete()
        }
    }
}
