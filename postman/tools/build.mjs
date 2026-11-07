// 从规格生成可导入的 Postman Collection v2.1 + 环境文件
// 运行：node postman/tools/build.mjs
import fs from 'node:fs';
import path from 'node:path';

const BASE = '{{baseUrl}}';

// ---------- 规格：folders -> items ----------
// item: {name, method, path, auth, body|rawBody, preScript, expect:{http,code,nonzero,raw,checks[],responseTimeBelow}, save{}}
const folders = [];
const F = (name) => { const f = { name, items: [] }; folders.push(f); return (...items) => items.forEach((it) => f.items.push(it)); };

// 00 初始化：管理员/演示账号登录 + 调整测试环境限流
F('00 初始化（登录与测试环境准备）')(
  { name: '00-1 管理端登录', method: 'POST', path: '/admin/auth/login', body: { username: 'admin', password: 'weiguang@2026' },
    expect: { checks: [{ path: 'data.token', type: 'string' }, { path: 'data.username', equals: 'admin' }] }, save: { adminToken: 'data.token' } },
  { name: '00-2 调整发布限流(测试环境)', method: 'PUT', path: '/admin/configs/rate.publish_per_minute', auth: 'adminToken', body: { value: '100' }, expect: {} },
  { name: '00-3 调整登录限流(测试环境)', method: 'PUT', path: '/admin/configs/rate.login_per_minute', auth: 'adminToken', body: { value: '100' }, expect: {} },
  { name: '00-4 调整发言限流(测试环境)', method: 'PUT', path: '/admin/configs/rate.chat_per_minute', auth: 'adminToken', body: { value: '200' }, expect: {} },
  { name: '00-5 设置待响应上限(测试=2)', method: 'PUT', path: '/admin/configs/order.pending_limit', auth: 'adminToken', body: { value: '2' }, expect: {} },
  { name: '00-6 登录-李秀英(A)', method: 'POST', path: '/api/auth/login', body: { username: '13867429153', password: 'wg123456' },
    expect: { checks: [{ path: 'data.token', type: 'string' }, { path: 'data.profile.nickname', equals: '李秀英' }] }, save: { tokenA: 'data.token' } },
  { name: '00-7 登录-陈志远(B)', method: 'POST', path: '/api/auth/login', body: { username: '15867132409', password: 'wg123456' },
    expect: { checks: [{ path: 'data.token', type: 'string' }] }, save: { tokenB: 'data.token' } },
  { name: '00-8 登录-王德明(C)', method: 'POST', path: '/api/auth/login', body: { username: '13957068214', password: 'wg123456' },
    expect: { checks: [{ path: 'data.token', type: 'string' }] }, save: { tokenC: 'data.token' } },
  { name: '00-9 登录-赵晓琳(D)', method: 'POST', path: '/api/auth/login', body: { username: '18652970318', password: 'wg123456' },
    expect: { checks: [{ path: 'data.token', type: 'string' }] }, save: { tokenD: 'data.token' } },
);

// 01 认证
F('01 认证模块')(
  { name: '01-1 登录-正常', method: 'POST', path: '/api/auth/login', body: { username: '13867429153', password: 'wg123456' },
    expect: { checks: [{ path: 'data.token', type: 'string' }, { path: 'data.profile.nickname', equals: '李秀英' }] } },
  { name: '01-2 登录-密码错误', method: 'POST', path: '/api/auth/login', body: { username: '13867429153', password: 'badpass' }, expect: { code: 20001 } },
  { name: '01-3 登录-缺少密码', method: 'POST', path: '/api/auth/login', body: { username: '13867429153' }, expect: { code: 10001 } },
  { name: '01-4 登录-缺少用户名', method: 'POST', path: '/api/auth/login', body: { password: 'wg123456' }, expect: { code: 10001 } },
  { name: '01-5 注册-新用户(正常)', method: 'POST', path: '/api/auth/register', preScript: 'pm.environment.set("regUser", "t" + String(Date.now()).slice(-10));',
    rawBody: '{"username":"{{regUser}}","password":"wg123456","privacyAgreed":true}',
    expect: { checks: [{ path: 'data.token', type: 'string' }] } },
  { name: '01-6 注册-缺少隐私同意', method: 'POST', path: '/api/auth/register', preScript: 'pm.environment.set("regUser2", "t2" + String(Date.now()).slice(-9));',
    rawBody: '{"username":"{{regUser2}}","password":"wg123456","privacyAgreed":false}', expect: { nonzero: true } },
);

