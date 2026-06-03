-- ============================================================
-- AI 旅行规划项目 — 完整数据库建表脚本
-- 数据库：ai_trip（UTF-8 字符集）
-- 适用引擎：MySQL 8.0+
-- ============================================================

CREATE DATABASE IF NOT EXISTS `ai_trip` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `ai_trip`;

-- ============================================================
-- 1. 用户表（已存在字段基础上扩展角色/会员与配额字段）
-- ============================================================
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `openid`              VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '微信openid',
  `unionid`             VARCHAR(64)  DEFAULT NULL COMMENT '微信unionid',
  `nickname`            VARCHAR(64)  DEFAULT '' COMMENT '微信昵称',
  `avatar`              VARCHAR(512) DEFAULT '' COMMENT '头像URL',
  `mobile`              VARCHAR(20)  DEFAULT '' COMMENT '手机号',
  `gender`              TINYINT      DEFAULT 0  COMMENT '性别：0未知 1男 2女',

  -- 角色 0=普通用户 1=admin管理员 2=超管
  `role`                TINYINT      DEFAULT 0  COMMENT '角色：0普通 1管理 2超管',
  `status`              TINYINT      DEFAULT 0  COMMENT '状态：0正常 1禁用',

  -- 会员
  `is_member`           TINYINT      DEFAULT 0  COMMENT '是否会员：0否 1是',
  `member_type`         TINYINT      DEFAULT 0  COMMENT '会员类型：1月卡 2季卡 3年卡 4终身',
  `member_expire_time`  DATETIME     DEFAULT NULL COMMENT '会员到期时间',

  -- 配额
  `total_trip_generate`     INT      DEFAULT 0  COMMENT '累计生成次数',
  `monthly_trip_limit`      INT      DEFAULT 3  COMMENT '每月生成上限（新用户默认3次）',
  `current_month_usage`     INT      DEFAULT 0  COMMENT '本月已用次数',
  `last_reset_month`        VARCHAR(7) DEFAULT NULL COMMENT '上次重置月份，如2025-06',

  `last_login_ip`       VARCHAR(45)  DEFAULT '' COMMENT '最后登录IP',
  `last_login_time`     DATETIME     DEFAULT NULL COMMENT '最后登录时间',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================================
