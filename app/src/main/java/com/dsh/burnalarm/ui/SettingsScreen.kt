package com.dsh.burnalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dsh.burnalarm.R
import com.dsh.burnalarm.burn.LlmClient
import com.dsh.burnalarm.data.AppSettings
import com.dsh.burnalarm.data.BurnMode
import com.dsh.burnalarm.i18n.AppLocale
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AlarmViewModel,
    onBack: () -> Unit
) {
    val stored by viewModel.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var loaded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(AppSettings()) }

    var keyVisible by remember { mutableStateOf(false) }
    var showCostWarning by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(stored) {
        val real = stored
        // 必须等真实配置读出来（stored != null）才填草稿。
        // 否则会把占位默认值写回磁盘，把用户的 API Key 冲掉。
        if (!loaded && real != null) {
            draft = real
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.common_settings)) },
                navigationIcon = {
                    IconButton(onClick = {
                        // 没读到真实配置之前不要写盘，否则会拿默认值把用户的 Key 冲掉
                        if (loaded) viewModel.saveSettings(draft)
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {

            Spacer(Modifier.height(8.dp))

            // ---------------- 语言 ----------------
            SectionLabel(stringResource(R.string.settings_language))
            Text(
                text = stringResource(R.string.settings_language_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppLocale.OPTIONS.forEach { tag ->
                    FilterChip(
                        selected = AppLocale.currentTag() == tag,
                        onClick = { AppLocale.apply(tag) },
                        label = { Text(stringResource(AppLocale.labelRes(tag))) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---------------- 花钱警告 ----------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.settings_cost_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_cost_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ---------------- 接口 ----------------
            SectionLabel(stringResource(R.string.settings_api_section))

            OutlinedTextField(
                value = draft.baseUrl,
                onValueChange = { draft = draft.copy(baseUrl = it.trim()) },
                label = { Text("Base URL") },
                placeholder = { Text("https://api.deepseek.com") },
                supportingText = { Text(stringResource(R.string.settings_base_url_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (draft.baseUrl.startsWith("http://", ignoreCase = true)) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_cleartext_warn),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = draft.apiKey,
                onValueChange = { draft = draft.copy(apiKey = it.trim()) },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (keyVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { keyVisible = !keyVisible }) {
                        Icon(
                            if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "显示/隐藏"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ---------------- 隐私声明 ----------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.settings_privacy_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = draft.model,
                onValueChange = { draft = draft.copy(model = it.trim()) },
                label = { Text(stringResource(R.string.settings_model)) },
                placeholder = { Text("deepseek-chat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    testing = true
                    testResult = null
                    scope.launch {
                        testResult = runCatching {
                            val r = LlmClient(context).chat(
                                settings = draft,
                                systemPrompt = context.getString(R.string.settings_test_system),
                                userPrompt = context.getString(R.string.settings_test_user),
                                maxTokens = 16
                            )
                            val cost = r.promptTokens / 1_000_000.0 * draft.inputPricePerMillion +
                                r.completionTokens / 1_000_000.0 * draft.outputPricePerMillion
                            context.getString(
                                R.string.settings_test_ok,
                                r.content.trim().take(20),
                                r.promptTokens + r.completionTokens,
                                String.format(Locale.US, "%.6f", cost)
                            )
                        }.getOrElse { "✗ ${it.message}" }
                        testing = false
                    }
                },
                enabled = !testing && draft.apiKey.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (testing) R.string.settings_testing
                        else R.string.settings_test_connection
                    )
                )
            }

            testResult?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it.startsWith("✓")) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---------------- 计价 ----------------
            SectionLabel(stringResource(R.string.settings_pricing))
            Text(
                text = stringResource(R.string.settings_pricing_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = draft.inputPricePerMillion.toString(),
                    onValueChange = {
                        it.toDoubleOrNull()?.let { v -> draft = draft.copy(inputPricePerMillion = v) }
                    },
                    label = { Text(stringResource(R.string.settings_price_in)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.outputPricePerMillion.toString(),
                    onValueChange = {
                        it.toDoubleOrNull()?.let { v -> draft = draft.copy(outputPricePerMillion = v) }
                    },
                    label = { Text(stringResource(R.string.settings_price_out)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---------------- 焚烧策略 ----------------
            SectionLabel(stringResource(R.string.settings_burn_mode))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BurnMode.entries.forEach { mode ->
                    FilterChip(
                        selected = draft.burnMode == mode,
                        onClick = { draft = draft.copy(burnMode = mode) },
                        label = {
                            Column {
                                Text(mode.label())
                                Text(
                                    mode.description(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionLabel(stringResource(R.string.settings_max_tokens, draft.maxOutputTokens))
            Text(
                text = stringResource(R.string.settings_max_tokens_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = draft.maxOutputTokens.toFloat(),
                onValueChange = { draft = draft.copy(maxOutputTokens = it.roundToInt()) },
                valueRange = 256f..8192f,
                steps = 30,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            SectionLabel(stringResource(R.string.settings_interval, draft.burnIntervalSeconds))
            Slider(
                value = draft.burnIntervalSeconds.toFloat(),
                onValueChange = { draft = draft.copy(burnIntervalSeconds = it.roundToInt()) },
                valueRange = 1f..60f,
                steps = 58,
                modifier = Modifier.fillMaxWidth()
            )

            // 按当前设置粗算一下烧钱速度，让人有直观感受
            val perCallTokens = draft.maxOutputTokens
            val perMinuteCalls = 60.0 / draft.burnIntervalSeconds.coerceAtLeast(1)
            val perHourCost = perCallTokens / 1_000_000.0 * draft.outputPricePerMillion *
                perMinuteCalls * 60

            Text(
                text = stringResource(
                    R.string.settings_hourly_estimate,
                    String.format(Locale.US, "%.2f", perHourCost)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.settings_speak_roast),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.settings_speak_roast_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.speakRoast,
                    onCheckedChange = { draft = draft.copy(speakRoast = it) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---------------- 跑飞保护 ----------------
            SectionLabel(stringResource(R.string.settings_guard))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.settings_guard_body1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_guard_body2),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = draft.runawayGuardMinutes.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let { v -> draft = draft.copy(runawayGuardMinutes = v.coerceIn(0, 1440)) }
                },
                label = { Text(stringResource(R.string.settings_guard_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            if (draft.runawayGuardMinutes == 0) R.string.settings_guard_off
                            else R.string.settings_guard_on
                        )
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = {
                    if (!draft.costWarningAccepted) {
                        showCostWarning = true
                    } else {
                        viewModel.saveSettings(draft)
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(stringResource(R.string.settings_save), fontSize = 17.sp)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showCostWarning) {
        AlertDialog(
            onDismissRequest = { showCostWarning = false },
            title = { Text(stringResource(R.string.settings_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_confirm_body,
                        draft.model.ifBlank { stringResource(R.string.settings_model_fallback) }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    draft = draft.copy(costWarningAccepted = true)
                    viewModel.saveSettings(draft)
                    showCostWarning = false
                    onBack()
                }) { Text(stringResource(R.string.settings_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showCostWarning = false }) {
                    Text(stringResource(R.string.settings_confirm_no))
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    )
}
