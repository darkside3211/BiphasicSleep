package com.darkside3211.biphasicsleep.ui

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkside3211.biphasicsleep.alarm.AlarmRingingService
import com.darkside3211.biphasicsleep.alarm.HybridAlarmScheduler
import com.darkside3211.biphasicsleep.challenge.BarcodeScannerChallengeView
import com.darkside3211.biphasicsleep.challenge.StepCounterChallengeController
import com.darkside3211.biphasicsleep.domain.ProtocolStateId
import com.darkside3211.biphasicsleep.domain.SleepBlockType

/**
 * Full-Screen Wake-Up Confirmation & Anti-Backslide Challenge Activity.
 * Designed with high-contrast bedside ergonomics (Oura / Whoop dark aesthetic) and zero emojis.
 */
class AlarmChallengeActivity : ComponentActivity() {

    private lateinit var stepController: StepCounterChallengeController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenFlags()

        stepController = StepCounterChallengeController(applicationContext)

        val blockName = intent.getStringExtra(HybridAlarmScheduler.EXTRA_BLOCK_TYPE)
            ?: SleepBlockType.FLUSH_NAP_20M.name
        val stateName = intent.getStringExtra(HybridAlarmScheduler.EXTRA_STATE_ID)
            ?: ProtocolStateId.STATE_WORK_NAP.name
        val isAntiBackslide = intent.getBooleanExtra(
            HybridAlarmScheduler.EXTRA_IS_ANTI_BACKSLIDE,
            false
        )
        val requireSteps = intent.getBooleanExtra(
            HybridAlarmScheduler.EXTRA_REQUIRE_STEPS,
            true
        )
        val requiredStepCount = intent.getIntExtra(
            HybridAlarmScheduler.EXTRA_REQUIRED_STEP_COUNT,
            15
        )
        val requireBarcode = intent.getBooleanExtra(
            HybridAlarmScheduler.EXTRA_REQUIRE_BARCODE,
            false
        )
        val registeredBarcode = intent.getStringExtra(
            HybridAlarmScheduler.EXTRA_REGISTERED_BARCODE
        )
        val enableAntiBackslide = intent.getBooleanExtra(
            HybridAlarmScheduler.EXTRA_ENABLE_ANTI_BACKSLIDE,
            true
        )
        val antiBackslideMinutes = intent.getIntExtra(
            HybridAlarmScheduler.EXTRA_ANTI_BACKSLIDE_MINUTES,
            5
        )

        val blockType = runCatching { SleepBlockType.valueOf(blockName) }
            .getOrDefault(SleepBlockType.FLUSH_NAP_20M)
        val stateId = runCatching { ProtocolStateId.valueOf(stateName) }
            .getOrDefault(ProtocolStateId.STATE_WORK_NAP)

