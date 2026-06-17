# 安卓同步引擎 + 曲库列表 (Android Plan 2/3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让安卓 App 发现局域网内的 Mac(mDNS,或手填 IP)、把 Mac 曲库**单向镜像**同步到手机 App 专属目录、索引出曲目并在列表里显示出来。**本阶段不做播放**(Plan 3)。

**Architecture:** 在已跑通的 Compose 骨架(`android/`)上加 `sync/` 与 `library/` 两个纯 Kotlin 模块 + 一个 `ui/` 层。纯逻辑(manifest 解析、同步 diff、LRC 解析、URL 逐段编码、状态持久化)走 JVM 单测;发现/下载/索引/UI 在真机验证。**不引入 Room/KSP**:同步状态用 `org.json` 存到 filesDir,曲库启动时扫镜像目录用 `MediaMetadataRetriever` 现建。

**Tech Stack:** Kotlin 2.2.10、Jetpack Compose(BOM 2026.02.01)、Coroutines、OkHttp、Android `NsdManager`、`MediaMetadataRetriever`、`org.json`。无 Media3(Plan 3)。

**前置:** Plan 1 已交付(Mac 端 `/manifest` + `/files/{*path}` + mDNS `_tingmusic._tcp`,端口默认 8737)。安卓骨架已交付(`android/`,Gradle 9.4.1 / AGP 9.2.0 内置 Kotlin / compileSdk 36 / minSdk 26)。**联调时 Mac 端 App 需运行且"局域网同步"开启**(默认开)。

**契约(来自 spec §4.5,务必遵守):**
- `/files/<path>` 的每个**路径段**要 percent-encode(`#`/`?`/空格/中日文),但**不编码** `/` 分隔符。manifest 给的是原始路径。
- 文件一律 `application/octet-stream`;判断歌词文件靠 manifest 里的 `.lrc` 扩展名,不看 Content-Type。
- 端口以 mDNS TXT 的 `port` 为准(默认 8737,可能顺延)。

