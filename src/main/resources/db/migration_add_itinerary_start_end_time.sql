-- 已有库执行一次：为行程主表增加每日开始/结束时间
ALTER TABLE t_itinerary
    ADD COLUMN start_time varchar(8) DEFAULT NULL COMMENT '每日开始时间 HH:mm' AFTER user_note,
    ADD COLUMN end_time   varchar(8) DEFAULT NULL COMMENT '每日结束时间 HH:mm' AFTER start_time;