        setContent {
            BiphasicSleepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBackground
                ) {
                    AlarmChallengeScreen(
                        blockType = blockType,
                        stateId = stateId,
                        isAntiBackslideCheck = isAntiBackslide,
                        requireSteps = requireSteps,
                        requiredStepCount = requiredStepCount,
                        requireBarcode = requireBarcode,
                        registeredBarcode = registeredBarcode,
                        enableAntiBackslideFollowUp = enableAntiBackslide,
                        antiBackslideMinutes = antiBackslideMinutes,
                        stepController = stepController,
                        onDismissVerified = {
                            AlarmRingingService.stop(applicationContext)
                            if (isAntiBackslide) {
                                HybridAlarmScheduler.confirmAntiBackslideAwake(applicationContext)
                            } else if (enableAntiBackslide) {
                                HybridAlarmScheduler.scheduleAntiBackslideCheckIn(
                                    context = applicationContext,
                                    sourceBlockTitle = "${stateId.title} (${blockType.shortTitle})",
                                    delayMinutes = antiBackslideMinutes,
                                    requireSteps = requireSteps,
                                    requiredStepCount = requiredStepCount,
                                    requireBarcode = requireBarcode,
                                    registeredBarcode = registeredBarcode
                                )
                            } else {
                                HybridAlarmScheduler.cancelArmedAlarm(applicationContext)
                            }
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun configureLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun AlarmChallengeScreen(
    blockType: SleepBlockType,
    stateId: ProtocolStateId,
    isAntiBackslideCheck: Boolean,
    requireSteps: Boolean,
    requiredStepCount: Int,
    requireBarcode: Boolean,
    registeredBarcode: String?,
    enableAntiBackslideFollowUp: Boolean,
    antiBackslideMinutes: Int,
    stepController: StepCounterChallengeController,
    onDismissVerified: () -> Unit
) {
    val stepsDetected by stepController.stepsDetected.collectAsState()
    val sensorLabel by stepController.sensorSourceLabel.collectAsState()
    var barcodeVerifiedValue by remember { mutableStateOf<String?>(null) }

    DisposableEffect(requireSteps) {
        if (requireSteps) {
            stepController.startListening()
        }
        onDispose {
            stepController.stopListening()
        }
    }

    val stepsSatisfied = !requireSteps || stepsDetected >= requiredStepCount
    val barcodeSatisfied = !requireBarcode || barcodeVerifiedValue != null
    val allChallengesSatisfied = stepsSatisfied && barcodeSatisfied

    val headerAccent = if (isAntiBackslideCheck) LockoutCrimson else CircadianAmber
    val headerBg = if (isAntiBackslideCheck) LockoutCrimsonBg else Color(0xFF26190B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // High-Contrast Header Card
        BiometricCard(
            borderColor = headerAccent,
            backgroundColor = headerBg
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isAntiBackslideCheck) Icons.Rounded.History else Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = headerAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAntiBackslideCheck) {
                            "ANTI-BACKSLIDE 5M CHECK-IN"
                        } else {
                            "CIRCADIAN WAKE CHALLENGE"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = headerAccent,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${stateId.title} • ${blockType.shortTitle}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isAntiBackslideCheck) {
                    "Prevent the Accidental Nap Trap: Confirm you have physically left the bed and are not falling back into slow-wave sleep."
                } else {
                    stateId.systemRulesSummary
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Challenge 1: Physical Step Counter
        if (requireSteps) {
            val progress = (stepsDetected.toFloat() / requiredStepCount.coerceAtLeast(1).toFloat())
                .coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(500),
                label = "StepGaugeProgress"
            )

            BiometricCard(
                borderColor = if (stepsSatisfied) VerifiedEmerald else AnchorCyan,
                backgroundColor = ObsidianCard
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.DirectionsWalk,
                            contentDescription = null,
                            tint = if (stepsSatisfied) VerifiedEmerald else AnchorCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Physical Step Challenge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (stepsSatisfied) VerifiedEmerald else AnchorCyan
                        )
                    }

                    if (stepsSatisfied) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = VerifiedEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VERIFIED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = VerifiedEmerald
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Centered Circular Progress Gauge with Step Count
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.size(130.dp),
                        strokeWidth = 10.dp,
                        color = if (stepsSatisfied) VerifiedEmerald else AnchorCyan,
                        trackColor = ObsidianBorderSubtle
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$stepsDetected",
                            style = MaterialTheme.typography.displayLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "of $requiredStepCount steps",
                            style = MaterialTheme.typography.labelSmall,
                            color = SoftSilver
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Sensor: $sensorLabel — Walk out of bed to register steps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = { stepController.recordSimulatedStep() },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+1 Simulated Step", color = AnchorCyan, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // Challenge 2: QR / Barcode Scanner
        if (requireBarcode) {
            BiometricCard(
                borderColor = if (barcodeSatisfied) VerifiedEmerald else AnchorCyan,
                backgroundColor = ObsidianCard
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = if (barcodeSatisfied) VerifiedEmerald else AnchorCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "QR / Barcode Challenge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (barcodeSatisfied) VerifiedEmerald else AnchorCyan
                        )
                    }

                    if (barcodeSatisfied) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = VerifiedEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SCANNED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = VerifiedEmerald
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (barcodeSatisfied) {
                    Text(
                        text = "Scanned Code: $barcodeVerifiedValue",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = VerifiedEmerald
                    )
                } else {
                    BarcodeScannerChallengeView(
                        expectedBarcode = registeredBarcode,
                        onBarcodeVerified = { code ->
                            barcodeVerifiedValue = code
                        }
                    )
                }
            }
        }

        // Follow-up Anti-Backslide Notice
        if (!isAntiBackslideCheck && enableAntiBackslideFollowUp) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, CircadianAmber.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                color = Color(0xFF22170D)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = CircadianAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Secondary ${antiBackslideMinutes}-Minute Check-In Armed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CircadianAmber
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "After stopping this alarm, a follow-up check-in will trigger in $antiBackslideMinutes minutes. Confirm awake on the app or alarm sounds again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Full-Width 60dp Bedside Action Button
        Button(
            onClick = onDismissVerified,
            enabled = allChallengesSatisfied,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allChallengesSatisfied) VerifiedEmerald else Color(0xFF1E283D),
                disabledContainerColor = Color(0xFF131B2A)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (allChallengesSatisfied) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        !allChallengesSatisfied -> "COMPLETE CHALLENGES TO DISMISS ALARM"
                        isAntiBackslideCheck -> "CONFIRM OUT OF BED — DISMISS"
                        enableAntiBackslideFollowUp -> "DISMISS & START ${antiBackslideMinutes}M CHECK-IN"
                        else -> "DISMISS ALARM — AWAKE VERIFIED"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (allChallengesSatisfied) Color.Black else MutedSlate
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
