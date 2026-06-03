-- 为路线节点增加「预估人均费用/门票」
ALTER TABLE t_route_node
    ADD COLUMN cost decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '预估人均费用/门票(元)' AFTER phone;
