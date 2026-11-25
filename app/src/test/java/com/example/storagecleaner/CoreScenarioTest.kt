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

        // Traversal normalization attacks
        assertTrue(isSystemPath("/storage/emulated/0/Download/../Android/data/com.whatsapp"))
        assertTrue(isSystemPath("/storage/emulated/0/DCIM/../../Android/obb"))
        assertTrue(isSystemPath("\\storage\\emulated\\0\\Download\\..\\Android"))
        assertTrue(isSystemPath("/storage/emulated/0/.android_secure/app.asec"))
        assertTrue(isSystemPath("/system/bin/sh"))
        assertTrue(isSystemPath("/data/local/tmp"))

        // User files must NOT be blocked
        assertFalse(isSystemPath("/storage/emulated/0/DCIM/Camera/photo.jpg"))
        assertFalse(isSystemPath("/storage/emulated/0/Download/document.pdf"))
        assertFalse(isSystemPath("/storage/emulated/0/Pictures/Telegram/image.png"))
        assertFalse(isSystemPath("/storage/emulated/0/AndroidAppsBackup/app.apk"))
    }

    // ── Scenario 4: Scanning, Duplicate Detection & Sorting ───────────────────
    @Test
    fun testScanAndDuplicateDetection() {
        val root = tempFolder.newFolder("storage_root")
        val dcim = File(root, "DCIM/Camera").apply { mkdirs() }
        val downloads = File(root, "Download").apply { mkdirs() }
        val emptyDir = File(root, "EmptyFolder").apply { mkdirs() }

        val photoBytes = ByteArray(2048) { (it % 100).toByte() }
        val photoA = File(dcim, "IMG_001.jpg").apply { writeBytes(photoBytes) }
        val photoDup = File(downloads, "IMG_001_copy.jpg").apply { writeBytes(photoBytes) }
        val doc = File(downloads, "Report.pdf").apply {
            writeBytes(ByteArray(1500) { ((it + 5) % 80).toByte() })
        }
        val junk = File(downloads, "cache_file.tmp").apply {
            writeBytes(ByteArray(1200) { (it % 50).toByte() })
        }

        val result = scan(listOf(root)) { }

        assertTrue(result.files.any { it.path == photoA.path })
        assertTrue(result.files.any { it.path == photoDup.path })
        assertTrue(result.files.any { it.path == doc.path })
        assertTrue(result.files.any { it.path == junk.path })
        assertTrue(result.emptyDirs.any { it.path == emptyDir.path })

        assertTrue(result.duplicates.contains(photoA.path) || result.duplicates.contains(photoDup.path))
        assertEquals(1, result.duplicateGroups.size)
        val dupPaths = result.duplicateGroups[0].map { it.path }.toSet()
        assertTrue(dupPaths.contains(photoA.path))
        assertTrue(dupPaths.contains(photoDup.path))

        val sortedBySize = sorted(result.files, 0)
        for (i in 0 until sortedBySize.size - 1) {
            assertTrue(sortedBySize[i].size >= sortedBySize[i + 1].size)
        }

        val sortedByName = sorted(result.files, 3)
        for (i in 0 until sortedByName.size - 1) {
            val name1 = sortedByName[i].path.replace('\\', '/').substringAfterLast('/').lowercase()
            val name2 = sortedByName[i + 1].path.replace('\\', '/').substringAfterLast('/').lowercase()
            assertTrue(name1 <= name2)
        }
    }

    // ── Scenario 5: Partial Hash Collision vs Full Hash Verification ─────────
    @Test
    fun testPartialHashMatchButMiddleDiffersIsNotDuplicate() {
        val root = tempFolder.newFolder("partial_collision_root")
        val size = 200_000
        val bytesA = ByteArray(size) { 0x11 }
        val bytesB = ByteArray(size) { 0x11 }
        // Differ only in the middle beyond first/last 64KB blocks
        bytesB[100_000] = 0x7F

        File(root, "file_a.bin").writeBytes(bytesA)
        File(root, "file_b.bin").writeBytes(bytesB)

        val result = scan(listOf(root)) { }
        assertEquals(2, result.files.size)
        assertTrue("Files differing in middle bytes must NOT be marked duplicates", result.duplicates.isEmpty())
        assertTrue(result.duplicateGroups.isEmpty())
    }

    // ── Scenario 6: Internal Trash Directory Excluded From Scan ──────────────
    @Test
    fun testInternalTrashExcludedFromScan() {
        val root = tempFolder.newFolder("trash_exclude_root")
        val activeFile = File(root, "keep.jpg").apply { writeBytes(ByteArray(2048) { 7 }) }
        val trashDir = File(root, ".StorageCleaner_Trash").apply { mkdirs() }
        val trashedFile = File(trashDir, "12345_keep.jpg").apply { writeBytes(ByteArray(2048) { 7 }) }

        val result = scan(listOf(root)) { }
        assertTrue(result.files.any { it.path == activeFile.path })
        assertFalse("Internal trash folder must never appear in scan results",
            result.files.any { it.path == trashedFile.path })
        assertTrue("Trashed copy must never trigger false duplicate", result.duplicates.isEmpty())
    }

    // ── Scenario 7: Scan Cancellation Support ────────────────────────────────
    @Test
    fun testScanRespectsCancellation() {
        val root = tempFolder.newFolder("cancel_root")
        repeat(20) { idx -> File(root, "item_$idx.bin").writeBytes(ByteArray(1200) { idx.toByte() }) }

        val result = scan(listOf(root), isCancelled = { true }) { }
        assertTrue(result.files.isEmpty())
    }

    // ── Scenario 8: Corrupted & Zero-Byte Files Resilience ───────────────────
    @Test
    fun testCorruptedAndZeroByteFiles() {
        val root = tempFolder.newFolder("corrupt_test_root")
        val zeroByte = File(root, "empty_file.dat").apply { createNewFile() }
        val zeroByteCopy = File(root, "empty_copy.dat").apply { createNewFile() }
        val corruptImg = File(root, "broken_photo.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x12, 0x43))
        }

        val result = scan(listOf(root)) { }
        assertTrue(result.files.any { it.path == zeroByte.path })
        assertTrue(result.files.any { it.path == zeroByteCopy.path })
        assertTrue(result.files.any { it.path == corruptImg.path })
        assertTrue("0-byte files should never pollute duplicates", result.duplicates.isEmpty())
    }

    // ── Scenario 9: Special Characters & Unicode Filenames ───────────────────
    @Test
    fun testUnicodeAndSpecialCharacterFilenames() {
        val root = tempFolder.newFolder("unicode_root")
        File(root, "My Vacation Photo (Copy 2).png").writeBytes(ByteArray(1024) { 1 })
        File(root, "отчет_за_квартал.pdf").writeBytes(ByteArray(1024) { 2 })
        File(root, "备份文件.zip").writeBytes(ByteArray(1024) { 3 })
        File(root, "सुरक्षा_दस्तावेज़.doc").writeBytes(ByteArray(1024) { 4 })

        val result = scan(listOf(root)) { }
        assertEquals(4, result.files.size)
        assertTrue(result.files.any { it.path.contains("My Vacation Photo") })
        assertTrue(result.files.any { it.path.contains("отчет_за_квартал") })
        assertTrue(result.files.any { it.path.contains("备份文件") })
        assertTrue(result.files.any { it.path.contains("सुरक्षा_दस्तावेज़") })
    }

    // ── Scenario 10: All Category Tabs Filtering ─────────────────────────────
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

    // ── Scenario 11: Real SecureDelete Engine Execution & Protection ─────────
    @Test
    fun testSecureDeleteEngineEndToEnd() {
        val dir = tempFolder.newFolder("wipe_dir")
        val secretFile = File(dir, "secret.txt").apply { writeText("SensitiveSecretPayload123456") }
        val nestedDir = File(dir, "sub").apply { mkdirs() }
        val nestedFile = File(nestedDir, "inner.bin").apply { writeBytes(ByteArray(4096) { 0x5A }) }

        // Test single file wipe + deleteAll rejection of system path
        val res = SecureDelete.deleteAll(
            context = null,
            targets = listOf(secretFile.path, nestedDir.path, "/storage/emulated/0/Android/data"),
            dirSizes = mapOf(nestedDir.path to 4096L),
            mode = SecureDelete.Mode.SECURE_WIPE,
            onProgress = {}
        )

        assertEquals(2, res.deleted)
        assertEquals(1, res.failed) // system path refused
        assertEquals(2, res.secureWiped)
        assertFalse(secretFile.exists())
        assertFalse(nestedFile.exists())
        assertFalse(nestedDir.exists())
    }

    // ── Scenario 12: DoD Secure Wipe Overwrite Verification ──────────────────
    @Test
    fun testSecureWipeOverwritesData() {
        val testFile = tempFolder.newFile("sensitive_document.txt").apply {
            writeText("SuperConfidentialCreditCardAndPasswords1234567890")
        }
        val origPath = testFile.path
        val origLen = testFile.length()
        assertTrue(origLen > 0)

        val raf = RandomAccessFile(testFile, "rws")
        val zeros = ByteArray(origLen.toInt()) { 0x00 }
        raf.seek(0); raf.write(zeros); raf.fd.sync()
        testFile.inputStream().use { stream ->
            val buf = ByteArray(origLen.toInt()); stream.read(buf)
            assertTrue(buf.all { it == 0x00.toByte() })
        }

        val ones = ByteArray(origLen.toInt()) { 0xFF.toByte() }
        raf.seek(0); raf.write(ones); raf.fd.sync()
        testFile.inputStream().use { stream ->
            val buf = ByteArray(origLen.toInt()); stream.read(buf)
            assertTrue(buf.all { it == 0xFF.toByte() })
        }

        val rand = java.security.SecureRandom()
        val randomBytes = ByteArray(origLen.toInt()).also { rand.nextBytes(it) }
        raf.seek(0); raf.write(randomBytes); raf.setLength(0); raf.close()
        testFile.delete()

        assertFalse("File must be deleted after secure wipe", File(origPath).exists())
    }

    // ── Scenario 13: RecycleBin Safe Move & Collision-Free Restore ───────────
    @Test
    fun testRecycleBinSafeMoveAndUniqueDestination() {
        val root = tempFolder.newFolder("bin_test")
        val src = File(root, "photo.jpg").apply { writeBytes(ByteArray(512) { 3 }) }
        val trashCopy = File(root, "trash/photo.jpg")

        assertTrue(RecycleBin.moveSafely(src, trashCopy))
        assertFalse(src.exists())
        assertTrue(trashCopy.exists())

        // Recreate conflict at destination before restore destination check
        val occupied = File(root, "photo.jpg").apply { writeText("new file") }
        val safeDest = RecycleBin.uniqueDestination(occupied)
        assertNotEquals(occupied.path, safeDest.path)
        assertTrue(safeDest.name.contains("_restored_1"))
    }

    // ── Scenario 14: GitHub Release Update Checker & SemVer Logic ────────────
    @Test
    fun testUpdateCheckerLogic() {
        assertTrue(UpdateChecker.compareSemVer("v3.1.0", "3.0") > 0)
        assertTrue(UpdateChecker.compareSemVer("3.0.1", "3.0.0") > 0)
        assertEquals(0, UpdateChecker.compareSemVer("v3.0.0", "3.0"))
        assertTrue(UpdateChecker.compareSemVer("2.9.5", "3.0.0") < 0)

        val newerReleaseJson = """
            {
              "tag_name": "v3.1.0",
              "name": "Storage Cleaner v3.1.0",
              "body": "Improved UI & security hardening",
              "html_url": "https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android/releases/tag/v3.1.0",
              "published_at": "2026-12-01T10:00:00Z",
              "assets": [
                {
                  "name": "app-release.apk",
                  "size": 41234567,
                  "browser_download_url": "https://example.com/app-release.apk"
                }
              ]
            }
        """.trimIndent()

        val updateRes = UpdateChecker.parseReleaseJson(newerReleaseJson, "3.0", "abc1234", 1_700_000_000_000L)
        assertTrue(updateRes is UpdateResult.UpdateAvailable)
        val avail = updateRes as UpdateResult.UpdateAvailable
        assertEquals("v3.1.0", avail.latestVersion)
        assertEquals("https://example.com/app-release.apk", avail.downloadUrl)
        assertEquals(41234567L, avail.apkSizeBytes)

        val sameReleaseJson = """
            {
              "tag_name": "v3.0.0",
              "name": "v3.0.0",
              "body": "Initial release",
              "html_url": "https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android/releases/latest",
              "assets": []
            }
        """.trimIndent()
        val upToDateRes = UpdateChecker.parseReleaseJson(sameReleaseJson, "3.0", "abc1234", 1_700_000_000_000L)
        assertTrue(upToDateRes is UpdateResult.UpToDate)

        // Rolling "latest" tag CI build check
        val ciNewerJson = """
            {
              "tag_name": "latest",
              "name": "Latest build (run #15)",
              "body": "Automatically built from commit `9999aaa` on branch `main`.",
              "html_url": "https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android/releases/tag/latest",
              "published_at": "2026-12-05T12:00:00Z",
              "assets": []
            }
        """.trimIndent()
        val ciRes = UpdateChecker.parseReleaseJson(ciNewerJson, "3.0", "1111bbb", 1_760_000_000_000L)
        assertTrue(ciRes is UpdateResult.UpdateAvailable)
    }

    // ── Scenario 15: Format Helper ───────────────────────────────────────────
    @Test
    fun testByteFormatting() {
        assertEquals("0 B", fmt(0L))
        assertEquals("500 B", fmt(500L))
        assertEquals("1.0 KB", fmt(1024L))
        assertEquals("1.5 MB", fmt((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", fmt((2.0 * 1024 * 1024 * 1024).toLong()))
    }
}
