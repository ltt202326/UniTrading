package com.unitrading.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unitrading.app.*
import com.unitrading.broker.*
import kotlinx.coroutines.launch

@Composable
internal fun CopyTradingDialog(runtime: TradingRuntime, broker: BrokerPlugin, leaderId: String, onDismiss: () -> Unit) {
    val status by runtime.copyTrading.status.collectAsState()
    val recovery by runtime.copyTrading.recovery.collectAsState()
    val scope = rememberCoroutineScope()
    val catalog by broker.catalog.collectAsState()
    val accounts = catalog.accounts.filter { it.id != leaderId && !it.readOnly }
    var selected by remember(broker, leaderId) { mutableStateOf(emptySet<String>()) }
    var symbol by remember(broker) { mutableStateOf("") }
    var multiplier by remember { mutableStateOf("1") }
    var maximum by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var confirmRecovery by remember { mutableStateOf(false) }
    val leader = catalog.accounts.firstOrNull { it.id == leaderId }
    val multiplierValue = multiplier.toIntOrNull()
    val maximumValue = maximum.toIntOrNull()
    val dated = catalog.instruments.firstOrNull { it.symbol == symbol.trim() && !it.isContinuous }
    val valid = leader != null && !leader.readOnly && selected.isNotEmpty() && dated != null &&
        multiplierValue != null && multiplierValue in 1..100 && maximumValue != null && maximumValue in 1..1000

    if (confirmRecovery) AlertDialog(
        onDismissRequest = { confirmRecovery = false },
        title = { Text("同步并核对待确认订单") },
        text = { Text("请先在券商端逐一检查原连接所有主账户和跟随账户的订单、成交及持仓，并处理完毕。确认后会重新同步，只有全部空仓且无挂单才能清除此记录。停止不会撤销已发送订单。") },
        confirmButton = { TextButton({
            confirmRecovery = false
            scope.launch { error = runtime.copyTrading.clearRecovery(broker) }
        }) { Text("已核对，重新同步") } },
        dismissButton = { TextButton({ confirmRecovery = false }) { Text("取消") } },
    )
    if (confirm && valid) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("确认启动 Demo 跟单") },
        text = { Text("主账户：${leader?.name}\n跟随账户：${accounts.filter { it.id in selected }.joinToString("、") { it.name }}\n合约：${dated?.symbol}；倍率：$multiplier；每个账户最多 $maximum 张。\n\nDemo 账户可能是考核账户。只复制成交后的净仓，使用市价单；不复制挂单、止盈止损。所有账户须空仓无挂单。") },
        confirmButton = { TextButton({
            val settings = CopyPlanSettings(leaderId, accounts.filter { it.id in selected }.map { CopyFollower(it.id, multiplierValue!!, maximumValue!!) }, dated!!.symbol)
            error = runtime.copyTrading.start(broker, settings)
            confirm = false
        }) { Text("确认启动") } },
        dismissButton = { TextButton({ confirm = false }) { Text("取消") } },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("账户跟单 · Demo 验证") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(status.message, color = if (status.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("仅支持同一 Tradovate Demo 登录下的账户。程序关闭、断线或异常后停止，不自动恢复。")
                recovery?.let { record ->
                    Text("有未确认订单：${record.symbol}，账户 ${record.accountIds.joinToString()}。请核对券商端。", color = MaterialTheme.colorScheme.error)
                    OutlinedButton({ confirmRecovery = true }) { Text("同步并核对") }
                }
                if (status.running) {
                    Text("当前主账户：${status.settings?.leaderId.orEmpty()} · ${status.settings?.symbol.orEmpty()} · ${status.settings?.followers?.size ?: 0} 个跟随账户")
                    OutlinedButton({ runtime.copyTrading.stop() }) { Text("停止跟单") }
                } else {
                    Text("主账户：${leader?.name ?: leaderId}")
                    if (broker.descriptor.environment != BrokerEnvironment.REMOTE_DEMO) Text("请切换到 Tradovate Demo 后使用。", color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(symbol, { symbol = it.trim().uppercase() }, label = { Text("到期合约，如 MESZ6") }, singleLine = true)
                    Text("选择跟随账户（只能在当前连接内选择）")
                    accounts.forEach { account ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(account.id in selected, { checked -> selected = if (checked) selected + account.id else selected - account.id })
                            Text(account.name, Modifier.padding(top = 12.dp))
                        }
                    }
                    if (accounts.isEmpty()) Text("当前连接没有其他可交易子账户")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(multiplier, { multiplier = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), label = { Text("倍率") }, singleLine = true)
                        OutlinedTextField(maximum, { maximum = it.filter(Char::isDigit).take(4) }, Modifier.weight(1f), label = { Text("最大张数") }, singleLine = true)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button({ confirm = true }, enabled = valid && broker.descriptor.environment == BrokerEnvironment.REMOTE_DEMO) { Text("准备启动") }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("关闭") } },
    )
}
