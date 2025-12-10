// seamless - High-performance cross-platform file transfer utility.
// Copyright (C) 2025 Iman Zamani
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

package com.example.seamless

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.*
import java.util.*
import android.net.wifi.WifiManager
import android.content.Context
class MainActivity : AppCompatActivity() {

    // Config
    private val UDP_PORT = 5000
    private val TCP_PORT = 5001
    private val SEPARATOR = "<SEPARATOR>"
    // Increased buffer size to 64KB for large files
    private val BUFFER_SIZE = 1024 * 64

    // UI Elements
    private lateinit var etUsername: EditText
    private lateinit var menuLayout: LinearLayout
    private lateinit var sendLayout: LinearLayout
    private lateinit var receiveLayout: LinearLayout
    private lateinit var deviceListContainer: LinearLayout
    private lateinit var tvSelectedFile: TextView
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    // State
    private var selectedFileUri: Uri? = null
    private var username = "Android_${Random().nextInt(1000)}"
    private var isReceiving = false
    private var receiveJob: Job? = null
    private var peers = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("SeamlessLock")
        lock.setReferenceCounted(true)
        lock.acquire()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Init UI
        etUsername = findViewById(R.id.etUsername)
        menuLayout = findViewById(R.id.menuLayout)
        sendLayout = findViewById(R.id.sendLayout)
        receiveLayout = findViewById(R.id.receiveLayout)
        deviceListContainer = findViewById(R.id.deviceListContainer)
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        etUsername.setText(username)

        // Menu Buttons
        findViewById<Button>(R.id.btnSendMode).setOnClickListener {
            username = etUsername.text.toString()
            showLayout(sendLayout)
        }

        findViewById<Button>(R.id.btnReceiveMode).setOnClickListener {
            username = etUsername.text.toString()
            showLayout(receiveLayout)
            startReceivingMode()
        }

