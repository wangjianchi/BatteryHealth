package com.batteryhealth.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryhealth.app.ui.theme.*
import com.batteryhealth.app.viewmodel.BatteryViewModel
import com.batteryhealth.app.viewmodel.RecordFormState

@Composable
fun RecordScreen(viewModel: BatteryViewModel) {
    val formState by viewModel.formState.collectAsState()

    // Show success snackbar
    formState.successMessage?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(2500)
            viewModel.clearSuccessMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Success banner
        formState.successMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = BatteryGreenDim)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = BatteryGreen, modifier = Modifier.size(18.dp))
                    Text(msg, fontSize = 13.sp, color = BatteryGreen, lineHeight = 18.sp)
                }
            }
        }

        // Form card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "⚡ 新建充电记录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface
                )

                // Rated capacity
                BatteryTextField(
                    value = formState.ratedCapacity,
                    onValueChange = { viewModel.updateForm(ratedCapacity = it) },
                    label = "设备标称容量 (mAh)",
                    placeholder = "如：5000",
                    hint = "可在手机背面、说明书或官网查询",
                    keyboardType = KeyboardType.Number,
                    leadingIcon = { Icon(Icons.Default.BatteryFull, null, tint = BatteryGreen, modifier = Modifier.size(18.dp)) }
                )

                // Before / After row
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BatteryTextField(
                        modifier = Modifier.weight(1f),
                        value = formState.batteryBefore,
                        onValueChange = { viewModel.updateForm(batteryBefore = it) },
                        label = "充电前电量 (%)",
                        placeholder = "如：15",
                        keyboardType = KeyboardType.Number
                    )
                    BatteryTextField(
                        modifier = Modifier.weight(1f),
                        value = formState.batteryAfter,
                        onValueChange = { viewModel.updateForm(batteryAfter = it) },
                        label = "充电后电量 (%)",
                        placeholder = "如：95",
                        keyboardType = KeyboardType.Number
                    )
                }

                // Duration
                BatteryTextField(
                    value = formState.chargingMinutes,
                    onValueChange = { viewModel.updateForm(chargingMinutes = it) },
                    label = "充电时长 (分钟)",
                    placeholder = "如：90",
                    keyboardType = KeyboardType.Number,
                    leadingIcon = { Icon(Icons.Default.Timer, null, tint = BatteryBlue, modifier = Modifier.size(18.dp)) }
                )

                // Watt (optional)
                BatteryTextField(
                    value = formState.chargingWatt,
                    onValueChange = { viewModel.updateForm(chargingWatt = it) },
                    label = "充电功率 (W) — 可选",
                    placeholder = "如：65（留空则自动估算）",
                    hint = "在充电器标签上查看额定输出功率",
                    keyboardType = KeyboardType.Decimal,
                    leadingIcon = { Icon(Icons.Default.FlashOn, null, tint = BatteryYellow, modifier = Modifier.size(18.dp)) }
                )

                // Note
                BatteryTextField(
                    value = formState.note,
                    onValueChange = { viewModel.updateForm(note = it) },
                    label = "备注",
                    placeholder = "如：慢充、旅行充电器…",
                    keyboardType = KeyboardType.Text,
                    leadingIcon = { Icon(Icons.Default.Notes, null, tint = OnSurfaceVariant, modifier = Modifier.size(18.dp)) }
                )

                // Error message
                formState.errorMessage?.let { err ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = BatteryRedDim)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Warning, null, tint = BatteryRed, modifier = Modifier.size(16.dp))
                            Text(err, fontSize = 12.sp, color = BatteryRed)
                        }
                    }
                }

                // Submit button
                Button(
                    onClick = { viewModel.submitRecord() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BatteryPurple),
                    enabled = !formState.isLoading
                ) {
                    if (formState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = OnSurface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("保存记录并计算", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Calculation principle card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Surface2)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "📐 计算原理",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "基础公式：估算容量 = 充电量% × 标称容量 ÷ 充电效率系数（0.85–0.90）\n\n" +
                            "若提供功率：通过 功率 × 时长 交叉校验，提升精度。\n\n" +
                            "健康度 = 估算容量 ÷ 标称容量 × 100%\n\n" +
                            "多次记录取加权平均，越新的数据权重越高。",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun BatteryTextField(
    modifier: Modifier = Modifier,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    hint: String? = null,
    keyboardType: KeyboardType,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, fontSize = 14.sp, color = OnSurfaceVariant.copy(alpha = 0.5f)) },
            leadingIcon = leadingIcon,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BatteryPurple,
                unfocusedBorderColor = Border,
                focusedContainerColor = Surface2,
                unfocusedContainerColor = Surface2,
                focusedTextColor = OnSurface,
                unfocusedTextColor = OnSurface
            )
        )
        hint?.let {
            Text(
                text = it,
                fontSize = 11.sp,
                color = OnSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}
