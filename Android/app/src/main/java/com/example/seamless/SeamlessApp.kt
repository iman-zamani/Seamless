package com.example.seamless

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.seamless.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeamlessApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var username by remember { mutableStateOf(TransferManager.username) }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {
        // --- HEADER ---
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp)
        ) {
            // Top Row: Title and Username Setup
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEAMLESS",
                    color = PrimaryPurple,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    modifier = Modifier.width(130.dp).height(50.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = PrimaryPurple,
                        unfocusedBorderColor = PrimaryPurple,
                        containerColor = Color(0xFF111111)
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        TransferManager.username = username
                        Toast.makeText(context, "Username set", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                ) { Text("Set") }
            }

            // Bottom Row: Back Button (Only shows if not on main menu)
            if (currentRoute != "menu") {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "← Back",
                    color = MutedText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable {
                            TransferManager.stopEverything()
                            navController.popBackStack("menu", false)
                        }
                        .padding(vertical = 4.dp, horizontal = 4.dp) // Added padding to make it easier to tap
                )
            }
        }

        // --- MAIN CONTENT AREA ---
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp).background(FrameColor, RoundedCornerShape(15.dp))) {
            NavHost(navController, startDestination = "menu") {
                composable("menu") { MenuScreen(navController) }
                composable("select_files") { SelectFilesScreen(navController) }
                composable("select_device") { SelectDeviceScreen(navController) }
                composable("sending/{ip}/{name}") { backStackEntry ->
                    SendingProgressScreen(
                        ip = backStackEntry.arguments?.getString("ip") ?: "",
                        name = backStackEntry.arguments?.getString("name") ?: "",
                        navController = navController
                    )
                }
                composable("receive") { ReceiveScreen() }
            }
        }
    }
}

@Composable
fun CircularProgress(progress: Float, size: Dp = 26.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val pad = 4f
        if (progress >= 1f) {
            drawCircle(color = HoverPurple, radius = size.toPx() / 2 - pad, style = Stroke(width = 6f))
            // Draw checkmark
            drawLine(color = HoverPurple, start = androidx.compose.ui.geometry.Offset(size.toPx()*0.3f, size.toPx()*0.5f),
                end = androidx.compose.ui.geometry.Offset(size.toPx()*0.45f, size.toPx()*0.65f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(color = HoverPurple, start = androidx.compose.ui.geometry.Offset(size.toPx()*0.45f, size.toPx()*0.65f),
                end = androidx.compose.ui.geometry.Offset(size.toPx()*0.7f, size.toPx()*0.35f), strokeWidth = 6f, cap = StrokeCap.Round)
        } else {
            drawCircle(color = Color(0xFF222222), radius = size.toPx() / 2 - pad, style = Stroke(width = 6f))
            drawArc(color = HoverPurple, startAngle = -90f, sweepAngle = progress * 360f, useCenter = false, style = Stroke(width = 6f))
        }
    }
}

// Ensure you maintain selected files in memory across navigation during the flow
var globalSelectedUris = listOf<Uri>()

@Composable
fun MenuScreen(navController: androidx.navigation.NavController) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(60.dp))
        Text("What would you like to do?", fontSize = 18.sp, color = TextColor)
        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = { navController.navigate("select_files") },
            modifier = Modifier.width(250.dp).height(70.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
        ) { Text("SEND FILES", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedButton(
            onClick = { navController.navigate("receive") },
            modifier = Modifier.width(250.dp).height(70.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextColor),
            border = androidx.compose.foundation.BorderStroke(2.dp, PrimaryPurple)
        ) { Text("RECEIVE FILES", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun SelectFilesScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    var uris by remember { mutableStateOf(globalSelectedUris) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { result ->
        uris = result
        globalSelectedUris = result
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("STEP 1: Choose Files", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = { launcher.launch("*/*") }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))) {
            Text("+ Browse Files")
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp).background(Color(0xFF111111))) {
            items(uris) { uri ->
                val name = TransferManager.getFileName(context, uri)
                val size = TransferManager.getFileSize(context, uri) / (1024f * 1024f)
                Text(text = "📄 $name (${"%.2f".format(size)} MB)", color = TextColor, modifier = Modifier.padding(8.dp))
            }
        }

        Button(
            onClick = { navController.navigate("select_device") },
            enabled = uris.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
        ) { Text("Next: Select Device →") }
    }
}

@Composable
fun SelectDeviceScreen(navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val peers by TransferManager.peers.collectAsState()

    LaunchedEffect(Unit) { TransferManager.scanNetwork(context) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("STEP 2: Select Destination", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = { TransferManager.scanNetwork(context) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))) {
            Text("↻ Refresh Network Scan")
        }

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 10.dp).background(Color(0xFF111111))) {
            if (peers.isEmpty()) {
                item { Text("Scanning local network...", color = MutedText, modifier = Modifier.padding(10.dp)) }
            }
            items(peers.entries.toList()) { (ip, name) ->
                Button(
                    onClick = { navController.navigate("sending/$ip/$name") },
                    modifier = Modifier.fillMaxWidth().padding(5.dp).height(60.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("💻 $name\n$ip", textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
fun SendingProgressScreen(ip: String, name: String, navController: androidx.navigation.NavController) {
    val context = LocalContext.current
    val overallProgress by TransferManager.overallProgress.collectAsState()
    val fileProgress by TransferManager.fileProgress.collectAsState()

    LaunchedEffect(Unit) {
        TransferManager.sendFiles(
            context = context, targetIp = ip, uris = globalSelectedUris,
            onComplete = {
                Toast.makeText(context, "All files sent successfully!", Toast.LENGTH_SHORT).show()
                navController.popBackStack("menu", false)
            },
            onError = { Toast.makeText(context, "Error: $it", Toast.LENGTH_LONG).show() }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Sending to $name...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextColor)
        Text("Total Progress: ${(overallProgress * 100).toInt()}%", color = MutedText)
        LinearProgressIndicator(progress = overallProgress, color = PrimaryPurple, trackColor = Color(0xFF222222), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF111111)).padding(10.dp)) {
            items(globalSelectedUris) { uri ->
                val fName = TransferManager.getFileName(context, uri)
                val prog = fileProgress[fName] ?: 0f
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    CircularProgress(progress = prog)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(fName, color = TextColor, maxLines = 1)
                }
            }
        }

        Button(
            onClick = {
                TransferManager.cancelTransfer = true
                navController.popBackStack("menu", false)
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B))
        ) { Text("Cancel Transfer") }
    }
}

@Composable
fun ReceiveScreen() {
    val overallProgress by TransferManager.overallProgress.collectAsState()
    val status by TransferManager.transferStatus.collectAsState()
    val logs by TransferManager.logs.collectAsState()

    LaunchedEffect(Unit) {
        TransferManager.startBroadcasting()
        TransferManager.startTcpServer()
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📡", fontSize = 40.sp)
        Text("Awaiting Transmissions...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryPurple)
        Text("Visible as: ${TransferManager.username}\n\nYour IP: ${TransferManager.getLocalIp()}", color = MutedText, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp))

        LinearProgressIndicator(progress = overallProgress, color = PrimaryPurple, trackColor = Color(0xFF222222), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        Text(status, color = TextColor)

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 20.dp).background(Color(0xFF111111)).border(1.dp, Color(0xFF333333)).padding(10.dp)) {
            items(logs) { log -> Text(log, color = MutedText, fontSize = 14.sp) }
        }
    }
}