        // Send UI Buttons
        val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            selectedFileUri = uri
            tvSelectedFile.text = uri?.path ?: "No file selected"
        }

        findViewById<Button>(R.id.btnPickFile).setOnClickListener {
            filePicker.launch("*/*")
        }

        findViewById<Button>(R.id.btnScan).setOnClickListener {
            scanNetwork()
        }

        findViewById<Button>(R.id.btnBackFromSend).setOnClickListener { showLayout(menuLayout) }
        findViewById<Button>(R.id.btnBackFromReceive).setOnClickListener {
            isReceiving = false
            receiveJob?.cancel()
            showLayout(menuLayout)
        }
    }

    private fun showLayout(view: View) {
        menuLayout.visibility = View.GONE
        sendLayout.visibility = View.GONE
        receiveLayout.visibility = View.GONE
        view.visibility = View.VISIBLE
    }

    // --- NETWORKING: DISCOVERY ---

    private fun scanNetwork() {
        deviceListContainer.removeAllViews()
        peers.clear()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val msg = "DISCOVER:$username".toByteArray()
                val packet = DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), UDP_PORT)
                socket.send(packet)
                socket.close()
                listenForDiscoveryResponses()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun listenForDiscoveryResponses() {
        lifecycleScope.launch(Dispatchers.IO) {
            val socket = DatagramSocket(UDP_PORT)
            socket.soTimeout = 5000
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            val endTime = System.currentTimeMillis() + 5000

            while (System.currentTimeMillis() < endTime) {
                try {
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length)
                    val address = packet.address.hostAddress

                    if (msg.startsWith("HERE:")) {
                        val name = msg.split(":")[1]
                        if (!peers.containsKey(address)) {
                            peers[address!!] = name
                            withContext(Dispatchers.Main) {
                                addDeviceButton(name, address)
                            }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    break
                } catch (e: Exception) { e.printStackTrace() }
            }
            socket.close()
        }
    }

    private fun addDeviceButton(name: String, ip: String) {
        val btn = Button(this)
        btn.text = "$name\n$ip"
        btn.setOnClickListener {
            sendFile(ip)
        }
        deviceListContainer.addView(btn)
    }

    // --- NETWORKING: SENDING ---

    private fun sendFile(ip: String) {
        if (selectedFileUri == null) {
            Toast.makeText(this, "Select a file first", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Sending...", Toast.LENGTH_SHORT).show()
                }

                val socket = Socket(ip, TCP_PORT)
                val outputStream = socket.getOutputStream()
                val inputStream = contentResolver.openInputStream(selectedFileUri!!)

                val cursor = contentResolver.query(selectedFileUri!!, null, null, null, null)
                val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor?.getColumnIndex(OpenableColumns.SIZE)
                cursor?.moveToFirst()
                val filename = cursor?.getString(nameIndex!!) ?: "unknown_file"
                val filesize = cursor?.getLong(sizeIndex!!) ?: 0
                cursor?.close()

                val header = "$filename$SEPARATOR$filesize\n"
                outputStream.write(header.toByteArray())

                // Send Body with Larger Buffer
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (inputStream!!.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }

                socket.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Sent Successfully!", Toast.LENGTH_LONG).show()
                    showLayout(menuLayout)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // --- NETWORKING: RECEIVING ---

    // --- Helper to get the correct Subnet Broadcast Address ---
    private fun getBroadcastAddress(): InetAddress? {
        return try {
            val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val dhcp = wifi.dhcpInfo
            // Calculate broadcast address from DHCP info
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val quads = ByteArray(4)
            for (k in 0..3) quads[k] = ((broadcast shr k * 8) and 0xFF).toByte()
            InetAddress.getByAddress(quads)
        } catch (e: Exception) {
            InetAddress.getByName("255.255.255.255") // Fallback
        }
    }

    // --- UPDATED RECEIVING MODE ---
    private fun startReceivingMode() {
        isReceiving = true
        progressBar.progress = 0
        tvStatus.text = "Initializing Server..."

        receiveJob = lifecycleScope.launch(Dispatchers.IO) {
            val broadcastAddress = getBroadcastAddress()

            // 1. Periodic Broadcaster (Heartbeat)
            launch {
                val socket = DatagramSocket()
                socket.broadcast = true
                while (isReceiving && isActive) {
                    try {
                        val msg = "HERE:$username".toByteArray()
                        val packet = DatagramPacket(msg, msg.size, broadcastAddress, UDP_PORT)
                        socket.send(packet)
                        Thread.sleep(2000)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                socket.close()
            }

            // 2. Listener for "DISCOVER" requests (Reply immediately when Desktop scans)
            launch {
                val socket = DatagramSocket(UDP_PORT)
                socket.broadcast = true
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                while (isReceiving && isActive) {
                    try {
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length)
                        if (msg.startsWith("DISCOVER")) {
                            // Reply directly to the scanner
                            val reply = "HERE:$username".toByteArray()
                            val replyPacket = DatagramPacket(reply, reply.size, packet.address, UDP_PORT)
                            socket.send(replyPacket)
                        }
                    } catch (e: Exception) { }
                }
                socket.close()
            }

            // 3. TCP Server (File Transfer)
            try {
                val serverSocket = ServerSocket(TCP_PORT)
                serverSocket.soTimeout = 1000

                withContext(Dispatchers.Main) { tvStatus.text = "Ready to receive..." }

                while (isReceiving && isActive) {
                    try {
                        val client = serverSocket.accept()
                        handleIncomingConnection(client)
                    } catch (e: SocketTimeoutException) {
                        continue
                    }
                }
                serverSocket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleIncomingConnection(socket: Socket) {
        withContext(Dispatchers.Main) {
            tvStatus.text = "Connected! Receiving data..."
            progressBar.progress = 0
        }

        try {
            val input = socket.getInputStream()

            // Read Header
            var headerString = ""
            while (true) {
                val b = input.read()
                if (b == '\n'.code || b == -1) break
                headerString += b.toChar()
            }

            val parts = headerString.split(SEPARATOR)
            val filename = parts[0]
            val filesize = parts[1].toLong()

            // Save logic
            val outputStream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "*/*")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let { contentResolver.openOutputStream(it) }
            } else {
                val targetFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
                FileOutputStream(targetFile)
            }

            if (outputStream != null) {
                outputStream.use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalRead: Long = 0
                    var bytesRead: Int
                    var lastUiUpdate = System.currentTimeMillis()

                    while (totalRead < filesize) {
                        bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        // Throttle UI updates to prevent freezing
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUiUpdate > 100 || totalRead == filesize) {
                            val progress = ((totalRead * 100) / filesize).toInt()
                            withContext(Dispatchers.Main) {
                                progressBar.progress = progress
                                tvStatus.text = "Receiving: $progress%"
                            }
                            lastUiUpdate = currentTime
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Saved: $filename"
                    progressBar.progress = 100
                }
            }
            socket.close()

        } catch (e: Exception) {
            withContext(Dispatchers.Main) { tvStatus.text = "Error: ${e.message}" }
            e.printStackTrace()
        }
    }
}
