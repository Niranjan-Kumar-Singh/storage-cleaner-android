# Storage Cleaner 🧹🔒

> **Free up storage. Permanently. Securely.**

A professional, open-source Android file manager & cleaner designed for maximum storage reclamation and privacy. Scan your entire device, preview images and media before deletion, compare duplicate files side-by-side, analyze storage categories with an interactive donut chart, and permanently delete files using a **DoD 5220.22-M 3-pass secure wipe** — making recovery impossible with standard recovery tools.

---

## 📱 Download

| Build | Link |
|-------|------|
| **Release APK** | [⬇️ Download Latest APK](https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android/releases/latest) |
| Build status | [![Build & Release APK](https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android/actions/workflows/build.yml/badge.svg)](https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android/actions/workflows/build.yml) |

---

## ✨ Key Features

### 🗂️ Deep Storage Scanner
- Scans **all storage volumes** (Internal phone storage + external SD cards).
- Detects hidden files (`.` prefix), `.nomedia` folders, thumbnails (`.thumbdata*`), caches, and `.trash`.
- Covers **WhatsApp, Telegram, Signal, GBWhatsApp** media directories, screenshots, downloads, and app-generated residual files.
- **System Path Guard**: Strictly protects core Android system directories (`/Android/data`, `/system`, `/proc`, `/sys`, `/dev`) from accidental modification.

### 🖼️ Inline Previews & Category Browsing
- **Inline image & video thumbnails** for instant visual inspection before selection.
- **16 dedicated category tabs**:
  Folders, All files, Large files (50MB+), Duplicates, Images, Videos, Audio, Docs, Archives, APKs, Downloads, Screenshots, Chat media, Junk files, Old files (1+ year), and Empty folders.
- Instant search filter + multiple sorting orders (Size ↓, Newest, Oldest, Name A-Z).
- Visual tag chips: `JUNK`, `DUP`, `CHAT`, `SHOT`, `DL`, `OLD`.

### 👥 Duplicate Finder with Side-by-Side Comparison
- Double-hash verification (quick SHA-256 header/tail check + full SHA-256 verification) for zero false-positives.
- Tap any duplicate in the Duplicates tab to open the **Duplicate Compare Sheet**:
  - Compare the original file (oldest) and duplicate side-by-side with preview, size, modified date, and path.
  - Choose with one tap: Keep original / Delete duplicate, or Keep duplicate / Delete original.

### 📊 Storage Breakdown Chart
- Interactive animated donut/pie chart right on the dashboard.
- Clear visual breakdown of storage usage across all 7 file types (Images, Videos, Audio, Documents, Archives, APKs, Other).

### 🗑️ Flexible 3-Way Deletion System
When selecting files, the persistent bottom action bar provides three distinct operations:

| Mode | Action | Behavior |
|------|--------|----------|
| **Trash** | Soft delete | Moves files to the in-app **Recycle Bin** (`.StorageCleaner_Trash`). Files can be restored at any time or wiped later. |
| **Delete** | Standard delete | Fast OS deletion. Useful for non-sensitive large files. |
| **Wipe** | DoD 3-Pass Secure Wipe | Unrecoverable permanent wipe using 3-pass cryptographic overwrite. |

### 🔒 DoD 5220.22-M Secure Wipe Details
Standard phone deletion only dereferences pointers; recovery software can reconstruct the data. Storage Cleaner overwrites every byte **3 times** prior to removal:
- **Pass 1**: Overwrite with `0x00` (zeros)
- **Pass 2**: Overwrite with `0xFF` (ones)
- **Pass 3**: Overwrite with cryptographically random bytes via `SecureRandom`
- Synchronizes changes to underlying flash hardware via `fsync()` before invoking file deletion.
- Automatic fallback: If secure in-place writing is blocked by a filesystem lock, it automatically completes standard removal.

### 🌓 Dark, Light & Auto System Theme
- Full Material3 design system with customized **Navy Blue (#1A237E)** & **Cyan (#00BCD4)** accents.
- Seamless one-tap toggle between **Auto (System Default)**, **Light Mode**, and **OLED-friendly Dark Mode**.

### ⏰ Scheduled Scan Reminders
- Schedule automated notifications (Every 7 days, 14 days, 30 days, or Off).
- Reminds you to clean junk, screenshots, and chat media to keep your storage healthy.
- Full support for Android 13+ notification permissions (`POST_NOTIFICATIONS`).

### 📦 App Manager & Cache Cleaner
- In-app list of all installed user & system applications.
- One-tap shortcut directly to each app's system storage settings to clear application cache and data.

---

## 🔐 Security & Privacy Architecture

- ✅ **Zero Internet Permission**: The app does NOT declare `android.permission.INTERNET`. No analytics, no ads, no telemetry, no remote servers.
- ✅ **No Background Spying**: Only standard alarm-based reminder notifications; no persistent background services.
- ✅ **Backup Shielding**: `android:allowBackup="false"` ensures sensitive state is never exported to cloud backups.
- ✅ **System Guard**: Hardcoded blacklists prevent modifying core OS components.

---

## 🛠️ Building & CI/CD

### Local Build
```bash
git clone https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android.git
cd storage-cleaner-android
gradle assembleDebug assembleRelease
```
APKs will be generated in `app/build/outputs/apk/`.

### Automated GitHub Actions CI/CD
Every push to `main` executes `.github/workflows/build.yml`:
1. Sets up JDK 17 & Gradle 8.7
2. Decodes release keystore from GitHub Secrets (if provided) or uses the build keystore
3. Compiles both **Debug** and **Release** ProGuard-minified APKs
4. Publishes both APKs automatically to [GitHub Releases](https://github.com/Niranjan-Kumar-Singh/storage-cleaner-android/releases/latest).

#### Configuring Release Keystore Secrets (Optional)
To sign release builds with your private signing key on GitHub Actions, add these repository secrets in **Settings → Secrets and variables → Actions**:
- `KEYSTORE_BASE64`: Base64-encoded string of your `.keystore` / `.jks` file (`base64 -w 0 your.keystore`)
- `RELEASE_STORE_PASSWORD`: Keystore password
- `RELEASE_KEY_ALIAS`: Key alias name
- `RELEASE_KEY_PASSWORD`: Key password

---

## 📋 Required Permissions

| Permission | Purpose |
|------------|---------|
| `MANAGE_EXTERNAL_STORAGE` | Android 11+ requirement to scan and delete files across the device |
| `READ_MEDIA_IMAGES/VIDEO/AUDIO` | Android 13+ granular media access for inline previews |
| `POST_NOTIFICATIONS` | Android 13+ permission for scan hygiene reminder notifications |
| `SCHEDULE_EXACT_ALARM` | Accurate reminder scheduling |

---

## 👨‍💻 Author

**Niranjan Kumar Singh**  
GitHub: [@Niranjan-Kumar-Singh](https://github.com/Niranjan-Kumar-Singh)

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
