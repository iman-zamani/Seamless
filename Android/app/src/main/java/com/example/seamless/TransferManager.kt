package com.example.seamless

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

object TransferManager {
    const val UDP_PORT = 5000
    const val TCP_PORT = 5001
    const val BUFFER_SIZE = 1024 * 64
    const val SEPARATOR = "<SEPARATOR>"

    var username = "User_${System.currentTimeMillis() % 10000}"
    val peers = MutableStateFlow<Map<String, String>>(emptyMap())

    // UI States
    val logs = MutableStateFlow<List<String>>(emptyList())
    val overallProgress = MutableStateFlow(0f)
    val fileProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val transferStatus = MutableStateFlow("Standing by...")

    private var udpSocket: DatagramSocket? = null
    private var tcpServerSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    var isServerRunning = false
    var cancelTransfer = false

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun scanNetwork(context: Context) {
        peers.value = emptyMap()
        scope.launch {
            try {
                val msg = "DISCOVER:$username".toByteArray()
                val socket = DatagramSocket()
                socket.broadcast = true
                val broadcastAddress = InetAddress.getByName("255.255.255.255")
                socket.send(DatagramPacket(msg, msg.size, broadcastAddress, UDP_PORT))
                socket.close()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun startUdpListener() {
        scope.launch {
            try {
                udpSocket = DatagramSocket(UDP_PORT).apply { reuseAddress = true }
                val buffer = ByteArray(1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket?.receive(packet)
                    val msg = String(packet.data, 0, packet.length)
                    val ip = packet.address.hostAddress ?: continue

                    if (msg.startsWith("HERE:")) {
                        val name = msg.substringAfter(":")
                        val current = peers.value.toMutableMap()
                        current[ip] = name
                        peers.value = current
                    } else if (msg.startsWith("DISCOVER") && isServerRunning) {
                        val reply = "HERE:$username".toByteArray()
                        DatagramSocket().apply {
                            send(DatagramPacket(reply, reply.size, packet.address, UDP_PORT))
                            close()
                        }
                    }
                }
            } catch (e: SocketException) { /* Socket closed */ }
        }
    }

    fun startBroadcasting() {
        scope.launch {
            while (isServerRunning) {
                try {
                    val msg = "HERE:$username".toByteArray()
                    val socket = DatagramSocket()
                    socket.broadcast = true
                    socket.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), UDP_PORT))
                    socket.close()
                    delay(2000)
                } catch (e: Exception) { break }
            }
        }
    }

    fun sendFiles(context: Context, targetIp: String, uris: List<Uri>, onComplete: () -> Unit, onError: (String) -> Unit) {
        cancelTransfer = false
        overallProgress.value = 0f
        fileProgress.value = uris.associate { getFileName(context, it) to 0f }

        scope.launch {
            try {
                var totalBytesToSend = 0L
                val fileDetails = uris.map { uri ->
                    val name = getFileName(context, uri)
                    val size = getFileSize(context, uri)
                    totalBytesToSend += size
                    Triple(uri, name, size)
                }

                var totalBytesSent = 0L

                for ((uri, name, size) in fileDetails) {
                    if (cancelTransfer) break

                    activeSocket = Socket(targetIp, TCP_PORT).apply { soTimeout = 10000 }
                    val outStream = activeSocket!!.getOutputStream()

                    val header = "$name$SEPARATOR$size\n".toByteArray()
                    outStream.write(header)

                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var fileBytesSent = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (cancelTransfer) break
                            outStream.write(buffer, 0, bytesRead)

                            fileBytesSent += bytesRead
                            totalBytesSent += bytesRead

                            fileProgress.value = fileProgress.value.toMutableMap().apply { put(name, fileBytesSent.toFloat() / size) }
                            overallProgress.value = totalBytesSent.toFloat() / totalBytesToSend
                        }
                    }
                    activeSocket?.close()
                    if (!cancelTransfer) {
                        fileProgress.value = fileProgress.value.toMutableMap().apply { put(name, 1.0f) }
                    }
                }
                withContext(Dispatchers.Main) { onComplete() }
            } catch (e: Exception) {
                if (!cancelTransfer) withContext(Dispatchers.Main) { onError(e.message ?: "Unknown Error") }
            }
        }
    }

    fun startTcpServer() {
        isServerRunning = true
        scope.launch {
            try {
                tcpServerSocket = ServerSocket(TCP_PORT).apply { reuseAddress = true; soTimeout = 1000 }
                while (isServerRunning) {
                    try {
                        val client = tcpServerSocket!!.accept()
                        handleIncomingFile(client)
                    } catch (e: java.net.SocketTimeoutException) { continue }
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { tcpServerSocket?.close() }
        }
    }

    private fun handleIncomingFile(client: Socket) {
        scope.launch {
            var saveFile: File? = null
            try {
                client.soTimeout = 10000
                val inStream = client.getInputStream()

                val headerBytes = mutableListOf<Byte>()
                while (isServerRunning) {
                    val b = inStream.read()
                    if (b == -1 || b.toByte() == '\n'.code.toByte()) break
                    headerBytes.add(b.toByte())
                }
                val header = String(headerBytes.toByteArray())
                val parts = header.split(SEPARATOR)
                if (parts.size != 2) return@launch

                val filename = parts[0]
                val filesize = parts[1].toLong()

                addLog("↓ Incoming: $filename (${"%.2f".format(filesize/1024f/1024f)} MB)")
                overallProgress.value = 0f
                transferStatus.value = "Receiving $filename: 0%"

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                saveFile = File(downloadsDir, filename)

                var receivedTotal = 0L
                FileOutputStream(saveFile).use { fos ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (receivedTotal < filesize && isServerRunning) {
                        bytesRead = inStream.read(buffer)
                        if (bytesRead == -1) break
                        fos.write(buffer, 0, bytesRead)
                        receivedTotal += bytesRead

                        val progress = receivedTotal.toFloat() / filesize
                        overallProgress.value = progress
                        transferStatus.value = "Receiving $filename: ${(progress * 100).toInt()}%"
                    }
                }

                if (isServerRunning) {
                    addLog("✓ Saved: $filename")
                    transferStatus.value = "Transfer Complete"
                } else {
                    saveFile.delete()
                }

            } catch (e: Exception) {
                if (isServerRunning) addLog("⚠ Error: ${e.message}")
                saveFile?.delete()
            } finally {
                client.close()
            }
        }
    }

    fun stopEverything() {
        isServerRunning = false
        cancelTransfer = true
        activeSocket?.close()
        tcpServerSocket?.close()
    }

    private fun addLog(msg: String) { logs.value = logs.value + msg }

    // Helpers to get file info from content resolver
    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }
        return result ?: uri.path?.substringAfterLast('/') ?: "Unknown_File"
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
            }
        }
        return 0L
    }

    fun getLocalIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (ex: Exception) { }
        return "127.0.0.1"
    }
}