**构建/测试命令(从 `android/` 目录,需 `JAVA_HOME` 指向 Android Studio JBR):**
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :app:testDebugUnitTest      # JVM 单测
./gradlew :app:assembleDebug          # 出 debug APK
```
真机:`ADB=~/Library/Android/sdk/platform-tools/adb`,装 `$ADB install -r app/build/outputs/apk/debug/app-debug.apk`(设备偶发掉线,命令前加 `$ADB wait-for-device`)。

---

### Task 1: 加依赖(OkHttp / coroutines / lifecycle-compose / 测试库)

**Files:**
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: 版本目录加条目**

在 `android/gradle/libs.versions.toml` 的 `[versions]` 末尾加:
```toml
okhttp = "4.12.0"
coroutines = "1.9.0"
junit = "4.13.2"
orgJson = "20240303"
```
`[libraries]` 末尾加:
```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "coroutines" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
org-json = { group = "org.json", name = "json", version.ref = "orgJson" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

- [ ] **Step 2: app 模块加依赖**

在 `android/app/build.gradle.kts` 的 `dependencies { }` 块里,`debugImplementation(libs.androidx.ui.tooling)` 之后加:
```kotlin
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
```

- [ ] **Step 3: 确认能解析依赖并编译**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL(会下载 okhttp/coroutines 等)。

- [ ] **Step 4: Commit**
```bash
git add android/gradle/libs.versions.toml android/app/build.gradle.kts
git commit -m "build(android): add OkHttp, coroutines, lifecycle-compose, test deps"
```

---

### Task 2: Manifest 模型 + 解析(org.json,TDD)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/sync/Manifest.kt`
- Create: `android/app/src/test/java/com/tingmusic/sync/ManifestParserTest.kt`

- [ ] **Step 1: 写失败测试**

Create `android/app/src/test/java/com/tingmusic/sync/ManifestParserTest.kt`:
```kotlin
package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class ManifestParserTest {
    @Test
    fun parsesVersionLibraryAndFiles() {
        val json = """
            {"version":1,"library_name":"TingMusic","files":[
              {"path":"a.mp3","size":100,"mtime":1778302258},
              {"path":"sub/b.lrc","size":20,"mtime":1778302259}
            ]}
        """.trimIndent()
        val m = ManifestParser.parse(json)
        assertEquals(1, m.version)
        assertEquals("TingMusic", m.libraryName)
        assertEquals(2, m.files.size)
        assertEquals("a.mp3", m.files[0].path)
        assertEquals(100L, m.files[0].size)
        assertEquals(1778302258L, m.files[0].mtime)
        assertEquals("sub/b.lrc", m.files[1].path)
    }

    @Test
    fun emptyFilesList() {
        val m = ManifestParser.parse("""{"version":1,"library_name":"X","files":[]}""")
        assertEquals(0, m.files.size)
        assertEquals("X", m.libraryName)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.sync.ManifestParserTest"`
Expected: 编译失败(`Manifest`/`ManifestParser` 未定义)。

- [ ] **Step 3: 实现**

Create `android/app/src/main/java/com/tingmusic/sync/Manifest.kt`:
```kotlin
package com.tingmusic.sync

import org.json.JSONObject

/** Mac 端 /manifest 里的一个文件项(相对路径 + 大小 + 修改时间秒)。 */
data class ManifestEntry(val path: String, val size: Long, val mtime: Long)

/** /manifest 的完整内容。 */
data class Manifest(val version: Int, val libraryName: String, val files: List<ManifestEntry>)

object ManifestParser {
    fun parse(json: String): Manifest {
        val root = JSONObject(json)
        val arr = root.getJSONArray("files")
        val files = ArrayList<ManifestEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            files.add(ManifestEntry(e.getString("path"), e.getLong("size"), e.getLong("mtime")))
        }
        return Manifest(
            version = root.optInt("version", 1),
            libraryName = root.optString("library_name", ""),
            files = files,
        )
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令。
Expected: 2 个测试 PASS。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/sync/Manifest.kt android/app/src/test/java/com/tingmusic/sync/ManifestParserTest.kt
git commit -m "feat(android-sync): manifest model + org.json parser"
```

---

### Task 3: 同步 diff(纯函数,TDD)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/sync/SyncDiff.kt`
- Create: `android/app/src/test/java/com/tingmusic/sync/SyncDiffTest.kt`

- [ ] **Step 1: 写失败测试**

Create `android/app/src/test/java/com/tingmusic/sync/SyncDiffTest.kt`:
```kotlin
package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDiffTest {
    private fun entry(path: String, size: Long, mtime: Long) = ManifestEntry(path, size, mtime)

    @Test
    fun newFileIsDownloaded() {
        val plan = SyncDiff.compute(emptyMap(), listOf(entry("a.mp3", 10, 100)))
        assertEquals(listOf("a.mp3"), plan.toDownload.map { it.path })
        assertEquals(emptyList<String>(), plan.toDelete)
    }

    @Test
    fun unchangedFileIsSkipped() {
        val local = mapOf("a.mp3" to FileKey(10, 100))
        val plan = SyncDiff.compute(local, listOf(entry("a.mp3", 10, 100)))
        assertEquals(emptyList<String>(), plan.toDownload.map { it.path })
        assertEquals(emptyList<String>(), plan.toDelete)
    }

    @Test
    fun changedSizeOrMtimeReDownloads() {
        val local = mapOf("a.mp3" to FileKey(10, 100), "b.mp3" to FileKey(20, 200))
        val plan = SyncDiff.compute(local, listOf(entry("a.mp3", 11, 100), entry("b.mp3", 20, 201)))
        assertEquals(setOf("a.mp3", "b.mp3"), plan.toDownload.map { it.path }.toSet())
    }

    @Test
    fun missingFromRemoteIsDeleted() {
        val local = mapOf("gone.mp3" to FileKey(5, 50), "keep.mp3" to FileKey(6, 60))
        val plan = SyncDiff.compute(local, listOf(entry("keep.mp3", 6, 60)))
        assertEquals(listOf("gone.mp3"), plan.toDelete)
        assertEquals(emptyList<String>(), plan.toDownload.map { it.path })
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.sync.SyncDiffTest"`
Expected: 编译失败(`SyncDiff`/`FileKey`/`SyncPlan` 未定义)。

- [ ] **Step 3: 实现**

Create `android/app/src/main/java/com/tingmusic/sync/SyncDiff.kt`:
```kotlin
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
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2 命令。Expected: 4 个测试 PASS。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/sync/SyncDiff.kt android/app/src/test/java/com/tingmusic/sync/SyncDiffTest.kt
git commit -m "feat(android-sync): mirror diff (download/delete sets)"
```

---

### Task 4: 同步状态持久化(org.json 文件,TDD)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/sync/SyncStateStore.kt`
- Create: `android/app/src/test/java/com/tingmusic/sync/SyncStateStoreTest.kt`

- [ ] **Step 1: 写失败测试(用临时文件,JVM 可跑)**

Create `android/app/src/test/java/com/tingmusic/sync/SyncStateStoreTest.kt`:
```kotlin
package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncStateStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun loadMissingFileReturnsEmpty() {
        val store = SyncStateStore(tmp.newFolder().resolve("state.json"))
        assertEquals(emptyMap<String, FileKey>(), store.load())
    }

    @Test
    fun roundTrip() {
        val store = SyncStateStore(tmp.newFolder().resolve("state.json"))
        val data = mapOf("a.mp3" to FileKey(10, 100), "sub/b.lrc" to FileKey(20, 200))
        store.save(data)
        assertEquals(data, store.load())
    }

    @Test
    fun corruptFileReturnsEmpty() {
        val f = tmp.newFolder().resolve("state.json")
        f.writeText("{not json")
        assertEquals(emptyMap<String, FileKey>(), SyncStateStore(f).load())
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.sync.SyncStateStoreTest"`
Expected: 编译失败(`SyncStateStore` 未定义)。

- [ ] **Step 3: 实现**

Create `android/app/src/main/java/com/tingmusic/sync/SyncStateStore.kt`:
```kotlin
package com.tingmusic.sync

import org.json.JSONObject
import java.io.File

/**
 * 把"上次同步的 manifest 状态"(path -> size,mtime)存成一个 JSON 文件。
 * 损坏或不存在时返回空(下次会被当成全新同步)。
 */
class SyncStateStore(private val file: File) {

    fun load(): Map<String, FileKey> {
        if (!file.isFile) return emptyMap()
        return try {
            val root = JSONObject(file.readText())
            val out = HashMap<String, FileKey>(root.length())
            for (key in root.keys()) {
                val o = root.getJSONObject(key)
                out[key] = FileKey(o.getLong("size"), o.getLong("mtime"))
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(state: Map<String, FileKey>) {
        val root = JSONObject()
        for ((path, key) in state) {
            root.put(path, JSONObject().put("size", key.size).put("mtime", key.mtime))
        }
        file.parentFile?.mkdirs()
        file.writeText(root.toString())
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: 3 个测试 PASS。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/sync/SyncStateStore.kt android/app/src/test/java/com/tingmusic/sync/SyncStateStoreTest.kt
git commit -m "feat(android-sync): persist last-synced manifest state as JSON"
```

---

### Task 5: LRC 解析器(移植 Rust lyrics.rs,TDD)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/library/Lyrics.kt`
- Create: `android/app/src/test/java/com/tingmusic/library/LrcParserTest.kt`

- [ ] **Step 1: 写失败测试(照搬 Rust 的用例)**

Create `android/app/src/test/java/com/tingmusic/library/LrcParserTest.kt`:
```kotlin
package com.tingmusic.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesBasicSynced() {
        val l = LrcParser.parse("[00:01.00]Hello\n[00:02.50]World")
        l as Lyrics.Synced
        assertEquals(2, l.lines.size)
        assertEquals(1000L, l.lines[0].timeMs)
        assertEquals("Hello", l.lines[0].text)
        assertEquals(2500L, l.lines[1].timeMs)
    }

    @Test
    fun handlesMetadataHeader() {
        val l = LrcParser.parse("[ti:Song]\n[ar:Artist]\n[00:01.00]Line")
        l as Lyrics.Synced
        assertEquals(1, l.lines.size)
    }

    @Test
    fun multiTimestampLineYieldsMultipleEntries() {
        val l = LrcParser.parse("[00:01.00][00:05.00]Repeat")
        l as Lyrics.Synced
        assertEquals(2, l.lines.size)
        assertEquals(1000L, l.lines[0].timeMs)
        assertEquals(5000L, l.lines[1].timeMs)
    }

    @Test
    fun sortsUnorderedInput() {
        val l = LrcParser.parse("[00:05.00]Late\n[00:01.00]Early") as Lyrics.Synced
        assertEquals("Early", l.lines[0].text)
        assertEquals("Late", l.lines[1].text)
    }

    @Test
    fun handlesBom() {
        val l = LrcParser.parse("﻿[00:01.00]Hi") as Lyrics.Synced
        assertEquals("Hi", l.lines[0].text)
    }

    @Test
    fun degradesToPlainWhenMajorityCorrupt() {
        val l = LrcParser.parse("garbage1\ngarbage2\ngarbage3\n[00:01.00]Good")
        assertTrue(l is Lyrics.Plain)
    }

    @Test
    fun emptyInputIsPlain() {
        val l = LrcParser.parse("")
        assertTrue(l is Lyrics.Plain)
    }

    @Test
    fun parsesMillisThreeDigits() {
        val l = LrcParser.parse("[00:01.234]X") as Lyrics.Synced
        assertEquals(1234L, l.lines[0].timeMs)
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.library.LrcParserTest"`
Expected: 编译失败(`Lyrics`/`LrcParser` 未定义)。

- [ ] **Step 3: 实现(对应 Rust parse_lrc 逻辑)**

Create `android/app/src/main/java/com/tingmusic/library/Lyrics.kt`:
```kotlin
package com.tingmusic.library

/** 一行同步歌词。 */
data class LyricLine(val timeMs: Long, val text: String)

/** 歌词:带时间戳的同步歌词,或纯文本回落。 */
sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
}

object LrcParser {
    private const val PARSE_FAIL_RATIO = 0.5
    private val metadataTags = setOf("ti", "ar", "al", "by", "offset", "re", "ve", "au", "length")

    /** 解析 LRC 文本。坏行超过一半、或没有任何带时间戳的行 → 退化为 Plain。 */
    fun parse(text: String): Lyrics {
        val trimmed = text.removePrefix("﻿")
        val lines = ArrayList<LyricLine>()
        var total = 0
        var bad = 0
        for (rawLine in trimmed.lineSequence()) {
            val raw = rawLine.trim()
            if (raw.isEmpty()) continue
            total++
            val parsed = parseLine(raw)
            if (parsed == null) bad++ else lines.addAll(parsed)
        }
        if (total == 0) return Lyrics.Plain(text)
        if (bad.toDouble() / total.toDouble() > PARSE_FAIL_RATIO) return Lyrics.Plain(text)
        if (lines.isEmpty()) return Lyrics.Plain(text)
        lines.sortBy { it.timeMs }
        return Lyrics.Synced(lines)
    }

    /** 单行:可能有多个 [mm:ss.xx] 时间戳;元数据标签([ti:..] 等)被吃掉。 */
    private fun parseLine(raw: String): List<LyricLine>? {
        if (!raw.startsWith("[")) return null
        var rest = raw
        val times = ArrayList<Long>()
        while (rest.startsWith("[")) {
            val end = rest.indexOf(']')
            if (end < 0) return null
            val tag = rest.substring(1, end)
            rest = rest.substring(end + 1)
            val ms = parseTimestamp(tag)
            if (ms != null) {
                times.add(ms)
            } else if (isMetadataTag(tag)) {
                continue
            } else {
                return null
            }
        }
        if (times.isEmpty()) return emptyList()
        val lineText = rest.trim()
        return times.map { LyricLine(it, lineText) }
    }

    /** 接受 mm:ss.xx / mm:ss.xxx / mm:ss。 */
    private fun parseTimestamp(tag: String): Long? {
        val colon = tag.indexOf(':')
        if (colon < 0) return null
        val minutes = tag.substring(0, colon).toLongOrNull() ?: return null
        val rest = tag.substring(colon + 1)
        val dot = rest.indexOf('.')
        val secStr = if (dot < 0) rest else rest.substring(0, dot)
        val fracStr = if (dot < 0) "0" else rest.substring(dot + 1)
        val seconds = secStr.toLongOrNull() ?: return null
        if (seconds >= 60) return null
        val fracDigits = minOf(fracStr.length, 3)
        val frac = (if (fracDigits == 0) "0" else fracStr.substring(0, fracDigits)).toLongOrNull() ?: return null
        val fracMs = when (fracDigits) {
            0 -> 0L
            1 -> frac * 100
            2 -> frac * 10
            else -> frac
        }
        return minutes * 60_000 + seconds * 1000 + fracMs
    }

    private fun isMetadataTag(tag: String): Boolean =
        metadataTags.contains(tag.substringBefore(':'))
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: 8 个测试 PASS。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/library/Lyrics.kt android/app/src/test/java/com/tingmusic/library/LrcParserTest.kt
git commit -m "feat(android-library): port LRC parser from Rust lyrics.rs"
```

---

### Task 6: SyncClient —— 逐段 URL 编码 + OkHttp 下载

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/sync/SyncClient.kt`
- Create: `android/app/src/test/java/com/tingmusic/sync/SyncClientEncodeTest.kt`

- [ ] **Step 1: 写失败测试(只测纯函数 encodePath,JVM 可跑)**

Create `android/app/src/test/java/com/tingmusic/sync/SyncClientEncodeTest.kt`:
```kotlin
package com.tingmusic.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncClientEncodeTest {
    @Test
    fun encodesSpacesAsPercent20NotPlus() {
        assertEquals("NIGHT%20DANCER-imase.mp3", SyncClient.encodePath("NIGHT DANCER-imase.mp3"))
    }

    @Test
    fun keepsSlashSeparatorsButEncodesSegments() {
        assertEquals("Anime/NIGHT%20DANCER.mp3", SyncClient.encodePath("Anime/NIGHT DANCER.mp3"))
    }

    @Test
    fun encodesHashAndQuestion() {
        assertEquals("Song%23hash.mp3", SyncClient.encodePath("Song#hash.mp3"))
        assertEquals("a%3Fb.mp3", SyncClient.encodePath("a?b.mp3"))
    }

    @Test
    fun encodesUnicode() {
        // 群青 -> UTF-8 percent-encoded; just assert it round-trips through decode.
        val enc = SyncClient.encodePath("群青 - YOASOBI.flac")
        assertEquals("群青 - YOASOBI.flac", java.net.URLDecoder.decode(enc.replace("/", "%2F"), "UTF-8").replace("%2F", "/"))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.sync.SyncClientEncodeTest"`
Expected: 编译失败(`SyncClient` 未定义)。

- [ ] **Step 3: 实现**

Create `android/app/src/main/java/com/tingmusic/sync/SyncClient.kt`:
```kotlin
package com.tingmusic.sync

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 跟 Mac 端同步服务通信:拉 manifest、下载文件。 */
class SyncClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    /** GET {baseUrl}/manifest -> 解析后的 Manifest。 */
    fun fetchManifest(baseUrl: String): Manifest {
        val req = Request.Builder().url(trim(baseUrl) + "/manifest").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("manifest HTTP ${resp.code}")
            val body = resp.body?.string() ?: error("empty manifest body")
            return ManifestParser.parse(body)
        }
    }

    /** GET {baseUrl}/files/{encoded relPath} -> 流式写入 dest(先写 .part 再 rename)。 */
    fun downloadFile(baseUrl: String, relPath: String, dest: File) {
        val url = trim(baseUrl) + "/files/" + encodePath(relPath)
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("file HTTP ${resp.code} for $relPath")
            val body = resp.body ?: error("empty body for $relPath")
            dest.parentFile?.mkdirs()
            val part = File(dest.parentFile, dest.name + ".part")
            body.byteStream().use { input ->
                part.outputStream().use { out -> input.copyTo(out, bufferSize = 64 * 1024) }
            }
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
        }
    }

    private fun trim(base: String) = base.trimEnd('/')

    companion object {
        /**
         * 逐段 percent-encode 相对路径,保留 `/` 分隔符。空格编码为 %20(不是 +)。
         * 对应 Mac 端 axum `/files/{*path}` 的解码方式(spec §4.5)。
         */
        fun encodePath(rel: String): String =
            rel.split('/').joinToString("/") { seg ->
                URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: 4 个测试 PASS。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/sync/SyncClient.kt android/app/src/test/java/com/tingmusic/sync/SyncClientEncodeTest.kt
git commit -m "feat(android-sync): OkHttp client with per-segment URL encoding"
```

---

### Task 7: NSD 发现 + SyncEngine 编排

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/sync/Discovery.kt`
- Create: `android/app/src/main/java/com/tingmusic/sync/SyncEngine.kt`

- [ ] **Step 1: NSD 发现封装**

Create `android/app/src/main/java/com/tingmusic/sync/Discovery.kt`:
```kotlin
package com.tingmusic.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 发现到的一台 Mac 同步服务:可直连的 baseUrl(http://host:port)。 */
data class DiscoveredServer(val name: String, val host: String, val port: Int) {
    val baseUrl: String get() = "http://$host:$port"
}

private const val SERVICE_TYPE = "_tingmusic._tcp."

/**
 * 浏览 _tingmusic._tcp;每发现并解析成功一台就 emit。调用方在协程里 collect,
 * 取消即停止浏览。NsdManager 的 resolve 是逐个排队的,这里串行 resolve。
 */
fun discoverServers(context: Context): Flow<DiscoveredServer> = callbackFlow {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    val resolveListener = object : NsdManager.ResolveListener {
        override fun onServiceResolved(info: NsdServiceInfo) {
            val host = info.host?.hostAddress ?: return
            trySend(DiscoveredServer(info.serviceName ?: "TingMusic", host, info.port))
        }
        override fun onResolveFailed(info: NsdServiceInfo?, errorCode: Int) { /* ignore one failure */ }
    }

    val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
            if (info.serviceType.trimEnd('.').endsWith("_tingmusic._tcp")) {
                @Suppress("DEPRECATION")
                nsd.resolveService(info, resolveListener)
            }
        }
        override fun onServiceLost(info: NsdServiceInfo?) {}
        override fun onDiscoveryStarted(serviceType: String?) {}
        override fun onDiscoveryStopped(serviceType: String?) {}
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) { close() }
        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
    }

    nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    awaitClose {
        try { nsd.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
    }
}
```
> 注:`resolveService` 在 API 34+ 标记 deprecated(改用 `registerServiceInfoCallback`),但 minSdk 26 下 `resolveService` 仍可用且最省事;`@Suppress("DEPRECATION")` 即可。Plan 3 不强制升级。

- [ ] **Step 2: SyncEngine 编排**

Create `android/app/src/main/java/com/tingmusic/sync/SyncEngine.kt`:
```kotlin
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
```

- [ ] **Step 3: 编译确认**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL。(Discovery/SyncEngine 是框架/IO 集成代码,真机在 Task 9 验证;此处只确保编译通过。)

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/sync/Discovery.kt android/app/src/main/java/com/tingmusic/sync/SyncEngine.kt
git commit -m "feat(android-sync): NSD discovery + mirror sync engine"
```

---

### Task 8: 曲库索引(MediaMetadataRetriever + .lrc 匹配)

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/library/Track.kt`
- Create: `android/app/src/main/java/com/tingmusic/library/LrcMatch.kt`
- Create: `android/app/src/main/java/com/tingmusic/library/LibraryIndexer.kt`
- Create: `android/app/src/test/java/com/tingmusic/library/LrcMatchTest.kt`

- [ ] **Step 1: 写 .lrc 匹配的失败测试(纯函数,含 #hash 容错,TDD)**

Create `android/app/src/test/java/com/tingmusic/library/LrcMatchTest.kt`:
```kotlin
package com.tingmusic.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcMatchTest {
    @Test
    fun exactStemMatch() {
        val lrcs = listOf("Song.lrc", "Other.lrc")
        assertEquals("Song.lrc", LrcMatch.findFor("Song.mp3", lrcs))
    }

    @Test
    fun hashSuffixTolerated() {
        // 音频带 #hash 缓存后缀,lrc 不带 -> 仍匹配
        val lrcs = listOf("NIGHT DANCER-imase.lrc")
        assertEquals("NIGHT DANCER-imase.lrc", LrcMatch.findFor("NIGHT DANCER-imase#2ryCf3.mp3", lrcs))
    }

    @Test
    fun exactWinsOverHashNormalized() {
        val lrcs = listOf("Song.lrc", "Song#abc.lrc")
        assertEquals("Song#abc.lrc", LrcMatch.findFor("Song#abc.mp3", lrcs))
    }

    @Test
    fun noMatchReturnsNull() {
        assertEquals(null, LrcMatch.findFor("A.mp3", listOf("B.lrc")))
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest --tests "com.tingmusic.library.LrcMatchTest"`
Expected: 编译失败(`LrcMatch` 未定义)。

- [ ] **Step 3: 实现 Track + LrcMatch + LibraryIndexer**

Create `android/app/src/main/java/com/tingmusic/library/Track.kt`:
```kotlin
package com.tingmusic.library

import java.io.File

/** 一首本地(已同步)曲目。lrcFile 为 null 表示没有歌词。 */
data class Track(
    val id: String,            // 相对路径,稳定唯一
    val file: File,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val lrcFile: File?,
)
```

Create `android/app/src/main/java/com/tingmusic/library/LrcMatch.kt`:
```kotlin
package com.tingmusic.library

/**
 * 给定一个音频文件名,在同目录的 .lrc 文件名列表里找匹配。
 * 优先精确同名(去扩展名、忽略大小写);否则去掉尾部 `#hash` 后再比(网易云/QQ 缓存命名)。
 * 移植自 Rust lyrics.rs 的 load_sidecar_lrc。
 */
object LrcMatch {
    fun findFor(audioFileName: String, lrcFileNames: List<String>): String? {
        val audioStem = stem(audioFileName).lowercase()
        val audioNorm = stripHash(audioStem)
        var fallback: String? = null
        for (lrc in lrcFileNames) {
            val lrcStem = stem(lrc).lowercase()
            if (lrcStem == audioStem) return lrc
            if (fallback == null && stripHash(lrcStem) == audioNorm) fallback = lrc
        }
        return fallback
    }

    private fun stem(name: String): String {
        val slash = name.lastIndexOf('/')
        val base = if (slash >= 0) name.substring(slash + 1) else name
        val dot = base.lastIndexOf('.')
        return if (dot > 0) base.substring(0, dot) else base
    }

    private fun stripHash(s: String): String {
        val h = s.lastIndexOf('#')
        return if (h >= 0) s.substring(0, h).trimEnd() else s
    }
}
```

Create `android/app/src/main/java/com/tingmusic/library/LibraryIndexer.kt`:
```kotlin
package com.tingmusic.library

import android.media.MediaMetadataRetriever
import java.io.File

/**
 * 扫描镜像目录,对每个音频文件用 MediaMetadataRetriever 取元数据,
 * 在同目录里按 LrcMatch 找 .lrc,产出按相对路径排序的曲目列表。
 */
class LibraryIndexer(private val mirrorRoot: File) {

    private val audioExts = setOf("mp3", "flac", "wav", "ogg", "m4a", "aac")

    fun index(): List<Track> {
        if (!mirrorRoot.isDirectory) return emptyList()
        val allFiles = mirrorRoot.walkTopDown().filter { it.isFile }.toList()
        val tracks = ArrayList<Track>()
        for (f in allFiles) {
            val ext = f.extension.lowercase()
            if (ext !in audioExts) continue
            val siblingLrcs = (f.parentFile?.listFiles { c -> c.extension.lowercase() == "lrc" }
                ?.map { it.name } ?: emptyList())
            val lrcName = LrcMatch.findFor(f.name, siblingLrcs)
            val lrcFile = lrcName?.let { File(f.parentFile, it) }
            tracks.add(readTrack(f, lrcFile))
        }
        return tracks.sortedBy { it.id }
    }

    private fun readTrack(file: File, lrcFile: File?): Track {
        val rel = file.relativeTo(mirrorRoot).path
        val fileNameStem = file.nameWithoutExtension
        val mmr = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L
        try {
            mmr.setDataSource(file.absolutePath)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // 损坏/不支持的文件:用文件名兜底
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
        // 文件名兜底:Title-Artist(取最后一个连字符;去尾部 #hash)
        val (fnTitle, fnArtist) = parseFilenameStem(fileNameStem)
        return Track(
            id = rel,
            file = file,
            title = title ?: fnTitle,
            artist = artist ?: (fnArtist ?: "Unknown"),
            album = album ?: "Unknown",
            durationMs = durationMs,
            lrcFile = lrcFile,
        )
    }

    private fun parseFilenameStem(stem: String): Pair<String, String?> {
        val idx = stem.lastIndexOf('-')
        if (idx > 0) {
            val title = stem.substring(0, idx).trim()
            var artist = stem.substring(idx + 1).trim()
            val h = artist.lastIndexOf('#')
            if (h >= 0) artist = artist.substring(0, h).trimEnd()
            if (title.isNotEmpty() && artist.isNotEmpty()) return title to artist
        }
        return stem to null
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: 同 Step 2。Expected: LrcMatchTest 4 个测试 PASS。

- [ ] **Step 5: 整体编译**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 6: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/library/Track.kt android/app/src/main/java/com/tingmusic/library/LrcMatch.kt android/app/src/main/java/com/tingmusic/library/LibraryIndexer.kt android/app/src/test/java/com/tingmusic/library/LrcMatchTest.kt
git commit -m "feat(android-library): MediaMetadataRetriever indexer + .lrc matching"
```

---

### Task 9: ViewModel + 同步/列表 UI + 真机端到端

**Files:**
- Create: `android/app/src/main/java/com/tingmusic/ui/SyncViewModel.kt`
- Create: `android/app/src/main/java/com/tingmusic/ui/SyncScreen.kt`
- Modify: `android/app/src/main/java/com/tingmusic/MainActivity.kt`

- [ ] **Step 1: ViewModel**

Create `android/app/src/main/java/com/tingmusic/ui/SyncViewModel.kt`:
```kotlin
package com.tingmusic.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tingmusic.library.LibraryIndexer
import com.tingmusic.library.Track
import com.tingmusic.sync.DiscoveredServer
import com.tingmusic.sync.SyncClient
import com.tingmusic.sync.SyncEngine
import com.tingmusic.sync.SyncProgress
import com.tingmusic.sync.SyncStateStore
import com.tingmusic.sync.discoverServers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class UiState(
    val servers: List<DiscoveredServer> = emptyList(),
    val manualHost: String = "",
    val progress: SyncProgress? = null,
    val tracks: List<Track> = emptyList(),
    val syncing: Boolean = false,
)

class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val mirrorRoot: File = File(app.getExternalFilesDir("Music") ?: app.filesDir, "")
    private val stateStore = SyncStateStore(File(app.filesDir, "sync_state.json"))
    private val client = SyncClient()
    private val engine = SyncEngine(client, stateStore, mirrorRoot)
    private val indexer = LibraryIndexer(mirrorRoot)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var discoveryJob: Job? = null

    init { refreshLibrary() }

    fun setManualHost(host: String) { _state.value = _state.value.copy(manualHost = host) }

    fun startDiscovery() {
        discoveryJob?.cancel()
        _state.value = _state.value.copy(servers = emptyList())
        discoveryJob = viewModelScope.launch {
            discoverServers(getApplication()).collect { server ->
                val cur = _state.value.servers
                if (cur.none { it.baseUrl == server.baseUrl }) {
                    _state.value = _state.value.copy(servers = cur + server)
                }
            }
        }
    }

    /** baseUrl 来自发现的服务器,或手填 host(补全成 http://host:8737)。 */
    fun sync(baseUrl: String) {
        if (_state.value.syncing) return
        _state.value = _state.value.copy(syncing = true, progress = SyncProgress.FetchingManifest)
        viewModelScope.launch {
            engine.sync(baseUrl) { p ->
                _state.value = _state.value.copy(progress = p)
            }
            refreshLibrary()
            _state.value = _state.value.copy(syncing = false)
        }
    }

    fun manualBaseUrl(): String {
        val h = _state.value.manualHost.trim()
        return if (h.startsWith("http")) h else "http://$h:8737"
    }

    private fun refreshLibrary() {
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) { indexer.index() }
            _state.value = _state.value.copy(tracks = tracks)
        }
    }
}
```

- [ ] **Step 2: Compose UI**

Create `android/app/src/main/java/com/tingmusic/ui/SyncScreen.kt`:
```kotlin
package com.tingmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingmusic.sync.SyncProgress

@Composable
fun SyncScreen(vm: SyncViewModel, modifier: Modifier = Modifier) {
    val s by vm.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("局域网同步", style = MaterialTheme.typography.titleLarge)

        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.startDiscovery() }) { Text("扫描 Mac") }
            Button(onClick = { vm.sync(vm.manualBaseUrl()) }, enabled = !s.syncing && s.manualHost.isNotBlank()) {
                Text("手填同步")
            }
        }

        OutlinedTextField(
            value = s.manualHost,
            onValueChange = { vm.setManualHost(it) },
            label = { Text("手动 IP(如 192.168.5.139)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        s.servers.forEach { server ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${server.name} — ${server.host}:${server.port}")
                Button(onClick = { vm.sync(server.baseUrl) }, enabled = !s.syncing) { Text("同步") }
            }
        }

        s.progress?.let { p ->
            Text(progressText(p), modifier = Modifier.padding(vertical = 8.dp))
        }

        HorizontalDivider()
        Text("曲库(${s.tracks.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(s.tracks, key = { it.id }) { t ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${t.artist}${if (t.lrcFile != null) "  · 有歌词" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun progressText(p: SyncProgress): String = when (p) {
    is SyncProgress.FetchingManifest -> "正在获取清单…"
    is SyncProgress.Downloading -> "下载中 ${p.done}/${p.total}:${p.current}"
    is SyncProgress.Done -> "完成:下载 ${p.downloaded},删除 ${p.deleted},失败 ${p.failed}"
    is SyncProgress.Failed -> "失败:${p.message}"
}
```

- [ ] **Step 3: MainActivity 挂上 SyncScreen**

Replace `android/app/src/main/java/com/tingmusic/MainActivity.kt` 全文为:
```kotlin
package com.tingmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tingmusic.ui.SyncScreen
import com.tingmusic.ui.SyncViewModel

class MainActivity : ComponentActivity() {
    private val vm: SyncViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    SyncScreen(vm = vm, modifier = Modifier.padding(inner))
                }
            }
        }
    }
}
```

- [ ] **Step 4: 编译 + 全部 JVM 单测**

Run: `cd android && export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: 所有单测 PASS(Manifest 2 + Diff 4 + State 3 + Lrc 8 + Encode 4 + LrcMatch 4 = 25),BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/tingmusic/ui android/app/src/main/java/com/tingmusic/MainActivity.kt
git commit -m "feat(android-ui): sync screen + library list, wired into MainActivity"
```

- [ ] **Step 6: 真机端到端验证(控制者执行,需 Mac 端 App 运行 + 局域网同步开启)**

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB wait-for-device
$ADB install -r /Users/kongkongyzt/Sites/TingMusic/android/app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.tingmusic/.MainActivity
```
然后(手机与 Mac 同一 WiFi):
- 点"扫描 Mac" → 几秒内列出 `TingMusic — 192.168.x.x:8737`(若 mDNS 被压制,在输入框填 Mac 的局域网 IP,点"手填同步")。
- 点该服务器的"同步" → 进度走完显示"完成:下载 N,删除 0,失败 0"。
- 下方"曲库(N)"列出曲目(标题/艺术家,有 `.lrc` 的显示"· 有歌词")。
- 抓图复核:`$ADB exec-out screencap -p > /tmp/tm_sync.png`。
- 复核镜像文件确实落地:`$ADB shell ls "/sdcard/Android/data/com.tingmusic/files/Music" | head`。
- 删除验证(可选):在 Mac 曲库删掉一首 → 再点同步 → 手机该曲消失、列表 -1。

