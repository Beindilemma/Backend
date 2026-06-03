-- 与 application.yml 中 spring.datasource.url 的数据库名保持一致
CREATE DATABASE IF NOT EXISTS ai_trip DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ai_trip;

-- 与 Navicat 中的 `user` 表结构一致（MySQL 保留字表名需反引号）
CREATE TABLE IF NOT EXISTS `user` (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    openid               VARCHAR(64)  NOT NULL COMMENT '微信 openid',
    unionid              VARCHAR(64)           DEFAULT NULL COMMENT '微信 unionid',
    nickname             VARCHAR(50)           DEFAULT NULL COMMENT '昵称',
    avatar               VARCHAR(255)          DEFAULT NULL COMMENT '头像URL',
    mobile               VARCHAR(20)           DEFAULT NULL COMMENT '手机号',
    gender               TINYINT               DEFAULT NULL COMMENT '0未知 1男 2女',
    role                 TINYINT      NOT NULL DEFAULT 0 COMMENT '0普通 1管理员 2超管',
    status               TINYINT      NOT NULL DEFAULT 0 COMMENT '0正常 1禁用',
    is_member            TINYINT               DEFAULT 0 COMMENT '0非会员 1会员',
    member_type          TINYINT               DEFAULT NULL COMMENT '1月2季3年4终身',
    member_expire_time   DATETIME              DEFAULT NULL COMMENT '会员到期时间',
    total_trip_generate    INT          NOT NULL DEFAULT 0 COMMENT '累计生成行程次数',
    monthly_trip_limit     INT          NOT NULL DEFAULT 10 COMMENT '每月行程额度',
    current_month_usage    INT          NOT NULL DEFAULT 0 COMMENT '当月已用次数',
    last_reset_month       VARCHAR(7)            DEFAULT NULL COMMENT '上次重置月份 如2025-01',
    last_login_ip          VARCHAR(45)           DEFAULT NULL COMMENT '最近登录IP',
    last_login_time        DATETIME              DEFAULT NULL COMMENT '最近登录时间',
    create_time            DATETIME              DEFAULT NULL COMMENT '注册时间',
    update_time            DATETIME              DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS t_itinerary (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '行程ID',
    user_id bigint NOT NULL COMMENT '创建者用户ID',
    title varchar(100) NOT NULL COMMENT '行程标题',
    city varchar(50) DEFAULT NULL COMMENT '目标城市',
    total_days int DEFAULT '1' COMMENT '总天数',
    budget decimal(10,2) DEFAULT '0.00' COMMENT '预算金额',
    route_type tinyint DEFAULT '0' COMMENT '方案类型(0:休闲, 1:精简, 2:深度)',
    source_type tinyint DEFAULT '0' COMMENT '来源(0:AI生成, 1:手动创建)',
    parent_id bigint DEFAULT NULL COMMENT '复刻来源行程ID(用于溯源)',
    is_public tinyint DEFAULT '0' COMMENT '是否公开到社区(0:私有, 1:公开)',
    cover_image varchar(255) DEFAULT NULL COMMENT '行程封面图',
    create_time datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_user_id (user_id),
    INDEX idx_city (city)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程主表';

CREATE TABLE IF NOT EXISTS t_route_node (
    id bigint NOT NULL AUTO_INCREMENT COMMENT '节点ID',
    itinerary_id bigint NOT NULL COMMENT '所属行程ID',
    day_number int NOT NULL DEFAULT '1' COMMENT '第几天',
    sort_order int NOT NULL COMMENT '当天游玩顺序',
    spot_name varchar(100) NOT NULL COMMENT '景点/地点名称',
    address varchar(255) DEFAULT NULL COMMENT '详细地址',
    lat decimal(10,7) NOT NULL COMMENT '纬度',
    lng decimal(11,7) NOT NULL COMMENT '经度',
    stay_time int DEFAULT '60' COMMENT '建议游玩时长(分钟)',
    open_hours varchar(100) DEFAULT NULL COMMENT '营业时间',
    planned_arrival varchar(8) DEFAULT NULL COMMENT '建议到达时间 HH:mm',
    cost decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '预估人均费用/门票(元)',
    note text COMMENT 'AI给出的避雷建议或备注',
    create_time datetime DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_itinerary_id (itinerary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='路线节点表';

CREATE TABLE IF NOT EXISTS t_transport (
    id bigint NOT NULL AUTO_INCREMENT,
    itinerary_id bigint NOT NULL COMMENT '所属行程ID',
    from_node_id bigint NOT NULL COMMENT '起点节点ID',
    to_node_id bigint NOT NULL COMMENT '终点节点ID',
    mode varchar(20) DEFAULT 'WALKING' COMMENT '方式(WALKING:步行, DRIVING:驾车, TRANSIT:公交)',
    distance int DEFAULT '0' COMMENT '距离(米)',
    duration int DEFAULT '0' COMMENT '耗时(秒)',
    polyline text COMMENT '地图渲染轨迹点串(高德返回的坐标集合)',
    PRIMARY KEY (id),
    INDEX idx_itinerary_id (itinerary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='节点间交通信息表';
