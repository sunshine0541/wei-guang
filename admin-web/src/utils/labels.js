/* 管理端展示用中文映射（详设 §2.6/§2.8 枚举） */

export const ORDER_STATUS_LABELS = {
  PENDING: '待响应',
  ACCEPTED: '已接单',
  IN_SERVICE: '服务中',
  PENDING_CONFIRM: '待确认',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXPIRED: '已超时',
  APPEALING: '申诉中'
}

export const ORDER_TYPE_LABELS = {
  HOME_VISIT: '上门',
  ACCOMPANY: '陪同外出',
  REMOTE: '远程'
}

export const USER_STATUS_LABELS = {
  NORMAL: '正常',
  BANNED: '已封禁',
  CANCELLED: '已注销'
}

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
  APPEAL_WITHDRAW: '撤回申诉',
  APPEAL_TIMEOUT: '申诉超时恢复',
  ARBITRATE: '管理员裁决',
  ADMIN_ACTION: '管理员操作',
  REPUBLISH: '一键重发'
}

export const OPERATOR_ROLE_LABELS = {
  SYSTEM: '系统',
  REQUESTER: '发布者',
  HELPER: '帮助者',
  ADMIN: '管理员'
}

export const APPEAL_TYPE_LABELS = {
  CONFIRM_REJECT: '确认被拒',
  POST_ORDER: '事后申诉',
  FEEDBACK: '反馈'
}

export const APPEAL_STATUS_LABELS = {
  PENDING: '待处理',
  RESOLVED: '已处理',
  WITHDRAWN: '已撤回'
}

export const LIGHT_STATE_LABELS = {
  UNLIT: '未点亮',
  LIT: '已点亮',
  STEADY: '常亮'
}

export const POINT_REASON_LABELS = {
  COMPLETE: '完成帮助',
  APPEAL_REVERT: '仲裁追回'
}

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

export const REPORT_STATUS_LABELS = {
  PENDING: '待处置',
  CONFIRMED: '确认违规',
  REJECTED: '判定误报'
}