Expected: 列表数量与 Mac `/manifest` 的音频文件数一致;镜像目录有对应文件。失败则查 `$ADB logcat -d -t 200 | grep -iE "tingmusic|okhttp|AndroidRuntime"`。

---

## Self-Review

**Spec 覆盖(§4 协议 / §5 安卓 / §11 v1 边界):**
- §5.1 sync 包(Discovery/SyncClient/SyncEngine/Manifest)→ T2/6/7;library 包(Track/LrcParser/Indexer)→ T5/8 ✅(Room 按已确认的简化改为 SyncStateStore+JSON / 内存索引)
- §5.5 镜像存 `getExternalFilesDir("Music")`、免权限 → T9 ViewModel ✅
- §4.4 镜像 diff(下载/删除/状态持久化/单文件失败跳过)→ T3/T7 ✅
- §4.5 逐段 URL 编码 + 不依赖 Content-Type(靠 .lrc 扩展名)→ T6 encodePath / T8 LrcMatch ✅
- §5.3 歌词只 `.lrc` sidecar + #hash 容错 → T5/T8 ✅
- 发现 mDNS `_tingmusic._tcp` + 手填兜底 → T7/T9 ✅
- §11 v1 不做:播放/封面/主题 → 本计划确实未做(Plan 3)✅

