-- 换一换次数限制：每次局部替换消耗一次，上限 5 次
ALTER TABLE `t_itinerary`
    ADD COLUMN `replace_count` INT NOT NULL DEFAULT 5 COMMENT '剩余换一换次数' AFTER `user_note`;
