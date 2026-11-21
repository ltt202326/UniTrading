package com.unitrading.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unitrading.app.*
import com.unitrading.broker.*

private data class CopyAccountOption(val key: CopyAccountKey, val label: String, val ready: Boolean)
private data class CopyFollowerDraft(val selected: Boolean = false, val size: CopyContractSize = CopyContractSize.MICRO,
    val multiplier: String = "1", val maximum: String = "1")

/** Group editor has no execution side effects; wiring it to the broker requires a verified copier. */
@Composable
internal fun CopyGroupEditor(registry: BrokerRegistry, onReview: (CopyGroupConfiguration) -> Unit) {
    val plugins by registry.state.collectAsState()
    val options = mutableListOf<CopyAccountOption>()
    for (broker in plugins.filter { it.descriptor.environment == BrokerEnvironment.REMOTE_DEMO && it.descriptor.id.startsWith("tradovate-") }) {
        val catalog by key(broker) { broker.catalog.collectAsState() }
        val market by key(broker) { broker.market.collectAsState() }
        catalog.accounts.filterNot { it.readOnly }.forEach { account ->
            options += CopyAccountOption(CopyAccountKey(broker.descriptor.id, account.id), "${broker.descriptor.name} · ${account.name}",
                market.connected && market.tradingReady)
        }
    }
    var selectedLeader by remember { mutableStateOf<CopyAccountKey?>(null) }
    var dropdown by remember { mutableStateOf(false) }
    var drafts by remember { mutableStateOf(emptyMap<CopyAccountKey, CopyFollowerDraft>()) }
    val leader = options.firstOrNull { it.key == selectedLeader }
    val followers = options.filter { it.key.accountId != leader?.key?.accountId }
    val selected = followers.filter { drafts[it.key]?.selected == true }
    fun follower(option: CopyAccountOption): CopyFollowerConfiguration? {
        val draft = drafts[option.key] ?: return null
        val multiplier = parseFixed(draft.multiplier, 2)?.takeIf { it in 1..10_000 }?.toInt() ?: return null
        val maximum = draft.maximum.toIntOrNull()?.takeIf { it in 1..1_000 } ?: return null
        return CopyFollowerConfiguration(option.key, multiplier, draft.size, maximum)
    }
    val configured = selected.mapNotNull(::follower)
    val proposal = leader?.let { CopyGroupConfiguration(it.key, configured) }
    val ready = leader?.ready == true && selected.isNotEmpty() && selected.all { it.ready } &&
        configured.size == selected.size && proposal?.valid() == true

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("主账户", style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton({ dropdown = true }) { Text(leader?.label ?: "选择主账户") }
            DropdownMenu(expanded = dropdown, onDismissRequest = { dropdown = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, enabled = option.ready,
                    onClick = { selectedLeader = option.key; dropdown = false }) }
            }
        }
        Text("跟随账户", style = MaterialTheme.typography.titleSmall)
        followers.forEach { option ->
            val draft = drafts[option.key] ?: CopyFollowerDraft()
            fun change(next: CopyFollowerDraft) { drafts = drafts + (option.key to next) }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(draft.selected, { change(draft.copy(selected = it)) }, enabled = option.ready)
                    Text(option.label)
                }
                if (draft.selected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(draft.size == CopyContractSize.MICRO, { change(draft.copy(size = CopyContractSize.MICRO)) }, label = { Text("Micro") })
                        FilterChip(draft.size == CopyContractSize.STANDARD, { change(draft.copy(size = CopyContractSize.STANDARD)) }, label = { Text("Standard") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(draft.multiplier, { change(draft.copy(multiplier = it.take(7))) }, Modifier.weight(1f), label = { Text("倍率") }, singleLine = true)
                        OutlinedTextField(draft.maximum, { change(draft.copy(maximum = it.filter(Char::isDigit).take(4))) }, Modifier.weight(1f), label = { Text("最大张数") }, singleLine = true)
                    }
                }
            }
        }
        Text("倍率按主账户下单张数计算，小数向上取整。Standard 合约的每张风险可能显著高于 Micro 或 Nano。",
            style = MaterialTheme.typography.bodySmall)
        Button({ proposal?.let(onReview) }, enabled = ready) { Text("核对跟单设置") }
    }
}