// 02 用户模块
F('02 用户模块（资料/模式/动效/隐私）')(
  { name: '02-1 获取我的资料', method: 'GET', path: '/api/user/profile', auth: 'tokenA',
    expect: { checks: [{ path: 'data.userId', type: 'number' }, { path: 'data.nickname', equals: '李秀英' }, { path: 'data.hobbies', type: 'array' }] }, save: { userIdA: 'data.userId' } },
  { name: '02-2 更新资料-正常', method: 'PUT', path: '/api/user/profile', auth: 'tokenA', body: { signature: '接口自动化测试签名', careTip: '自动化测试备注' },
    expect: {} },
  { name: '02-3 更新资料-昵称超长(21字)', method: 'PUT', path: '/api/user/profile', auth: 'tokenA', body: { nickname: '一二三四五六七八九十一二三四五六七八九十一' }, expect: { code: 10002 } },
  { name: '02-4 更新资料-年龄越界(200)', method: 'PUT', path: '/api/user/profile', auth: 'tokenA', body: { age: 200 }, expect: { code: 10001 } },
  { name: '02-5 切换界面模式-年长', method: 'PUT', path: '/api/user/mode', auth: 'tokenA', body: { uiMode: 'ELDER' }, expect: { checks: [{ path: 'data.uiMode', equals: 'ELDER' }] } },
  { name: '02-6 切换界面模式-标准', method: 'PUT', path: '/api/user/mode', auth: 'tokenA', body: { uiMode: 'NORMAL' }, expect: { checks: [{ path: 'data.uiMode', equals: 'NORMAL' }] } },
  { name: '02-7 切换界面模式-非法值', method: 'PUT', path: '/api/user/mode', auth: 'tokenA', body: { uiMode: 'XXX' }, expect: { code: 10001 } },
  { name: '02-8 动效开关-关闭', method: 'PUT', path: '/api/user/motion', auth: 'tokenA', body: { enabled: 0 }, expect: {} },
  { name: '02-9 动效开关-打开', method: 'PUT', path: '/api/user/motion', auth: 'tokenA', body: { enabled: 1 }, expect: {} },
  { name: '02-10 隐私协议-同意', method: 'POST', path: '/api/user/privacy-agree', auth: 'tokenA', body: { version: 'v1.0' }, expect: {} },
  { name: '02-11 开启微光邀请-缺参数', method: 'POST', path: '/api/user/light-invite', auth: 'tokenA', body: {}, expect: { code: 10001 } },
);

// 03 地址簿
F('03 地址簿模块（增删改查）')(
  { name: '03-1 地址列表', method: 'GET', path: '/api/addresses', auth: 'tokenD', expect: { checks: [{ path: 'data', type: 'array' }] } },
  { name: '03-2 新增地址', method: 'POST', path: '/api/addresses', auth: 'tokenD', body: { label: '测试地址', areaText: '测试小区1号楼', detailText: '101室', latitude: 30.2, longitude: 120.1, isDefault: 0 },
    expect: { checks: [{ path: 'data.id', type: 'number' }] }, save: { addrId: 'data.id' } },
  { name: '03-3 修改地址', method: 'PUT', path: '/api/addresses/{{addrId}}', auth: 'tokenD', body: { label: '测试地址改', areaText: '测试小区2号楼', detailText: '202室', isDefault: 0 }, expect: {} },
  { name: '03-4 设为默认', method: 'PUT', path: '/api/addresses/{{addrId}}/default', auth: 'tokenD', expect: {} },
  { name: '03-5 删除地址', method: 'DELETE', path: '/api/addresses/{{addrId}}', auth: 'tokenD', expect: {} },
  { name: '03-6 新增地址-缺label', method: 'POST', path: '/api/addresses', auth: 'tokenD', body: { areaText: '某某小区' }, expect: { code: 10001 } },
  { name: '03-7 新增地址-纬度越界', method: 'POST', path: '/api/addresses', auth: 'tokenD', body: { label: '测试', areaText: '某某小区', latitude: 100 }, expect: { code: 10001 } },
  { name: '03-8 新增地址-label超长(21)', method: 'POST', path: '/api/addresses', auth: 'tokenD', body: { label: '一二三四五六七八九十一二三四五六七八九十一', areaText: '某某小区' }, expect: { code: 10002 } },
);

