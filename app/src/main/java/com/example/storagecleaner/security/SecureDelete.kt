package com.example.storagecleaner.security

import android.content.Context
import android.media.MediaScannerConnection
import com.example.storagecleaner.data.covered
import com.example.storagecleaner.data.isSystemPath
import com.example.storagecleaner.data.parentOf
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom

/**
 * DoD 5220.22-M 3-pass secure wipe:
 *  Pass 1 – overwrite with 0x00
 *  Pass 2 – overwrite with 0xFF
 *  Pass 3 – cryptographically random bytes (SecureRandom)
 *
 * Falls back to standard OS delete if any pass fails (e.g. file already
 * locked or on a file-system that doesn't support in-place writes).
 *
 * Note: on modern flash/eMMC the OS routes writes through wear-levelling
 * (FTL), so physical recovery is extremely unlikely but cannot be 100%
 * ruled out at the hardware level.  This is the best achievable from
 * Android user-space.
 */
object SecureDelete {

    private val rng = SecureRandom()

    enum class Mode {
        /** 3-pass DoD overwrite + delete */
        SECURE_WIPE,
        /** Plain OS delete — no overwriting, fastest */
        STANDARD_DELETE
    }

    data class DeleteResult(
        val deleted: Int,
        val failed: Int,
        val freedBytes: Long,
        val secureWiped: Int,     // how many got the full 3-pass wipe
        val standardDeleted: Int  // how many fell back to plain delete
    )

    // ─── Low-level file wipe ─────────────────────────────────────────────
    /**
     * Returns true if fully secure-wiped, false if fell back to plain delete.
     */
    private fun wipeFile(f: File): Boolean {
        val len = f.length()
        if (len <= 0L) { f.delete(); return true }

        var wiped = false
        try {
            RandomAccessFile(f, "rw").use { raf ->
                val buf = ByteArray(minOf(65_536L, len).toInt())

                fun overwriteWith(fill: Byte? = null) {
                    raf.seek(0); var rem = len
                    while (rem > 0) {
                        val chunk = minOf(buf.size.toLong(), rem).toInt()
                        if (fill != null) buf.fill(fill) else rng.nextBytes(buf)
                        raf.write(buf, 0, chunk); rem -= chunk
                    }
                    raf.fd.sync()
                }

                overwriteWith(0x00.toByte())   // Pass 1 – zeros
                overwriteWith(0xFF.toByte())   // Pass 2 – ones
                overwriteWith()                // Pass 3 – random
                wiped = true
            }
        } catch (_: Exception) { /* will fall through to plain delete */ }

        f.delete()
        return wiped
    }

    private fun wipeDir(dir: File): Boolean {
        var allWiped = true
        dir.walkBottomUp().forEach { f ->
            if (f.isFile) { if (!wipeFile(f)) allWiped = false }
            else f.delete()
        }
        return allWiped
    }

    private fun deleteFileStandard(f: File): Boolean =
        try { if (f.isDirectory) f.deleteRecursively() else f.delete() } catch (_: Exception) { false }

    // ─── Public API ───────────────────────────────────────────────────────
    /**
     * Delete [targets] with the chosen [mode].
     * - Secure wipe falls back to standard delete automatically if wipe fails.
     * - System-protected paths are always skipped.
     */
    fun deleteAll(
        context: Context,
        targets: List<String>,
        dirSizes: Map<String, Long>,
        mode: Mode = Mode.SECURE_WIPE,
        onProgress: (String) -> Unit
    ): DeleteResult {
        val topLevel = targets.filter { !covered(parentOf(it), targets.toSet()) }
        var deleted = 0; var failed = 0; var freed = 0L
        var secureWiped = 0; var standardDeleted = 0

        for (path in topLevel) {
            if (isSystemPath(path)) { failed++; continue }

            val f  = File(path)
            val sz = if (f.isDirectory) (dirSizes[path] ?: 0L) else f.length()

            onProgress(if (mode == Mode.SECURE_WIPE) "🔒 Wiping ${f.name}…" else "🗑️ Deleting ${f.name}…")

            val (ok, wasSecure) = when (mode) {
                Mode.SECURE_WIPE -> {
                    val wiped = try {
                        if (f.isDirectory) wipeDir(f) else wipeFile(f)
                    } catch (_: Exception) { false }
                    // If wipe left the file, attempt plain delete as last resort
                    val finallyGone = !File(path).exists() ||
                        try { deleteFileStandard(File(path)) } catch (_: Exception) { false }
                    Pair(finallyGone, wiped && finallyGone)
                }
                Mode.STANDARD_DELETE -> {
                    val gone = try { deleteFileStandard(f) } catch (_: Exception) { false }
                    Pair(gone, false)
                }
            }

            if (ok) {
                deleted++; freed += sz
                if (wasSecure) secureWiped++ else standardDeleted++
            } else {
                failed++
            }
        }

        // Notify MediaStore that these paths are gone
        try {
            MediaScannerConnection.scanFile(context, topLevel.toTypedArray(), null, null)
        } catch (_: Exception) { }

        return DeleteResult(deleted, failed, freed, secureWiped, standardDeleted)
    }
}
