-- ============================================================
-- 微光——社区互助服务系统 · 全量建表脚本（详设第 2 章，共 23 张表）
-- 执行：mysql --default-character-set=utf8mb4 -u root -p weiguang -e "source <本文件>"
-- 说明：本脚本为「全量重建」语义（先 DROP 再 CREATE），重复执行安全。
-- 通用约定：主键 BIGINT UNSIGNED AUTO_INCREMENT；枚举以 VARCHAR 存英文值；
--          账本与证据类表（release_record/order_timeline/chat_message/credit_ledger/
--          point_ledger/light_event/whisper）只增不改不删，无 updated_at、无 deleted；
--          业务表逻辑删除用 deleted TINYINT DEFAULT 0。
-- ============================================================
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `user`;
DROP TABLE IF EXISTS `user_profile`;
DROP TABLE IF EXISTS `address_book`;
DROP TABLE IF EXISTS `help_template`;
DROP TABLE IF EXISTS `help_order`;
DROP TABLE IF EXISTS `release_record`;
DROP TABLE IF EXISTS `order_timeline`;
DROP TABLE IF EXISTS `chat_session`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `credit_ledger`;
DROP TABLE IF EXISTS `point_ledger`;
DROP TABLE IF EXISTS `evaluation`;
DROP TABLE IF EXISTS `thank_letter`;
DROP TABLE IF EXISTS `story_card`;
DROP TABLE IF EXISTS `light_event`;
DROP TABLE IF EXISTS `whisper`;
DROP TABLE IF EXISTS `notification`;
DROP TABLE IF EXISTS `appeal`;
DROP TABLE IF EXISTS `report`;
DROP TABLE IF EXISTS `sensitive_word`;
DROP TABLE IF EXISTS `sys_config`;
DROP TABLE IF EXISTS `admin_user`;
DROP TABLE IF EXISTS `order_view`;

