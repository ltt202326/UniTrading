@Entry
@Component
struct Index {
  @State private selected: number = 0;

  build() {
    Column() {
      Row() {
        Text('UniTrading').fontSize(25).fontWeight(FontWeight.Bold).fontColor('#E6E9EF')
        Blank()
        Text('未连接').fontSize(14).fontColor('#ADB7C7')
      }.width('100%').padding({ left: 20, right: 20, top: 20, bottom: 20 })

      Column({ space: 12 }) {
        Text(this.selected === 0 ? '账户' : this.selected === 1 ? '行情' : this.selected === 2 ? '图表' : '设置')
          .fontSize(24).fontWeight(FontWeight.Medium).fontColor('#E6E9EF')
        Text('鸿蒙原生版正在适配。券商连接、账户和交易功能尚不可用。')
          .fontSize(15).fontColor('#ADB7C7').lineHeight(23)
        Text('请继续使用现有版本管理真实账户和订单。')
          .fontSize(14).fontColor('#ADB7C7').lineHeight(22)
      }.alignItems(HorizontalAlign.Start).width('100%').padding(20).layoutWeight(1)

      Row() {
        Button('账户').onClick(() => { this.selected = 0; }).layoutWeight(1).fontColor(this.selected === 0 ? '#A8C9FF' : '#ADB7C7').backgroundColor(Color.Transparent)
        Button('行情').onClick(() => { this.selected = 1; }).layoutWeight(1).fontColor(this.selected === 1 ? '#A8C9FF' : '#ADB7C7').backgroundColor(Color.Transparent)
        Button('图表').onClick(() => { this.selected = 2; }).layoutWeight(1).fontColor(this.selected === 2 ? '#A8C9FF' : '#ADB7C7').backgroundColor(Color.Transparent)
        Button('设置').onClick(() => { this.selected = 3; }).layoutWeight(1).fontColor(this.selected === 3 ? '#A8C9FF' : '#ADB7C7').backgroundColor(Color.Transparent)
      }.width('100%').padding({ left: 8, right: 8, bottom: 12 })
    }.height('100%').width('100%').backgroundColor('#101419')
  }
}
