# 跟单 Demo 验证版

基于 PR #11 的开发版。入口为「设置 → 交易 → 账户跟单」。只支持同一个 Tradovate Demo 登录内的子账户、一个具体到期合约和整数倍率。Demo 也可能是考核账户；请先用不影响考核结果的模拟账户验证。本版不支持 Live、跨登录、挂单复制或止盈止损同步。

启动时要求主账户和跟随账户全部空仓无挂单；启动确认列出账户、合约、倍率和手数上限。只在主账户成交后按净仓变化向跟随账户发市价单。跟随账户依次处理，反手先平仓，券商确认对应订单成交和目标仓位后才允许下一单。走账户风控；风控拒绝、行情不可用、账户不完整、保护单、挂单、断线、未知结果、成交超时或仓位偏差均停止且不重发，用户应逐一核对。退出、程序重建或网络中断后不自动恢复；停止不会撤销已经发出的订单。

订单执行条件使用同一券商账本的快照版本，在命令发送前由 Tradovate 适配器复核；订单标记为自动交易。跟单模式下不允许跟随账户的金额保护自动改写订单。券商数据模型、行情及事件仍需在真实 Tradovate Demo 环境逐项验证；模拟器通过不代表券商支持或实盘可靠。本功能未授权在 Live 使用。

参考：[VIP Copy 更新日志](https://github.com/Mind2Money/PropfirmVIPCopyUpdates/blob/main/CHANGELOG.md)、[Localfill](https://github.com/Youcxf/Localfill)、[Futures Broker CopyTrader](https://github.com/abdulxahmed/futures-broker-copytrader)。
