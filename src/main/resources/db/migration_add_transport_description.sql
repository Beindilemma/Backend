-- 已有库执行一次：为交通路段增加「AI 交通描述」
ALTER TABLE t_transport
    ADD COLUMN description varchar(500) DEFAULT NULL COMMENT 'AI 生成的交通描述' AFTER duration;
