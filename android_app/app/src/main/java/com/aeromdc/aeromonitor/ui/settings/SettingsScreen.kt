package com.aeromdc.aeromonitor.ui.settings

import android.app.Application
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aeromdc.aeromonitor.data.repository.AeroMonitorRepository
import com.aeromdc.aeromonitor.ui.dashboard.GlassCard
import com.aeromdc.aeromonitor.ui.theme.*
import com.aeromdc.aeromonitor.util.AppPreferences
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val repo = remember { AeroMonitorRepository(context.applicationContext as Application) }
    val scope = rememberCoroutineScope()

    val backendUrl by prefs.backendUrl.collectAsState(initial = "http://10.0.2.2/api")
    val rooktecBaseUrl by prefs.rooktecBaseUrl.collectAsState(initial = "https://www.rooktec.in/wmapp")
    val rooktecUsername by prefs.rooktecUsername.collectAsState(initial = "smb")
    val rooktecPassword by prefs.rooktecPassword.collectAsState(initial = "wind@smb")

    var backendUrlInput by remember(backendUrl) { mutableStateOf(backendUrl) }
    var rooktecUrlInput by remember(rooktecBaseUrl) { mutableStateOf(rooktecBaseUrl) }
    var usernameInput by remember(rooktecUsername) { mutableStateOf(rooktecUsername) }
    var passwordInput by remember(rooktecPassword) { mutableStateOf(rooktecPassword) }
    var showPassword by remember { mutableStateOf(false) }

    var connectionStatus by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var isScraping by remember { mutableStateOf(false) }
    var scrapeStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().background(Surface).verticalScroll(rememberScrollState()),
    ) {
        // ---- Header ----
        Surface(color = SurfaceContainer, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, color = OnSurface)
                Text("Configure backend and scraper", style = MaterialTheme.typography.bodyMedium, color = OnSurfaceVariant)
            }
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ---- Backend API Config ----
            SettingsSection(title = "BACKEND API") {
                SettingsField(
                    label = "API Base URL",
                    hint = "http://10.0.2.2/api  (emulator)\nhttp://192.168.x.x/api  (real device)",
                    value = backendUrlInput,
                    onValueChange = { backendUrlInput = it },
                    icon = Icons.Filled.Cloud,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                prefs.setBackendUrl(backendUrlInput.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", color = Primary)
                    }
                    Button(
                        onClick = {
                            isTesting = true
                            connectionStatus = null
                            scope.launch {
                                prefs.setBackendUrl(backendUrlInput.trim())
                                val ok = repo.testConnection()
                                connectionStatus = if (ok) "✓ Backend reachable" else "✗ Cannot reach backend"
                                isTesting = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest),
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Wifi, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Test", color = OnSurface)
                        }
                    }
                }
                if (connectionStatus != null) {
                    val isOk = connectionStatus!!.startsWith("✓")
                    Surface(
                        color = if (isOk) ColorOperational.copy(alpha = 0.1f) else Error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            connectionStatus!!,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOk) ColorOperational else Error,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // ---- Rooktec Scraper Config ----
            SettingsSection(title = "ROOKTEC SCRAPER") {
                SettingsField(
                    label = "Portal URL",
                    hint = "https://www.rooktec.in/wmapp",
                    value = rooktecUrlInput,
                    onValueChange = { rooktecUrlInput = it },
                    icon = Icons.Filled.Web,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(8.dp))
                SettingsField(
                    label = "Username",
                    hint = "smb",
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    icon = Icons.Filled.Person,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = OnSurfaceVariant) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null, tint = OnSurfaceVariant)
                        }
                    },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = settingsFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                prefs.setRooktecBaseUrl(rooktecUrlInput.trim())
                                prefs.setRooktecUsername(usernameInput.trim())
                                prefs.setRooktecPassword(passwordInput)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest),
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", color = Primary)
                    }
                    Button(
                        onClick = {
                            isScraping = true
                            scrapeStatus = null
                            scope.launch {
                                prefs.setRooktecBaseUrl(rooktecUrlInput.trim())
                                prefs.setRooktecUsername(usernameInput.trim())
                                prefs.setRooktecPassword(passwordInput)
                                val result = repo.fetchLiveTelemetry()
                                scrapeStatus = result.fold(
                                    onSuccess = { data -> "✓ Scraped ${data.turbines.size} turbines" },
                                    onFailure = { e -> "✗ Scrape failed: ${e.message}" },
                                )
                                isScraping = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isScraping,
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainerHighest),
                    ) {
                        if (isScraping) {
                            CircularProgressIndicator(color = Primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = ColorOperational, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Scrape Now", color = OnSurface)
                        }
                    }
                }
                if (scrapeStatus != null) {
                    val isOk = scrapeStatus!!.startsWith("✓")
                    Surface(
                        color = if (isOk) ColorOperational.copy(alpha = 0.1f) else Error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            scrapeStatus!!,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isOk) ColorOperational else Error,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // ---- Info card ----
            SettingsSection(title = "NETWORK INFO") {
                InfoRow("Emulator (Android Studio)", "Use 10.0.2.2 as host IP", Icons.Filled.Computer)
                InfoRow("Real Device", "Use your PC's LAN IP (e.g. 192.168.1.x)", Icons.Filled.PhoneAndroid)
                InfoRow("API Path", "/api/telemetry/live, /api/alerts/active", Icons.Filled.Api)
                InfoRow("Scrape Target", "rooktec.in/wmapp/reload_status_tst.php", Icons.Filled.Web)
            }

            // ---- Version ----
            Text(
                "AeroMonitor v2.4 • Wind Farm Alpha Analytics",
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant, letterSpacing = 2.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingsField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.5f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = OnSurfaceVariant) },
        modifier = Modifier.fillMaxWidth(),
        colors = settingsFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
    )
}

@Composable
fun InfoRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = OnSurface)
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp), color = OnSurfaceVariant, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = OutlineVariant,
    focusedTextColor = OnSurface,
    unfocusedTextColor = OnSurface,
    focusedLabelColor = Primary,
    unfocusedLabelColor = OnSurfaceVariant,
    cursorColor = Primary,
)
