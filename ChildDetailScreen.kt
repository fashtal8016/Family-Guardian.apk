package com.example.ui.parent

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FamilyLink
import com.example.data.model.LocationRecord
import com.example.ui.chat.ChatScreen
import com.example.ui.map.FamilyMapView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDetailScreen(
    child: FamilyLink,
    latestLocation: LocationRecord?,
    locationHistory: List<LocationRecord>,
    onRequestLocation: (note: String) -> Unit,
    onUnlinkDevice: () -> Unit,
    onDeleteChild: () -> Unit,
    onBack: () -> Unit,
    isRequestingLocation: Boolean = false,
    currentUserId: String = "",
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showUnlinkDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = child.childName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (child.isOnline) "🟢 آنلاین • مدل: ${child.deviceModel.ifBlank { "اندروید" }}"
                            else "⚪ آفلاین • کد: ${child.pairingCode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("child_detail_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showUnlinkDialog = true },
                        modifier = Modifier.testTag("unlink_device_menu_button")
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = "لغو اتصال دستگاه", tint = MaterialTheme.colorScheme.error)
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
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("نقشه و موقعیت") },
                    icon = { Icon(Icons.Default.MyLocation, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("گفت‌وگو و پیام") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) }
                )
            }

            if (selectedTab == 0) {
                // Map & Location Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Status Telemetry Bar
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.BatteryChargingFull,
                                    contentDescription = null,
                                    tint = if (child.batteryLevel > 20) Color(0xFF16A34A) else Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "باتری: ${child.batteryLevel}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.GpsFixed,
                                    contentDescription = null,
                                    tint = if (child.gpsEnabled) Color(0xFF2563EB) else Color(0xFFDC2626),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (child.gpsEnabled) "GPS فعال" else "GPS غیرفعال",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = "مجوز: ${if (child.locationPermissionStatus == "granted") "کامل" else "محدود"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Map Component
                    FamilyMapView(
                        currentLocation = latestLocation,
                        history = locationHistory,
                        childName = child.childName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    // Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onRequestLocation("درخواست موقعیت فوری از طرف سرپرست") },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("request_location_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            enabled = !isRequestingLocation
                        ) {
                            if (isRequestingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Text("درخواست موقعیت فوری")
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showHistorySheet = !showHistorySheet },
                            modifier = Modifier.height(50.dp).testTag("toggle_history_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = "تاریخچه")
                        }
                    }

                    // Location History List (if toggled)
                    if (showHistorySheet) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "تاریخچه موقعیت‌های مکانی (با اطلاع‌رسانی)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                if (locationHistory.isEmpty()) {
                                    Text(
                                        text = "هنوز سابقه‌ای ثبت نشده است.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                } else {
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(locationHistory) { loc ->
                                            val dateStr = loc.createdAt?.toDate()?.let {
                                                SimpleDateFormat("HH:mm:ss", Locale.US).format(it)
                                            } ?: "--:--"
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "📍 عرض: ${String.format(Locale.US, "%.4f", loc.latitude)} | طول: ${String.format(Locale.US, "%.4f", loc.longitude)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp
                                                )
                                                Text(
                                                    text = "زمان: $dateStr (±${loc.accuracy.toInt()}m)",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // In-App Chat Tab
                ChatScreen(
                    parentId = child.parentId,
                    childId = child.childId,
                    currentSenderType = "parent",
                    partnerName = child.childName,
                    onSendSmsFallback = { text ->
                        // Official Android SMS Intent per guidelines
                        val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("smsto:")
                            putExtra("sms_body", text)
                        }
                        try {
                            context.startActivity(smsIntent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Unlink Device Dialog
    if (showUnlinkDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkDialog = false },
            title = { Text("لغو دسترسی دستگاه") },
            text = { Text("آیا مطمئن هستید که می‌خواهید اتصال این دستگاه را لغو کنید؟ پس از لغو، ارتباط قطع خواهد شد.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlinkDialog = false
                        onUnlinkDevice()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("لغو دسترسی")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkDialog = false }) {
                    Text("انصراف")
                }
            }
        )
    }
}
