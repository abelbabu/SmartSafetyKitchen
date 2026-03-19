package com.example.myapplication

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    var gasCutState by mutableStateOf(false)
    var alertType by mutableStateOf("none")

    var gasLevel by mutableStateOf(0)
    var flameValue by mutableStateOf(1)
    var temperature by mutableStateOf(0f)
    var humidity by mutableStateOf(0f)

    private var lastUpdate by mutableStateOf("--:--:--")

    var deviceLastSeen by mutableStateOf(0L)
    var eventLogs by mutableStateOf(listOf<String>())

    // 🔥 NEW
    var flameTimeout by mutableStateOf(10)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = FirebaseDatabase.getInstance()

        val gasCutRef = database.getReference("Kitchen/gas_cut")
        val alertRef = database.getReference("Kitchen/alert_type")
        val gasRef = database.getReference("Kitchen/gas")
        val flameRef = database.getReference("Kitchen/flame")
        val tempRef = database.getReference("Kitchen/temperature")
        val humRef = database.getReference("Kitchen/humidity")
        val logsRef = database.getReference("Kitchen/logs")
        val lastSeenRef = database.getReference("Kitchen/last_seen")

        // 🔥 NEW
        val flameTimeoutRef = database.getReference("Kitchen/flame_timeout")

        gasCutRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                gasCutState = snapshot.getValue(Boolean::class.java) ?: false
                updateTime()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        alertRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val newType = snapshot.getValue(String::class.java) ?: "none"
                if (newType != alertType) {
                    if (newType == "gas") showGasAlert()
                    if (newType == "flame") showFlameAlert()
                }
                alertType = newType
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        gasRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                gasLevel = snapshot.getValue(Int::class.java) ?: 0
                updateTime()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        flameRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                flameValue = snapshot.getValue(Int::class.java) ?: 1
                updateTime()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        tempRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                temperature = snapshot.getValue(Float::class.java) ?: 0f
                updateTime()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        humRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                humidity = snapshot.getValue(Float::class.java) ?: 0f
                updateTime()
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        logsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val logsList = mutableListOf<String>()
                for (logSnapshot in snapshot.children) {
                    val log = logSnapshot.getValue(String::class.java)
                    if (log != null) logsList.add(log)
                }
                logsList.reverse()
                eventLogs = logsList
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        lastSeenRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                deviceLastSeen = snapshot.getValue(Long::class.java) ?: 0L
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 🔥 NEW LISTENER
        flameTimeoutRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                flameTimeout = snapshot.getValue(Int::class.java) ?: 10
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        startService(Intent(this, FirebaseService::class.java))

        setContent {
            MyApplicationTheme {
                Dashboard(
                    gasCutState,
                    gasLevel,
                    flameValue,
                    temperature,
                    humidity,
                    lastUpdate,
                    deviceLastSeen,
                    eventLogs,
                    flameTimeout
                )
            }
        }
    }

    fun updateTime() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        lastUpdate = sdf.format(Date())
    }

    @SuppressLint("MissingPermission")
    fun showGasAlert() {
        val channelId = "gas_alert"
        createChannel(channelId,"Gas Alerts")

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 GAS LEAK DETECTED")
            .setContentText("Immediate action required!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        NotificationManagerCompat.from(this).notify(1,builder.build())
    }

    @SuppressLint("MissingPermission")
    fun showFlameAlert() {
        val channelId = "flame_alert"
        createChannel(channelId,"Flame Timeout")

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏱ Flame Timeout")
            .setContentText("Gas supply stopped automatically.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(this).notify(2,builder.build())
    }

    private fun createChannel(id:String,name:String){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(id,name,NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Dashboard(
    gasCut:Boolean,
    gasLevel:Int,
    flameValue:Int,
    temperature:Float,
    humidity:Float,
    lastUpdate:String,
    deviceLastSeen:Long,
    logs:List<String>,
    flameTimeout:Int
){

    var currentTime by remember { mutableStateOf(System.currentTimeMillis()/1000) }

    LaunchedEffect(Unit) {
        while(true){
            currentTime = System.currentTimeMillis()/1000
            delay(1000)
        }
    }

    val diff = currentTime - deviceLastSeen

    val cloudStatus:String
    val cloudColor:Color

    if(diff < 10){
        cloudStatus="Cloud Connected"
        cloudColor=Color(0xFF2E7D32)
    }else if(diff < 30){
        cloudStatus="Cloud Delay"
        cloudColor=Color(0xFFFF9800)
    }else{
        cloudStatus="Device Offline"
        cloudColor=Color.Red
    }

    val scrollState = rememberScrollState()
    val safeStatusText = if (!gasCut) "System safe" else "System alert"
    val safeStatusColor = if (!gasCut) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error

    var sliderValue by remember { mutableStateOf(flameTimeout.toFloat()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Smart Safety Kitchen",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Realtime monitoring & safety controls",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                color = safeStatusColor.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp)
                            )
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "System status",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = safeStatusText,
                            style = MaterialTheme.typography.headlineSmall,
                            color = safeStatusColor
                        )
                    }
                    StatusPill(text = cloudStatus, color = cloudColor)
                }
            }

            Button(
                onClick = {
                    FirebaseDatabase.getInstance()
                        .getReference("Kitchen/reset")
                        .setValue(true)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    text = "Reset gas system",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            SectionHeader(
                title = "Safety controls",
                subtitle = "Tune how the system reacts to hazards"
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Flame safety timer",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Auto cut-off delay",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(999.dp),
                            tonalElevation = 0.dp
                        ) {
                            Text(
                                text = "${sliderValue.toInt()} sec",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 5f..120f
                    )

                    Button(
                        onClick = {
                            FirebaseDatabase.getInstance()
                                .getReference("Kitchen/flame_timeout")
                                .setValue(sliderValue.toInt())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Update timer",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            SectionHeader(
                title = "Sensor data",
                subtitle = "Latest readings from the device"
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    MetricRow(label = "Gas level", value = "$gasLevel ppm")
                    MetricDivider()
                    MetricRow(
                        label = "Flame sensor",
                        value = if (flameValue == 0) "Detected" else "Safe",
                        valueColor = if (flameValue == 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                    )
                    MetricDivider()
                    MetricRow(label = "Temperature", value = "$temperature °C")
                    MetricDivider()
                    MetricRow(label = "Humidity", value = "$humidity %")
                }
            }

            SectionHeader(
                title = "Network",
                subtitle = "Cloud status and last refresh"
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(text = cloudStatus, color = cloudColor)
                    Text(
                        text = "Last update: $lastUpdate",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SectionHeader(
                title = "Recent safety events",
                subtitle = "Most recent events from the system log"
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    val recent = logs.take(10)
                    if (recent.isEmpty()) {
                        Text(
                            text = "No events yet.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recent.forEachIndexed { index, item ->
                            Text(
                                text = item,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (index != recent.lastIndex) MetricDivider()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun StatusPill(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}