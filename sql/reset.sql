-- ============================================================
-- 微光——社区互助服务系统 · 一键重置（T18/T27；内容 ≡ sql/seed.sql，单跑即回到干净演示状态）
--
-- 执行（推荐 docker 方式，避免 Windows 管道中文编码问题）：
--   docker cp sql\reset.sql weiguang-mysql:/tmp/seed.sql
--   docker exec -i weiguang-mysql mysql --default-character-set=utf8mb4 -uroot -p weiguang -e "source /tmp/seed.sql"
--
-- 语义：本脚本为「自包含一键重置」——先清空全部业务数据，再全量插入演示数据。
--       保留并重置：sys_config（75 项回默认值）/ help_template / sensitive_word；
--       admin_user 不受本脚本影响（管理员账号由后端按 .env 初始化）。
--       reset.sql = 本文件内容副本，单跑即可回到干净演示状态。
-- 演示账号（账密登录 D31，密码均为 wg123456；账号即本人手机号）：
--   13867429153 李秀英（A·受助主线·68 岁年长模式）/ 13957068214 王德明（B·年龄未填）
--   15867132409 陈志远（C·助人主线）/ 18652970318 赵晓琳（D·微光值 50 门槛外）
--   背景邻居（同为 wg123456）：
--   13385649271 张桂花（E）/ 13745286109 刘建军（F）/ 15162384756 周慧珍（G）
--   18245037619 吴国强（H）/ 13072849563 孙丽华（I）
-- 时间基准：全部时间戳以执行时刻为基准（NOW() - INTERVAL ...）。
-- 警告：执行后既有业务数据（含旧登录账号与旧 token 对应的账号）全部消失，
--       请在开发者工具中退出登录/清缓存后，用上述演示账号重新登录。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 0. 清空业务数据（幂等：可重复执行） ----------
DELETE FROM `user_profile`;
DELETE FROM `user`;
DELETE FROM `address_book`;
DELETE FROM `help_order`;
DELETE FROM `release_record`;
DELETE FROM `order_timeline`;
DELETE FROM `chat_session`;
DELETE FROM `chat_message`;
DELETE FROM `credit_ledger`;
DELETE FROM `point_ledger`;
DELETE FROM `evaluation`;
DELETE FROM `thank_letter`;
DELETE FROM `story_card`;
DELETE FROM `light_event`;
DELETE FROM `whisper`;
DELETE FROM `notification`;
DELETE FROM `appeal`;
DELETE FROM `report`;
DELETE FROM `order_view`;
DELETE FROM `community_like`;
DELETE FROM `community_comment`;
DELETE FROM `community_post`;
ALTER TABLE `user` AUTO_INCREMENT = 1;
ALTER TABLE `user_profile` AUTO_INCREMENT = 1;
ALTER TABLE `address_book` AUTO_INCREMENT = 1;
ALTER TABLE `help_order` AUTO_INCREMENT = 1;
ALTER TABLE `release_record` AUTO_INCREMENT = 1;
ALTER TABLE `order_timeline` AUTO_INCREMENT = 1;
ALTER TABLE `chat_session` AUTO_INCREMENT = 1;
ALTER TABLE `chat_message` AUTO_INCREMENT = 1;
ALTER TABLE `credit_ledger` AUTO_INCREMENT = 1;
ALTER TABLE `point_ledger` AUTO_INCREMENT = 1;
ALTER TABLE `evaluation` AUTO_INCREMENT = 1;
ALTER TABLE `thank_letter` AUTO_INCREMENT = 1;
ALTER TABLE `story_card` AUTO_INCREMENT = 1;
ALTER TABLE `light_event` AUTO_INCREMENT = 1;
ALTER TABLE `whisper` AUTO_INCREMENT = 1;
ALTER TABLE `notification` AUTO_INCREMENT = 1;
ALTER TABLE `appeal` AUTO_INCREMENT = 1;
ALTER TABLE `report` AUTO_INCREMENT = 1;
ALTER TABLE `order_view` AUTO_INCREMENT = 1;
ALTER TABLE `community_post` AUTO_INCREMENT = 1;
ALTER TABLE `community_comment` AUTO_INCREMENT = 1;
ALTER TABLE `community_like` AUTO_INCREMENT = 1;