// 04 求助单-发布与查询
F('04 求助单-发布与查询')(
  { name: '04-1 发布求助-正常(远程)', method: 'POST', path: '/api/orders', auth: 'tokenB', body: { source: 'FREE', orderType: 'REMOTE', title: '接口自动化测试-远程求助', description: '这是一条接口自动化测试创建的求助单，用于验证完整主流程。', tags: ['网上办事'], timeExpect: 'ASAP', urgentFlag: 0 },
    expect: { checks: [{ path: 'data.orderId', type: 'number' }] }, save: { mainOrderId: 'data.orderId' } },
  { name: '04-2 发布求助-缺标题', method: 'POST', path: '/api/orders', auth: 'tokenB', body: { source: 'FREE', orderType: 'REMOTE', description: 'x', tags: ['跑腿'] }, expect: { code: 10001 } },
  { name: '04-3 发布求助-标题超长(21字)', method: 'POST', path: '/api/orders', auth: 'tokenB', body: { source: 'FREE', orderType: 'REMOTE', title: '一二三四五六七八九十一二三四五六七八九十一', tags: ['跑腿'] }, expect: { code: 10002 } },
  { name: '04-4 发布求助-非法标签', method: 'POST', path: '/api/orders', auth: 'tokenB', body: { source: 'FREE', orderType: 'REMOTE', title: '非法标签测试', tags: ['不存在的标签'] }, expect: { code: 10001 } },
  { name: '04-5 发布求助-远程却带地址', method: 'POST', path: '/api/orders', auth: 'tokenB', body: { source: 'FREE', orderType: 'REMOTE', title: '远程带地址测试', tags: ['跑腿'], addressId: 1 }, expect: { code: 10001 } },
  { name: '04-6 发布求助-上门缺地址', method: 'POST', path: '/api/orders', auth: 'tokenB', body: { source: 'FREE', orderType: 'HOME_VISIT', title: '上门缺地址测试', tags: ['维修'] }, expect: { code: 10001 } },
  { name: '04-7 发布求助-待响应达上限', method: 'POST', path: '/api/orders', auth: 'tokenC', body: { source: 'FREE', orderType: 'REMOTE', title: '待响应上限测试', tags: ['跑腿'] }, expect: { code: 30003 } },
  { name: '04-8 求助大厅-全部', method: 'GET', path: '/api/orders?tab=all&page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.records', type: 'array' }, { path: 'data.total', type: 'number' }] } },
  { name: '04-9 求助大厅-推荐', method: 'GET', path: '/api/orders?tab=recommend&page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '04-10 求助大厅-非法类型', method: 'GET', path: '/api/orders?orderType=XXX', auth: 'tokenA', expect: { code: 10001 } },
  { name: '04-11 我的求助-我发布的', method: 'GET', path: '/api/orders/mine?tab=published&page=1&size=5', auth: 'tokenB', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '04-12 我的求助-我接的', method: 'GET', path: '/api/orders/mine?tab=accepted&page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '04-13 求助详情', method: 'GET', path: '/api/orders/{{mainOrderId}}', auth: 'tokenB', expect: { checks: [{ path: 'data.id', type: 'number' }, { path: 'data.status', equals: 'PENDING' }] } },
  { name: '04-14 求助时间线', method: 'GET', path: '/api/orders/{{mainOrderId}}/timeline', auth: 'tokenB', expect: { checks: [{ path: 'data', type: 'array' }] } },
  { name: '04-15 修改待响应求助', method: 'PUT', path: '/api/orders/{{mainOrderId}}', auth: 'tokenB', body: { title: '修改后的标题-自动化', description: '修改后的描述', tags: ['网上办事'] }, expect: {} },
  { name: '04-16 AI 智能识别求助', method: 'POST', path: '/api/orders/ai-suggest', auth: 'tokenB', body: { text: '我想请人帮我去药店买点感冒药' }, expect: {} },
);

// 05 主流程（状态机）
F('05 求助单-主流程（接单→开始→完成→确认→评价）')(
  { name: '05-1 接单', method: 'POST', path: '/api/orders/{{mainOrderId}}/accept', auth: 'tokenA', expect: { checks: [{ path: 'data.sessionId', type: 'number' }] }, save: { mainSessionId: 'data.sessionId' } },
  { name: '05-2 照护提示卡(接单者可看)', method: 'GET', path: '/api/orders/{{mainOrderId}}/care-tip', auth: 'tokenA', expect: { checks: [{ path: 'data', type: 'object' }] } },
  { name: '05-3 开始服务', method: 'POST', path: '/api/orders/{{mainOrderId}}/start', auth: 'tokenA', expect: {} },
  { name: '05-4 标记完成', method: 'POST', path: '/api/orders/{{mainOrderId}}/complete', auth: 'tokenA', expect: {} },
  { name: '05-5 详情-待确认', method: 'GET', path: '/api/orders/{{mainOrderId}}', auth: 'tokenB', expect: { checks: [{ path: 'data.status', equals: 'PENDING_CONFIRM' }] } },
  { name: '05-6 确认结算', method: 'POST', path: '/api/orders/{{mainOrderId}}/confirm', auth: 'tokenB', body: { agree: true }, expect: {} },
  { name: '05-7 详情-已完成', method: 'GET', path: '/api/orders/{{mainOrderId}}', auth: 'tokenB', expect: { checks: [{ path: 'data.status', equals: 'COMPLETED' }] } },
  { name: '05-8 评价', method: 'POST', path: '/api/orders/{{mainOrderId}}/evaluation', auth: 'tokenB', body: { stars: 5, advice: '服务很好，自动化测试评价' }, expect: { checks: [{ path: 'data.id', type: 'number' }] } },
  { name: '05-9 感谢信', method: 'POST', path: '/api/orders/{{mainOrderId}}/thank-letter', auth: 'tokenB', body: { content: '谢谢你的帮助！', aiAssisted: false }, expect: { checks: [{ path: 'data.id', type: 'number' }] } },
  { name: '05-10 故事卡', method: 'GET', path: '/api/orders/{{mainOrderId}}/story-card', auth: 'tokenA', expect: { checks: [{ path: 'data.thing', type: 'string' }] } },
  { name: '05-11 重复确认-应拒绝', method: 'POST', path: '/api/orders/{{mainOrderId}}/confirm', auth: 'tokenB', body: { agree: true }, expect: { code: 40003 } },
  { name: '05-12 已完成再被接单-应拒绝(接单冲突)', method: 'POST', path: '/api/orders/{{mainOrderId}}/accept', auth: 'tokenA', expect: { code: 40001 } },
);

// 06 释放/取消/再来一次
F('06 求助单-释放/取消/再来一次')(
  { name: '06-1 发布求助(用于释放)', method: 'POST', path: '/api/orders', auth: 'tokenB', body: { source: 'FREE', orderType: 'REMOTE', title: '接口自动化-释放测试单', description: '用于测试放弃/释放流程。', tags: ['聊天'], timeExpect: 'TODAY' },
    expect: { checks: [{ path: 'data.orderId', type: 'number' }] }, save: { releaseOrderId: 'data.orderId' } },
  { name: '06-2 接单', method: 'POST', path: '/api/orders/{{releaseOrderId}}/accept', auth: 'tokenA', expect: { checks: [{ path: 'data.sessionId', type: 'number' }] } },
  { name: '06-3 接单者放弃(释放)', method: 'POST', path: '/api/orders/{{releaseOrderId}}/release', auth: 'tokenA', body: { reasonType: 'HELPER_QUIT' }, expect: {} },
  { name: '06-4 详情-释放后回到待响应', method: 'GET', path: '/api/orders/{{releaseOrderId}}', auth: 'tokenB', expect: { checks: [{ path: 'data.status', equals: 'PENDING' }] } },
  { name: '06-5 再次接单', method: 'POST', path: '/api/orders/{{releaseOrderId}}/accept', auth: 'tokenA', expect: { checks: [{ path: 'data.sessionId', type: 'number' }] }, save: { chatSessionId: 'data.sessionId' } },
  { name: '06-6 发布求助(用于取消)', method: 'POST', path: '/api/orders', auth: 'tokenB', body: { source: 'FREE', orderType: 'REMOTE', title: '接口自动化-取消测试单', description: '用于测试取消流程。', tags: ['跑腿'], timeExpect: 'TODAY' },
    expect: { checks: [{ path: 'data.orderId', type: 'number' }] }, save: { cancelOrderId: 'data.orderId' } },
  { name: '06-7 求助者取消', method: 'POST', path: '/api/orders/{{cancelOrderId}}/cancel', auth: 'tokenB', body: { reason: '自动化测试：暂不需要了', mode: 'CANCEL' }, expect: {} },
  { name: '06-8 详情-已取消', method: 'GET', path: '/api/orders/{{cancelOrderId}}', auth: 'tokenB', expect: { checks: [{ path: 'data.status', equals: 'CANCELLED' }] } },
  { name: '06-9 再来一次-已取消不可重发(应拒绝)', method: 'POST', path: '/api/orders/{{cancelOrderId}}/republish', auth: 'tokenB', expect: { code: 40002 } },
);

// 07 社区
F('07 社区模块（帖子/点赞/评论/举报）')(
  { name: '07-1 社区概览', method: 'GET', path: '/api/community/summary', auth: 'tokenA', expect: { checks: [{ path: 'data.stars', type: 'array' }] } },
  { name: '07-2 帖子列表-推荐', method: 'GET', path: '/api/community/posts?tab=recommend&page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '07-3 发布帖子', method: 'POST', path: '/api/community/posts', auth: 'tokenD', body: { category: 'NORMAL', title: '接口自动化测试帖', content: '这是一条接口自动化测试创建的社区帖子内容。', images: [] },
    expect: { checks: [{ path: 'data.id', type: 'number' }, { path: 'data.status', type: 'string' }] }, save: { postId: 'data.id' } },
  { name: '07-4 帖子详情(作者可见)', method: 'GET', path: '/api/community/posts/{{postId}}', auth: 'tokenD', expect: { checks: [{ path: 'data.id', type: 'number' }] } },
  { name: '07-5 点赞', method: 'POST', path: '/api/community/posts/{{postId}}/like', auth: 'tokenA', expect: {} },
  { name: '07-6 取消点赞', method: 'DELETE', path: '/api/community/posts/{{postId}}/like', auth: 'tokenA', expect: {} },
  { name: '07-7 发表评论', method: 'POST', path: '/api/community/posts/{{postId}}/comments', auth: 'tokenA', body: { content: '自动化测试评论' }, expect: {} },
  { name: '07-8 评论列表', method: 'GET', path: '/api/community/posts/{{postId}}/comments', auth: 'tokenD', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '07-9 发布帖子-缺内容', method: 'POST', path: '/api/community/posts', auth: 'tokenD', body: { category: 'NORMAL', title: '缺少内容' }, expect: { code: 10001 } },
  { name: '07-10 举报帖子(举报本轮新建帖)', method: 'POST', path: '/api/community/reports', auth: 'tokenA', rawBody: '{"targetType":"POST","targetId": {{postId}}, "reason":"自动化测试举报内容违规"}', expect: {} },
  { name: '07-11 删除自己的帖子', method: 'DELETE', path: '/api/community/posts/{{postId}}', auth: 'tokenD', expect: {} },
);

// 08 聊天
F('08 聊天模块')(
  { name: '08-1 获取会话', method: 'GET', path: '/api/orders/{{releaseOrderId}}/session', auth: 'tokenA', expect: { checks: [{ path: 'data.sessionId', type: 'number' }] }, save: { chatSessionId: 'data.sessionId' } },
  { name: '08-2 会话消息列表', method: 'GET', path: '/api/sessions/{{chatSessionId}}/messages?page=1&size=10', auth: 'tokenA', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '08-3 发送消息', method: 'POST', path: '/api/sessions/{{chatSessionId}}/messages', auth: 'tokenA', body: { clientMsgId: 'auto-' + 'm1', messageType: 'TEXT', content: '你好，我是自动化测试' },
    expect: { checks: [{ path: 'data', type: 'object' }] } },
  { name: '08-4 标记已读', method: 'POST', path: '/api/sessions/{{chatSessionId}}/read', auth: 'tokenB', expect: {} },
  { name: '08-5 快捷短语-求助者', method: 'GET', path: '/api/quick-phrases?role=REQUESTER', auth: 'tokenA', expect: { checks: [{ path: 'data', type: 'array' }] } },
  { name: '08-6 快捷短语-接单者', method: 'GET', path: '/api/quick-phrases?role=HELPER', auth: 'tokenA', expect: { checks: [{ path: 'data', type: 'array' }] } },
);

// 09 通知
F('09 通知模块')(
  { name: '09-1 通知列表', method: 'GET', path: '/api/notifications?page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.records', type: 'array' }] }, save: { notifId: 'data.records[0].id' } },
  { name: '09-2 通知横幅', method: 'GET', path: '/api/notifications/banner', auth: 'tokenA', expect: { checks: [{ path: 'data', type: 'object' }] } },
  { name: '09-3 单条已读', method: 'POST', path: '/api/notifications/{{notifId}}/read', auth: 'tokenA', expect: {} },
  { name: '09-4 全部已读', method: 'POST', path: '/api/notifications/read-all', auth: 'tokenA', expect: { checks: [{ path: 'data.cleared', type: 'number' }] } },
  { name: '09-5 删除一条通知', method: 'DELETE', path: '/api/notifications/{{notifId}}', auth: 'tokenA', expect: {} },
);

