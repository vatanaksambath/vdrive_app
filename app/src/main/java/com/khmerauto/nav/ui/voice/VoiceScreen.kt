package com.khmerauto.nav.ui.voice

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.khmerauto.nav.core.database.entity.EnrolledPhraseEntity
import com.khmerauto.nav.hal.VehicleHalContract
import com.khmerauto.nav.ui.theme.*
import com.khmerauto.nav.voice.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Complete Khmer & English Automotive Voice Assistant Screen matching EZNav layout.
 *
 * Includes:
 * 1. Voice Command Picker across 7 categories (140+ commands).
 * 2. Instant command execution ("Test Command").
 * 3. On-car 3-second acoustic enrollment ("Record My Voice").
 * 4. Voice Settings Sheet (Wake word, sensitivity, gender, volume, enrolled phrases).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    currentHal: VehicleHalContract,
    voiceService: VoiceAssistantService,
    navController: NavigationController,
    onBackToCockpit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val voiceState by voiceService.uiState.collectAsState()

    var selectedCategory by remember { mutableStateOf(VoiceCategory.CLIMATE) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var enrollmentTargetCommand by remember { mutableStateOf<VoiceCommandEntry?>(null) }
    var lastExecutionMessage by remember { mutableStateOf<String?>(null) }

    // Pulsing Voice Orb animation
    val infiniteTransition = rememberInfiniteTransition(label = "voiceOrb")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Status Header
            VoiceHeader(
                voiceState = voiceState,
                pulseScale = pulseScale,
                lastExecutionMessage = lastExecutionMessage,
                onBack = onBackToCockpit,
                onOpenSettings = { showSettingsSheet = true }
            )

            // 2. Main Two-Column Landscape Layout
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Sidebar: 7 Voice Categories
                VoiceCategorySidebar(
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    modifier = Modifier.width(260.dp).fillMaxHeight()
                )

                // Right Grid: Command Cards for Selected Category
                VoiceCommandGrid(
                    commands = VoiceCommandCatalog.getCommandsByCategory(selectedCategory),
                    onTestCommand = { cmd ->
                        coroutineScope.launch {
                            val result = cmd.execute(currentHal, navController)
                            lastExecutionMessage = result
                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onEnrollVoice = { cmd ->
                        enrollmentTargetCommand = cmd
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }

        // 3. 3-Second Voice Enrollment Dialog
        enrollmentTargetCommand?.let { cmd ->
            VoiceEnrollmentDialog(
                command = cmd,
                onDismiss = { enrollmentTargetCommand = null },
                onConfirmEnrollment = {
                    coroutineScope.launch {
                        val result = voiceService.enrollCommandVoice(
                            commandId = cmd.commandId,
                            phraseTextKm = cmd.titleKm,
                            phraseTextEn = cmd.titleEn,
                            category = cmd.category.name
                        )
                        enrollmentTargetCommand = null
                        if (result.isSuccess) {
                            Toast.makeText(context, "បានកត់ត្រាសំឡេងដោយជោគជ័យ! / Voice enrolled successfully!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "មិនអាចកត់ត្រាសំឡេងបានទេ / Voice enrollment failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // 4. Voice Settings Sheet
        if (showSettingsSheet) {
            VoiceSettingsSheet(
                voiceState = voiceState,
                onDismiss = { showSettingsSheet = false },
                onToggleWakeWord = { voiceService.setWakeWordEnabled(it) },
                onSensitivityChange = { voiceService.setWakeSensitivity(it) },
                onVolumeChange = { voiceService.setSpeechVolume(it) },
                onGenderChange = { voiceService.setVoiceGender(it) }
            )
        }
    }
}

@Composable
fun VoiceHeader(
    voiceState: VoiceAssistantUiState,
    pulseScale: Float,
    lastExecutionMessage: String?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Back button and Title
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "ប្រព័ន្ធបញ្ជាដោយសំឡេងខ្មែរ (Khmer Voice Assistant)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "EZNav Two-Tier Engine • 140+ Spoken Commands",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Center: Live Voice Orb & Recognition Feedback
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing Voice Mic Orb
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (voiceState.isProcessing) GeelyAmberGold.copy(alpha = 0.25f)
                            else if (voiceState.isListening) GeelyAccentCyan.copy(alpha = 0.25f * pulseScale)
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (voiceState.isProcessing) Icons.Default.HourglassEmpty else Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = if (voiceState.isProcessing) GeelyAmberGold else GeelyAccentCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = if (voiceState.isProcessing) "កំពុងស្តាប់ & វិភាគ... / Analyzing speech..."
                        else if (lastExecutionMessage != null) lastExecutionMessage.take(45)
                        else "និយាយ \"ហេឡូ ហ្គាឡាក់ស៊ី\" ឬ ចុចប៊ូតុងសំឡេងលើចង្កូត",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (voiceState.isProcessing) GeelyAmberGold else MaterialTheme.colorScheme.onSurface
                    )

                    // Tier status badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (voiceState.activeTier) {
                                VoiceTier.LOCAL_TIER1 -> GeelyEmeraldGreen.copy(alpha = 0.2f)
                                VoiceTier.SERVER_TIER2 -> GeelyAccentCyan.copy(alpha = 0.2f)
                                VoiceTier.IDLE -> MaterialTheme.colorScheme.primaryContainer
                            }
                        ) {
                            Text(
                                text = when (voiceState.activeTier) {
                                    VoiceTier.LOCAL_TIER1 -> "TIER 1 • ON-CAR DTW (<120ms)"
                                    VoiceTier.SERVER_TIER2 -> "TIER 2 • SPEECH SERVER"
                                    VoiceTier.IDLE -> "VOICE READY"
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (voiceState.activeTier) {
                                    VoiceTier.LOCAL_TIER1 -> GeelyEmeraldGreen
                                    VoiceTier.SERVER_TIER2 -> GeelyAccentCyan
                                    VoiceTier.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Right: Settings Button
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Voice Settings",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ការកំណត់ / Settings",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun VoiceCategorySidebar(
    selectedCategory: VoiceCategory,
    onSelectCategory: (VoiceCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(VoiceCategory.values()) { category ->
                val isSelected = category == selectedCategory
                val count = VoiceCommandCatalog.getCommandsByCategory(category).size

                val icon = when (category) {
                    VoiceCategory.CLIMATE -> Icons.Default.AcUnit
                    VoiceCategory.WINDOWS -> Icons.Default.Window
                    VoiceCategory.DOORS_TAILGATE -> Icons.Default.Lock
                    VoiceCategory.SEATS_LIGHT -> Icons.Default.AirlineSeatReclineExtra
                    VoiceCategory.MEDIA -> Icons.Default.MusicNote
                    VoiceCategory.NAVIGATION -> Icons.Default.Navigation
                    VoiceCategory.SCENES -> Icons.Default.AutoAwesome
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectCategory(category) },
                    color = if (isSelected) GeelyAccentCyan.copy(alpha = 0.2f) else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, GeelyAccentCyan) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = category.titleEn,
                            tint = if (isSelected) GeelyAccentCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = category.titleKm,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) GeelyAccentCyan else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = category.titleEn,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$count",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceCommandGrid(
    commands: List<VoiceCommandEntry>,
    onTestCommand: (VoiceCommandEntry) -> Unit,
    onEnrollVoice: (VoiceCommandEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(commands) { cmd ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Command Title
                    Text(
                        text = cmd.titleKm,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = cmd.titleEn,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Accepted Spoken Phrases Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cmd.acceptedPhrasesKm.take(2).forEach { phrase ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "\"$phrase\"",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Test Command Button
                        Button(
                            onClick = { onTestCommand(cmd) },
                            colors = ButtonDefaults.buttonColors(containerColor = GeelyAccentCyan),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Test",
                                tint = GeelyNavyDark,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "តេស្ត / Test",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GeelyNavyDark
                            )
                        }

                        // Record My Voice Enrollment Button
                        OutlinedButton(
                            onClick = { onEnrollVoice(cmd) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                            border = BorderStroke(1.dp, GeelyEmeraldGreen)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Record",
                                tint = GeelyEmeraldGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "កត់ត្រាសំឡេង / Enroll",
                                fontSize = 11.sp,
                                color = GeelyEmeraldGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceEnrollmentDialog(
    command: VoiceCommandEntry,
    onDismiss: () -> Unit,
    onConfirmEnrollment: () -> Unit
) {
    var countdownSeconds by remember { mutableIntStateOf(3) }

    LaunchedEffect(Unit) {
        while (countdownSeconds > 0) {
            delay(1000)
            countdownSeconds--
        }
        onConfirmEnrollment()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(450.dp).wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.5.dp, GeelyAccentCyan)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "កត់ត្រាសំឡេងផ្ទាល់ខ្លួន (On-Car Voice Enrollment)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "និយាយបញ្ជានេះជាភាសាខ្មែរឥឡូវនេះ (Speak command now)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = command.titleKm,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeelyAccentCyan
                        )
                        Text(
                            text = "\"${command.acceptedPhrasesKm.firstOrNull() ?: command.titleEn}\"",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Pulsing Recording Indicator
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GeelyCoralRed.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$countdownSeconds",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = GeelyCoralRed
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "កំពុងថត & ស្រង់យក MFCC + Delta Matrices...",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(onClick = onDismiss) {
                    Text("បោះបង់ / Cancel")
                }
            }
        }
    }
}

@Composable
fun VoiceSettingsSheet(
    voiceState: VoiceAssistantUiState,
    onDismiss: () -> Unit,
    onToggleWakeWord: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onGenderChange: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(550.dp).wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ការកំណត់ប្រព័ន្ធសំឡេង (Voice Settings)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Wake Word Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("បើកការស្តាប់ពាក្យបញ្ជា (Wake Word Listening)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("\"ហេឡូ ហ្គាឡាក់ស៊ី\" / \"Hello Galaxy\"", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = voiceState.wakeWordEnabled,
                        onCheckedChange = onToggleWakeWord,
                        colors = SwitchDefaults.colors(checkedThumbColor = GeelyAccentCyan)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Wake Sensitivity Slider (0.1 to 1.0)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("កម្រិតភាពប្រែប្រួលនៃការស្តាប់ (Wake Sensitivity)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${(voiceState.wakeSensitivity * 100).toInt()}%", fontSize = 12.sp, color = GeelyAccentCyan)
                    }
                    Slider(
                        value = voiceState.wakeSensitivity,
                        onValueChange = onSensitivityChange,
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = GeelyAccentCyan, activeTrackColor = GeelyAccentCyan)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Speech Readout Volume
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("កម្រិតសំឡេងឆ្លើយតប (Speech Readout Volume)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("${(voiceState.speechVolume * 100).toInt()}%", fontSize = 12.sp, color = GeelyEmeraldGreen)
                    }
                    Slider(
                        value = voiceState.speechVolume,
                        onValueChange = onVolumeChange,
                        valueRange = 0.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = GeelyEmeraldGreen, activeTrackColor = GeelyEmeraldGreen)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Gender Selection
                Text("ប្រភេទសំឡេង (Voice Gender)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = voiceState.voiceGender == "FEMALE",
                        onClick = { onGenderChange("FEMALE") },
                        label = { Text("ស្រី / Female") }
                    )
                    FilterChip(
                        selected = voiceState.voiceGender == "MALE",
                        onClick = { onGenderChange("MALE") },
                        label = { Text("ប្រុស / Male") }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GeelyAccentCyan)
                ) {
                    Text("រួចរាល់ / Done", fontWeight = FontWeight.Bold, color = GeelyNavyDark)
                }
            }
        }
    }
}
