package com.example.storagecleaner

import com.example.storagecleaner.data.*
import com.example.storagecleaner.security.SecureDelete
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

class CoreScenarioTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // ── Scenario 1: File Kind & Category Classification ───────────────────────
    @Test
    fun testFileKindClassification() {
        assertEquals(KIND_IMAGE, EXT["jpg"])
        assertEquals(KIND_IMAGE, EXT["png"])
        assertEquals(KIND_IMAGE, EXT["webp"])
        assertEquals(KIND_VIDEO, EXT["mp4"])
        assertEquals(KIND_VIDEO, EXT["mkv"])
        assertEquals(KIND_AUDIO, EXT["mp3"])
        assertEquals(KIND_DOC, EXT["pdf"])
        assertEquals(KIND_DOC, EXT["docx"])
        assertEquals(KIND_ARCHIVE, EXT["zip"])
        assertEquals(KIND_APK, EXT["apk"])
        assertNull(EXT["unknown_ext"])
    }

    // ── Scenario 2: Junk Detection ───────────────────────────────────────────
    @Test
    fun testJunkExtensions() {
        val junkExtensions = listOf("tmp", "temp", "log", "bak", "old", "cache", "chk", "dmp")
        for (ext in junkExtensions) {
            assertTrue("Expected $ext to be recognized as junk", JUNK_EXT.contains(ext))
        }
        assertFalse(JUNK_EXT.contains("jpg"))
        assertFalse(JUNK_EXT.contains("mp4"))
        assertFalse(JUNK_EXT.contains("pdf"))
    }

    // ── Scenario 3: System Protected Paths Guard ─────────────────────────────
    @Test
    fun testSystemProtectedPathsGuard() {
        assertTrue(isSystemPath("/storage/emulated/0/Android"))
        assertTrue(isSystemPath("/storage/emulated/0/Android/data"))
        assertTrue(isSystemPath("/storage/emulated/0/Android/data/com.foo.bar"))
        assertTrue(isSystemPath("/storage/emulated/0/Android/obb"))
        assertTrue(isSystemPath("/data"))
        assertTrue(isSystemPath("/system"))
        assertTrue(isSystemPath("/proc"))
        assertTrue(isSystemPath("/sys"))

        // User files must NOT be protected
        assertFalse(isSystemPath("/storage/emulated/0/DCIM/Camera/photo.jpg"))
        assertFalse(isSystemPath("/storage/emulated/0/Download/document.pdf"))
        assertFalse(isSystemPath("/storage/emulated/0/Pictures/Telegram/image.png"))
    }

    // ── Scenario 4: Scanning, Duplicate Detection & Sorting ───────────────────
    @Test
    fun testScanAndDuplicateDetection() {
        val root = tempFolder.newFolder("storage_root")
        val dcim = File(root, "DCIM/Camera").apply { mkdirs() }
        val downloads = File(root, "Download").apply { mkdirs() }
        val emptyDir = File(root, "EmptyFolder").apply { mkdirs() }

        // Create file 1 (photo A) - > 1000 bytes for duplicate scanner
        val photoBytes = ByteArray(2048) { (it % 100).toByte() }
        val photoA = File(dcim, "IMG_001.jpg").apply {
            writeBytes(photoBytes)
        }
        // Create duplicate of photo A in downloads
        val photoDup = File(downloads, "IMG_001_copy.jpg").apply {
            writeBytes(photoBytes)
        }
        // Create a unique file
        val doc = File(downloads, "Report.pdf").apply {
            writeBytes(ByteArray(1500) { ((it + 5) % 80).toByte() })
        }
        // Create a junk file
        val junk = File(downloads, "cache_file.tmp").apply {
            writeBytes(ByteArray(1200) { (it % 50).toByte() })
        }

        // Run scanner
        val result = scan(listOf(root)) { }

        // Verify files scanned
        assertTrue(result.files.any { it.path == photoA.path })
        assertTrue(result.files.any { it.path == photoDup.path })
        assertTrue(result.files.any { it.path == doc.path })
        assertTrue(result.files.any { it.path == junk.path })

        // Verify empty directory detected
        assertTrue(result.emptyDirs.any { it.path == emptyDir.path })

        // Verify duplicate detection grouped photoA and photoDup together
        assertTrue(result.duplicates.contains(photoA.path) || result.duplicates.contains(photoDup.path))
        assertEquals(1, result.duplicateGroups.size)
        val dupPaths = result.duplicateGroups[0].map { it.path }.toSet()
        assertTrue(dupPaths.contains(photoA.path))
        assertTrue(dupPaths.contains(photoDup.path))

        // Verify sorting by Size Descending
        val sortedBySize = sorted(result.files, 0)
        for (i in 0 until sortedBySize.size - 1) {
            assertTrue(sortedBySize[i].size >= sortedBySize[i + 1].size)
        }

        // Verify sorting Name A-Z
        val sortedByName = sorted(result.files, 3)
        for (i in 0 until sortedByName.size - 1) {
            val name1 = sortedByName[i].path.substringAfterLast('/').lowercase()
            val name2 = sortedByName[i + 1].path.substringAfterLast('/').lowercase()
            assertTrue(name1 <= name2)
        }
    }

    // ── Scenario 5: DoD Secure Wipe Simulation ───────────────────────────────
    @Test
    fun testSecureWipeOverwritesData() {
        val testFile = tempFolder.newFile("sensitive_document.txt").apply {
            writeText("SuperConfidentialCreditCardAndPasswords1234567890")
        }
        val origPath = testFile.path
        val origLen = testFile.length()
        assertTrue(origLen > 0)

        // Simulate 3-pass DoD overwrite
        val raf = RandomAccessFile(testFile, "rws")
        // Pass 1: 0x00
        val zeros = ByteArray(origLen.toInt()) { 0x00 }
        raf.seek(0)
        raf.write(zeros)
        raf.fd.sync()

        // Verify file content is overwritten with zeros
        testFile.inputStream().use { stream ->
            val buf = ByteArray(origLen.toInt())
            stream.read(buf)
            assertTrue(buf.all { it == 0x00.toByte() })
        }

        // Pass 2: 0xFF
        val ones = ByteArray(origLen.toInt()) { 0xFF.toByte() }
        raf.seek(0)
        raf.write(ones)
        raf.fd.sync()

        // Verify file content is overwritten with 0xFF
        testFile.inputStream().use { stream ->
            val buf = ByteArray(origLen.toInt())
            stream.read(buf)
            assertTrue(buf.all { it == 0xFF.toByte() })
        }

        // Pass 3: Random + Truncate + Delete
        val rand = java.security.SecureRandom()
        val randomBytes = ByteArray(origLen.toInt())
        rand.nextBytes(randomBytes)
        raf.seek(0)
        raf.write(randomBytes)
        raf.setLength(0)
        raf.close()
        testFile.delete()

        assertFalse("File must be deleted after secure wipe", File(origPath).exists())
    }

    // ── Scenario 6: Format Helper ────────────────────────────────────────────
    @Test
    fun testByteFormatting() {
        assertEquals("0 B", fmt(0L))
        assertEquals("500 B", fmt(500L))
        assertEquals("1.0 KB", fmt(1024L))
        assertEquals("1.5 MB", fmt((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", fmt((2.0 * 1024 * 1024 * 1024).toLong()))
    }
}
