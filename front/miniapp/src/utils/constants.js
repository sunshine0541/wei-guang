/* 微光 · 全局常量（前端常量，不入 sys_config）
   真机预览时把 BASE_URL / WS_URL 换成后端所在局域网的 IP（详见 README） */
export const BASE_URL = 'http://localhost:8080'
export const WS_URL = 'ws://localhost:8080/ws'

export const STORAGE_KEYS = {
  TOKEN: 'wg_token'
}

/* 写死文案（详设 §6.1 空/加载/错误通用文案、§6.5） */
export const MESSAGES = {
  EMPTY: '这里还静悄悄的，去看看别的邻居吧',
  LOADING: '正在为您准备，请稍等',
  ERROR: '刚才没成功，别急，再试一次，我陪着你',
  NETWORK: '网络打了个盹，请检查网络后再试一次',
  FORBIDDEN: '这个页面暂时不能看哦',
  RATE_LIMIT: '您操作得有点快，喝口水歇一下，我马上就好',
  LOGIN_FAILED: '网络打了个盹，请再试一次，我在这儿等您。',
  PUBLISH_OK: '已发布，邻居们很快就能看到了。'
}

/* WebSocket（详设确认项 22：客户端 30s 心跳、退避重连 1s→2s→4s…上限 30s） */
export const WS_HEARTBEAT_INTERVAL = 30000
export const WS_MAX_BACKOFF = 30000

/* 一次帮助的写死文案（详设 §6.5） */
export const PAGE_PATHS = {
  INDEX: '/pages/index/index',
  PRIVACY: '/pages/guide/privacy',
  GUIDE: '/pages/guide/index'
}

/* 受控标签词表（详设 §8.4，10 项，与后端 HelpTagConstants 一致） */
export const HELP_TAGS = ['跑腿代买', '维修', '家务', '陪伴', '散步', '手机教学', '网上办事', '宠物花草', '聊天', '其他']

/* 求助类型与时间期望的中文文案 */
export const ORDER_TYPE_LABELS = {
  HOME_VISIT: '上门',
  ACCOMPANY: '陪同外出',
  REMOTE: '远程'
}

export const TIME_EXPECT_LABELS = {
  ASAP: '尽快',
  TODAY: '今天内',
  TOMORROW: '明天',
  THIS_WEEK: '本周内',
  ANY: '不限'
}

export const TIME_EXPECT_OPTIONS = ['ASAP', 'TODAY', 'TOMORROW', 'THIS_WEEK', 'ANY']

/* 紧急提示（详设 §6.1 三处常驻之一：发布页底部固定条） */
export const EMERGENCY_TIP = '如果情况紧急，请直接拨打 120 或 110'