-- 2. 行程主表
-- ============================================================
DROP TABLE IF EXISTS `t_itinerary`;
CREATE TABLE `t_itinerary` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`             BIGINT       NOT NULL COMMENT '创建者ID',
  `title`               VARCHAR(255) DEFAULT '' COMMENT '行程标题',
  `city`                VARCHAR(64)  DEFAULT '' COMMENT '目的地城市',
  `total_days`          INT          DEFAULT 1  COMMENT '总天数',
  `budget`              DECIMAL(12,2) DEFAULT 0.00 COMMENT '预算',
  `route_type`          TINYINT      DEFAULT 0  COMMENT '0休闲 1精简 2深度',
  `source_type`         TINYINT      DEFAULT 0  COMMENT '0AI生成 1手动创建',
  `parent_id`           BIGINT       DEFAULT NULL COMMENT '基于历史行程的父ID（续对话用）',
  `is_public`           TINYINT      DEFAULT 0  COMMENT '0私有 1公开分享',
  `cover_image`         VARCHAR(512) DEFAULT '' COMMENT '封面图URL',
  `like_count`          INT          DEFAULT 0  COMMENT '点赞数',
  `fav_count`           INT          DEFAULT 0  COMMENT '收藏数',
  `view_count`          INT          DEFAULT 0  COMMENT '浏览数',

  -- 时间范围
  `start_date`          DATE         DEFAULT NULL COMMENT '旅游开始日期',
  `end_date`            DATE         DEFAULT NULL COMMENT '旅游结束日期',

  -- 兴趣标签（JSON数组，如 ["美食","历史"]）
  `interest_tags`       VARCHAR(255) DEFAULT '' COMMENT '兴趣标签',
  `user_note`           VARCHAR(500) DEFAULT '' COMMENT '用户补充描述',
  `replace_count`       INT          NOT NULL DEFAULT 5 COMMENT '剩余换一换次数',

  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_public` (`is_public`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程主表';

-- ============================================================
-- 3. 路线节点表（景点/餐饮/酒店/小吃统一存储）
-- ============================================================
DROP TABLE IF EXISTS `t_route_node`;
CREATE TABLE `t_route_node` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `itinerary_id`        BIGINT       NOT NULL COMMENT '行程ID',
  `day_number`          INT          NOT NULL DEFAULT 1 COMMENT '第几天',
  `sort_order`          INT          NOT NULL DEFAULT 1 COMMENT '当天内排序',
  `node_type`           VARCHAR(16)  NOT NULL DEFAULT 'SPOT' COMMENT '节点类型：SPOT景点 BREAKFAST早餐 LUNCH午餐 DINNER晚餐 SNACK小吃 HOTEL酒店',

  `spot_name`           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '名称',
  `address`             VARCHAR(255) DEFAULT '' COMMENT '地址',
  `lat`                 DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
  `lng`                 DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
  `stay_time`           INT          DEFAULT 60 COMMENT '停留时长(分钟)',
  `open_hours`          VARCHAR(255) DEFAULT '' COMMENT '营业时间',
  `planned_arrival`     VARCHAR(10)  DEFAULT NULL COMMENT '建议到达时间 HH:mm',

  -- 增强字段
  `recommend_reason`    VARCHAR(500) DEFAULT '' COMMENT '推荐理由',
  `image_url`           VARCHAR(512) DEFAULT '' COMMENT '图片URL',
  `image_source`        VARCHAR(32)  DEFAULT '' COMMENT '图片来源：AMAP / UNSPLASH',
  `phone`               VARCHAR(32)  DEFAULT '' COMMENT '联系电话',
  `cost`                DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '预估人均费用/门票(元)',

  `note`                VARCHAR(500) DEFAULT '' COMMENT '备注',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_itinerary` (`itinerary_id`, `day_number`, `sort_order`),
  KEY `idx_node_type` (`itinerary_id`, `node_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线节点表（景点/餐饮/酒店/小吃）';

-- ============================================================
-- 4. 交通路段表
-- ============================================================
DROP TABLE IF EXISTS `t_transport`;
CREATE TABLE `t_transport` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `itinerary_id`        BIGINT       NOT NULL COMMENT '行程ID',
  `from_node_id`        BIGINT       NOT NULL COMMENT '起始节点ID',
  `to_node_id`          BIGINT       NOT NULL COMMENT '到达节点ID',
  `mode`                VARCHAR(16)  NOT NULL DEFAULT 'WALKING' COMMENT '交通方式：WALKING/DRIVING/TRANSIT/BICYCLING',
  `distance`            INT          DEFAULT 0  COMMENT '距离（米）',
  `duration`            INT          DEFAULT 0  COMMENT '耗时（秒）',
  `description`         VARCHAR(500) DEFAULT NULL COMMENT 'AI 生成的交通描述',
  `polyline`            TEXT         DEFAULT NULL COMMENT '路线坐标串',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_itinerary` (`itinerary_id`),
  KEY `idx_from_to` (`from_node_id`, `to_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='交通路段表';

-- ============================================================
-- 5. 社区帖子表（分享路线）
-- ============================================================
DROP TABLE IF EXISTS `t_post`;
CREATE TABLE `t_post` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`             BIGINT       NOT NULL COMMENT '发布者ID',
  `itinerary_id`        BIGINT       NOT NULL COMMENT '关联行程ID',
  `content`             VARCHAR(2000) DEFAULT '' COMMENT '分享文案',
  `like_count`          INT          DEFAULT 0  COMMENT '点赞数',
  `comment_count`       INT          DEFAULT 0  COMMENT '评论数',
  `fav_count`           INT          DEFAULT 0  COMMENT '收藏数',
  `view_count`          INT          DEFAULT 0  COMMENT '浏览数',
  `status`              TINYINT      DEFAULT 0  COMMENT '状态：0正常 1屏蔽',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子表';

-- ============================================================
-- 6. 评论表
-- ============================================================
DROP TABLE IF EXISTS `t_comment`;
CREATE TABLE `t_comment` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `post_id`             BIGINT       NOT NULL COMMENT '帖子ID',
  `user_id`             BIGINT       NOT NULL COMMENT '评论者ID',
  `parent_id`           BIGINT       DEFAULT NULL COMMENT '父评论ID（回复功能）',
  `content`             VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '评论内容',
  `like_count`          INT          DEFAULT 0  COMMENT '点赞数',
  `status`              TINYINT      DEFAULT 0  COMMENT '状态：0正常 1屏蔽',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ============================================================
-- 7. 点赞表
-- ============================================================
DROP TABLE IF EXISTS `t_like`;
CREATE TABLE `t_like` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`             BIGINT       NOT NULL COMMENT '用户ID',
  `target_type`         VARCHAR(16)  NOT NULL COMMENT '点赞类型：POST / COMMENT',
  `target_id`           BIGINT       NOT NULL COMMENT '点赞目标ID',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞表';

-- ============================================================
-- 8. 收藏表
-- ============================================================
DROP TABLE IF EXISTS `t_favorite`;
CREATE TABLE `t_favorite` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`             BIGINT       NOT NULL COMMENT '用户ID',
  `target_type`         VARCHAR(16)  NOT NULL COMMENT '收藏类型：ITINERARY / POST',
  `target_id`           BIGINT       NOT NULL COMMENT '收藏目标ID',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`, `target_type`, `target_id`),
  KEY `idx_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏表';

-- ============================================================
-- 9. AI 生成日志表（异步任务跟踪）
-- ============================================================
DROP TABLE IF EXISTS `t_ai_generation_log`;
CREATE TABLE `t_ai_generation_log` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`             BIGINT       NOT NULL COMMENT '用户ID',
  `itinerary_id`        BIGINT       DEFAULT NULL COMMENT '生成完成的行程ID',
  `status`              VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/SUCCESS/FAILED',
  `progress`            INT          DEFAULT 0  COMMENT '进度百分比 0-100',
  `progress_msg`        VARCHAR(500) DEFAULT '' COMMENT '进度描述',
  `request_text`        TEXT         DEFAULT NULL COMMENT '用户请求原文',
  `city`                VARCHAR(64)  DEFAULT '' COMMENT '目标城市',
  `error_msg`           VARCHAR(1000) DEFAULT '' COMMENT '错误信息',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_itinerary_id` (`itinerary_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI生成日志表';

