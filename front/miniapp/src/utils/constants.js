/* 微光 · 全局常量（前端常量，不入 sys_config）
   后端地址按运行环境分流，避免构建期 IP 过期后模拟器整体不可用：
   · 微信开发者工具模拟器：与后端同机，固定走 localhost:8080（不受构建期 IP 影响）
   · 真机预览/调试：走构建期注入的局域网地址 VITE_API_HOST
     （模板见 front/miniapp/.env.example；实际值由 scripts/detect-lan-host.mjs 在 npm run 前置钩子写入 .env.local，
      换 Wi‑Fi / 换电脑后重跑 npm run dev:mp-weixin 即可，该文件不入库，详见 README） */
const BUILD_HOST = import.meta.env.VITE_API_HOST || 'localhost:8080'

/* 微信开发者工具模拟器的 platform 为 'devtools'（真机为 ios / android） */
function isDevtools() {
  try {
    if (typeof uni === 'undefined') {
      return false
    }
    const device = typeof uni.getDeviceInfo === 'function' ? uni.getDeviceInfo() : null
    const platform = device ? device.platform : (uni.getSystemInfoSync().platform || '')
    return platform === 'devtools'
  } catch (e) {
    return false
  }
}

const API_HOST = isDevtools() ? 'localhost:8080' : BUILD_HOST
export const BASE_URL = `http://${API_HOST}`
export const WS_URL = `ws://${API_HOST}/ws`

export const STORAGE_KEYS = {
  TOKEN: 'wg_token',
  /** 首次进入紧急提示（详设 §6.1 三处常驻之①）已展示标记，只弹一次 */
  ENTRY_TIP_SHOWN: 'wg_entry_tip_shown',
  /** A1 发布流程跨页草稿：P30 选模板/自由描述 → P31 表单 之间传递（B30 草稿恢复后续复用） */
  PUBLISH_DRAFT: 'wg_publish_draft',
  /** P17 → 处理详情页的单条申诉/反馈传递（B：查看处理详情） */
  APPEAL_DETAIL: 'wg_appeal_detail'
}

/* 手机号：单 11 位大陆手机号（D52） */
export const MOBILE_PATTERN = /^1[3-9]\d{9}$/

/** 规范化手机号：去空格/横线、去 +86 前缀，便于通讯录号码与手输号码统一校验 */
export function normalizePhone(value) {
  if (!value) {
    return ''
  }
  return String(value).replace(/[\s-]/g, '').replace(/^\+?86/, '')
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
  PUBLISH_OK: '已发布，邻居们很快就能看到了。',
  /* 封禁拦截（详设 §6.5，20004 时替代错误码表 message 展示） */
  BANNED: "您的账号暂时被限制了，如有疑问可在'反馈与申诉'中留言。"
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
export const HELP_TAGS = ['跑腿', '维修', '家务', '陪伴', '散步', '教学', '网上办事', '宠物花草', '聊天', '其他']

/* 喜欢做的事（8 项多选，详设 §8.4：P02 画像填写与 P14 画像编辑共用） */
export const HOBBY_OPTIONS = ['聊天', '养花', '下棋', '散步', '做饭', '看书', '手机使用', '手工维修']

/* 求助类型与时间期望的中文文案 */
export const ORDER_TYPE_LABELS = {
  HOME_VISIT: '上门',
  ACCOMPANY: '外出',
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

/* 账本 reason_code 中文（详设 §2.11/§2.12 枚举，仅展示用） */
export const CREDIT_REASON_LABELS = {
  COMPLETE: '完成帮助',
  ACCEPT_CANCEL: '接单后取消',
  START_TIMEOUT: '超时未确认开始',
  MID_QUIT: '服务中失联或中途退出',
  URGENT_ABUSE: '滥用急标',
  MALICIOUS_CANCEL: '恶意取消',
  FAKE_ORDER: '虚假求助',
  APPEAL_LOSS: '仲裁败诉',
  ADMIN_ADJUST: '管理员人工调整',
  APPEAL_REVERT: '仲裁撤销扣分'
}

export const POINT_REASON_LABELS = {
  COMPLETE: '完成帮助'
}

/* O14 时间线事件中文（详设 §2.8 枚举，仅展示用） */
export const TIMELINE_LABELS = {
  PUBLISH: '发布求助',
  ACCEPT: '邻居接单',
  START: '开始帮忙',
  RELEASE: '释放重派',
  CANCEL: '已取消',
  CANCEL_REQUEST: '发起商量取消',
  CANCEL_CONFIRM_REJECT: '拒绝了商量取消',
  COMPLETE_REQUEST: '标记完成',
  CONFIRM: '确认完成',
  COMPLETE: '完成结算',
  EXPIRE: '超时未响应',
  APPEAL_OPEN: '进入申诉',
  ARBITRATE: '管理员裁决',
  ADMIN_ACTION: '管理员操作',
  REPUBLISH: '一键重发',
  MODIFY: '修改求助'
}

/* 紧急提示（详设 §6.1 三处常驻之一：发布页底部固定条） */
export const EMERGENCY_TIP = '如果情况紧急，请直接拨打 120 或 110'
