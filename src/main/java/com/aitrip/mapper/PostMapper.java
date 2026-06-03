package com.aitrip.mapper;

import com.aitrip.entity.Post;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PostMapper extends BaseMapper<Post> {
}
