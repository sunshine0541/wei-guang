-- ============================================================
-- 微光——社区互助服务系统 · 基础 seed（T02：配置 / 模板 / 敏感词）
-- 执行：mysql --default-character-set=utf8mb4 -u root -p weiguang -e "source seed.sql"
-- 幂等：全部使用 INSERT ... ON DUPLICATE KEY UPDATE，可重复执行；
--       重复执行会把配置值重置为默认值（reset.sql 依赖此语义回到干净演示状态）。
-- 业务数据 seed（演示账号/求助单矩阵等）见 T18。
-- ============================================================
SET NAMES utf8mb4;

-- ---------- 1. sys_config 全量 62 项（详设第 7 章，逐项对应） ----------
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
('order.post_action_window_days',   '7',          '评价/感谢信/悄悄话窗口（天）',           '7',          '1-30',        'system'),
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
('ai.real-impl',                    'springai',   '真实实现 springai/restclient',           'springai',   'springai/restclient', 'system'),
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
('privacy.policy_version',          'v1.0',       '隐私说明版本号',                         'v1.0',       'vX.Y',        'system')
ON DUPLICATE KEY UPDATE
  `config_value`  = VALUES(`config_value`),
  `description`   = VALUES(`description`),
  `default_value` = VALUES(`default_value`),
  `value_range`   = VALUES(`value_range`),
  `updated_by`    = VALUES(`updated_by`);

-- ---------- 2. help_template 8 个（详设 8.4） ----------
INSERT INTO `help_template` (`id`, `name`, `icon`, `cover_desc`, `default_type`, `title`, `sort`, `enabled`) VALUES
(1, '代买跑腿',   '🛒', '买菜、取药、取快递等跑腿小事',     'HOME_VISIT', '帮我买点东西',       1, 1),
(2, '小修小补',   '💡', '灯泡、水龙头、小家电的小修小补',   'HOME_VISIT', '家里有个小东西坏了', 2, 1),
(3, '家务搭把手', '🏠', '打扫、晾晒、搬挪等家务搭把手',     'HOME_VISIT', '想请人搭把手做家务', 3, 1),
(4, '陪伴同行',   '🌿', '散步、就医、办事的陪伴同行',       'ACCOMPANY',  '想找个人陪我走一趟', 4, 1),
(5, '手机教学',   '📱', '教您用手机：微信、视频、扫码',     'REMOTE',     '教我用手机',         5, 1),
(6, '网上办事',   '💳', '线上缴费、挂号、办事的教学与指导', 'REMOTE',     '教我在网上办点事',   6, 1),
(7, '花草宠物',   '🌱', '浇花、喂猫狗等花草宠物照看',       'HOME_VISIT', '帮我照看一下花草宠物', 7, 1),
(8, '其他小事',   '✍️', '说不清类型的小事，由您来定',       'REMOTE',     '有件小事想请邻居帮忙', 8, 1)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`), `icon` = VALUES(`icon`), `cover_desc` = VALUES(`cover_desc`),
  `default_type` = VALUES(`default_type`), `title` = VALUES(`title`),
  `sort` = VALUES(`sort`), `enabled` = VALUES(`enabled`);

-- ---------- 3. sensitive_word（详设 8.4） ----------
-- 防诈要素 8 条（action=REVIEW，触发能力二 AI 复核）
INSERT INTO `sensitive_word` (`word`, `category`, `action`, `enabled`) VALUES
('验证码',     'FRAUD',     'REVIEW', 1),
('银行卡',     'FRAUD',     'REVIEW', 1),
('密码',       'FRAUD',     'REVIEW', 1),
('转账',       'FRAUD',     'REVIEW', 1),
('汇款',       'FRAUD',     'REVIEW', 1),
('扫码付款',   'FRAUD',     'REVIEW', 1),
('身份证号',   'FRAUD',     'REVIEW', 1),
('社保卡',     'FRAUD',     'REVIEW', 1)
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
