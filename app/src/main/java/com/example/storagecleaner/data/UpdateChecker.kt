package com.example.storagecleaner.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

sealed class UpdateResult {
    data class UpToDate(
        val currentVersion: String,
        val latestLabel: String,
        val releaseNotes: String,
        val releasePageUrl: String
    ) : UpdateResult()

    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val title: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val releasePageUrl: String,
        val apkSizeBytes: Long,
        val publishedAt: String
    ) : UpdateResult()

    data class Error(val message: String) : UpdateResult()
}

/**
 * Lightweight, privacy-respecting update checker querying GitHub Releases only
 * when explicitly triggered by the user. Uses a pure-Kotlin JSON parser so it
 * stays zero-dependency and 100% testable in local JVM unit tests.
 */
object UpdateChecker {
    private const val RELEASES_LATEST_API =
        "https://api.github.com/repos/Niranjan-Kumar-Singh/storage-cleaner-android/releases/latest"
    const val RELEASES_PAGE_URL =
        "https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android/releases"

    fun checkForUpdate(
        currentVersion: String,
        currentGitSha: String,
        buildTimeMs: Long
    ): UpdateResult {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(RELEASES_LATEST_API)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "StorageCleaner-Android/$currentVersion")
            }

            val code = conn.responseCode
            if (code == 404) {
                return UpdateResult.UpToDate(
                    currentVersion = currentVersion,
                    latestLabel = "v$currentVersion",
                    releaseNotes = "No newer public release published yet.",
                    releasePageUrl = RELEASES_PAGE_URL
                )
            }
            if (code !in 200..299) {
                val hint = if (code == 403) "GitHub API rate limit reached. Try again later."
                else "Update server returned HTTP $code."
                return UpdateResult.Error(hint)
            }

            val body = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            parseReleaseJson(body, currentVersion, currentGitSha, buildTimeMs)
        } catch (e: Exception) {
            UpdateResult.Error(
                e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Could not reach GitHub. Check your internet connection."
            )
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Parses a GitHub release JSON payload and determines whether it represents a
     * newer version or CI build than [currentVersion]/[currentGitSha]/[buildTimeMs].
     */
    internal fun parseReleaseJson(
        jsonText: String,
        currentVersion: String,
        currentGitSha: String,
        buildTimeMs: Long
    ): UpdateResult {
        return try {
            @Suppress("UNCHECKED_CAST")
            val obj = MiniJsonParser(jsonText).parse() as? Map<String, Any?>
                ?: return UpdateResult.Error("Unexpected JSON root element")

            val tagName = (obj["tag_name"] as? String).orEmpty().trim()
            val name = (obj["name"] as? String).orEmpty().trim().ifEmpty { tagName.ifEmpty { "Latest Release" } }
            val body = (obj["body"] as? String).orEmpty().trim()
            val htmlUrl = (obj["html_url"] as? String).orEmpty().ifEmpty { RELEASES_PAGE_URL }
            val publishedAt = (obj["published_at"] as? String).orEmpty()

            var apkUrl = ""
            var apkSize = 0L
            @Suppress("UNCHECKED_CAST")
            val assets = obj["assets"] as? List<Map<String, Any?>> ?: emptyList()
            for (asset in assets) {
                val aName = (asset["name"] as? String).orEmpty().lowercase()
                val dl = (asset["browser_download_url"] as? String).orEmpty()
                if (aName.endsWith(".apk") && dl.isNotBlank()) {
                    val isReleaseApk = "release" in aName
                    if (apkUrl.isEmpty() || isReleaseApk) {
                        apkUrl = dl
                        apkSize = (asset["size"] as? Number)?.toLong() ?: 0L
                        if (isReleaseApk) break
                    }
                }
            }

            val finalDownloadUrl = apkUrl.ifEmpty { htmlUrl }

            val hasSemVerTag = Regex("""^v?\d+(\.\d+)*$""").matches(tagName)
            val isNewer = if (hasSemVerTag) {
                compareSemVer(tagName, currentVersion) > 0
            } else {
                val shaInBody = Regex("""commit\s+`?([0-9a-fA-F]{7,40})`?""").find(body)?.groupValues?.get(1)
                val shaDiffers = !shaInBody.isNullOrBlank() &&
                    currentGitSha.isNotBlank() &&
                    currentGitSha != "local" &&
                    !shaInBody.startsWith(currentGitSha, ignoreCase = true) &&
                    !currentGitSha.startsWith(shaInBody, ignoreCase = true)

                val publishedMs = try {
                    if (publishedAt.isNotBlank()) Instant.parse(publishedAt).toEpochMilli() else 0L
                } catch (_: Exception) { 0L }
                val publishedAfterBuild = publishedMs > (buildTimeMs + 120_000L)

                if (currentGitSha == "local" || currentGitSha.isBlank()) {
                    publishedAfterBuild
                } else {
                    shaDiffers && publishedAfterBuild
                }
            }

            val cleanBody = body.ifBlank { "New build available on GitHub Releases." }

            if (isNewer) {
                UpdateResult.UpdateAvailable(
                    currentVersion = currentVersion,
                    latestVersion = if (hasSemVerTag) tagName else name,
                    title = name,
                    releaseNotes = cleanBody,
                    downloadUrl = finalDownloadUrl,
                    releasePageUrl = htmlUrl,
                    apkSizeBytes = apkSize,
                    publishedAt = publishedAt
                )
            } else {
                UpdateResult.UpToDate(
                    currentVersion = currentVersion,
                    latestLabel = if (hasSemVerTag) tagName else name,
                    releaseNotes = cleanBody,
                    releasePageUrl = htmlUrl
                )
            }
        } catch (e: Exception) {
            UpdateResult.Error("Invalid update response: ${e.message ?: "parse error"}")
        }
    }

    /** Returns > 0 if [versionA] > [versionB], < 0 if smaller, 0 if equal. */
    fun compareSemVer(versionA: String, versionB: String): Int {
        val partsA = versionA.removePrefix("v").removePrefix("V")
            .split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val partsB = versionB.removePrefix("v").removePrefix("V")
            .split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val a = partsA.getOrElse(i) { 0 }
            val b = partsB.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}

/** Compact RFC-8259 recursive-descent JSON parser with zero external or Android dependencies. */
private class MiniJsonParser(private val s: String) {
    private var i = 0

    fun parse(): Any? {
        skipWs()
        return parseValue()
    }

    private fun skipWs() {
        while (i < s.length && s[i].isWhitespace()) i++
    }

    private fun parseValue(): Any? {
        skipWs()
        if (i >= s.length) throw IllegalArgumentException("Unexpected end of JSON")
        return when (val c = s[i]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> { expect("true"); true }
            'f' -> { expect("false"); false }
            'n' -> { expect("null"); null }
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected character '$c' at $i")
        }
    }

    private fun expect(lit: String) {
        if (!s.startsWith(lit, i)) throw IllegalArgumentException("Expected '$lit' at $i")
        i += lit.length
    }

    private fun parseObject(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        i++ // skip '{'
        skipWs()
        if (i < s.length && s[i] == '}') { i++; return map }
        while (i < s.length) {
            skipWs()
            val key = parseString()
            skipWs()
            if (i >= s.length || s[i] != ':') throw IllegalArgumentException("Expected ':' at $i")
            i++
            val value = parseValue()
            map[key] = value
            skipWs()
            if (i < s.length && s[i] == ',') { i++; continue }
            if (i < s.length && s[i] == '}') { i++; return map }
            throw IllegalArgumentException("Malformed JSON object at $i")
        }
        throw IllegalArgumentException("Unterminated JSON object")
    }

    private fun parseArray(): List<Any?> {
        val list = ArrayList<Any?>()
        i++ // skip '['
        skipWs()
        if (i < s.length && s[i] == ']') { i++; return list }
        while (i < s.length) {
            list.add(parseValue())
            skipWs()
            if (i < s.length && s[i] == ',') { i++; continue }
            if (i < s.length && s[i] == ']') { i++; return list }
            throw IllegalArgumentException("Malformed JSON array at $i")
        }
        throw IllegalArgumentException("Unterminated JSON array")
    }

    private fun parseString(): String {
        if (s[i] != '"') throw IllegalArgumentException("Expected '\"' at $i")
        i++
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (i >= s.length) throw IllegalArgumentException("Unterminated escape")
                    when (val esc = s[i++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = s.substring(i, minOf(i + 4, s.length))
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> sb.append(esc)
                    }
                }
                else -> sb.append(c)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun parseNumber(): Number {
        val start = i
        while (i < s.length && s[i] in "-+0123456789.eE") i++
        val raw = s.substring(start, i)
        return raw.toLongOrNull() ?: raw.toDouble()
    }
}