-- 2.2 user（账号主体）
CREATE TABLE `user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid` VARCHAR(64) NOT NULL COMMENT '微信 openid；注销时改写为 deleted_<user_id>',
  `ui_mode` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL 普通 / ELDER 年长',
  `motion_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '动效开关',
  `credit_score` INT NOT NULL DEFAULT 100 COMMENT '微光值，下限 0',
  `violation_count` INT NOT NULL DEFAULT 0 COMMENT '违约累计计数，冷却结束清零',
  `cooldown_until` DATETIME NULL DEFAULT NULL COMMENT '接单冷却截止',
  `status` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/BANNED/CANCELLED',
  `light_state` VARCHAR(16) NOT NULL DEFAULT 'UNLIT' COMMENT 'UNLIT/STEADY/RESTING',
  `light_prev_state` VARCHAR(16) NULL DEFAULT NULL COMMENT '进入熄灭前的持久状态',
  `rest_since` DATETIME NULL DEFAULT NULL COMMENT '进入熄灭时刻',
  `light_passed` TINYINT NOT NULL DEFAULT 0 COMMENT '光已传递徽记',
  `helped_before` TINYINT NOT NULL DEFAULT 0 COMMENT '是否曾被帮助完成',
  `consecutive_release_count` INT NOT NULL DEFAULT 0 COMMENT '连续被释放计数（求助者视角）',
  `last_interaction_at` DATETIME NULL DEFAULT NULL COMMENT '最近互动时间',
  `last_light_invite_at` DATETIME NULL DEFAULT NULL COMMENT '最近微光邀请弹窗时间',
  `invite_flag` VARCHAR(16) NULL DEFAULT NULL COMMENT 'WILLING/LATER/NULL',
  `privacy_version` VARCHAR(16) NULL DEFAULT NULL COMMENT '已同意的隐私说明版本号',
  `privacy_agreed_at` DATETIME NULL DEFAULT NULL COMMENT '同意时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账号主体';

-- 2.3 user_profile（画像）
CREATE TABLE `user_profile` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT 'FK->user.id',
  `nickname` VARCHAR(20) NULL DEFAULT NULL COMMENT '昵称',
  `avatar_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '头像 URL',
  `age` INT NULL DEFAULT NULL COMMENT '自主申报年龄；未填=普通模式',
  `signature` VARCHAR(50) NULL DEFAULT NULL COMMENT '个性签名',
  `hobbies` VARCHAR(255) NULL DEFAULT NULL COMMENT '喜欢做的事，JSON 数组（8 项）',
  `care_tip` VARCHAR(100) NULL DEFAULT NULL COMMENT '需要留意的小习惯',
  `help_directions` VARCHAR(255) NULL DEFAULT NULL COMMENT '可提供帮助方向，JSON 数组',
  `emergency_contact_name` VARCHAR(20) NULL DEFAULT NULL COMMENT '紧急联系人姓名',
  `emergency_contact_phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '紧急联系人电话',
  `phone` VARCHAR(20) NULL DEFAULT NULL COMMENT '选填联系方式，不参与鉴权',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_profile_user` (`user_id`),
  CONSTRAINT `fk_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像';

-- 2.4 address_book（常去地址簿）
CREATE TABLE `address_book` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `label` VARCHAR(20) NOT NULL COMMENT '标签：家/居委会/公园/超市等',
  `area_text` VARCHAR(100) NOT NULL COMMENT '小区/区域（接单后可见；陪同类即集合点）',
  `detail_text` VARCHAR(100) NULL DEFAULT NULL COMMENT '楼栋门牌（上门类确认开始后解锁）',
  `is_default` TINYINT NOT NULL DEFAULT 0 COMMENT '默认地址，每用户至多 1 条',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_address_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='常去地址簿（每用户 <=10 条）';

-- 2.5 help_template（求助模板，seed 内置 8 个）
CREATE TABLE `help_template` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(20) NOT NULL COMMENT '模板名',
  `icon` VARCHAR(16) NOT NULL COMMENT 'emoji 图标',
  `cover_desc` VARCHAR(100) NOT NULL COMMENT '覆盖的小事描述',
  `default_type` VARCHAR(16) NOT NULL COMMENT 'HOME_VISIT/ACCOMPANY/REMOTE',
  `title` VARCHAR(20) NOT NULL COMMENT '预填规范标题',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '展示排序',
  `enabled` TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求助模板（允许热点缓存）';

-- 2.6 help_order（求助单，核心）
CREATE TABLE `help_order` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `requester_id` BIGINT UNSIGNED NOT NULL COMMENT '发布者',
  `helper_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '当前轮接单者',
  `source` VARCHAR(16) NOT NULL COMMENT 'TEMPLATE/FREE/REPEAT',
  `order_type` VARCHAR(16) NOT NULL COMMENT 'HOME_VISIT/ACCOMPANY/REMOTE',
  `urgent_flag` TINYINT NOT NULL DEFAULT 0,
  `urgency_base` INT NOT NULL COMMENT '类型基准分',
  `urgency_score` INT NOT NULL COMMENT '紧急度总分',
  `title` VARCHAR(20) NOT NULL,
  `description` VARCHAR(200) NULL DEFAULT NULL,
  `address_area` VARCHAR(100) NULL DEFAULT NULL COMMENT '地址快照-小区/区域',
  `address_detail` VARCHAR(100) NULL DEFAULT NULL COMMENT '地址快照-楼栋门牌（仅上门类可有值）',
  `tags` VARCHAR(100) NOT NULL DEFAULT '[]' COMMENT 'JSON 数组，受控词表，<=3 个',
  `time_expect` VARCHAR(16) NULL DEFAULT NULL COMMENT 'ASAP/TODAY/TOMORROW/THIS_WEEK/ANY',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ACCEPTED/IN_SERVICE/PENDING_CONFIRM/COMPLETED/CANCELLED/EXPIRED/APPEALING',
  `pre_appeal_status` VARCHAR(16) NULL DEFAULT NULL COMMENT '进入申诉前状态',
  `current_round` INT NOT NULL DEFAULT 1 COMMENT '派单轮次',
  `release_count` INT NOT NULL DEFAULT 0 COMMENT '被释放次数',
  `age_priority` TINYINT NOT NULL DEFAULT 0 COMMENT '年长排序键；只参与 SQL 排序，禁止出现在任何 API 响应',
  `view_count` INT NOT NULL DEFAULT 0 COMMENT '浏览计数',
  `publish_deadline` DATETIME NOT NULL COMMENT '待响应截止',
  `accepted_at` DATETIME NULL DEFAULT NULL,
  `start_deadline` DATETIME NULL DEFAULT NULL COMMENT '确认开始截止',
  `started_at` DATETIME NULL DEFAULT NULL,
  `confirm_initiator` VARCHAR(16) NULL DEFAULT NULL COMMENT 'REQUESTER/HELPER',
  `confirm_deadline` DATETIME NULL DEFAULT NULL COMMENT '待确认截止',
  `completed_at` DATETIME NULL DEFAULT NULL,
  `cancelled_at` DATETIME NULL DEFAULT NULL,
  `cancel_by` VARCHAR(16) NULL DEFAULT NULL COMMENT 'REQUESTER/HELPER/ADMIN',
  `cancel_reason` VARCHAR(200) NULL DEFAULT NULL,
  `cancel_pending_by` VARCHAR(16) NULL DEFAULT NULL COMMENT '协商取消发起方',
  `cancel_pending_deadline` DATETIME NULL DEFAULT NULL COMMENT '协商取消响应截止',
  `cancel_arbitrate_at` DATETIME NULL DEFAULT NULL COMMENT '协商取消裁决时间（CANCEL/KEEP 均写）',
  `appeal_deadline` DATETIME NULL DEFAULT NULL COMMENT '申诉截止',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'MyBatis-Plus @Version 乐观锁',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_order_list` (`status`, `urgency_score`, `created_at`),
  KEY `idx_order_helper` (`helper_id`, `status`),
  KEY `idx_order_requester` (`requester_id`, `status`),
  KEY `idx_order_created` (`created_at`),
  KEY `idx_order_publish_deadline` (`publish_deadline`),
  KEY `idx_order_start_deadline` (`start_deadline`),
  KEY `idx_order_confirm_deadline` (`confirm_deadline`),
  KEY `idx_order_cancel_pending_deadline` (`cancel_pending_deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求助单';