-- ============================================================
-- 10. 通知消息表
-- ============================================================
DROP TABLE IF EXISTS `t_notification`;
CREATE TABLE `t_notification` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`             BIGINT       NOT NULL COMMENT '接收用户ID',
  `type`                VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '通知类型：LIKE / COMMENT / SYSTEM',
  `title`               VARCHAR(128) DEFAULT '' COMMENT '通知标题',
  `content`             VARCHAR(500) DEFAULT '' COMMENT '通知内容',
  `related_id`          BIGINT       DEFAULT NULL COMMENT '关联目标ID',
  `is_read`             TINYINT      DEFAULT 0  COMMENT '是否已读：0未读 1已读',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`, `is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息表';

-- ============================================================
-- 11. 系统配置表（管理员配置）
-- ============================================================
DROP TABLE IF EXISTS `t_config`;
CREATE TABLE `t_config` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `config_key`          VARCHAR(64)  NOT NULL COMMENT '配置键',
  `config_value`        TEXT         DEFAULT NULL COMMENT '配置值',
  `remark`              VARCHAR(255) DEFAULT '' COMMENT '备注',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ============================================================
-- 初始化管理员账号（微信登录后绑定 admin 角色）
-- openid 占位符，实际使用需替换
-- ============================================================
INSERT INTO `user` (`openid`, `nickname`, `role`, `status`, `is_member`, `member_type`, `monthly_trip_limit`, `current_month_usage`)
VALUES ('admin_init_openid', 'Admin', 1, 0, 1, 4, 9999, 0);

-- ============================================================
-- 初始化默认配置
-- ============================================================
INSERT INTO `t_config` (`config_key`, `config_value`, `remark`) VALUES
('new_user_monthly_limit', '3', '新用户每月生成次数上限'),
('member_monthly_limit', '50', '会员用户每月生成次数上限'),
('lifetime_user_limit', '9999', '终身用户生成次数上限（实际不限制）'),
('default_stay_time', '120', '默认景点停留时长（分钟）'),
('max_days_per_generation', '30', '单次生成最大天数');