// 10 激励/积分/信誉/微光
F('10 激励与账户（微光/积分/信誉/荣誉）')(
  { name: '10-1 微光状态', method: 'GET', path: '/api/user/light', auth: 'tokenA', expect: { checks: [{ path: 'data.lightState', type: 'string' }] } },
  { name: '10-2 信誉流水', method: 'GET', path: '/api/user/credit?page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.balance', type: 'number' }, { path: 'data.records', type: 'array' }] } },
  { name: '10-3 积分流水', method: 'GET', path: '/api/user/points?page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.balance', type: 'number' }] } },
  { name: '10-4 我收到的感谢', method: 'GET', path: '/api/user/thanks?page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '10-5 首页/荣誉汇总', method: 'GET', path: '/api/home/summary', auth: 'tokenA', expect: { checks: [{ path: 'data.honorTop10', type: 'array' }] } },
  { name: '10-6 感谢信AI草稿', method: 'POST', path: '/api/orders/{{mainOrderId}}/thank-letter/ai-draft', auth: 'tokenB', body: { content: '帮我生成一封感谢信' }, expect: {} },
);

// 11 申诉与反馈
F('11 申诉与反馈')(
  { name: '11-1 提交申诉', method: 'POST', path: '/api/appeals', auth: 'tokenB', rawBody: '{"orderId": {{mainOrderId}}, "type":"POST_ORDER", "reason":"自动化测试申诉：实际服务与描述不符"}',
    expect: { checks: [{ path: 'data.id', type: 'number' }] }, save: { appealId: 'data.id' } },
  { name: '11-2 我的申诉列表', method: 'GET', path: '/api/appeals/mine?tab=APPEAL&page=1&size=5', auth: 'tokenB', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '11-3 提交反馈', method: 'POST', path: '/api/feedbacks', auth: 'tokenD', body: { content: '自动化测试反馈：界面很友好' }, expect: { checks: [{ path: 'data.id', type: 'number' }] }, save: { feedbackId: 'data.id' } },
  { name: '11-4 我的反馈列表', method: 'GET', path: '/api/appeals/mine?tab=FEEDBACK&page=1&size=5', auth: 'tokenD', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '11-5 申诉-缺少订单号', method: 'POST', path: '/api/appeals', auth: 'tokenB', body: { type: 'POST_ORDER', reason: 'x' }, expect: { code: 10001 } },
  { name: '11-6 申诉-非法类型(反馈)', method: 'POST', path: '/api/appeals', auth: 'tokenB', rawBody: '{"orderId": {{mainOrderId}}, "type":"FEEDBACK", "reason":"x"}', expect: { code: 40002 } },
);