-- ---------- 1. sys_config 全量 75 项（详设第 7 章 67 项 + 4C 风险保底 8 项） ----------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`, `default_value`, `value_range`, `updated_by`) VALUES
('elder.age_threshold',             '60',         '年长模式年龄阈值（岁）',                 '60',         '50-100',      'system'),
('urgency.base_accompany',          '3',          '紧急度基准分·陪同外出',                  '3',          '0-10',        'system'),
('urgency.base_home',               '2',          '紧急度基准分·上门',                      '2',          '0-10',        'system'),
('urgency.base_remote',             '1',          '紧急度基准分·远程',                      '1',          '0-10',        'system'),
('urgency.urgent_weight',           '3',          '急标加权',                               '3',          '0-10',        'system'),
('help.tag_max',                    '3',          '标签上限',                               '3',          '1-5',         'system'),
('help.title_max_length',           '20',         '规范标题长度上限（字）',                 '20',         '5-50',        'system'),
('content.desc_max_length',         '200',        '求助描述长度上限（字）',                 '200',        '50-1000',     'system'),
('content.chat_text_max_length',    '500',        '聊天单条文本上限（字）',                 '500',        '100-2000',    'system'),
('order.pending_limit',             '2',          '同用户待响应求助上限',                   '2',          '1-10',        'system'),
('order.pending_timeout_hours',     '24',         '待响应超时（普通，小时）',               '24',         '1-168',       'system'),
('order.pending_timeout_urgent_hours', '6',       '待响应超时（急，小时）',                 '6',          '1-72',        'system'),
('order.accept_timeout_hours',      '2',          '确认开始超时（小时）',                   '2',          '1-24',        'system'),
('order.confirm_timeout_hours',     '24',         '待确认超时（小时）',                     '24',         '1-168',       'system'),
('order.appeal_window_days',        '7',          '申诉窗口（天）',                         '7',          '1-30',        'system'),
('order.post_action_window_days',   '7',          '评价/感谢信窗口（天）',                   '7',          '1-30',        'system'),
('order.cancel_confirm_timeout_hours', '24',      '协商取消响应超时（小时）',               '24',         '1-168',       'system'),
('order.violation_threshold',       '2',          '违约计数阈值（次）',                     '2',          '1-10',        'system'),
('order.violation_cooldown_hours',  '48',         '违约接单冷却（小时）',                   '48',         '1-720',       'system'),
('order.helpless_release_limit',    '3',          '免罚释放上限（7 天内次）',               '3',          '0-10',        'system'),
('order.zombie_clean_days',         '7',          '僵尸单清理时长（天）',                   '7',          '1-30',        'system'),
('credit.init',                     '100',        '微光值初值',                             '100',        '0-1000',      'system'),
('credit.threshold',                '60',         '接上门/陪同门槛',                        '60',         '0-100',       'system'),
('credit.complete',                 '2',          '完成帮助',                               '2',          '0-100',       'system'),
('credit.accept_cancel',            '-5',         '接单后取消',                             '-5',         '-100-0',      'system'),
('credit.start_timeout',            '-15',        '超时未确认开始',                         '-15',        '-100-0',      'system'),
('credit.mid_quit',                 '-15',        '服务中失联/中途退出',                    '-15',        '-100-0',      'system'),
('credit.urgent_abuse',             '-10',        '滥用急标',                               '-10',        '-100-0',      'system'),
('credit.malicious_cancel',         '-10',        '恶意取消',                               '-10',        '-100-0',      'system'),
('credit.fake_order',               '-20',        '虚假求助（管理员）',                     '-20',        '-100-0',      'system'),
('credit.appeal_loss',              '-20',        '仲裁败诉',                               '-20',        '-100-0',      'system'),
('abuse.urgent_count_7d',           '2',          '滥用急标判定条数（7 天）',               '2',          '1-10',        'system'),
('abuse.cancel_count_7d',           '3',          '恶意取消判定次数（7 天）',               '3',          '1-10',        'system'),
('points.per_complete',             '10',         '单次完成奖励积分',                       '10',         '1-100',       'system'),
('points.weekly_pair_limit',        '3',          '同对用户每周计分上限（次）',             '3',          '1-20',        'system'),
('light.rest_trigger_days',         '30',         '小憩触发·无互动天数',                    '30',         '7-365',       'system'),
('light.rest_release_count',        '2',          '小憩触发·连续被释放次数',                '2',          '1-10',        'system'),
('light.rest_duration_days',        '7',          '小憩时长（自动恢复期，天）',             '7',          '1-90',        'system'),
('light.invite_interval_days',      '7',          '微光邀请弹窗间隔（天）',                 '7',          '1-30',        'system'),
('compliance.retention_days',       '180',        '注销后留存期（天）',                     '180',        '30-3650',     'system'),
('notify.banner_max_extra',         '3',          '横幅聚合条数上限',                       '3',          '1-10',        'system'),
('ai.mode',                         'mock',       'AI 模式 real/mock',                      'mock',       'real/mock',   'system'),
('ai.real-impl',                    'restclient', '真实实现 springai/restclient（默认 restclient，见实现偏差清单 D22）', 'restclient', 'springai/restclient', 'system'),
('ai.model.understand',             'qwen3.8-flash', '能力一模型',                          'qwen3.8-flash', '已开通模型名', 'system'),
('ai.model.review',                 'qwen3.7-plus',  '能力二复核模型',                      'qwen3.7-plus',  '已开通模型名', 'system'),
('ai.model.letter',                 'qwen3.8-flash', '能力三模型',                          'qwen3.8-flash', '已开通模型名', 'system'),
('ai.thinking',                     'false',      '思考模式开关（固定 false，管理端不可改）', 'false',    '固定 false（写死，管理端不可改）', 'system'),
('ai.timeout_ms',                   '5000',       'AI 超时（毫秒）',                        '5000',       '1000-30000',  'system'),
('ai.retry',                        '1',          'AI 重试次数',                            '1',          '0-3',         'system'),
('ai.letter_max_length',            '200',        '感谢信草稿长度上限（字）',               '200',        '50-1000',     'system'),
('storage.provider',                'local',      '存储实现 local/oss',                     'local',      'local/oss（oss 后置不实现）', 'system'),
('storage.image_max_size_mb',       '5',          '图片大小上限（MB）',                     '5',          '1-20',        'system'),
('storage.local_path',              './uploads',  '本地存储目录',                           './uploads',  '合法路径',    'system'),
('redis.enabled',                   'true',       'Redis 开关',                             'true',       'true/false（false 全部降级本地）', 'system'),
('rate.login_per_minute',           '5',          '登录限流（次/分，管理端复用）',          '5',          '1-100',       'system'),
('rate.publish_per_minute',         '3',          '发布限流（次/分）',                      '3',          '1-100',       'system'),
('rate.chat_per_minute',            '20',         '发言限流（次/分）',                      '20',         '1-200',       'system'),
('rate.browse_dedup_ttl_hours',     '24',         '浏览去重预检 TTL（小时）',               '24',         '1-168',       'system'),
('cache.hot_ttl_seconds',           '60',         '热点缓存 TTL（秒）',                     '60',         '10-3600',     'system'),
('sys.config_refresh_seconds',      '60',         '配置定时刷新间隔（秒）',                 '60',         '10-3600',     'system'),
('jwt.expire_days',                 '7',          'JWT 有效期（天）',                       '7',          '1-30',        'system'),
('risk.min_minutes.paotui',         '5',          '存疑求助保底·跑腿（分钟）',               '5',          '1-120',       'system'),
('risk.min_minutes.repair',         '10',         '存疑求助保底·小修小补（分钟）',           '10',         '1-120',       'system'),
('risk.min_minutes.housework',      '8',          '存疑求助保底·家务搭把手（分钟）',         '8',          '1-120',       'system'),
('risk.min_minutes.accompany',      '10',         '存疑求助保底·陪伴同行（分钟）',           '10',         '1-120',       'system'),
('risk.min_minutes.teach',          '5',          '存疑求助保底·教学（分钟）',               '5',          '1-120',       'system'),
('risk.min_minutes.online',         '5',          '存疑求助保底·网上办事（分钟）',           '5',          '1-120',       'system'),
('risk.min_minutes.plants',         '5',          '存疑求助保底·花草宠物（分钟）',           '5',          '1-120',       'system'),
('risk.min_minutes.other',          '3',          '存疑求助保底·其他小事（分钟）',           '3',          '1-120',       'system'),
('privacy.policy_version',          'v1.0',       '隐私说明版本号',                         'v1.0',       'vX.Y',        'system'),
('community.post_max_length',       '500',        '动态正文长度上限（字）',                 '500',        '50-2000',     'system'),
('community.post_image_max',        '3',          '动态图片张数上限',                       '3',          '0-9',         'system'),
('community.comment_max_length',    '200',        '评论长度上限（字）',                     '200',        '20-500',      'system'),
('community.ai_review_enabled',     'true',       '动态/评论 AI 内容审核开关',              'true',       'true/false',  'system'),
('community.feed_page_size',        '10',         '社区信息流默认分页条数',                 '10',         '5-50',        'system')
ON DUPLICATE KEY UPDATE
  `config_value`  = VALUES(`config_value`),
  `description`   = VALUES(`description`),
  `default_value` = VALUES(`default_value`),
  `value_range`   = VALUES(`value_range`),
  `updated_by`    = VALUES(`updated_by`);

-- ---------- 2. help_template 8 个（详设 8.4） ----------
INSERT INTO `help_template` (`id`, `name`, `icon`, `cover_desc`, `default_type`, `title`, `sort`, `enabled`) VALUES
(1, '跑腿',   '🛒', '买菜、取药、取快递等跑腿小事',     'HOME_VISIT', '帮我买点东西',       1, 1),
(2, '小修小补',   '💡', '灯泡、水龙头、小家电的小修小补',   'HOME_VISIT', '家里有个小东西坏了', 2, 1),
(3, '家务搭把手', '🏠', '打扫、晾晒、搬挪等家务搭把手',     'HOME_VISIT', '想请人搭把手做家务', 3, 1),
(4, '陪伴同行',   '🌿', '散步、就医、办事的陪伴同行',       'ACCOMPANY',  '想找个人陪我走一趟', 4, 1),
(5, '教学',   '📱', '教您用手机：微信、视频、扫码',     'REMOTE',     '教我用手机',         5, 1),
(6, '网上办事',   '💳', '线上缴费、挂号、办事的教学与指导', 'REMOTE',     '教我在网上办点事',   6, 1),
(7, '花草宠物',   '🌱', '浇花、喂猫狗等花草宠物照看',       'HOME_VISIT', '帮我照看一下花草宠物', 7, 1),
(8, '其他小事',   '✍️', '说不清类型的小事，由您来定',       'REMOTE',     '有件小事想请邻居帮忙', 8, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `icon` = VALUES(`icon`), `cover_desc` = VALUES(`cover_desc`),
  `default_type` = VALUES(`default_type`), `title` = VALUES(`title`),
  `sort` = VALUES(`sort`), `enabled` = VALUES(`enabled`);

-- ---------- 3. sensitive_word（详设 8.4） ----------
-- 防诈要素 8 条 + v1.3 双向补充 3 条（action=REVIEW，触发能力二 AI 复核）
INSERT INTO `sensitive_word` (`word`, `category`, `action`, `enabled`) VALUES
('验证码',     'FRAUD',     'REVIEW', 1),
('银行卡',     'FRAUD',     'REVIEW', 1),
('密码',       'FRAUD',     'REVIEW', 1),
('转账',       'FRAUD',     'REVIEW', 1),
('汇款',       'FRAUD',     'REVIEW', 1),
('扫码付款',   'FRAUD',     'REVIEW', 1),
('身份证号',   'FRAUD',     'REVIEW', 1),
('社保卡',     'FRAUD',     'REVIEW', 1),
('垫付',       'FRAUD',     'REVIEW', 1),
('代付',       'FRAUD',     'REVIEW', 1),
('转给',       'FRAUD',     'REVIEW', 1)
ON DUPLICATE KEY UPDATE
  `category` = VALUES(`category`), `action` = VALUES(`action`), `enabled` = VALUES(`enabled`);

-- 敏感词示例（action=BLOCK，发布场景温柔拦截）
INSERT INTO `sensitive_word` (`word`, `category`, `action`, `enabled`) VALUES
('赌博',               'SENSITIVE', 'BLOCK',     1),
('加微信私下交易',     'SENSITIVE', 'BLOCK',     1),
('刷单',               'SENSITIVE', 'BLOCK',     1)
ON DUPLICATE KEY UPDATE
  `category` = VALUES(`category`), `action` = VALUES(`action`), `enabled` = VALUES(`enabled`);

-- 高亮预警词（action=HIGHLIGHT，聊天场景仅高亮不硬拦）
INSERT INTO `sensitive_word` (`word`, `category`, `action`, `enabled`) VALUES
('借钱',   'SENSITIVE', 'HIGHLIGHT', 1),
('保证金', 'SENSITIVE', 'HIGHLIGHT', 1)
ON DUPLICATE KEY UPDATE
  `category` = VALUES(`category`), `action` = VALUES(`action`), `enabled` = VALUES(`enabled`);

-- ---------- 4. 演示账号（9 人：主演示 4 + 背景邻居 5；账密登录 D31，账号=手机号，密码统一 wg123456） ----------
-- 光语状态与派生数据同源一致（v2：UNLIT/LIT/STEADY，RESTING 已下线）：
--   A/B/E/G/I 常亮（受助结算后成为微光：STEADY）；C/D/F/H 未点亮（未曾受助）。
--   状态变更事件时间与对应订单完成时刻一致（见第 11 节光语事件）。
-- 说明：C 微光值 106/F 108/H 106 = 初值 100 + 各自完成结算（+2/次，见偏差 D32）。
INSERT INTO `user`
  (`id`, `openid`, `username`, `password_hash`, `ui_mode`, `motion_enabled`, `credit_score`,
   `violation_count`, `cooldown_until`, `status`, `light_state`, `light_prev_state`, `rest_since`,
   `light_passed`, `helped_before`, `consecutive_release_count`, `last_interaction_at`,
   `last_light_invite_at`, `invite_flag`, `privacy_version`, `privacy_agreed_at`, `created_at`) VALUES
(1, NULL, '13867429153', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'ELDER',  0, 102, 0, NULL, 'NORMAL', 'STEADY', NULL, NULL, 1, 1, 1, NOW() - INTERVAL 1 HOUR,    NOW() - INTERVAL 30 MINUTE, 'WILLING', 'v1.0', NOW() - INTERVAL 60 DAY,  NOW() - INTERVAL 60 DAY),
(2, NULL, '13957068214', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'NORMAL', 1, 100, 0, NULL, 'NORMAL', 'STEADY', NULL, NULL, 0, 1, 0, NOW() - INTERVAL 1 HOUR,    NULL,                       NULL,      'v1.0', NOW() - INTERVAL 40 DAY,  NOW() - INTERVAL 40 DAY),
(3, NULL, '15867132409', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'NORMAL', 1, 106, 0, NULL, 'NORMAL', 'UNLIT',  NULL, NULL, 0, 0, 0, NOW() - INTERVAL 2 DAY,     NULL,                       NULL,      'v1.0', NOW() - INTERVAL 50 DAY,  NOW() - INTERVAL 50 DAY),
(4, NULL, '18652970318', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'NORMAL', 1, 50,  0, NULL, 'NORMAL', 'UNLIT',  NULL, NULL, 0, 0, 0, NOW() - INTERVAL 55 MINUTE, NULL,                       NULL,      'v1.0', NOW() - INTERVAL 30 DAY,  NOW() - INTERVAL 30 DAY),
(5, NULL, '13385649271', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'ELDER',  0, 100, 0, NULL, 'NORMAL', 'STEADY', NULL, NULL, 0, 1, 0, NOW() - INTERVAL 3 DAY,     NULL,                       NULL,      'v1.0', NOW() - INTERVAL 90 DAY,  NOW() - INTERVAL 90 DAY),
(6, NULL, '13745286109', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'NORMAL', 1, 108, 0, NULL, 'NORMAL', 'UNLIT',  NULL, NULL, 0, 0, 0, NOW() - INTERVAL 3 DAY,     NULL,                       NULL,      'v1.0', NOW() - INTERVAL 120 DAY, NOW() - INTERVAL 120 DAY),
(7, NULL, '15162384756', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'ELDER',  0, 100, 0, NULL, 'NORMAL', 'STEADY', NULL, NULL, 0, 1, 0, NOW() - INTERVAL 6 HOUR,    NULL,                       NULL,      'v1.0', NOW() - INTERVAL 70 DAY,  NOW() - INTERVAL 70 DAY),
(8, NULL, '18245037619', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'NORMAL', 1, 106, 0, NULL, 'NORMAL', 'UNLIT',  NULL, NULL, 0, 0, 0, NOW() - INTERVAL 4 DAY,     NULL,                       NULL,      'v1.0', NOW() - INTERVAL 100 DAY, NOW() - INTERVAL 100 DAY),
(9, NULL, '13072849563', '$2a$10$FaU.wuOV0V6ZOd55hb/sie/g3x3ebuMMfIYhgZqidDeEEkb4Fcj7e', 'ELDER',  0, 100, 0, NULL, 'NORMAL', 'STEADY', NULL, NULL, 0, 1, 0, NOW() - INTERVAL 50 MINUTE, NULL,                       NULL,      'v1.0', NOW() - INTERVAL 85 DAY,  NOW() - INTERVAL 85 DAY);

-- ---------- 5. 画像与地址簿 ----------
-- A 画像完整：小习惯（照护提示卡）、紧急联系人、地址簿 2 条；帮助方向=爱好映射结果（U5 成为微光后并入）。
INSERT INTO `user_profile`
  (`user_id`, `nickname`, `avatar_url`, `age`, `signature`, `hobbies`, `care_tip`, `help_directions`,
   `emergency_contact_name`, `emergency_contact_phone`, `phone`) VALUES
(1, '李秀英', NULL, 68,   '爱晒太阳、喜欢热闹', '["聊天","散步","养花"]', '腿脚不便，需要扶一下', '["聊天","陪伴","散步","宠物花草"]', '李强', '13905518372', '13867429153'),
(2, '王德明', NULL, NULL, NULL,                 NULL,                     NULL,                    NULL,                                 NULL,   NULL,          '13957068214'),
(3, '陈志远', NULL, 32,   '爱跑腿、爱修小物件', NULL,                     NULL,                    '["教学","维修"]',                NULL,   NULL,          '15867132409'),
(4, '赵晓琳', NULL, 24,   '喜欢唠嗑，随叫随到', NULL,                     NULL,                    '["聊天"]',                           NULL,   NULL,          '18652970318'),
(5, '张桂花', NULL, 71,   '闲不住，爱逛早市',   '["散步","做饭"]',        NULL,                    NULL,                                 NULL,   NULL,          '13385649271'),
(6, '刘建军', NULL, 45,   '会点水电小活，帮得上就搭把手', NULL,           NULL,                    '["维修","家务"]',                    NULL,   NULL,          '13745286109'),
(7, '周慧珍', NULL, 66,   '爱干净，喜欢花草',   '["养花"]',               NULL,                    NULL,                                 NULL,   NULL,          '15162384756'),
(8, '吴国强', NULL, 52,   '楼上楼下的活都能搭把手', NULL,                 NULL,                    '["维修","跑腿"]',                NULL,   NULL,          '18245037619'),
(9, '孙丽华', NULL, 74,   '看着孙女长大最开心', '["聊天","看书"]',        NULL,                    NULL,                                 NULL,   NULL,          '13072849563');

INSERT INTO `address_book` (`user_id`, `label`, `area_text`, `detail_text`, `is_default`) VALUES
(1, '家',     '梧桐苑小区 7 号楼', '2 单元 603', 1),
(1, '社区医院', '城南社区医院',    NULL,         0),
(5, '家',     '桂花苑小区 3 号楼', '1 单元 201', 1),
(5, '早市',   '东关早市北门',      NULL,         0),
(7, '家',     '和平里小区 8 号楼', '3 单元 401', 1),
(9, '家',     '丽景华庭 9 号楼',   '3 单元 502', 1);

-- 4D 演示坐标：围绕同一城区（约 30.27, 120.15），用于地址导入后的距离计算与排序
UPDATE `address_book` SET `latitude` = CASE
    WHEN `area_text` LIKE '梧桐苑%' THEN 30.274100
    WHEN `area_text` LIKE '桂花苑%' THEN 30.270200
    WHEN `area_text` LIKE '和平里%' THEN 30.268500
    WHEN `area_text` LIKE '丽景华庭%' THEN 30.279200
    WHEN `area_text` LIKE '城南社区医院%' THEN 30.271800
    WHEN `area_text` LIKE '东关早市%' THEN 30.269400
    ELSE `latitude` END,
  `longitude` = CASE
    WHEN `area_text` LIKE '梧桐苑%' THEN 120.150200
    WHEN `area_text` LIKE '桂花苑%' THEN 120.146800
    WHEN `area_text` LIKE '和平里%' THEN 120.154000
    WHEN `area_text` LIKE '丽景华庭%' THEN 120.143500
    WHEN `area_text` LIKE '城南社区医院%' THEN 120.148900
    WHEN `area_text` LIKE '东关早市%' THEN 120.152600
    ELSE `longitude` END
WHERE `area_text` IS NOT NULL;

-- ---------- 6. 求助单 28 条（详设 8.3 十二单覆盖矩阵 + 拟真扩量） ----------
-- 覆盖：8 状态 × 3 类型抽样（PENDING 含上门/远程/陪同）；#1 急标；#8 徽记与回流示例（同时是 4C 低于保底的存疑单）；
--       #9 已接单后被受助者取消（给助人者留取消通知）；#10 一键重发样例；#11 多轮次释放单（round=2、release=1）；#12 申诉中（证据在聊天）；
--       #13 额外单：拒绝确认→申诉→仲裁落回的历史 + C 第 2 次完成；#14 额外单：C 第 3 次完成（涉钱场景仅陪同不代办的正面样例）；
--       #21–#27 背景完整单：撑起荣耀榜（刘建军 4 次 / 吴国强 3 次 / 陈志远 3 次 / 李秀英 1 次）与感谢/评价生态。
--       完成单服务用时按类型拟真：远程 15–25 分钟、上门 40–50 分钟、陪同 60–100 分钟（#8 例外，见第 375 行说明）。
INSERT INTO `help_order`
  (`id`, `requester_id`, `helper_id`, `source`, `order_type`, `urgent_flag`, `urgency_base`, `urgency_score`,
   `title`, `description`, `address_area`, `address_detail`, `tags`, `time_expect`, `status`, `pre_appeal_status`,
   `current_round`, `release_count`, `age_priority`, `view_count`, `publish_deadline`, `accepted_at`,
   `start_deadline`, `started_at`, `confirm_initiator`, `confirm_deadline`, `completed_at`, `cancelled_at`,
   `cancel_by`, `cancel_reason`, `appeal_deadline`, `version`, `created_at`) VALUES
(1,  1, NULL, 'FREE',     'HOME_VISIT', 1, 2, 5, '厨房水管一直渗水', '厨房水管接口渗水好几天了，地上总有一小滩，想请邻居帮忙看看，有点着急。',
     '梧桐苑小区 7 号楼', '2 单元 603', '["维修"]', 'ASAP', 'PENDING', NULL, 1, 0, 1, 2,
     NOW() + INTERVAL 5 HOUR, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 1 HOUR),
(2,  2, NULL, 'TEMPLATE', 'REMOTE',     0, 1, 1, '教我用手机发照片', '想学怎么用微信发照片、跟闺女视频，一步步教我就行。',
     NULL, NULL, '["教学"]', 'ANY', 'PENDING', NULL, 1, 0, 1, 2,
     NOW() + INTERVAL 20 HOUR, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 4 HOUR),
(3,  2, NULL, 'TEMPLATE', 'ACCOMPANY',  0, 3, 3, '想找个人陪我走一趟', '想去河边公园散步，一个人走不远，想请邻居陪我走一段。',
     '幸福里小区门口', NULL, '["散步"]', 'TODAY', 'PENDING', NULL, 1, 0, 0, 1,
     NOW() + INTERVAL 21 HOUR, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 3 HOUR),
(4,  1, 3,    'TEMPLATE', 'HOME_VISIT', 0, 2, 2, '想请人搭把手做家务', '换季了想把厚被子晾一晾，顺带挪一下柜子。',
     '梧桐苑小区 7 号楼', '2 单元 603', '["家务"]', 'TODAY', 'ACCEPTED', NULL, 1, 0, 1, 0,
     NOW() + INTERVAL 18 HOUR, NOW() - INTERVAL 30 MINUTE, NOW() + INTERVAL 90 MINUTE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 6 HOUR),
(5,  1, 4,    'FREE',     'REMOTE',     0, 1, 1, '教我用手机打视频', '想学会和在外地的儿子视频通话，麻烦教教我。',
     NULL, NULL, '["教学"]', 'ANY', 'IN_SERVICE', NULL, 1, 0, 1, 0,
     NOW() + INTERVAL 22 HOUR, NOW() - INTERVAL 90 MINUTE, NOW() + INTERVAL 30 MINUTE, NOW() - INTERVAL 72 MINUTE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 2 HOUR),
(6,  1, 3,    'TEMPLATE', 'HOME_VISIT', 0, 2, 2, '卫生间的灯不亮了', '卫生间的灯不亮了，想请邻居帮忙换个灯泡。',
     '梧桐苑小区 7 号楼', '2 单元 603', '["维修"]', 'TODAY', 'PENDING_CONFIRM', NULL, 1, 0, 1, 0,
     NOW() + INTERVAL 20 HOUR, NOW() - INTERVAL 3 HOUR, NOW() - INTERVAL 1 HOUR, NOW() - INTERVAL 90 MINUTE,
     'HELPER', NOW() + INTERVAL 23 HOUR + INTERVAL 30 MINUTE, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 4 HOUR),
(7,  1, 3,    'FREE',     'ACCOMPANY',  0, 3, 3, '陪我去社区医院拿药', '周三上午想去社区医院取降压药，想请邻居陪我一起去。',
     '梧桐苑小区南门', NULL, '["陪伴"]', 'TOMORROW', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 48 HOUR, NOW() - INTERVAL 70 HOUR, NOW() - INTERVAL 68 HOUR, NOW() - INTERVAL 68 HOUR - INTERVAL 40 MINUTE,
     'HELPER', NOW() - INTERVAL 43 HOUR - INTERVAL 10 MINUTE, NOW() - INTERVAL 67 HOUR, NULL, NULL, NULL, NOW() - INTERVAL 67 HOUR + INTERVAL 7 DAY, 0, NOW() - INTERVAL 72 HOUR),
(8,  2, 1,    'TEMPLATE', 'REMOTE',     0, 1, 1, '教我在网上交水电费', '想学着在手机上把水电费交了，不用再跑营业厅。',
     NULL, NULL, '["网上办事"]', 'THIS_WEEK', 'COMPLETED', NULL, 1, 0, 0, 0,
     NOW() + INTERVAL 21 HOUR, NOW() - INTERVAL 140 MINUTE, NOW() - INTERVAL 20 MINUTE, NOW() - INTERVAL 63 MINUTE,
     'HELPER', NOW() + INTERVAL 23 HOUR, NOW() - INTERVAL 1 HOUR, NULL, NULL, NULL, NOW() - INTERVAL 1 HOUR + INTERVAL 7 DAY, 0, NOW() - INTERVAL 3 HOUR),
(9,  2, 8,    'FREE',     'HOME_VISIT', 0, 2, 2, '想把窗帘拆下来洗洗', '年关了想把窗帘拆下来洗一洗，自己够不着。',
     '幸福里小区 2 号楼', '1 单元 102', '["家务"]', 'TODAY', 'CANCELLED', NULL, 1, 0, 0, 0,
     NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY - INTERVAL 2 HOUR, NOW() - INTERVAL 4 DAY, NULL, NULL, NULL, NULL, NOW() - INTERVAL 4 DAY, 'REQUESTER',
     '后来孩子们周末回来帮我弄好了，先不麻烦邻居了。', NULL, 0, NOW() - INTERVAL 5 DAY),
(10, 1, NULL, 'TEMPLATE', 'REMOTE',     0, 1, 1, '想学网上买火车票', '闺女教过一次没记住，想请邻居再教教我。',
     NULL, NULL, '["网上办事"]', 'ANY', 'EXPIRED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 2 DAY, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 3 DAY),
(11, 1, NULL, 'FREE',     'HOME_VISIT', 0, 2, 2, '帮我把米面搬上楼', '买了两袋大米和一袋面，在楼下车里，想请邻居帮忙搬上楼。',
     '梧桐苑小区 7 号楼', '2 单元 603', '["跑腿","家务"]', 'ASAP', 'PENDING', NULL, 2, 1, 1, 2,
     NOW() + INTERVAL 4 HOUR, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NOW() - INTERVAL 22 HOUR),
(12, 1, 3,    'FREE',     'HOME_VISIT', 0, 2, 2, '厨房水龙头有点松', '厨房水龙头有些松动，想请邻居帮忙拧紧一下。',
     '梧桐苑小区 7 号楼', '2 单元 603', '["维修"]', 'TODAY', 'APPEALING', 'PENDING_CONFIRM', 1, 0, 1, 0,
     NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 28 HOUR, NOW() - INTERVAL 26 HOUR, NOW() - INTERVAL 24 HOUR,
     'HELPER', NOW(), NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 30 HOUR),
(13, 1, 3,    'TEMPLATE', 'REMOTE',     0, 1, 1, '教我在网上挂号', '想去医院复查，想学着在手机上预约挂号。',
     NULL, NULL, '["网上办事"]', 'THIS_WEEK', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 27 DAY, NOW() - INTERVAL 28 DAY + INTERVAL 20 MINUTE, NOW() - INTERVAL 28 DAY + INTERVAL 2 HOUR + INTERVAL 20 MINUTE, NOW() - INTERVAL 28 DAY + INTERVAL 70 MINUTE,
     'HELPER', NOW() - INTERVAL 27 DAY + INTERVAL 70 MINUTE, NOW() - INTERVAL 28 DAY + INTERVAL 150 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 28 DAY + INTERVAL 150 MINUTE + INTERVAL 7 DAY, 0, NOW() - INTERVAL 28 DAY),
(14, 2, 3,    'TEMPLATE', 'ACCOMPANY',  0, 3, 3, '陪我去银行取退休金', '想去银行取退休金，不太会用取号机，想请邻居陪我跑一趟。',
     '幸福里小区门口', NULL, '["陪伴"]', 'TOMORROW', 'COMPLETED', NULL, 1, 0, 0, 0,
     NOW() - INTERVAL 19 DAY, NOW() - INTERVAL 20 DAY + INTERVAL 1 HOUR, NOW() - INTERVAL 20 DAY + INTERVAL 3 HOUR, NOW() - INTERVAL 20 DAY + INTERVAL 2 HOUR + INTERVAL 50 MINUTE,
     'HELPER', NOW() - INTERVAL 19 DAY + INTERVAL 3 HOUR + INTERVAL 50 MINUTE, NOW() - INTERVAL 20 DAY + INTERVAL 4 HOUR, NULL, NULL, NULL, NOW() - INTERVAL 20 DAY + INTERVAL 4 HOUR + INTERVAL 7 DAY, 0, NOW() - INTERVAL 20 DAY),
(15, 5, NULL, 'FREE',     'ACCOMPANY',  0, 3, 3, '想去早市买点新鲜菜', '想去东关早市挑点青菜，路有点远，想请邻居陪我走一趟。',
     '桂花苑小区门口', NULL, '["散步"]', 'TODAY', 'PENDING', NULL, 1, 0, 1, 1,
     NOW() + INTERVAL 4 HOUR, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 20 HOUR),
(16, 7, NULL, 'TEMPLATE', 'REMOTE',     0, 1, 1, '教我用手机买菜', '听说手机上买菜能送到家，想请邻居教教我。',
     NULL, NULL, '["教学"]', 'ANY', 'PENDING', NULL, 1, 0, 1, 0,
     NOW() + INTERVAL 19 HOUR, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 5 HOUR),
(17, 9, NULL, 'FREE',     'HOME_VISIT', 0, 2, 2, '厨房下水有点堵', '厨房水槽下水变慢了，想请邻居帮忙看看。',
     '丽景华庭 9 号楼', '3 单元 502', '["维修"]', 'TODAY', 'PENDING', NULL, 1, 0, 1, 1,
     NOW() + INTERVAL 21 HOUR, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 3 HOUR),
(18, 7, 4,    'TEMPLATE', 'REMOTE',     0, 1, 1, '教我在网上缴电费', '想学着在手机上把电费缴了，不用跑营业厅。',
     NULL, NULL, '["网上办事"]', 'ANY', 'ACCEPTED', NULL, 1, 0, 1, 0,
     NOW() + INTERVAL 22 HOUR, NOW() - INTERVAL 40 MINUTE, NOW() + INTERVAL 80 MINUTE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 2 HOUR),
(19, 9, 8,    'FREE',     'HOME_VISIT', 0, 2, 2, '想把冬衣收拾进柜子', '换季了想把冬衣收进柜子，柜子有点高，想请邻居搭把手。',
     '丽景华庭 9 号楼', '3 单元 502', '["家务"]', 'TODAY', 'IN_SERVICE', NULL, 1, 0, 1, 0,
     NOW() + INTERVAL 22 HOUR + INTERVAL 30 MINUTE, NOW() - INTERVAL 70 MINUTE, NOW() + INTERVAL 50 MINUTE, NOW() - INTERVAL 50 MINUTE, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 90 MINUTE),
(20, 7, 6,    'FREE',     'HOME_VISIT', 0, 2, 2, '马桶水箱一直流水', '马桶水箱有点漏水，一直有水流声，想请邻居帮忙看看。',
     '和平里小区 8 号楼', '3 单元 401', '["维修"]', 'TODAY', 'PENDING_CONFIRM', NULL, 1, 0, 1, 0,
     NOW() + INTERVAL 18 HOUR, NOW() - INTERVAL 5 HOUR - INTERVAL 30 MINUTE, NOW() - INTERVAL 3 HOUR - INTERVAL 30 MINUTE, NOW() - INTERVAL 90 MINUTE,
     'HELPER', NOW() + INTERVAL 22 HOUR + INTERVAL 30 MINUTE, NULL, NULL, NULL, NULL, NULL, 0, NOW() - INTERVAL 6 HOUR),
(21, 5, 6,    'TEMPLATE', 'REMOTE',     0, 1, 1, '教我用手机拍照', '想学用手机给孙女拍照，再发给老姐妹看看。',
     NULL, NULL, '["教学"]', 'THIS_WEEK', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 24 DAY, NOW() - INTERVAL 25 DAY + INTERVAL 15 MINUTE, NOW() - INTERVAL 25 DAY + INTERVAL 2 HOUR + INTERVAL 15 MINUTE, NOW() - INTERVAL 25 DAY + INTERVAL 60 MINUTE,
     'HELPER', NOW() - INTERVAL 24 DAY + INTERVAL 80 MINUTE, NOW() - INTERVAL 25 DAY + INTERVAL 80 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 25 DAY + INTERVAL 80 MINUTE + INTERVAL 7 DAY, 0, NOW() - INTERVAL 25 DAY),
(22, 9, 6,    'FREE',     'HOME_VISIT', 0, 2, 2, '灯泡闪了好几天', '客厅灯一闪一闪的，想请邻居帮忙换一下。',
     '丽景华庭 9 号楼', '3 单元 502', '["维修"]', 'TODAY', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 16 DAY, NOW() - INTERVAL 17 DAY + INTERVAL 15 MINUTE, NOW() - INTERVAL 17 DAY + INTERVAL 2 HOUR + INTERVAL 15 MINUTE, NOW() - INTERVAL 17 DAY + INTERVAL 75 MINUTE,
     'HELPER', NOW() - INTERVAL 16 DAY + INTERVAL 120 MINUTE, NOW() - INTERVAL 17 DAY + INTERVAL 120 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 17 DAY + INTERVAL 120 MINUTE + INTERVAL 7 DAY, 0, NOW() - INTERVAL 17 DAY),
(23, 7, 6,    'TEMPLATE', 'REMOTE',     0, 1, 1, '想学怎么发语音', '打字太慢，想学发语音消息。',
     NULL, NULL, '["教学"]', 'ANY', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 10 DAY + INTERVAL 15 MINUTE, NOW() - INTERVAL 10 DAY + INTERVAL 2 HOUR + INTERVAL 15 MINUTE, NOW() - INTERVAL 10 DAY + INTERVAL 55 MINUTE,
     'HELPER', NOW() - INTERVAL 9 DAY + INTERVAL 80 MINUTE, NOW() - INTERVAL 10 DAY + INTERVAL 80 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 10 DAY + INTERVAL 80 MINUTE + INTERVAL 7 DAY, 0, NOW() - INTERVAL 10 DAY),
(24, 5, 6,    'FREE',     'ACCOMPANY',  0, 3, 3, '陪我去邮局寄点东西', '想给老家寄点特产，去邮局跑一趟，想请邻居陪我。',
     '桂花苑小区门口', NULL, '["陪伴"]', 'TOMORROW', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 4 DAY + INTERVAL 30 MINUTE, NOW() - INTERVAL 4 DAY + INTERVAL 2 HOUR + INTERVAL 30 MINUTE, NOW() - INTERVAL 4 DAY + INTERVAL 3 HOUR,
     'HELPER', NOW() - INTERVAL 3 DAY + INTERVAL 3 HOUR + INTERVAL 50 MINUTE, NOW() - INTERVAL 4 DAY + INTERVAL 4 HOUR, NULL, NULL, NULL, NOW() - INTERVAL 4 DAY + INTERVAL 4 HOUR + INTERVAL 7 DAY, 0, NOW() - INTERVAL 4 DAY),
(25, 9, 8,    'FREE',     'HOME_VISIT', 0, 2, 2, '水龙头滴水不停', '阳台水龙头关不严，一直滴水，想请邻居帮忙看看。',
     '丽景华庭 9 号楼', '3 单元 502', '["维修"]', 'TODAY', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 21 DAY + INTERVAL 20 MINUTE, NOW() - INTERVAL 21 DAY + INTERVAL 2 HOUR + INTERVAL 20 MINUTE, NOW() - INTERVAL 21 DAY + INTERVAL 90 MINUTE,
     'HELPER', NOW() - INTERVAL 20 DAY + INTERVAL 140 MINUTE, NOW() - INTERVAL 21 DAY + INTERVAL 140 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 21 DAY + INTERVAL 140 MINUTE + INTERVAL 7 DAY, 0, NOW() - INTERVAL 21 DAY),
(26, 7, 8,    'TEMPLATE', 'REMOTE',     0, 1, 1, '教我在手机上交话费', '想学着在手机上把话费交了。',
     NULL, NULL, '["网上办事"]', 'ANY', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 11 DAY, NOW() - INTERVAL 12 DAY + INTERVAL 10 MINUTE, NOW() - INTERVAL 12 DAY + INTERVAL 2 HOUR + INTERVAL 10 MINUTE, NOW() - INTERVAL 12 DAY + INTERVAL 45 MINUTE,
     'HELPER', NOW() - INTERVAL 11 DAY + INTERVAL 60 MINUTE, NOW() - INTERVAL 12 DAY + INTERVAL 60 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 12 DAY + INTERVAL 60 MINUTE + INTERVAL 7 DAY, 0, NOW() - INTERVAL 12 DAY),
(27, 5, 8,    'FREE',     'HOME_VISIT', 0, 2, 2, '纱窗破了个洞', '厨房纱窗破了个洞，想请邻居帮忙换一下。',
     '桂花苑小区 3 号楼', '1 单元 201', '["维修"]', 'TODAY', 'COMPLETED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 5 DAY + INTERVAL 20 MINUTE, NOW() - INTERVAL 5 DAY + INTERVAL 2 HOUR + INTERVAL 20 MINUTE, NOW() - INTERVAL 5 DAY + INTERVAL 85 MINUTE,
     'HELPER', NOW() - INTERVAL 4 DAY + INTERVAL 125 MINUTE, NOW() - INTERVAL 5 DAY + INTERVAL 125 MINUTE, NULL, NULL, NULL, NOW() - INTERVAL 5 DAY + INTERVAL 125 MINUTE + INTERVAL 7 DAY, 0, NOW() - INTERVAL 5 DAY),
(28, 9, NULL, 'TEMPLATE', 'REMOTE',     0, 1, 1, '想学用手机看天气', '想学会在手机上看天气预报，出门前看看。',
     NULL, NULL, '["网上办事"]', 'ANY', 'CANCELLED', NULL, 1, 0, 1, 0,
     NOW() - INTERVAL 1 DAY, NULL, NULL, NULL, NULL, NULL, NULL, NOW() - INTERVAL 1 DAY, 'REQUESTER',
     '孙女周末回来教我了，先不用啦，谢谢。', NULL, 0, NOW() - INTERVAL 2 DAY);

-- 4C 风险演示：真实用时口径 = 开始服务 → 助人者标记完成（不含等待对方确认）；
--    #8 为「网上办事」低于保底 5 分钟的存疑样例（实际 3 分钟），其余完成单按类型给出合理服务时长。
UPDATE `help_order` SET `complete_requested_at` = CASE `id`
    WHEN 7  THEN `completed_at` - INTERVAL 10 MINUTE
    WHEN 13 THEN `completed_at` - INTERVAL 60 MINUTE
    WHEN 14 THEN `completed_at` - INTERVAL 10 MINUTE
    WHEN 24 THEN `completed_at` - INTERVAL 10 MINUTE
    ELSE `completed_at` END
WHERE `status` = 'COMPLETED' AND `complete_requested_at` IS NULL;
-- 申诉中单（#12）：助人者已标记完成，受助者拒绝后进入申诉，故完成时间戳保留
UPDATE `help_order` SET `complete_requested_at` = NOW() - INTERVAL 10 HOUR
WHERE `id` = 12 AND `complete_requested_at` IS NULL;
-- 接受后 2 小时内须确认开始（order.accept_timeout_hours=2）
UPDATE `help_order` SET `start_deadline` = `accepted_at` + INTERVAL 2 HOUR
WHERE `accepted_at` IS NOT NULL;
-- 标记完成后 24 小时内须确认（order.confirm_timeout_hours=24）
UPDATE `help_order` SET `confirm_deadline` = `complete_requested_at` + INTERVAL 24 HOUR
WHERE `complete_requested_at` IS NOT NULL;
-- 申诉窗口 7 天（order.appeal_window_days=7）
UPDATE `help_order` SET `appeal_deadline` = `completed_at` + INTERVAL 7 DAY
WHERE `status` = 'COMPLETED';

-- 4D 地址快照坐标（演示同一城区；远程单无地址，保持 NULL）
UPDATE `help_order` SET `address_lat` = CASE
    WHEN `address_area` LIKE '梧桐苑%' THEN 30.274100
    WHEN `address_area` LIKE '桂花苑%' THEN 30.270200
    WHEN `address_area` LIKE '幸福里%' THEN 30.268500
    WHEN `address_area` LIKE '丽景华庭%' THEN 30.279200
    ELSE `address_lat` END,
  `address_lng` = CASE
    WHEN `address_area` LIKE '梧桐苑%' THEN 120.150200
    WHEN `address_area` LIKE '桂花苑%' THEN 120.146800
    WHEN `address_area` LIKE '幸福里%' THEN 120.154000
    WHEN `address_area` LIKE '丽景华庭%' THEN 120.143500
    ELSE `address_lng` END
WHERE `address_area` IS NOT NULL;

-- 说明：受控标签词表 10 项（常量，与后端 HelpTagConstants 一致）：跑腿、维修、家务、陪伴、
--       散步、教学、网上办事、宠物花草、聊天、其他；快捷短语 5 条（常量配置）：好的 / 麻烦你了 /
--       我在家门口 / 稍等一下 / 谢谢你。

-- ---------- 7. 释放记录与时间线（#11 多轮次；时间线与各单流转一致） ----------
INSERT INTO `release_record`
  (`id`, `order_id`, `round`, `from_status`, `reason_type`, `operator_id`, `reason_text`, `penalty`, `created_at`) VALUES
(1, 11, 1, 'IN_SERVICE', 'HELPLESS', 3, '这两袋米面我实在搬不动，先请其他邻居来帮您。', 'NONE', NOW() - INTERVAL 20 HOUR);

INSERT INTO `order_timeline` (`order_id`, `event_type`, `operator_id`, `remark`, `created_at`) VALUES
(1,  'PUBLISH', 1,  NULL, NOW() - INTERVAL 1 HOUR),
(2,  'PUBLISH', 1,  NULL, NOW() - INTERVAL 4 HOUR),
(3,  'PUBLISH', 2,  NULL, NOW() - INTERVAL 3 HOUR),
(4,  'PUBLISH', 1,  NULL, NOW() - INTERVAL 30 HOUR),
(4,  'ACCEPT',  3,  NULL, NOW() - INTERVAL 30 MINUTE),
(5,  'PUBLISH', 1,  NULL, NOW() - INTERVAL 2 HOUR),
(5,  'ACCEPT',  4,  NULL, NOW() - INTERVAL 90 MINUTE),
(5,  'START',   4,  NULL, NOW() - INTERVAL 72 MINUTE),
(6,  'PUBLISH', 1,  NULL, NOW() - INTERVAL 4 HOUR),
(6,  'ACCEPT',  3,  NULL, NOW() - INTERVAL 3 HOUR),
(6,  'START',   3,  NULL, NOW() - INTERVAL 150 MINUTE),
(6,  'COMPLETE_REQUEST', 3, NULL, NOW() - INTERVAL 30 MINUTE),
(7,  'PUBLISH', 1,  NULL, NOW() - INTERVAL 3 DAY),
(7,  'ACCEPT',  3,  NULL, NOW() - INTERVAL 70 HOUR),
(7,  'START',   3,  NULL, NOW() - INTERVAL 69 HOUR),
(7,  'COMPLETE_REQUEST', 3, NULL, NOW() - INTERVAL 52 HOUR),
(7,  'CONFIRM', 1,  NULL, NOW() - INTERVAL 2 DAY),
(7,  'COMPLETE', 0, NULL, NOW() - INTERVAL 2 DAY),
(8,  'PUBLISH', 2,  NULL, NOW() - INTERVAL 31 HOUR),
(8,  'ACCEPT',  1,  NULL, NOW() - INTERVAL 29 HOUR),
(8,  'START',   1,  NULL, NOW() - INTERVAL 28 HOUR),
(8,  'COMPLETE_REQUEST', 1, NULL, NOW() - INTERVAL 2 HOUR),
(8,  'CONFIRM', 2,  NULL, NOW() - INTERVAL 1 HOUR),
(8,  'COMPLETE', 0, NULL, NOW() - INTERVAL 1 HOUR),
(9,  'PUBLISH', 2,  NULL, NOW() - INTERVAL 5 DAY),
(9,  'CANCEL',  2,  '孩子们周末回来帮我弄好了', NOW() - INTERVAL 4 DAY),
(10, 'PUBLISH', 1,  NULL, NOW() - INTERVAL 3 DAY),
(10, 'EXPIRE',  0,  '待响应超时', NOW() - INTERVAL 2 DAY),
(11, 'PUBLISH', 1,  NULL, NOW() - INTERVAL 22 HOUR),
(11, 'ACCEPT',  3,  NULL, NOW() - INTERVAL 21 HOUR - INTERVAL 30 MINUTE),
(11, 'START',   3,  NULL, NOW() - INTERVAL 21 HOUR),
(11, 'RELEASE', 3,  'HELPLESS', NOW() - INTERVAL 20 HOUR),
(12, 'PUBLISH', 1,  NULL, NOW() - INTERVAL 30 HOUR),
(12, 'ACCEPT',  3,  NULL, NOW() - INTERVAL 28 HOUR),
(12, 'START',   3,  NULL, NOW() - INTERVAL 27 HOUR),
(12, 'COMPLETE_REQUEST', 3, NULL, NOW() - INTERVAL 10 HOUR),
(12, 'APPEAL_OPEN', 1, '水龙头还是有点渗水，想请邻居再来看看，先不确认完成。', NOW() - INTERVAL 9 HOUR),
(13, 'PUBLISH', 1,  NULL, NOW() - INTERVAL 28 DAY),
(13, 'ACCEPT',  3,  NULL, NOW() - INTERVAL 27 DAY - INTERVAL 12 HOUR),
(13, 'START',   3,  NULL, NOW() - INTERVAL 27 DAY - INTERVAL 10 HOUR),
(13, 'COMPLETE_REQUEST', 3, NULL, NOW() - INTERVAL 27 DAY),
(13, 'APPEAL_OPEN', 1, '当时对这一步不太明白，想请管理员帮忙看看。', NOW() - INTERVAL 26 DAY - INTERVAL 22 HOUR),
(13, 'ARBITRATE', 999999, '经核对聊天记录与双方流程，完成情况属实，予以确认完成。', NOW() - INTERVAL 26 DAY),
(13, 'CONFIRM', 999999, NULL, NOW() - INTERVAL 26 DAY),
(13, 'COMPLETE', 0, NULL, NOW() - INTERVAL 26 DAY),
(14, 'PUBLISH', 2,  NULL, NOW() - INTERVAL 20 DAY),
(14, 'ACCEPT',  3,  NULL, NOW() - INTERVAL 19 DAY - INTERVAL 19 HOUR),
(14, 'START',   3,  NULL, NOW() - INTERVAL 19 DAY - INTERVAL 18 HOUR),
(14, 'COMPLETE_REQUEST', 3, NULL, NOW() - INTERVAL 18 DAY - INTERVAL 7 HOUR),
(14, 'CONFIRM', 2,  NULL, NOW() - INTERVAL 18 DAY),
(14, 'COMPLETE', 0, NULL, NOW() - INTERVAL 18 DAY),
(15, 'PUBLISH', 5,  NULL, NOW() - INTERVAL 20 HOUR),
(16, 'PUBLISH', 7,  NULL, NOW() - INTERVAL 5 HOUR),
(17, 'PUBLISH', 9,  NULL, NOW() - INTERVAL 3 HOUR),
(18, 'PUBLISH', 7,  NULL, NOW() - INTERVAL 2 HOUR),
(18, 'ACCEPT',  4,  NULL, NOW() - INTERVAL 40 MINUTE),
(19, 'PUBLISH', 9,  NULL, NOW() - INTERVAL 90 MINUTE),
(19, 'ACCEPT',  8,  NULL, NOW() - INTERVAL 70 MINUTE),
(19, 'START',   8,  NULL, NOW() - INTERVAL 50 MINUTE),
(20, 'PUBLISH', 7,  NULL, NOW() - INTERVAL 6 HOUR),
(20, 'ACCEPT',  6,  NULL, NOW() - INTERVAL 5 HOUR - INTERVAL 30 MINUTE),
(20, 'START',   6,  NULL, NOW() - INTERVAL 5 HOUR),
(20, 'COMPLETE_REQUEST', 6, NULL, NOW() - INTERVAL 1 HOUR),
(21, 'PUBLISH', 5,  NULL, NOW() - INTERVAL 25 DAY),
(21, 'ACCEPT',  6,  NULL, NOW() - INTERVAL 24 DAY - INTERVAL 19 HOUR),
(21, 'START',   6,  NULL, NOW() - INTERVAL 24 DAY - INTERVAL 18 HOUR),
(21, 'COMPLETE_REQUEST', 6, NULL, NOW() - INTERVAL 24 DAY - INTERVAL 7 HOUR),
(21, 'CONFIRM', 5,  NULL, NOW() - INTERVAL 24 DAY),
(21, 'COMPLETE', 0, NULL, NOW() - INTERVAL 24 DAY),
(22, 'PUBLISH', 9,  NULL, NOW() - INTERVAL 17 DAY),
(22, 'ACCEPT',  6,  NULL, NOW() - INTERVAL 16 DAY - INTERVAL 19 HOUR),
(22, 'START',   6,  NULL, NOW() - INTERVAL 16 DAY - INTERVAL 18 HOUR),
(22, 'COMPLETE_REQUEST', 6, NULL, NOW() - INTERVAL 16 DAY - INTERVAL 7 HOUR),
(22, 'CONFIRM', 9,  NULL, NOW() - INTERVAL 16 DAY),
(22, 'COMPLETE', 0, NULL, NOW() - INTERVAL 16 DAY),
(23, 'PUBLISH', 7,  NULL, NOW() - INTERVAL 10 DAY),
(23, 'ACCEPT',  6,  NULL, NOW() - INTERVAL 9 DAY - INTERVAL 19 HOUR),
(23, 'START',   6,  NULL, NOW() - INTERVAL 9 DAY - INTERVAL 18 HOUR),
(23, 'COMPLETE_REQUEST', 6, NULL, NOW() - INTERVAL 9 DAY - INTERVAL 7 HOUR),
(23, 'CONFIRM', 7,  NULL, NOW() - INTERVAL 9 DAY),
(23, 'COMPLETE', 0, NULL, NOW() - INTERVAL 9 DAY),
(24, 'PUBLISH', 5,  NULL, NOW() - INTERVAL 4 DAY),
(24, 'ACCEPT',  6,  NULL, NOW() - INTERVAL 3 DAY - INTERVAL 19 HOUR),
(24, 'START',   6,  NULL, NOW() - INTERVAL 3 DAY - INTERVAL 18 HOUR),
(24, 'COMPLETE_REQUEST', 6, NULL, NOW() - INTERVAL 3 DAY - INTERVAL 7 HOUR),
(24, 'CONFIRM', 5,  NULL, NOW() - INTERVAL 3 DAY),
(24, 'COMPLETE', 0, NULL, NOW() - INTERVAL 3 DAY),
(25, 'PUBLISH', 9,  NULL, NOW() - INTERVAL 21 DAY),
(25, 'ACCEPT',  8,  NULL, NOW() - INTERVAL 20 DAY - INTERVAL 19 HOUR),
(25, 'START',   8,  NULL, NOW() - INTERVAL 20 DAY - INTERVAL 18 HOUR),
(25, 'COMPLETE_REQUEST', 8, NULL, NOW() - INTERVAL 20 DAY - INTERVAL 7 HOUR),
(25, 'CONFIRM', 9,  NULL, NOW() - INTERVAL 20 DAY),
(25, 'COMPLETE', 0, NULL, NOW() - INTERVAL 20 DAY),
(26, 'PUBLISH', 7,  NULL, NOW() - INTERVAL 12 DAY),
(26, 'ACCEPT',  8,  NULL, NOW() - INTERVAL 11 DAY - INTERVAL 19 HOUR),
(26, 'START',   8,  NULL, NOW() - INTERVAL 11 DAY - INTERVAL 18 HOUR),
(26, 'COMPLETE_REQUEST', 8, NULL, NOW() - INTERVAL 11 DAY - INTERVAL 7 HOUR),
(26, 'CONFIRM', 7,  NULL, NOW() - INTERVAL 11 DAY),
(26, 'COMPLETE', 0, NULL, NOW() - INTERVAL 11 DAY),
(27, 'PUBLISH', 5,  NULL, NOW() - INTERVAL 5 DAY),
(27, 'ACCEPT',  8,  NULL, NOW() - INTERVAL 4 DAY - INTERVAL 19 HOUR),
(27, 'START',   8,  NULL, NOW() - INTERVAL 4 DAY - INTERVAL 18 HOUR),
(27, 'COMPLETE_REQUEST', 8, NULL, NOW() - INTERVAL 4 DAY - INTERVAL 7 HOUR),
(27, 'CONFIRM', 5,  NULL, NOW() - INTERVAL 4 DAY),
(27, 'COMPLETE', 0, NULL, NOW() - INTERVAL 4 DAY),
(28, 'PUBLISH', 9,  NULL, NOW() - INTERVAL 2 DAY),
(28, 'CANCEL',  9,  '孙女周末回来教我了', NOW() - INTERVAL 1 DAY);

-- 时间线自洽：主流程事件一律取自订单实际时间戳，避免「订单时间改了、时间线还是旧值」
UPDATE `order_timeline` t JOIN `help_order` o ON o.id = t.order_id
SET t.`created_at` = CASE t.`event_type`
    WHEN 'PUBLISH'          THEN o.`created_at`
    WHEN 'ACCEPT'           THEN o.`accepted_at`
    WHEN 'START'            THEN o.`started_at`
    WHEN 'COMPLETE_REQUEST' THEN o.`complete_requested_at`
    WHEN 'CONFIRM'          THEN o.`completed_at`
    WHEN 'COMPLETE'         THEN o.`completed_at`
    ELSE t.`created_at` END
WHERE t.`event_type` IN ('PUBLISH','ACCEPT','START','COMPLETE_REQUEST','CONFIRM','COMPLETE')
  AND ((t.`event_type` = 'PUBLISH')
    OR (t.`event_type` = 'ACCEPT' AND o.`accepted_at` IS NOT NULL)
    OR (t.`event_type` = 'START' AND o.`started_at` IS NOT NULL)
    OR (t.`event_type` = 'COMPLETE_REQUEST' AND o.`complete_requested_at` IS NOT NULL)
    OR (t.`event_type` IN ('CONFIRM','COMPLETE') AND o.`completed_at` IS NOT NULL));
-- #13 拒绝确认→申诉→仲裁落回：仲裁与最终完成同刻，申诉发起在标记完成之后
UPDATE `order_timeline` t JOIN `help_order` o ON o.id = t.order_id
SET t.`created_at` = o.`completed_at` - INTERVAL 50 MINUTE
WHERE t.`order_id` = 13 AND t.`event_type` = 'APPEAL_OPEN';
UPDATE `order_timeline` t JOIN `help_order` o ON o.id = t.order_id
SET t.`created_at` = o.`completed_at`
WHERE t.`order_id` = 13 AND t.`event_type` = 'ARBITRATE';

-- ---------- 8. 会话与消息（订单 7 完整聊天记录，含系统消息 sender_id=0 示例） ----------
-- 会话按「订单+轮次」建立：#11 仅第一轮会话（CLOSED），验证新接单者不可见上一轮；#12 保留证据消息。
INSERT INTO `chat_session`
  (`id`, `order_id`, `round`, `requester_id`, `helper_id`, `status`, `requester_unread`, `helper_unread`, `closed_at`) VALUES
(1,  4,  1, 1, 3, 'ACTIVE', 0, 0, NULL),
(2,  5,  1, 1, 4, 'ACTIVE', 0, 0, NULL),
(3,  6,  1, 1, 3, 'ACTIVE', 1, 0, NULL),
(4,  7,  1, 1, 3, 'CLOSED', 0, 0, NOW() - INTERVAL 2 DAY),
(5,  8,  1, 2, 1, 'CLOSED', 0, 0, NOW() - INTERVAL 1 HOUR),
(6,  11, 1, 1, 3, 'CLOSED', 0, 0, NOW() - INTERVAL 20 HOUR),
(7,  12, 1, 1, 3, 'ACTIVE', 0, 0, NULL),
(8,  13, 1, 1, 3, 'CLOSED', 0, 0, NOW() - INTERVAL 26 DAY),
(9,  14, 1, 2, 3, 'CLOSED', 0, 0, NOW() - INTERVAL 18 DAY),
(10, 18, 1, 7, 4, 'ACTIVE', 0, 0, NULL),
(11, 19, 1, 9, 8, 'ACTIVE', 0, 0, NULL),
(12, 20, 1, 7, 6, 'ACTIVE', 1, 0, NULL),
(13, 21, 1, 5, 6, 'CLOSED', 0, 0, NOW() - INTERVAL 24 DAY),
(14, 22, 1, 9, 6, 'CLOSED', 0, 0, NOW() - INTERVAL 16 DAY),
(15, 23, 1, 7, 6, 'CLOSED', 0, 0, NOW() - INTERVAL 9 DAY),
(16, 24, 1, 5, 6, 'CLOSED', 0, 0, NOW() - INTERVAL 3 DAY),
(17, 25, 1, 9, 8, 'CLOSED', 0, 0, NOW() - INTERVAL 20 DAY),
(18, 26, 1, 7, 8, 'CLOSED', 0, 0, NOW() - INTERVAL 11 DAY),
(19, 27, 1, 5, 8, 'CLOSED', 0, 0, NOW() - INTERVAL 4 DAY);

-- 会话关闭时间与已完成订单的完成时间保持一致
UPDATE `chat_session` s JOIN `help_order` o ON o.id = s.order_id
SET s.`closed_at` = o.`completed_at`
WHERE s.`status` = 'CLOSED' AND o.`status` = 'COMPLETED';

INSERT INTO `chat_message` (`session_id`, `sender_id`, `message_type`, `content`, `client_msg_id`, `risk_flag`, `created_at`) VALUES
(8,  3, 'TEXT', '阿姨，挂号我教您用手机一步步操作。', 'seed_msg_01', 'NONE', NOW() - INTERVAL 28 DAY + INTERVAL 30 MINUTE),
(8,  1, 'TEXT', '好，我记一下，谢谢你志远。', 'seed_msg_02', 'NONE', NOW() - INTERVAL 28 DAY + INTERVAL 80 MINUTE),
(9,  3, 'TEXT', '王爷爷，明天上午九点我在银行门口等您。', 'seed_msg_03', 'NONE', NOW() - INTERVAL 20 DAY + INTERVAL 80 MINUTE),
(9,  2, 'TEXT', '好的，谢谢你。', 'seed_msg_04', 'NONE', NOW() - INTERVAL 20 DAY + INTERVAL 2 HOUR),
(13, 5, 'TEXT', '小刘，拍照是不是按这个圆圈？', 'seed_msg_05', 'NONE', NOW() - INTERVAL 25 DAY + INTERVAL 70 MINUTE),
(13, 6, 'TEXT', '对，您先对好，轻轻按一下就行。', 'seed_msg_06', 'NONE', NOW() - INTERVAL 25 DAY + INTERVAL 78 MINUTE),
(17, 9, 'TEXT', '小吴，阳台水龙头一直滴水，你哪天方便来看看？', 'seed_msg_07', 'NONE', NOW() - INTERVAL 21 DAY + INTERVAL 100 MINUTE),
(17, 8, 'TEXT', '我明天上午过去。', 'seed_msg_08', 'NONE', NOW() - INTERVAL 21 DAY + INTERVAL 130 MINUTE),
(14, 9, 'TEXT', '小吴，客厅灯闪得人心里发慌。', 'seed_msg_09', 'NONE', NOW() - INTERVAL 17 DAY + INTERVAL 80 MINUTE),
(14, 8, 'TEXT', '灯泡松了，我给您换个新的。', 'seed_msg_10', 'NONE', NOW() - INTERVAL 17 DAY + INTERVAL 110 MINUTE),
(18, 7, 'TEXT', '小吴，手机上怎么交话费呀？', 'seed_msg_11', 'NONE', NOW() - INTERVAL 12 DAY + INTERVAL 50 MINUTE),
(18, 8, 'TEXT', '打开微信，点我、服务、手机充值就行。', 'seed_msg_12', 'NONE', NOW() - INTERVAL 12 DAY + INTERVAL 58 MINUTE),
(15, 7, 'TEXT', '小刘，发语音是按着下面这个键说话吗？', 'seed_msg_13', 'NONE', NOW() - INTERVAL 10 DAY + INTERVAL 65 MINUTE),
(15, 6, 'TEXT', '对，按住说话，松手就发出去了。', 'seed_msg_14', 'NONE', NOW() - INTERVAL 10 DAY + INTERVAL 78 MINUTE),
(19, 5, 'TEXT', '小吴，纱窗破了，麻烦你有空帮看看。', 'seed_msg_15', 'NONE', NOW() - INTERVAL 5 DAY + INTERVAL 95 MINUTE),
(19, 8, 'TEXT', '好嘞，我吃过饭就过去。', 'seed_msg_16', 'NONE', NOW() - INTERVAL 5 DAY + INTERVAL 120 MINUTE),
(16, 5, 'TEXT', '小刘，明天十点我在小区门口等你。', 'seed_msg_17', 'NONE', NOW() - INTERVAL 4 DAY + INTERVAL 3 HOUR + INTERVAL 20 MINUTE),
(16, 6, 'TEXT', '好的张阿姨，我准时到。', 'seed_msg_18', 'NONE', NOW() - INTERVAL 4 DAY + INTERVAL 3 HOUR + INTERVAL 50 MINUTE),
(4,  0, 'SYSTEM', '你们的对话已建立。为了安全，请不要透露验证码、密码，也不要在聊天中转账。', NULL, 'NONE', NOW() - INTERVAL 70 HOUR),
(4,  3, 'TEXT', '阿姨，明天上午八点半我在小区门口等您，陪您去社区医院。', 'seed_msg_19', 'NONE', NOW() - INTERVAL 68 HOUR - INTERVAL 30 MINUTE),
(4,  1, 'TEXT', '好的，麻烦志远了。', 'seed_msg_20', 'NONE', NOW() - INTERVAL 68 HOUR - INTERVAL 10 MINUTE),
(4,  3, 'TEXT', '药取好了，我送您到楼下，您慢点走。', 'seed_msg_21', 'NONE', NOW() - INTERVAL 67 HOUR - INTERVAL 30 MINUTE),
(4,  1, 'TEXT', '今天谢谢你，一路陪我说话，心里踏实多了。', 'seed_msg_22', 'NONE', NOW() - INTERVAL 67 HOUR - INTERVAL 15 MINUTE),
(4,  3, 'TEXT', '应该的阿姨，回家了您早点休息。', 'seed_msg_23', 'NONE', NOW() - INTERVAL 67 HOUR - INTERVAL 10 MINUTE),
(5,  1, 'TEXT', '王大哥，交水电费我教您，先打开微信。', 'seed_msg_24', 'NONE', NOW() - INTERVAL 95 MINUTE),
(5,  2, 'TEXT', '好，我跟着你点。', 'seed_msg_25', 'NONE', NOW() - INTERVAL 92 MINUTE),
(5,  2, 'TEXT', '交上了交上了，太谢谢你了秀英！', 'seed_msg_26', 'NONE', NOW() - INTERVAL 65 MINUTE),
(5,  1, 'TEXT', '不客气，王大哥，以后有不会的随时说。', 'seed_msg_27', 'NONE', NOW() - INTERVAL 62 MINUTE),
(6,  3, 'TEXT', '阿姨，米面我一个人不好搬，实在抱歉，我帮您释放一下，让其他邻居来帮您。', 'seed_msg_28', 'NONE', NOW() - INTERVAL 20 HOUR - INTERVAL 10 MINUTE),
(6,  1, 'TEXT', '没事没事，谢谢你志远。', 'seed_msg_29', 'NONE', NOW() - INTERVAL 20 HOUR - INTERVAL 2 MINUTE),
(7,  3, 'TEXT', '阿姨您好，水龙头我过来看一下。', 'seed_msg_30', 'NONE', NOW() - INTERVAL 27 HOUR - INTERVAL 30 MINUTE),
(7,  3, 'TEXT', '拧紧了，您看看还漏不漏水。', 'seed_msg_31', 'NONE', NOW() - INTERVAL 10 HOUR - INTERVAL 10 MINUTE),
(7,  1, 'TEXT', '好像还是有点渗水，我再看看。', 'seed_msg_32', 'NONE', NOW() - INTERVAL 9 HOUR - INTERVAL 30 MINUTE),
(7,  3, 'TEXT', '那我明天带个新垫圈来换上。', 'seed_msg_33', 'NONE', NOW() - INTERVAL 9 HOUR - INTERVAL 10 MINUTE),
(10, 4, 'TEXT', '周阿姨，缴电费我教您，先打开微信。', 'seed_msg_34', 'NONE', NOW() - INTERVAL 35 MINUTE),
(10, 7, 'TEXT', '好，我戴上眼镜跟着你点。', 'seed_msg_35', 'NONE', NOW() - INTERVAL 30 MINUTE),
(11, 8, 'TEXT', '孙阿姨，我这就上来帮您收拾。', 'seed_msg_36', 'NONE', NOW() - INTERVAL 45 MINUTE),
(11, 9, 'TEXT', '好，我在家，门口给你留着灯。', 'seed_msg_37', 'NONE', NOW() - INTERVAL 40 MINUTE),
(12, 6, 'TEXT', '周阿姨，我到啦，先看看水箱。', 'seed_msg_38', 'NONE', NOW() - INTERVAL 2 HOUR),
(12, 6, 'TEXT', '浮球调好了，您再听听还有没有流水声。', 'seed_msg_39', 'NONE', NOW() - INTERVAL 1 HOUR),
(3,  3, 'TEXT', '阿姨，我到啦，先看看灯座的问题。', 'seed_msg_40', 'NONE', NOW() - INTERVAL 2 HOUR),
(3,  1, 'TEXT', '好，麻烦你了志远。', 'seed_msg_41', 'NONE', NOW() - INTERVAL 115 MINUTE),
(3,  3, 'TEXT', '灯泡换好了，您开灯试试，有问题随时说。', 'seed_msg_42', 'NONE', NOW() - INTERVAL 35 MINUTE),
(2,  4, 'TEXT', '阿姨您好，视频通话我教您，咱们先打开微信。', 'seed_msg_43', 'NONE', NOW() - INTERVAL 66 MINUTE),
(2,  1, 'TEXT', '好嘞，我这就打开。', 'seed_msg_44', 'NONE', NOW() - INTERVAL 60 MINUTE),
(2,  4, 'TEXT', '点右下角"我"，再点"服务"，找到"视频通话"就行。', 'seed_msg_45', 'NONE', NOW() - INTERVAL 55 MINUTE),
(10, 4, 'TEXT', '阿姨，平台要核验一下，把验证码发我。', 'seed_msg_46', 'STRONG', NOW() - INTERVAL 28 MINUTE);

-- ---------- 8.1 防诈上报（看板 AI 命中示例；证据指向 STRONG 消息） ----------
INSERT INTO `report`
(`order_id`, `message_id`, `target_type`, `target_id`, `target_user_id`, `reporter_id`, `type`, `evidence`, `status`, `handler_id`, `handle_note`, `action`, `created_at`, `handled_at`) VALUES
(18, (SELECT `id` FROM `chat_message` WHERE `client_msg_id` = 'seed_msg_46'),
 18, 18, 4, NULL, 'FRAUD_RISK', '命中要素：验证码；方向：HELPER→REQUESTER', 'PENDING', NULL, NULL, NULL, NOW() - INTERVAL 26 MINUTE, NULL);

-- ---------- 9. 微光值与积分账本（用户余额=账本末笔 balance_after，保持一致） ----------
INSERT INTO `credit_ledger` (`user_id`, `delta`, `reason_code`, `order_id`, `balance_after`, `operator_id`, `remark`, `created_at`) VALUES
(3, 2,   'COMPLETE',     13,   102, 0, NULL,                       NOW() - INTERVAL 26 DAY),
(6, 2,   'COMPLETE',     21,   102, 0, NULL,                       NOW() - INTERVAL 24 DAY),
(8, 2,   'COMPLETE',     25,   102, 0, NULL,                       NOW() - INTERVAL 20 DAY),
(3, 2,   'COMPLETE',     14,   104, 0, NULL,                       NOW() - INTERVAL 18 DAY),
(6, 2,   'COMPLETE',     22,   104, 0, NULL,                       NOW() - INTERVAL 16 DAY),
(8, 2,   'COMPLETE',     26,   104, 0, NULL,                       NOW() - INTERVAL 11 DAY),
(4, -50, 'ADMIN_ADJUST', NULL, 50,  1, '演示数据：历史违规累计调整', NOW() - INTERVAL 10 DAY),
(6, 2,   'COMPLETE',     23,   106, 0, NULL,                       NOW() - INTERVAL 9 DAY),
(8, 2,   'COMPLETE',     27,   106, 0, NULL,                       NOW() - INTERVAL 4 DAY),
(6, 2,   'COMPLETE',     24,   108, 0, NULL,                       NOW() - INTERVAL 3 DAY),
(3, 2,   'COMPLETE',     7,    106, 0, NULL,                       NOW() - INTERVAL 2 DAY),
(1, 2,   'COMPLETE',     8,    102, 0, NULL,                       NOW() - INTERVAL 1 HOUR);

INSERT INTO `point_ledger` (`user_id`, `delta`, `reason_code`, `order_id`, `unique_key`, `balance_after`, `created_at`) VALUES
(3, 10, 'COMPLETE', 13, 'ORDER_COMPLETE_13', 10, NOW() - INTERVAL 26 DAY),
(6, 10, 'COMPLETE', 21, 'ORDER_COMPLETE_21', 10, NOW() - INTERVAL 24 DAY),
(8, 10, 'COMPLETE', 25, 'ORDER_COMPLETE_25', 10, NOW() - INTERVAL 20 DAY),
(3, 10, 'COMPLETE', 14, 'ORDER_COMPLETE_14', 20, NOW() - INTERVAL 18 DAY),
(6, 10, 'COMPLETE', 22, 'ORDER_COMPLETE_22', 20, NOW() - INTERVAL 16 DAY),
(8, 10, 'COMPLETE', 26, 'ORDER_COMPLETE_26', 20, NOW() - INTERVAL 11 DAY),
(6, 10, 'COMPLETE', 23, 'ORDER_COMPLETE_23', 30, NOW() - INTERVAL 9 DAY),
(8, 10, 'COMPLETE', 27, 'ORDER_COMPLETE_27', 30, NOW() - INTERVAL 4 DAY),
(6, 10, 'COMPLETE', 24, 'ORDER_COMPLETE_24', 40, NOW() - INTERVAL 3 DAY),
(3, 10, 'COMPLETE', 7,  'ORDER_COMPLETE_7',  30, NOW() - INTERVAL 2 DAY),
(1, 10, 'COMPLETE', 8,  'ORDER_COMPLETE_8',  10, NOW() - INTERVAL 1 HOUR);

-- ---------- 10. 评价 / 感谢信 / 故事卡（故事卡为结算自动生成，凡已结算单均有） ----------
INSERT INTO `evaluation` (`order_id`, `requester_id`, `helper_id`, `stars`, `advice`, `created_at`) VALUES
(13, 1, 3, 4, '教得挺仔细，是我学得慢，耽误他时间了。',             NOW() - INTERVAL 25 DAY - INTERVAL 10 HOUR),
(21, 5, 6, 5, '小刘教得耐心，我当天就学会给孙女发照片了。',         NOW() - INTERVAL 23 DAY - INTERVAL 10 HOUR),
(25, 9, 8, 4, '来得挺快，活儿也细致，就是新龙头装上有点紧。',       NOW() - INTERVAL 19 DAY - INTERVAL 10 HOUR),
(23, 7, 6, 5, '讲得清楚，语音我现在用得可溜了。',                   NOW() - INTERVAL 8 DAY - INTERVAL 10 HOUR),
(7,  1, 3, 5, '志远很耐心，一路陪我慢慢走，还帮我排队取药。',       NOW() - INTERVAL 46 HOUR);

INSERT INTO `thank_letter` (`order_id`, `sender_id`, `helper_id`, `content`, `ai_assisted`, `created_at`) VALUES
(7,  1, 3, '志远，谢谢你那天陪我去医院，一路上陪我说话，我心里特别踏实。有你这样的邻居，真好。——李秀英', 1, NOW() - INTERVAL 44 HOUR),
(24, 5, 6, '小刘，谢谢你陪我去邮局寄东西，还帮我拎了一路。远亲不如近邻，住在这个院里，心里暖和。——张桂花', 1, NOW() - INTERVAL 2 DAY - INTERVAL 20 HOUR),
(25, 9, 8, '小吴，谢谢你跑上跑下帮我修水龙头，现在夜里再也听不到滴水声了。——孙丽华', 1, NOW() - INTERVAL 19 DAY - INTERVAL 6 HOUR);

INSERT INTO `story_card` (`order_id`, `requester_name`, `helper_name`, `thing`, `summary`, `created_at`) VALUES
(13, '李秀英', '陈志远', '教我在网上挂号',     '隔着屏幕，温暖也准时到达。', NOW() - INTERVAL 26 DAY),
(21, '张桂花', '刘建军', '教我用手机拍照',     '隔着屏幕，温暖也准时到达。', NOW() - INTERVAL 24 DAY),
(25, '孙丽华', '吴国强', '水龙头滴水不停',     '一次敲门，一份安心。',       NOW() - INTERVAL 20 DAY),
(14, '王德明', '陈志远', '陪我去银行取退休金', '一路同行，一路话家常。',     NOW() - INTERVAL 18 DAY),
(22, '孙丽华', '刘建军', '灯泡闪了好几天',     '一次敲门，一份安心。',       NOW() - INTERVAL 16 DAY),
(26, '周慧珍', '吴国强', '教我在手机上交话费', '隔着屏幕，温暖也准时到达。', NOW() - INTERVAL 11 DAY),
(23, '周慧珍', '刘建军', '想学怎么发语音',     '隔着屏幕，温暖也准时到达。', NOW() - INTERVAL 9 DAY),
(27, '张桂花', '吴国强', '纱窗破了个洞',       '一次敲门，一份安心。',       NOW() - INTERVAL 4 DAY),
(24, '张桂花', '刘建军', '陪我去邮局寄点东西', '一路同行，一路话家常。',     NOW() - INTERVAL 3 DAY),
(7,  '李秀英', '陈志远', '陪我去社区医院拿药', '一路同行，一路话家常。',     NOW() - INTERVAL 2 DAY),
(8,  '王德明', '李秀英', '教我在网上交水电费', '隔着屏幕，温暖也准时到达。', NOW() - INTERVAL 1 HOUR);

-- ---------- 11. 光语事件（徽记与状态流转留痕；推导态"被点亮"不落库） ----------
INSERT INTO `light_event` (`user_id`, `event_type`, `from_state`, `to_state`, `badge`, `order_id`, `created_at`) VALUES
(1, 'STATE_CHANGE', 'UNLIT',   'STEADY',  NULL,           13,   NOW() - INTERVAL 26 DAY),
(5, 'STATE_CHANGE', 'UNLIT',   'STEADY',  NULL,           21,   NOW() - INTERVAL 24 DAY),
(9, 'STATE_CHANGE', 'UNLIT',   'STEADY',  NULL,           25,   NOW() - INTERVAL 20 DAY),
(2, 'STATE_CHANGE', 'UNLIT',   'STEADY',  NULL,           14,   NOW() - INTERVAL 18 DAY),
(7, 'STATE_CHANGE', 'UNLIT',   'STEADY',  NULL,           26,   NOW() - INTERVAL 11 DAY),
(1, 'BADGE_GAIN',   NULL,      NULL,      'LIGHT_PASSED', 8,    NOW() - INTERVAL 1 HOUR);

-- 派生数据同源：故事卡 / 微光值 / 荣誉积分 / 光语事件一律落在订单实际完成时刻
UPDATE `story_card` s JOIN `help_order` o ON o.id = s.order_id
SET s.`created_at` = o.`completed_at` WHERE o.`status` = 'COMPLETED';
UPDATE `credit_ledger` c JOIN `help_order` o ON o.id = c.order_id
SET c.`created_at` = o.`completed_at`
WHERE c.`reason_code` = 'COMPLETE' AND o.`status` = 'COMPLETED';
UPDATE `point_ledger` p JOIN `help_order` o ON o.id = p.order_id
SET p.`created_at` = o.`completed_at`
WHERE p.`reason_code` = 'COMPLETE' AND o.`status` = 'COMPLETED';
UPDATE `light_event` l JOIN `help_order` o ON o.id = l.order_id
SET l.`created_at` = o.`completed_at` WHERE o.`status` = 'COMPLETED';

-- ---------- 12. 申诉与反馈（#12 待管理员裁决；#13 历史仲裁已落回；反馈复用本表） ----------
INSERT INTO `appeal` (`id`, `order_id`, `initiator_id`, `type`, `reason`, `status`, `handler_id`, `result`, `conclusion`, `created_at`, `resolved_at`) VALUES
(1, 12,   1, 'CONFIRM_REJECT', '水龙头还是有点渗水，想请邻居再来看看，先不确认完成。', 'PENDING',  NULL, NULL,            NULL, NOW() - INTERVAL 9 HOUR, NULL),
(2, 13,   1, 'CONFIRM_REJECT', '当时对这一步不太明白，想请管理员帮忙看看。',             'RESOLVED', 1,    'TO_COMPLETED', '经核对聊天记录与双方流程，完成情况属实，予以确认完成。', NOW() - INTERVAL 28 DAY + INTERVAL 100 MINUTE, NOW() - INTERVAL 28 DAY + INTERVAL 150 MINUTE),
(3, NULL, 1, 'FEEDBACK',       '希望字能再大一些，看着更清楚。',                         'RESOLVED', 1,    NULL,           '感谢建议，字号设置已在优化中，会尽快与您见面。',        NOW() - INTERVAL 8 DAY,  NOW() - INTERVAL 8 DAY);

-- ---------- 13. 通知（覆盖 19 类型示例；文案同 NotifyService 写死模板） ----------
INSERT INTO `notification` (`user_id`, `type`, `title`, `content`, `jump_type`, `jump_id`, `is_read`, `created_at`) VALUES
(1, 'APPEAL_RESULT',   '申诉有结果了', '您关于"教我在网上挂号"的申诉已有处理结果，请查看',                        'APPEAL',  2,    1, NOW() - INTERVAL 28 DAY + INTERVAL 150 MINUTE),
(1, 'SETTLED',         '这次帮助完成了', '"教我在网上挂号"已圆满完成，感谢您的信任',                              'ORDER',   13,   1, NOW() - INTERVAL 28 DAY + INTERVAL 150 MINUTE),
(3, 'SETTLED',         '这次帮助完成了', '"教我在网上挂号"已圆满完成，感谢您的信任',                              'ORDER',   13,   1, NOW() - INTERVAL 28 DAY + INTERVAL 150 MINUTE),
(3, 'POINTS_CREDITED', '荣誉积分到账', '您获得了 10 荣誉积分，感谢您的善意',                                                  'NONE',    NULL, 1, NOW() - INTERVAL 28 DAY + INTERVAL 150 MINUTE),
(3, 'CREDIT_CHANGED',  '微光值变动', '您的微光值有变动，去看看明细吧',                                               'NONE',    NULL, 1, NOW() - INTERVAL 28 DAY + INTERVAL 150 MINUTE),
(1, 'COOLDOWN',        '接单小休息', '您的账号进入 48 小时接单休息期，远程的帮助依然可以照亮别人',                    'NONE',    NULL, 1, NOW() - INTERVAL 15 DAY),
(1, 'ADMIN_ACTION',    '管理员通知', '管理员对您的账号进行了处理，请查看',                                        'NONE',    NULL, 1, NOW() - INTERVAL 12 DAY),
(4, 'ADMIN_ACTION',    '管理员通知', '管理员对您的账号进行了处理，请查看',                                        'NONE',    NULL, 1, NOW() - INTERVAL 10 DAY),
(4, 'CREDIT_CHANGED',  '微光值变动', '您的微光值有变动，去看看明细吧',                                               'NONE',    NULL, 1, NOW() - INTERVAL 10 DAY),
(1, 'FEEDBACK_RESULT', '您的反馈有回音了', '您提交的反馈已处理，感谢您的建议',                                      'NONE',    NULL, 1, NOW() - INTERVAL 8 DAY),
(1, 'SETTLED',         '这次帮助完成了', '"陪我去社区医院拿药"已圆满完成，感谢您的信任',                            'ORDER',   7,    1, NOW() - INTERVAL 67 HOUR),
(1, 'EXPIRED',         '暂时没有邻居接单', '"想学网上买火车票"还没有邻居响应，可以试试"一键重发"',                    'ORDER',   10,   1, NOW() - INTERVAL 2 DAY),
(3, 'SETTLED',         '这次帮助完成了', '"陪我去社区医院拿药"已圆满完成，感谢您的信任',                            'ORDER',   7,    1, NOW() - INTERVAL 67 HOUR),
(3, 'POINTS_CREDITED', '荣誉积分到账', '您获得了 10 荣誉积分，感谢您的善意',                                                  'NONE',    NULL, 1, NOW() - INTERVAL 67 HOUR),
(3, 'CREDIT_CHANGED',  '微光值变动', '您的微光值有变动，去看看明细吧',                                               'NONE',    NULL, 1, NOW() - INTERVAL 67 HOUR),
(1, 'ACCEPTED',        '有人来接您的求助啦', '陈志远 愿意帮您"厨房水龙头有点松"，去打个招呼吧',                        'ORDER',   12,   1, NOW() - INTERVAL 28 HOUR),
(1, 'SERVICE_STARTED', '邻居已出发', '陈志远 已确认开始帮忙"厨房水龙头有点松"',                                        'ORDER',   12,   1, NOW() - INTERVAL 24 HOUR),
(3, 'SETTLED',         '这次帮助完成了', '"陪我去银行取退休金"已圆满完成，感谢您的信任',                            'ORDER',   14,   1, NOW() - INTERVAL 20 DAY + INTERVAL 4 HOUR),
(3, 'POINTS_CREDITED', '荣誉积分到账', '您获得了 10 荣誉积分，感谢您的善意',                                                  'NONE',    NULL, 1, NOW() - INTERVAL 20 DAY + INTERVAL 4 HOUR),
(3, 'CREDIT_CHANGED',  '微光值变动', '您的微光值有变动，去看看明细吧',                                               'NONE',    NULL, 1, NOW() - INTERVAL 20 DAY + INTERVAL 4 HOUR),
(1, 'ACCEPTED',        '有人来接您的求助啦', '陈志远 愿意帮您"帮我把米面搬上楼"，去打个招呼吧',                        'ORDER',   11,   1, NOW() - INTERVAL 21 HOUR - INTERVAL 30 MINUTE),
(1, 'RELEASED',        '别干等，已为您重新寻找', '很抱歉，"帮我把米面搬上楼"的邻居临时有事，我们已为您重新寻找其他邻居', 'ORDER',   11,   0, NOW() - INTERVAL 20 HOUR),
(1, 'ACCEPTED',        '有人来接您的求助啦', '陈志远 愿意帮您"卫生间的灯不亮了"，去打个招呼吧',                      'ORDER',   6,    1, NOW() - INTERVAL 3 HOUR),
(1, 'SERVICE_STARTED', '邻居已出发', '陈志远 已确认开始帮忙"卫生间的灯不亮了"',                                      'ORDER',   6,    1, NOW() - INTERVAL 90 MINUTE),
(1, 'ACCEPTED',        '有人来接您的求助啦', '赵晓琳 愿意帮您"教我用手机打视频"，去打个招呼吧',                        'ORDER',   5,    1, NOW() - INTERVAL 90 MINUTE),
(1, 'SERVICE_STARTED', '邻居已出发', '赵晓琳 已确认开始帮忙"教我用手机打视频"',                                        'ORDER',   5,    1, NOW() - INTERVAL 72 MINUTE),
(1, 'SETTLED',         '这次帮助完成了', '"教我在网上交水电费"已圆满完成，感谢您的信任',                            'ORDER',   8,    1, NOW() - INTERVAL 1 HOUR),
(1, 'POINTS_CREDITED', '荣誉积分到账', '您获得了 10 荣誉积分，感谢您的善意',                                                  'NONE',    NULL, 1, NOW() - INTERVAL 1 HOUR),
(1, 'CREDIT_CHANGED',  '微光值变动', '您的微光值有变动，去看看明细吧',                                               'NONE',    NULL, 1, NOW() - INTERVAL 1 HOUR),
(1, 'NEW_MESSAGE',     '新消息', '陈志远 给您发来新消息，去看看吧',                                                    'SESSION', 3,    0, NOW() - INTERVAL 35 MINUTE),
(1, 'CONFIRM_REQUEST', '对方标记了完成', '陈志远 说"卫生间的灯不亮了"已经完成啦，请您确认一下',                       'ORDER',   6,    0, NOW() - INTERVAL 30 MINUTE),
(1, 'ACCEPTED',        '有人来接您的求助啦', '陈志远 愿意帮您"想请人搭把手做家务"，去打个招呼吧',                      'ORDER',   4,    0, NOW() - INTERVAL 30 MINUTE),
(8, 'CANCEL_NOTICE',   '求助已取消', '您正在帮忙的"想把窗帘拆下来洗洗"已被取消，辛苦了，去看看吧',                        'ORDER',   9,    0, NOW() - INTERVAL 4 DAY),
(8, 'CANCEL_REQUEST',  '邻居想和您商量取消', '王德明 想和您商量取消"想把窗帘拆下来洗洗"，去看看吧',                            'ORDER',   9,    0, NOW() - INTERVAL 4 DAY - INTERVAL 1 HOUR),
(3, 'CONFIRM_REJECT',  '完成确认被拒绝了', '"教我在网上挂号"的完成确认被对方拒绝了，已进入申诉处理',                        'ORDER',   13,   0, NOW() - INTERVAL 28 DAY + INTERVAL 100 MINUTE),
(3, 'APPEAL_OPEN',     '邻居提交了申诉', '李秀英 就"厨房水龙头有点松"提交了申诉，请留意处理结果',                          'ORDER',   12,   0, NOW() - INTERVAL 9 HOUR),
(9, 'POST_REVIEW_RESULT', '动态审核中', '您的动态需要人工确认一下，请耐心等待',                                              'POST',    5,    0, NOW() - INTERVAL 35 MINUTE),
(5, 'COMMENT_REPLY',   '收到新评论', '李秀英 评论了您的动态，去看看吧',                                                    'POST',    3,    0, NOW() - INTERVAL 3 HOUR);

-- 聊天类通知补齐发送方昵称（message_count 走默认值 1）
UPDATE `notification`
SET `sender_nickname` = SUBSTRING_INDEX(`content`, ' 给您发来', 1)
WHERE `type` = 'NEW_MESSAGE' AND (`sender_nickname` IS NULL OR `sender_nickname` = '');

-- ---------- 14. 浏览记录（与各待响应单 view_count 一致） ----------
INSERT INTO `order_view` (`order_id`, `user_id`, `created_at`) VALUES
(1,  3, NOW() - INTERVAL 40 MINUTE),
(1,  4, NOW() - INTERVAL 50 MINUTE),
(2,  3, NOW() - INTERVAL 3 HOUR),
(3,  4, NOW() - INTERVAL 2 HOUR),
(11, 3, NOW() - INTERVAL 18 HOUR),
(11, 4, NOW() - INTERVAL 17 HOUR),
(15, 3, NOW() - INTERVAL 35 MINUTE),
(17, 8, NOW() - INTERVAL 2 HOUR);

-- ---------- 15. 社区 2.0（v1.3 临时演示数据；T27 重写时并入正式 seed 契约） ----------
INSERT INTO `community_post`
(`author_id`, `category`, `title`, `content`, `images`, `status`, `review_degraded`, `review_reason`, `like_count`, `comment_count`, `created_at`) VALUES
(0, 'OFFICIAL', '怎么在邻里圈发一条动态', '点社区页右下角的“发布”，写几句想和邻居说的话，最多可以配 3 张图。发完会先审核一下，通过了邻居们就能看到。', NULL, 'PUBLISHED', 0, NULL, 2, 2, NOW() - INTERVAL 2 DAY),
(0, 'OFFICIAL', '防诈小提醒', '邻里帮忙不涉及转账、代付、垫付。遇到有人让您先转钱、要验证码或银行卡，请先停下来，和家人或管理员说一声。', NULL, 'PUBLISHED', 0, NULL, 2, 2, NOW() - INTERVAL 1 DAY),
(5, 'NORMAL', '楼下的桂花开了', '今天下楼晒太阳，闻到桂花香了。谁家有多的小凳子，想借一张在树下坐坐。', NULL, 'PUBLISHED', 0, NULL, 2, 2, NOW() - INTERVAL 5 HOUR),
(7, 'STORY', '一束光，从屏幕那头照进来', '想学发语音，小刘在手机上一句句教我。现在我给老姐妹发语音，一点不费劲了。他说不用谢，以后有事再叫他。', NULL, 'PUBLISHED', 0, NULL, 2, 2, NOW() - INTERVAL 26 HOUR),
(9, 'NORMAL', '有人一起晨练吗', '想找几位邻居每天早上在小区花园一起走走，七点出发，走半小时。', NULL, 'PENDING_MANUAL', 0, '本地命中：垫付', 0, 0, NOW() - INTERVAL 40 MINUTE),
(6, 'NORMAL', '私下交易信息', '有需要买保健品的可以加微信私下交易，价格好商量。', NULL, 'REJECTED', 0, '内容含私下交易信息', 0, 0, NOW() - INTERVAL 3 DAY);

INSERT INTO `community_comment` (`post_id`, `author_id`, `content`, `status`, `created_at`) VALUES
(1, 5, '学会了，谢谢小助手。', 'PUBLISHED', NOW() - INTERVAL 1 DAY),
(1, 7, '字再大一点就更好了。', 'PUBLISHED', NOW() - INTERVAL 20 HOUR),
(2, 9, '记住了，不随便转钱。', 'PUBLISHED', NOW() - INTERVAL 18 HOUR),
(2, 1, '这个提醒很及时。', 'PUBLISHED', NOW() - INTERVAL 12 HOUR),
(3, 1, '我家有小凳子，明天给您送过去。', 'PUBLISHED', NOW() - INTERVAL 4 HOUR),
(3, 9, '桂花香，真好。', 'PUBLISHED', NOW() - INTERVAL 2 HOUR),
(4, 3, '远亲不如近邻。', 'PUBLISHED', NOW() - INTERVAL 20 HOUR),
(4, 1, '看得心里暖暖的。', 'PUBLISHED', NOW() - INTERVAL 10 HOUR);

INSERT INTO `community_like` (`post_id`, `user_id`, `created_at`) VALUES
(1, 1, NOW() - INTERVAL 1 DAY), (1, 3, NOW() - INTERVAL 20 HOUR),
(2, 1, NOW() - INTERVAL 18 HOUR), (2, 5, NOW() - INTERVAL 12 HOUR),
(3, 3, NOW() - INTERVAL 4 HOUR), (3, 9, NOW() - INTERVAL 2 HOUR),
(4, 1, NOW() - INTERVAL 20 HOUR), (4, 3, NOW() - INTERVAL 10 HOUR);