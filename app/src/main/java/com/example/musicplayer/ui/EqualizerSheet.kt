@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package com.example.musicplayer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.musicplayer.player.TenBandEqualizerProcessor
import kotlin.math.roundToInt

/** 10 段均衡器面板。每段增益独立 state，拖动一根只重组那一个滑块。 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun EqualizerSheet(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    // 每段一个独立 state：避免"改一段、10 个滑块全重组"
    val bandStates = remember {
        List(TenBandEqualizerProcessor.BAND_COUNT) { mutableFloatStateOf(0f) }
    }
    val preamp = remember { mutableFloatStateOf(0f) }

    // 打开时把 ViewModel 里的当前设置灌进来
    LaunchedEffect(Unit) {
        viewModel.eqBands.forEachIndexed { i, v ->
            if (i in bandStates.indices) bandStates[i].floatValue = v
        }
        preamp.floatValue = viewModel.eqPreampDb
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // ---------------- 标题 + 总开关 ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("10 段均衡器", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "31Hz – 16kHz · 每段 ±${TenBandEqualizerProcessor.MAX_DB.toInt()}dB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = viewModel.eqEnabled,
                onCheckedChange = viewModel::updateEqEnabled
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------------- 预设 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TenBandEqualizerProcessor.PRESETS.forEach { preset ->
                FilterChip(
                    selected = false,
                    onClick = {
                        viewModel.applyEqPreset(preset)
                        preset.bandsDb.forEachIndexed { i, v ->
                            if (i in bandStates.indices) bandStates[i].floatValue = v
                        }
                        preamp.floatValue = preset.preampDb
                    },
                    label = { Text(preset.name, fontSize = 13.sp) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------------- 10 段增益滑块 ----------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            for (band in 0 until TenBandEqualizerProcessor.BAND_COUNT) {
                BandColumn(
                    modifier = Modifier.weight(1f),
                    freqLabel = bandLabel(band),
                    value = bandStates[band].floatValue,
                    enabled = viewModel.eqEnabled,
                    onValueChange = { newValue ->
                        bandStates[band].floatValue = newValue
                        viewModel.setEqBandGain(band, newValue)
                    }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---------------- 预衰减 ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("预衰减", fontSize = 13.sp, modifier = Modifier.width(56.dp))
            Slider(
                value = preamp.floatValue,
                onValueChange = {
                    preamp.floatValue = it
                    viewModel.setEqPreamp(it)
                },
                valueRange = TenBandEqualizerProcessor.MIN_PREAMP_DB..TenBandEqualizerProcessor.MAX_PREAMP_DB,
                enabled = viewModel.eqEnabled,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${preamp.floatValue.roundToInt()}dB",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(52.dp),
                textAlign = TextAlign.End
            )
        }

        Spacer(Modifier.height(4.dp))

        // ---------------- 底部操作 ----------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    viewModel.applyEqPreset(TenBandEqualizerProcessor.FLAT)
                    bandStates.forEach { it.floatValue = 0f }
                    preamp.floatValue = 0f
                }
            ) { Text("重置") }

            TextButton(onClick = onDismiss) { Text("完成") }
        }
    }
}

/** 单段：数值在上，竖向滑块，频点在下 */
@Composable
private fun BandColumn(
    modifier: Modifier = Modifier,
    freqLabel: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = modifier
    ) {
        Text(
            text = "${value.roundToInt()}",
            fontSize = 11.sp,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            }
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier.size(width = 28.dp, height = 150.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = TenBandEqualizerProcessor.MIN_DB..TenBandEqualizerProcessor.MAX_DB,
                enabled = enabled,
                modifier = Modifier
                    .requiredSize(width = 120.dp, height = 26.dp)
                    .rotate(-90f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    disabledActiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = freqLabel,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun bandLabel(band: Int): String = when (band) {
    0 -> "31"
    1 -> "62"
    2 -> "125"
    3 -> "250"
    4 -> "500"
    5 -> "1K"
    6 -> "2K"
    7 -> "4K"
    8 -> "8K"
    9 -> "16K"
    else -> ""
}