-- 2.7 release_record（释放重派记录，只增不改）
CREATE TABLE `release_record` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `round` INT NOT NULL COMMENT '释放前轮次',
  `from_status` VARCHAR(16) NOT NULL COMMENT 'ACCEPTED/IN_SERVICE',
  `reason_type` VARCHAR(24) NOT NULL COMMENT 'START_TIMEOUT/HELPER_QUIT/HELPLESS/HELPER_LOST',
  `operator_id` BIGINT UNSIGNED NOT NULL COMMENT '操作者，0=系统定时任务',
  `reason_text` VARCHAR(200) NULL DEFAULT NULL,
  `penalty` VARCHAR(16) NOT NULL COMMENT 'NONE/MINUS5/MINUS15',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_release_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='释放重派记录（只增不改不删）';

-- 2.8 order_timeline（时间线，只增不改）
CREATE TABLE `order_timeline` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(32) NOT NULL COMMENT 'PUBLISH/ACCEPT/START/RELEASE/CANCEL/CANCEL_REQUEST/CANCEL_CONFIRM_REJECT/COMPLETE_REQUEST/CONFIRM/COMPLETE/EXPIRE/APPEAL_OPEN/ARBITRATE/ADMIN_ACTION/REPUBLISH',
  `operator_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=系统',
  `remark` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_timeline_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单时间线（只增不改不删）';

-- 2.9 chat_session（会话，按订单+轮次建立）
CREATE TABLE `chat_session` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `round` INT NOT NULL DEFAULT 1,
  `requester_id` BIGINT UNSIGNED NOT NULL,
  `helper_id` BIGINT UNSIGNED NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/CLOSED',
  `requester_unread` INT NOT NULL DEFAULT 0,
  `helper_unread` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `closed_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_session_order_round` (`order_id`, `round`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话（一个会话只服务一轮接单）';

-- 2.10 chat_message（消息，只增不改）
CREATE TABLE `chat_message` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `session_id` BIGINT UNSIGNED NOT NULL,
  `sender_id` BIGINT UNSIGNED NOT NULL COMMENT '系统消息 sender_id=0',
  `message_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE/SYSTEM',
  `content` VARCHAR(500) NOT NULL COMMENT '文本内容或图片 URL',
  `client_msg_id` VARCHAR(64) NULL DEFAULT NULL COMMENT '客户端幂等键；系统消息为 NULL',
  `risk_flag` VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/HIGHLIGHT/STRONG',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_message_session` (`session_id`, `id`),
  UNIQUE KEY `uk_message_client_msg_id` (`client_msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息（只增不改不删）';

-- 2.11 credit_ledger（微光值明细，只增不改不删）
CREATE TABLE `credit_ledger` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `delta` INT NOT NULL COMMENT '变化值（±）',
  `reason_code` VARCHAR(24) NOT NULL COMMENT 'COMPLETE/ACCEPT_CANCEL/START_TIMEOUT/MID_QUIT/URGENT_ABUSE/MALICIOUS_CANCEL/FAKE_ORDER/APPEAL_LOSS/ADMIN_ADJUST/APPEAL_REVERT',
  `order_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `balance_after` INT NOT NULL COMMENT '余额快照（下限 0 收敛）',
  `operator_id` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=系统；管理员处置时为 admin_user.id',
  `remark` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_credit_user` (`user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微光值明细（只增不改不删）';

-- 2.12 point_ledger（积分明细，只增不改）
CREATE TABLE `point_ledger` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `delta` INT NOT NULL COMMENT 'MVP 仅 +10',
  `reason_code` VARCHAR(24) NOT NULL COMMENT 'COMPLETE',
  `order_id` BIGINT UNSIGNED NOT NULL,
  `unique_key` VARCHAR(64) NOT NULL COMMENT 'ORDER_COMPLETE_<order_id> 防重复发放',
  `balance_after` INT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_point_user` (`user_id`),
  UNIQUE KEY `uk_point_unique_key` (`unique_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分明细（只增不改不删）';

-- 2.13 evaluation（评价：受助者评助人者，一单一评）
CREATE TABLE `evaluation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `requester_id` BIGINT UNSIGNED NOT NULL,
  `helper_id` BIGINT UNSIGNED NOT NULL,
  `stars` INT NOT NULL COMMENT '1-5，必填',
  `advice` VARCHAR(100) NULL DEFAULT NULL COMMENT '可选一句建议',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_evaluation_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价（一单一评）';

-- 2.14 thank_letter（感谢信）
CREATE TABLE `thank_letter` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `sender_id` BIGINT UNSIGNED NOT NULL,
  `helper_id` BIGINT UNSIGNED NOT NULL,
  `content` VARCHAR(500) NOT NULL,
  `ai_assisted` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_letter_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='感谢信（一单最多一封）';

-- 2.15 story_card（光之故事卡，本地模板生成）
CREATE TABLE `story_card` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `requester_name` VARCHAR(20) NOT NULL,
  `helper_name` VARCHAR(20) NOT NULL,
  `thing` VARCHAR(50) NOT NULL COMMENT '事情（取求助标题）',
  `summary` VARCHAR(100) NOT NULL COMMENT '一句话小结（本地模板拼接）',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_story_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='光之故事卡';

-- 2.16 light_event（光语状态事件与徽记，只增不改）
CREATE TABLE `light_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `event_type` VARCHAR(16) NOT NULL COMMENT 'STATE_CHANGE/BADGE_GAIN',
  `from_state` VARCHAR(16) NULL DEFAULT NULL,
  `to_state` VARCHAR(16) NULL DEFAULT NULL,
  `badge` VARCHAR(16) NULL DEFAULT NULL COMMENT 'LIGHT_PASSED',
  `order_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_light_event_user` (`user_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='光语状态事件与徽记（只增不改不删）';

-- 2.17 whisper（悄悄话，只增不改）
CREATE TABLE `whisper` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `sender_id` BIGINT UNSIGNED NOT NULL,
  `receiver_id` BIGINT UNSIGNED NOT NULL,
  `content` VARCHAR(200) NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_whisper_order_sender` (`order_id`, `sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='悄悄话（只增不改不删）';

-- 2.18 notification（通知）
CREATE TABLE `notification` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `type` VARCHAR(32) NOT NULL COMMENT 'NEW_MESSAGE/ACCEPTED/SERVICE_STARTED/CONFIRM_REQUEST/SETTLED/RELEASED/EXPIRED/APPEAL_RESULT/COOLDOWN/POINTS_CREDITED/CREDIT_CHANGED/ADMIN_ACTION/FEEDBACK_RESULT',
  `title` VARCHAR(50) NOT NULL,
  `content` VARCHAR(200) NOT NULL,
  `jump_type` VARCHAR(16) NULL DEFAULT NULL COMMENT 'ORDER/SESSION/APPEAL/NONE',
  `jump_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `is_read` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notify_user` (`user_id`, `is_read`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知';

-- 2.19 appeal（申诉；反馈复用本表，order_id 为空）
CREATE TABLE `appeal` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '申诉必填；FEEDBACK 为 NULL',
  `initiator_id` BIGINT UNSIGNED NOT NULL,
  `type` VARCHAR(24) NOT NULL COMMENT 'CONFIRM_REJECT/POST_ORDER/FEEDBACK',
  `reason` VARCHAR(200) NOT NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED',
  `handler_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `result` VARCHAR(24) NULL DEFAULT NULL COMMENT 'TO_COMPLETED/TO_CANCELLED',
  `conclusion` VARCHAR(255) NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `resolved_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_appeal_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='申诉与反馈';

-- 2.20 report（防诈高危上报，仅管理员可见）
CREATE TABLE `report` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `message_id` BIGINT UNSIGNED NOT NULL COMMENT '证据消息',
  `target_user_id` BIGINT UNSIGNED NOT NULL COMMENT '被上报人',
  `type` VARCHAR(24) NOT NULL COMMENT 'FRAUD_RISK',
  `evidence` VARCHAR(255) NOT NULL COMMENT '脱敏后的命中要素与方向',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/CONFIRMED/REJECTED',
  `handler_id` BIGINT UNSIGNED NULL DEFAULT NULL,
  `handle_note` VARCHAR(255) NULL DEFAULT NULL,
  `action` VARCHAR(16) NULL DEFAULT NULL COMMENT 'NONE/CREDIT_MINUS/BAN',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `handled_at` DATETIME NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_report_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='防诈高危上报';

-- 2.21 sensitive_word（敏感词与防诈要素词库）
CREATE TABLE `sensitive_word` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `word` VARCHAR(32) NOT NULL,
  `category` VARCHAR(16) NOT NULL COMMENT 'SENSITIVE/FRAUD',
  `action` VARCHAR(16) NOT NULL COMMENT 'BLOCK/HIGHLIGHT/REVIEW',
  `enabled` TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sensitive_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词与防诈要素词库';

-- 2.22 sys_config（配置，唯一事实来源）
CREATE TABLE `sys_config` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `config_key` VARCHAR(64) NOT NULL COMMENT '模块.参数名',
  `config_value` VARCHAR(255) NOT NULL,
  `description` VARCHAR(255) NULL DEFAULT NULL,
  `default_value` VARCHAR(255) NULL DEFAULT NULL,
  `value_range` VARCHAR(100) NULL DEFAULT NULL,
  `updated_by` VARCHAR(32) NULL DEFAULT NULL,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置（唯一事实来源）';

-- 2.23 admin_user（管理员，独立体系）
CREATE TABLE `admin_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username` VARCHAR(32) NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  `status` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  `last_login_at` DATETIME NULL DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员';

-- 2.24 order_view（浏览去重记录，只增）
CREATE TABLE `order_view` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id` BIGINT UNSIGNED NOT NULL,
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '发布者本人不写入',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_view_order_user` (`order_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='浏览去重记录（只增）';
