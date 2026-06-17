package com.tingmusic.sync

/** 本地已同步文件的版本键(与 manifest 的 size+mtime 对比)。 */
data class FileKey(val size: Long, val mtime: Long)

/** 一次同步要下载哪些、删除哪些(相对路径)。 */
data class SyncPlan(val toDownload: List<ManifestEntry>, val toDelete: List<String>)

object SyncDiff {
    /**
     * 单向镜像 diff:remote 有而 local 没有或 size/mtime 不同 → 下载;
     * local 有而 remote 没有 → 删除。
     */
    fun compute(local: Map<String, FileKey>, remote: List<ManifestEntry>): SyncPlan {
        val remotePaths = HashSet<String>(remote.size)
        val toDownload = ArrayList<ManifestEntry>()
        for (e in remote) {
            remotePaths.add(e.path)
            val l = local[e.path]
            if (l == null || l.size != e.size || l.mtime != e.mtime) {
                toDownload.add(e)
            }
        }
        val toDelete = local.keys.filter { it !in remotePaths }
        return SyncPlan(toDownload, toDelete)
    }
}
