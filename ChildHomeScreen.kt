package com.example.ui.child

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FamilyLink
import com.example.data.repository.FamilyRepository
import com.example.location.LocationHelper
import com.example.service.FamilyLocationService
import com.example.ui.chat.ChatScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildHomeScreen(
    childLink: FamilyLink,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { FamilyRepository(context) }
    val locationHelper = remember { LocationHelper(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isServiceActive by remember { mutableStateOf(FamilyLocationService.isServiceRunning) }
    var hasLocationPerm by remember { mutableStateOf(locationHelper.hasLocationPermission()) }
    var isGpsOn by remember { mutableStateOf(locationHelper.isGpsEnabled()) }
    var showChat by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasLocationPerm = locationHelper.hasLocationPermission()
        isGpsOn = locationHelper.isGpsEnabled()
    }

    val pendingRequests by repository.observePendingRequestsForChild(childLink.childId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Periodic telemetry and status sync
    LaunchedEffect(Unit) {
        while (true) {
            hasLocationPerm = locationHelper.hasLocationPermission()
            isGpsOn = locationHelper.isGpsEnabled()
            isServiceActive = FamilyLocationService.isServiceRunning

            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? BatteryManager
            val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            val permStatus = if (locationHelper.hasFineLocationPermission()) "granted" else if (hasLocationPerm) "foreground_only" else "denied"

            repository.updateChildTelemetry(
                linkId = childLink.id,
                batteryLevel = battery,
                isOnline = true,
                isSharing = isServiceActive,
                permStatus = permStatus,
                gpsEnabled = isGpsOn
            )
            delay(20_000L)
        }
    }

    if (showChat) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("گفت‌وگو با سرپرست") },
                    navigationIcon = {
                        TextButton(onClick = { showChat = false }) {
                            Text("بازگشت", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        ) { padding ->
            ChatScreen(
                parentId = childLink.parentId,
                childId = childLink.childId,
                currentSenderType = "child",
                partnerName = "والدین",
                onSendSmsFallback = { text ->
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:")
                        putExtra("sms_body", text)
                    }
                    try { context.startActivity(smsIntent) } catch (_: Exception) {}
                },
                modifier = Modifier.padding(padding)
            )
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "حالت فرزند: ${childLink.childName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "متصل به حساب سرپرست خانواده",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onSignOut) {
                        Text("خروج", color = MaterialTheme.colorScheme.error)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Protection Status Card
            Card(
                modifier = Modifier.fillMaxWidth().testTag("child_status_hero_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isServiceActive && hasLocationPerm && isGpsOn)
                        Color(0xFF0F766E) else Color(0xFF1E293B)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Default.LocationOn else Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isServiceActive) "اشتراک‌گذاری موقعیت فعال است" else "اشتراک‌گذاری موقتاً متوقف شده است",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isServiceActive)
                            "موقعیت دستگاه شما با اعلان شفاف و دائمی در پس‌زمینه برای سرپرست ارسال می‌شود."
                        else "برای حفظ امنیت و اطمینان والدین، اشتراک‌گذاری موقعیت را فعال کنید.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = {
                            if (!isServiceActive) {
                                if (!hasLocationPerm) {
                                    val req = mutableListOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        req.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionLauncher.launch(req.toTypedArray())
                                    return@Button
                                }
                                FamilyLocationService.start(
                                    context = context,
                                    linkId = childLink.id,
                                    parentId = childLink.parentId,
                                    childId = childLink.childId
                                )
                                isServiceActive = true
                            } else {
                                FamilyLocationService.stop(context)
                                isServiceActive = false
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServiceActive) Color(0xFFDC2626) else Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(48.dp)
                            .testTag("toggle_sharing_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isServiceActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isServiceActive) Color.White else Color(0xFF0F766E)
                            )
                            Text(
                                text = if (isServiceActive) "توقف اشتراک‌گذاری موقعیت" else "شروع اشتراک‌گذاری موقعیت",
                                fontWeight = FontWeight.Bold,
                                color = if (isServiceActive) Color.White else Color(0xFF0F766E)
                            )
                        }
                    }
                }
            }

            // Pending Urgent Request from Parent
            if (pendingRequests.isNotEmpty()) {
                val latestReq = pendingRequests.first()
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("urgent_request_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "والدین درخواست موقعیت کرده‌اند!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                        if (latestReq.message.isNotBlank()) {
                            Text(
                                text = "پیام سرپرست: ${latestReq.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF78350F),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val loc = locationHelper.getCurrentLocation()
                                    if (loc != null) {
                                        repository.reportLocation(
                                            parentId = childLink.parentId,
                                            childId = childLink.childId,
                                            latitude = loc.latitude,
                                            longitude = loc.longitude,
                                            accuracy = loc.accuracy.toDouble(),
                                            provider = loc.provider ?: "fused",
                                            speed = loc.speed.toDouble()
                                        )
                                        repository.updateLocationRequestStatus(latestReq.id, "fulfilled")
                                        snackbarHostState.showSnackbar("موقعیت مکانی شما با موفقیت ارسال شد.")
                                    } else {
                                        snackbarHostState.showSnackbar("امکان دریافت GPS در حال حاضر میسر نیست.")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("fulfill_request_button")
                        ) {
                            Text("ارسال فوری موقعیت به سرپرست", color = Color.White)
                        }
                    }
                }
            }

            // Permissions & GPS Diagnostics Cards
            Text(
                text = "وضعیت دسترسی‌ها و سنسورها",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Location Permission Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (hasLocationPerm) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (hasLocationPerm) Color(0xFF16A34A) else Color(0xFFDC2626)
                            )
                            Text("مجوز موقعیت مکانی (Location)")
                        }
                        if (!hasLocationPerm) {
                            OutlinedButton(
                                onClick = {
                                    val req = mutableListOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        req.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    permissionLauncher.launch(req.toTypedArray())
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("اعطا", fontSize = 12.sp)
                            }
                        } else {
                            Text("فعال", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // GPS Switch Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isGpsOn) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                contentDescription = null,
                                tint = if (isGpsOn) Color(0xFF16A34A) else Color(0xFFDC2626)
                            )
                            Text("سخت‌افزار GPS دستگاه")
                        }
                        if (!isGpsOn) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("روشن کردن", fontSize = 12.sp)
                            }
                        } else {
                            Text("روشن", color = Color(0xFF16A34A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Quick Chat with Parent Card
            Card(
                onClick = { showChat = true },
                modifier = Modifier.fillMaxWidth().testTag("open_child_chat_button"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = "گفت‌وگو با والدین",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ارسال و دریافت پیام متنی امن",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Text("مشاهده >", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
