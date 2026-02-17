<p align="center">
  <img src="Assets/seamless_logo.png" width="300" alt="Seamless App Icon">
</p>

# Seamless

Seamless is an open-source utility for transferring files between Android, macOS, Windows, and Linux over a local network. It uses UDP for device discovery and TCP for file transmission without requiring external servers or internet access.

---

## Downloads

Pre-compiled binaries are available for all major platforms. You can download the latest version (v0.1.3) from the [Releases page](https://github.com/iman-zamani/seamless/releases).

| Platform | Available Formats | Download Link |
| :--- | :--- | :--- |
| **Android** | `.apk` | [Download for Android](https://github.com/iman-zamani/seamless/releases/latest) |
| **Windows** | `.exe` (x64) | [Download for Windows](https://github.com/iman-zamani/seamless/releases/latest) |
| **macOS** | `.dmg` (Apple Silicon & Intel) | [Download for macOS](https://github.com/iman-zamani/seamless/releases/latest) |
| **Linux** | `.deb`, `.rpm`, `.tar.gz` (x64/amd64) | [Download for Linux](https://github.com/iman-zamani/seamless/releases/latest) |

*Alternatively, you can run the application directly from source (see **Development Setup** below).*

---

## Usage

**Note:** The receiver must be in "Receive" mode before the sender begins scanning.

1.  **Network:** Connect both devices to the same Wi-Fi network.
2.  **Receiver:** Open the application and select **Receive Files**.
3.  **Sender:**
      * Open the application and select **Send Files**.
      * Select the file(s) to transfer.
      * Click **Scan Network**.
      * Select the target device from the list to initiate transfer.

---

## Features

  * **Local Network Only:** Transfers occur strictly over LAN; no data leaves the local subnet.
  * **Discovery:** Uses UDP broadcasting to locate devices without manual IP entry.
  * **Transport:** Uses raw TCP sockets with 64KB buffers for data streaming.
  * **Cross-Platform:** Python/CustomTkinter (Desktop) and Kotlin (Android) clients are fully interoperable.

---

## Troubleshooting

If the connection fails, verify the following:

  * **Network Isolation:** Ensure both devices are on the exact same SSID. Guest networks often isolate clients.
  * **VPN:** Disable VPNs on both devices, as they route traffic away from the local network.
  * **Firewall:**
      * **Windows:** Allow the application (or `python.exe` if running from source) through the firewall.
      * **macOS:** Grant permissions in **System Settings > Privacy & Security > Local Network**.

### Installation on macOS

**Note:** This app is not signed with an Apple Developer Certificate. Because of this, macOS Gatekeeper may block the app from opening the first time.

**If you see "App is damaged" or "Can't be opened":**

**For macOS Sequoia (15) and newer:**
1. Open the app. If you see a "Move to Trash" or "Cancel" popup, click **Done** or **Cancel** (do not move to trash).
2. Go to **System Settings** > **Privacy & Security**.
3. Scroll to the bottom to the **Security** section.
4. You will see a message: *"seamless.app was blocked..."*. Click **Open Anyway**.
5. Enter your password and click **Open**. 
   *(You only need to do this once. The app will open normally from now on.)*

**For older macOS versions:**
1. **Right-click** (or Control+click) the `seamless.app` icon.
2. Select **Open** from the menu.
3. Click **Open** in the popup warning.

**Terminal Solution:**
If the above does not work, you can manually whitelist the app using your terminal:
```bash
xattr -d com.apple.quarantine /Applications/seamless.app

```

### Installation on Windows

**Note:** Because the Windows `.exe` is not currently signed with an Extended Validation (EV) certificate, Windows Defender SmartScreen might flag it when you first run it.

* If you see a blue "Windows protected your PC" popup, click **More info**.
* Click **Run anyway** to launch the application.

---

## Development Setup

### Tech Stack

* **Desktop:** Python 3.x, CustomTkinter, Native Sockets.
* **Android:** Kotlin, Coroutines (min SDK: Android 7.0).

### Desktop (Windows / Linux / macOS)

Requires Python 3.8+.

```bash
git clone [https://github.com/iman-zamani/seamless.git](https://github.com/iman-zamani/seamless.git)
cd seamless
pip install customtkinter
python seamless.py

```

### Android

Open the `Android/` directory in **Android Studio**. Sync Gradle files and run on a connected device or emulator.

### Protocol Overview

* **Discovery:** UDP Broadcast on port `5000`. Payload: `HERE:Username`.
* **Transfer:** TCP Stream on port `5001`.
* **Header:** `filename<SEPARATOR>filesize\n`
* **Body:** Binary file data.



---

## License

Licensed under the **GPL (General Public License)**. See `LICENSE` for details.
