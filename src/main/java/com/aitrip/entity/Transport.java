package com.aitrip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_transport")
public class Transport {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itineraryId;
    private Long fromNodeId;
    private Long toNodeId;
    /** WALKING / DRIVING / TRANSIT */
    private String mode;
    /** 距离（米） */
    private Integer distance;
    /** 耗时（秒） */
    private Integer duration;
    /** AI 生成的交通描述，如"步行约800米到地铁站，乘坐2号线到鼓楼站" */
    private String description;
    private String polyline;
}
