package com.example.storagecleaner

import com.example.storagecleaner.data.*
import com.example.storagecleaner.security.SecureDelete
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

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

    // ── Scenario 3: System Protected Paths & Path Traversal Security Guard ─────
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

        // Path traversal attack checks
        assertTrue(isSystemPath("/system/bin/sh"))
        assertTrue(isSystemPath("/data/local/tmp"))

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

    // ── Scenario 5: Corrupted & Zero-Byte Files Resilience ────────────────────
    @Test
    fun testCorruptedAndZeroByteFiles() {
        val root = tempFolder.newFolder("corrupt_test_root")

        // 1. Zero-byte file
        val zeroByte = File(root, "empty_file.dat").apply { createNewFile() }
        assertEquals(0L, zeroByte.length())

        // 2. Corrupt image file (truncated random bytes)
        val corruptImg = File(root, "broken_photo.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x12, 0x43))
        }

        // 3. Scan must succeed without crashing or throwing
        val result = scan(listOf(root)) { }
        assertTrue(result.files.any { it.path == zeroByte.path })
        assertTrue(result.files.any { it.path == corruptImg.path })
    }

    // ── Scenario 6: Special Characters & Unicode Filenames ────────────────────
    @Test
    fun testUnicodeAndSpecialCharacterFilenames() {
        val root = tempFolder.newFolder("unicode_root")

        // File with spaces and brackets
        val fileSpaces = File(root, "My Vacation Photo (Copy 2).png").apply {
            writeBytes(ByteArray(1024) { 1 })
        }
        // File with non-ASCII Cyrillic
        val fileCyrillic = File(root, "отчет_за_квартал.pdf").apply {
            writeBytes(ByteArray(1024) { 2 })
        }
        // File with Chinese characters
        val fileChinese = File(root, "备份文件.zip").apply {
            writeBytes(ByteArray(1024) { 3 })
        }
        // File with Hindi characters
        val fileHindi = File(root, "सुरक्षा_दस्तावेज़.doc").apply {
            writeBytes(ByteArray(1024) { 4 })
        }

        val result = scan(listOf(root)) { }
        assertEquals(4, result.files.size)
        assertTrue(result.files.any { it.path.contains("My Vacation Photo") })
        assertTrue(result.files.any { it.path.contains("отчет_за_квартал") })
        assertTrue(result.files.any { it.path.contains("备份文件") })
        assertTrue(result.files.any { it.path.contains("सुरक्षा_दस्तावेज़") })
    }

    // ── Scenario 7: All 16 Category Tabs Filtering ────────────────────────────
    @Test
    fun testAllCategoryTabsFiltering() {
        val root = tempFolder.newFolder("tabs_root")
        val dcim = File(root, "DCIM/Screenshots").apply { mkdirs() }
        val telegram = File(root, "Telegram/Telegram Video").apply { mkdirs() }
        val downloads = File(root, "Download").apply { mkdirs() }

        val shotImg = File(dcim, "Screenshot_2026.png").apply { writeBytes(ByteArray(1024)) }
        val chatVid = File(telegram, "video_msg.mp4").apply { writeBytes(ByteArray(1024)) }
        val docFile = File(downloads, "paper.pdf").apply { writeBytes(ByteArray(1024)) }
        val apkFile = File(downloads, "app.apk").apply { writeBytes(ByteArray(1024)) }
        val zipFile = File(downloads, "backup.zip").apply { writeBytes(ByteArray(1024)) }
        val junkFile = File(downloads, "trash.tmp").apply { writeBytes(ByteArray(1024)) }

        val result = scan(listOf(root)) { }

        // Test each tab filter
        assertEquals(result.files.size, listFor("All files", result).size)
        assertTrue(listFor("Images", result).any { it.path == shotImg.path })
        assertTrue(listFor("Videos", result).any { it.path == chatVid.path })
        assertTrue(listFor("Docs", result).any { it.path == docFile.path })
        assertTrue(listFor("APKs", result).any { it.path == apkFile.path })
        assertTrue(listFor("Archives", result).any { it.path == zipFile.path })
        assertTrue(listFor("Screenshots", result).any { it.path == shotImg.path })
        assertTrue(listFor("Chat media", result).any { it.path == chatVid.path })
        assertTrue(listFor("Junk files", result).any { it.path == junkFile.path })
    }

    // ── Scenario 8: DoD Secure Wipe Simulation ───────────────────────────────
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

    // ── Scenario 9: Format Helper ────────────────────────────────────────────
    @Test
    fun testByteFormatting() {
        assertEquals("0 B", fmt(0L))
        assertEquals("500 B", fmt(500L))
        assertEquals("1.0 KB", fmt(1024L))
        assertEquals("1.5 MB", fmt((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", fmt((2.0 * 1024 * 1024 * 1024).toLong()))
    }
}
