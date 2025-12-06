# Seamless

**Seamless** is a modern, high-performance file transfer tool designed to bridge the gap between mobile and desktop ecosystems. It eliminates the need for cables, cloud uploads, or accounts by allowing instant, unlimited file sharing over your local Wi-Fi network.

Whether you are on **macOS, Windows, Linux, or Android**, Seamless connects your devices automatically.

-----

## Features

  * **Universal Compatibility:** Connect Android to Mac, Windows to Linux, or Android to Android.
  * **Zero Configuration:** Devices automatically discover each other using UDP broadcasting. No IP typing required.
  * **High Speed:** Uses raw TCP sockets for maximum transfer speed, limited only by your router.
  * **Large File Support:** optimized buffer sizes (64KB) ensure stability when sending GBs of data.
  * **Modern UI:** Features a sleek, dark-mode interface using `CustomTkinter` (Desktop) and Material Design (Android).

-----

## Tech Stack

### Desktop (Windows / macOS / Linux)

  * **Language:** Python 3.x
  * **GUI Framework:** [CustomTkinter](https://github.com/TomSchimansky/CustomTkinter)
  * **Networking:** Native `socket` (Threading for asynchronous UI)

### Mobile (Android)

  * **Language:** Kotlin
  * **Concurrency:** Kotlin Coroutines (`Dispatchers.IO`)
  * **Networking:** `java.net.Socket` / `DatagramPacket` with Multicast Lock support.

-----

## Getting Started

### Prerequisites

  * **Desktop:** Python 3.8 or higher installed.
  * **Android:** Android 7.0 (Nougat) or higher.
  * **Network:** Both devices must be connected to the **same Wi-Fi network**.

### 1\. Running the Desktop Client

The desktop version is a single Python script that runs on any OS.

1.  **Clone the repository:**

    ```bash
    git clone https://github.com/iman-zamani/seamless.git
    cd seamless
    ```

2.  **Install dependencies:**

    ```bash
    pip install customtkinter
    ```

3.  **Run the application:**

    ```bash
    python Desktop_seamless.py
    ```

### 2\. Running the Android App

1.  Open the `Android/` folder in **Android Studio**.
2.  Sync Gradle files.
3.  Connect your Android device via USB (or use an emulator).
4.  Click **Run**.
5.  *Note: Grant Storage permissions if prompted upon first launch.*

-----

##  Usage Guide

### Sending Files

1.  Open Seamless on both devices.
2.  On the **Sender**, click **"Send Files"** and select your file(s).
3.  On the **Receiver**, click **"Receive Files"**.
4.  The Sender will click **"Scan Network"**.
5.  Select the Receiver's name from the list. The transfer begins immediately.

### Receiving Files

1.  Click **"Receive Files"**.
2.  The app will broadcast your username to the network.
3.  Wait for the incoming connection.
4.  **Desktop:** Files are saved to a `received_files` folder in the app directory.
5.  **Android:** Files are saved to your system **Downloads** folder.

-----

## Troubleshooting

If devices cannot find each other:

1.  **Check Wi-Fi:** Ensure both devices are on the exact same SSID (Frequency 2.4GHz vs 5GHz usually doesn't matter, but "Guest" networks do).
2.  **Disable VPN:** VPNs tunnel traffic away from your local network. Turn them off.
3.  **Windows Firewall:** If using Windows, allow `python.exe` access to Public/Private networks when prompted.
4.  **macOS Permissions:**
      * Go to **System Settings \> Privacy & Security \> Local Network**.
      * Ensure your Terminal or IDE (VS Code/PyCharm) has permission enabled.

-----

## Protocol Details

For developers interested in the networking logic:

  * **Discovery (Port 5000/UDP):**
      * Devices listen on `0.0.0.0:5000`.
      * Broadcaster calculates Subnet Broadcast Address (e.g., `192.168.1.255`) and sends `HERE:Username`.
  * **Transfer (Port 5001/TCP):**
      * Header: `filename<SEPARATOR>filesize\n`
      * Body: Raw bytes streamed in 64KB chunks.