**占位扫描:** 无 TBD;每个代码步骤给了完整代码。T9 Step 6 为真机验证(控制者执行)。

**类型一致性:** `ManifestEntry{path,size,mtime}`、`FileKey{size,mtime}`、`SyncPlan{toDownload,toDelete}`、`Lyrics.Synced/Plain`+`LyricLine{timeMs,text}`、`Track{id,file,title,artist,album,durationMs,lrcFile}`、`DiscoveredServer{name,host,port,baseUrl}`、`SyncProgress` 各分支、`UiState` 字段在各 Task 间一致。`SyncClient.encodePath`、`SyncDiff.compute`、`SyncStateStore.load/save`、`LrcParser.parse`、`LrcMatch.findFor`、`LibraryIndexer.index`、`SyncEngine.sync` 签名前后一致。

**执行注记:**
- 所有 `./gradlew` 命令需先 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`。
- JVM 单测靠 `testImplementation(org.json:json)` 让 org.json 在单测可用(否则 Android 桩会抛 "not mocked")。
- 首次 `assembleDebug` 会下载 okhttp/coroutines/lifecycle-compose;后续增量构建快。
- 设备偶发掉线:adb 命令前用 `$ADB wait-for-device` 兜。
- AGP 9 内置 Kotlin:**不要**再 apply `kotlin.android`;compose 插件已在骨架里。
