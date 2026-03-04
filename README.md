# SAF Plugin for ZArchiver (Unofficial)

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)
![Release Workflow](https://github.com/Eutalix/saf-bridge-zarchiver/actions/workflows/release.yml/badge.svg?style=for-the-badge&logo=github)
![Downloads](https://img.shields.io/github/downloads/Eutalix/saf-bridge-zarchiver/total?label=Downloads&logo=github&style=for-the-badge)

A bridge application that connects Android's **Storage Access Framework (SAF)** to **ZArchiver**.

**The Main Goal:** This tool allows you to map specific internal directories of other applications—such as **Termux**, **Acode**, or **Git Clients**—and access them directly within ZArchiver as virtual roots. This solves the isolation problem where ZArchiver cannot natively browse or "mount" other Document Providers easily.

> **⚠️ DISCLAIMER:** This is an unofficial plugin. I am not affiliated, associated, authorized, endorsed by, or in any way officially connected with **ZDevs** (the developers of ZArchiver).

---

## 📱 Screenshots

| 1. Add Folders | 2. Plugin Manager | 3. Access in ZArchiver |
|:---:|:---:|:---:|
| <img src="screenshots/home-mapped.jpg" width="250" /> | <img src="screenshots/zarchiver-settings.jpg" width="250" /> | <img src="screenshots/zarchiver-dropdown.png" width="250" /> |
| *Map your Termux or local folders* | *Quick access via ZArchiver settings* | *Browse files seamlessly* |

---

## 🚀 Features

*   **Inter-App Connectivity:** Easily mount directories from apps that expose a DocumentProvider (e.g., Termux `$HOME`).
*   **Full File Operations:** Support for Copy, Move, Rename, Delete, and Create Folder within these virtual roots.
*   **Modern UI:** Built entirely with **Jetpack Compose** and Material 3.
*   **Security:** Built-in safeguards to prevent file corruption when using the Free version of ZArchiver.

---

## ⚠️ Important: Free vs. Pro Version

During the development and reverse engineering of this plugin, a crucial behavior in ZArchiver was identified:

*   **ZArchiver Pro:** Fully compatible. You can read, write, and edit text files inside the mapped folders perfectly.
*   **ZArchiver Free:** The free version has internal limitations regarding external file editing.
    *   *Read/Copy/Create:* Works perfectly.
    *   *Edit Existing Files:* **Blocked by this plugin.** 
    
    **Reason:** The Free version does not handle the edit-stream correctly for external plugins, which results in files being saved as empty (0-byte). To protect your data (e.g., your code in Termux), this plugin automatically detects the Free version and prevents overwriting existing files.

---

## 🛠️ Engineering Deep Dive

This project was built by reverse-engineering the proprietary **ZArchiver Cloud Plugin Protocol**. Since there is no public documentation, the implementation relied on analyzing `smali` code and log behavior.

### Key Discoveries & Implementation Details:

1.  **The Handshake Protocol:**
    *   ZArchiver discovers the plugin via a specific Intent Action: `ru.zdevs.zarchiver.plugin.PLUGIN_APPLICATION`.
    *   It queries the ContentProvider with `selection="get=accounts"` to list the roots.
    *   We map `SharedPreferences` keys (SAF URIs) to this query response.

2.  **Custom Write Modes:**
    *   **Discovery:** ZArchiver sends non-standard file modes like `"w2"` or `"w4"` during write operations.
    *   **Solution:** The plugin sanitizes these modes to `"wt"` (Write Truncate) before passing them to the system, ensuring files are cleanly overwritten rather than corrupted.

3.  **Rename Logic (POSIX vs SAF):**
    *   Android's SAF `renameDocument` fails if the destination filename already exists. ZArchiver expects standard POSIX behavior (overwrite).
    *   **Solution:** The plugin's `call("rename")` method proactively detects collisions, deletes the destination file, and *then* performs the rename.

---

## 💻 The "No-IDE" Development Story

This project is unique because **it was developed entirely on a smartphone.** 📱

*   **Environment:** Coded on an Android device using **Termux** and text editors (like Acode).
*   **The Problem:** I needed a way to manage my project files easily with ZArchiver, but SAF restrictions made it difficult.
*   **The Solution:** I built this plugin *on* the phone, *for* the phone.
*   **CI/CD:** Without Android Studio on the device, I relied 100% on **GitHub Actions** to compile the APKs. Every commit triggered a cloud build to verify the code logic and catch errors.

---

## 📥 Installation

1.  Download the latest APK from the [Releases](../../releases) page.
2.  Install the **SAF Plugin** app.
3.  Open the app and click the **(+)** button.
4.  Select the folder you want to map (e.g., open the drawer and select **Termux**).
5.  Open **ZArchiver**.
6.  Tap the storage dropdown menu (top bar).
7.  **Done!** Your mapped folders will appear there automatically.

> *Note: You can also manage your folders by going to ZArchiver Settings > Plugin > SAF Plugin.*

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.
