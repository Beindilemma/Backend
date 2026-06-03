-- 已有库执行一次：为路线节点增加「建议到达时间」
ALTER TABLE t_route_node
    ADD COLUMN planned_arrival varchar(8) DEFAULT NULL COMMENT '建议到达时间 HH:mm' AFTER open_hours;