// 12 助手/模板/地理
F('12 智能助手/模板/地理')(
  { name: '12-1 助手对话', method: 'POST', path: '/api/assistant/chat', auth: 'tokenA', body: { message: '我想请人帮我去药店买药' }, expect: { checks: [{ path: 'data.sessionId', type: 'string' }] }, save: { assistSessionId: 'data.sessionId' } },
  { name: '12-2 助手会话查询', method: 'GET', path: '/api/assistant/session?sessionId={{assistSessionId}}', auth: 'tokenA', expect: { checks: [{ path: 'data', type: 'object' }] } },
  { name: '12-3 助手草稿更新', method: 'PUT', path: '/api/assistant/draft', auth: 'tokenA', rawBody: '{"sessionId":"{{assistSessionId}}","title":"助手草稿标题","orderType":"REMOTE","urgentFlag":0}', expect: {} },
  { name: '12-4 求助模板', method: 'GET', path: '/api/templates', auth: 'tokenA', expect: { checks: [{ path: 'data', type: 'array' }] } },
  { name: '12-5 地理提示', method: 'GET', path: '/api/geo/tips', auth: 'tokenA', expect: { checks: [{ path: 'data', type: 'array' }] } },
  { name: '12-6 地理搜索', method: 'GET', path: '/api/geo/pois?keyword=公园', auth: 'tokenA', expect: { checks: [{ path: 'data', type: 'array' }] } },
  { name: '12-7 地理搜索-缺关键词', method: 'GET', path: '/api/geo/pois?keyword=', auth: 'tokenA', expect: { code: 10001 } },
);

// 13 上传
F('13 文件上传')(
  { name: '13-1 上传图片', method: 'POST', path: '/api/upload/image', auth: 'tokenA',
    formdata: [{ key: 'file', type: 'file', src: 'postman/tools/tiny.png' }], expect: { checks: [{ path: 'data.url', type: 'string' }] } },
);

// 14 邻居主页
F('14 邻居主页')(
  { name: '14-1 邻居资料卡', method: 'GET', path: '/api/users/3/profile', auth: 'tokenA', expect: { checks: [{ path: 'data.nickname', equals: '陈志远' }, { path: 'data.evaluationAvg', type: 'number' }] } },
  { name: '14-2 邻居评价列表', method: 'GET', path: '/api/users/3/evaluations?page=1&size=5', auth: 'tokenA', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
);

// 15 管理端-只读
F('15 管理端-查询')(
  { name: '15-1 运营概览', method: 'GET', path: '/admin/overview', auth: 'adminToken', expect: { checks: [{ path: 'data.userCount', type: 'number' }, { path: 'data.typeDistribution', type: 'object' }] } },
  { name: '15-2 订单管理列表', method: 'GET', path: '/admin/orders?page=1&size=5', auth: 'adminToken', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '15-3 订单管理详情', method: 'GET', path: '/admin/orders/4', auth: 'adminToken', expect: { checks: [{ path: 'data.id', type: 'number' }, { path: 'data.timeline', type: 'array' }] } },
  { name: '15-4 用户管理列表', method: 'GET', path: '/admin/users?page=1&size=5', auth: 'adminToken', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '15-5 用户管理详情', method: 'GET', path: '/admin/users/1', auth: 'adminToken', expect: { checks: [{ path: 'data.id', type: 'number' }, { path: 'data.credit', type: 'object' }] } },
  { name: '15-6 系统配置列表', method: 'GET', path: '/admin/configs', auth: 'adminToken', expect: { checks: [{ path: 'data', type: 'array' }] } },
  { name: '15-7 举报列表', method: 'GET', path: '/admin/reports?page=1&size=5', auth: 'adminToken', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '15-8 申诉列表', method: 'GET', path: '/admin/appeals?page=1&size=5', auth: 'adminToken', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '15-9 社区帖子管理', method: 'GET', path: '/admin/community/posts?page=1&size=5', auth: 'adminToken', expect: { checks: [{ path: 'data.records', type: 'array' }] } },
  { name: '15-10 帖子管理详情', method: 'GET', path: '/admin/community/posts/5', auth: 'adminToken', expect: { checks: [{ path: 'data.post', type: 'object' }] } },
);

// 16 管理端-操作
F('16 管理端-操作')(
  { name: '16-1 新建待审帖子(含敏感词触发人工审核)', method: 'POST', path: '/api/community/posts', auth: 'tokenD', body: { category: 'NORMAL', title: '待管理员审核的帖子', content: '这条内容包含刷单等敏感词，应被转入人工审核。', images: [] },
    expect: { checks: [{ path: 'data.id', type: 'number' }, { path: 'data.status', equals: 'PENDING_MANUAL' }] }, save: { reviewPostId: 'data.id' } },
  { name: '16-2 审核通过帖子', method: 'POST', path: '/admin/community/posts/{{reviewPostId}}/review', auth: 'adminToken', body: { result: 'APPROVE', note: '自动化测试审核通过' }, expect: {} },
  { name: '16-3 隐藏帖子', method: 'POST', path: '/admin/community/posts/{{reviewPostId}}/hide', auth: 'adminToken', expect: {} },
  { name: '16-4 恢复帖子', method: 'POST', path: '/admin/community/posts/{{reviewPostId}}/restore', auth: 'adminToken', expect: {} },
  { name: '16-5 删除帖子', method: 'DELETE', path: '/admin/community/posts/{{reviewPostId}}', auth: 'adminToken', expect: {} },
  { name: '16-6 发布官方公告', method: 'POST', path: '/admin/community/official', auth: 'adminToken', body: { title: '自动化测试公告', content: '这是一条接口自动化测试发布的官方公告内容。', images: [] }, expect: { checks: [{ path: 'data', type: 'number' }] } },
  { name: '16-7 回复用户反馈', method: 'POST', path: '/admin/feedbacks/{{feedbackId}}/reply', auth: 'adminToken', body: { conclusion: '感谢反馈，已处理（自动化测试）' }, expect: {} },
  { name: '16-8 封禁用户5', method: 'POST', path: '/admin/users/5/ban', auth: 'adminToken', expect: {} },
  { name: '16-9 解封用户5', method: 'POST', path: '/admin/users/5/unban', auth: 'adminToken', expect: {} },
  { name: '16-10 调整用户5信誉+5', method: 'POST', path: '/admin/users/5/credit-adjust', auth: 'adminToken', body: { delta: 5, remark: '自动化测试加5' }, expect: {} },
  { name: '16-11 调整用户5信誉-5', method: 'POST', path: '/admin/users/5/credit-adjust', auth: 'adminToken', body: { delta: -5, remark: '自动化测试还原' }, expect: {} },
  { name: '16-12 更新系统配置(幂等)', method: 'PUT', path: '/admin/configs/rate.browse_dedup_ttl_hours', auth: 'adminToken', body: { value: '24' }, expect: {} },
);

// 17 健壮性/鉴权
F('17 鉴权与健壮性')(
  { name: '17-1 无 token 访问受保护接口', method: 'GET', path: '/api/user/profile', expect: { code: 20001 } },
  { name: '17-2 错误 token 访问', method: 'GET', path: '/api/user/profile', auth: 'badToken', expect: { code: 20001 } },
  { name: '17-3 普通用户访问管理端', method: 'GET', path: '/admin/overview', auth: 'tokenA', expect: { nonzero: true } },
  { name: '17-4 健康检查', method: 'GET', path: '/actuator/health', expect: { raw: true, checks: [{ path: 'status', equals: 'UP' }] } },
);

// 18 清理（恢复限流配置 + 注销）
F('18 收尾（恢复配置/注销）')(
  { name: '18-1 恢复发布限流', method: 'PUT', path: '/admin/configs/rate.publish_per_minute', auth: 'adminToken', body: { value: '3' }, expect: {} },
  { name: '18-2 恢复登录限流', method: 'PUT', path: '/admin/configs/rate.login_per_minute', auth: 'adminToken', body: { value: '5' }, expect: {} },
  { name: '18-3 恢复发言限流', method: 'PUT', path: '/admin/configs/rate.chat_per_minute', auth: 'adminToken', body: { value: '20' }, expect: {} },
  { name: '18-4 退出登录(D)', method: 'POST', path: '/api/auth/logout', auth: 'tokenD', expect: {} },
);

// ---------- 生成 ----------
function buildScript(it) {
  const L = [];
  const http = it.expect?.http ?? 200;
  L.push(`pm.test("HTTP ${http}", () => pm.response.to.have.status(${http}));`);
  if (it.expect?.raw) {
    // 非 Result 包装的响应（如 health）
  } else if (it.expect?.nonzero) {
    L.push('pm.test("业务码非 0（预期失败）", () => pm.expect(pm.response.json().code).to.not.eql(0));');
  } else {
    const code = it.expect?.code ?? 0;
    L.push(`pm.test("业务码 = ${code}", () => pm.expect(pm.response.json().code).to.eql(${code}));`);
  }
  for (const ch of it.expect?.checks || []) {
    const expr = 'pm.response.json().' + ch.path;
    let assertion = '';
    if (ch.equals !== undefined) assertion = `pm.expect(${expr}).to.eql(${JSON.stringify(ch.equals)});`;
    else if (ch.type) assertion = `pm.expect(${expr}).to.be.a("${ch.type}");`;
    else if (ch.exists) assertion = `pm.expect(${expr}).to.not.be.undefined;`;
    else if (ch.gte !== undefined) assertion = `pm.expect(${expr}).to.be.at.least(${ch.gte});`;
    else if (ch.below !== undefined) assertion = `pm.expect(${expr}).to.be.below(${ch.below});`;
    else if (ch.nonzero) assertion = `pm.expect(${expr}).to.not.eql(0);`;
    const label = ch.label || (ch.path + (ch.equals !== undefined ? ` = ${ch.equals}` : ''));
    L.push(`pm.test(${JSON.stringify(label)}, () => { ${assertion} });`);
  }
  const rt = it.expect?.responseTimeBelow ?? 3000;
  L.push(`pm.test("响应时间 < ${rt}ms", () => pm.expect(pm.response.responseTime).to.be.below(${rt}));`);
  for (const [v, p] of Object.entries(it.save || {})) {
    L.push(`if (pm.response.json().code === 0) { pm.environment.set(${JSON.stringify(v)}, pm.response.json().${p}); }`);
  }
  return L;
}

function buildItem(it) {
  const header = [];
  const request = { method: it.method, header, url: BASE + it.path };
  if (it.rawBody !== undefined) {
    header.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
    request.body = { mode: 'raw', raw: it.rawBody, options: { raw: { language: 'json' } } };
  } else if (it.body !== undefined) {
    header.push({ key: 'Content-Type', value: 'application/json; charset=utf-8' });
    request.body = { mode: 'raw', raw: JSON.stringify(it.body), options: { raw: { language: 'json' } } };
  } else if (it.formdata) {
    request.body = { mode: 'formdata', formdata: it.formdata };
  }
  if (it.auth) header.push({ key: 'Authorization', value: 'Bearer {{' + it.auth + '}}' });
  const event = [];
  if (it.preScript) event.push({ listen: 'prerequest', script: { type: 'text/javascript', exec: it.preScript.split('\n') } });
  event.push({ listen: 'test', script: { type: 'text/javascript', exec: buildScript(it) } });
  return { name: it.name, request, event };
}

const collection = {
  info: {
    name: '微光 API 接口测试',
    description: '微光（邻里互助）项目全量接口测试集合：认证、用户、地址簿、求助单全生命周期、社区、聊天、通知、激励、申诉、助手、管理端等。包含正向、边界、异常与状态流转用例，附断言。',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
  },
  variable: [{ key: 'baseUrl', value: 'http://localhost:8080' }],
  item: folders.map((f) => ({ name: f.name, item: f.items.map(buildItem) })),
};

const envVars = ['baseUrl', 'adminToken', 'tokenA', 'tokenB', 'tokenC', 'tokenD', 'badToken',
  'regUser', 'regUser2', 'userIdA', 'addrId', 'mainOrderId', 'mainSessionId', 'releaseOrderId', 'cancelOrderId', 'republishedId',
  'postId', 'chatSessionId', 'notifId', 'assistSessionId', 'appealId', 'feedbackId', 'reviewPostId'];
const environment = {
  name: '微光-本地',
  values: envVars.map((k) => ({ key: k, value: k === 'baseUrl' ? 'http://localhost:8080' : (k === 'badToken' ? 'invalid.token.value' : ''), enabled: true })),
};

const outDir = path.resolve('postman');
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, '微光-API接口测试.postman_collection.json'), JSON.stringify(collection, null, 2), 'utf8');
fs.writeFileSync(path.join(outDir, '微光-本地.postman_environment.json'), JSON.stringify(environment, null, 2), 'utf8');

const total = folders.reduce((s, f) => s + f.items.length, 0);
console.log(`Generated collection with ${folders.length} folders, ${total} requests.`);
