package com.resumecopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 技能别名（t_skill_alias，计划书 5.2 规则通道字典）：一行一个"标准名<-别名"映射 */
@Data
@TableName("t_skill_alias")
public class SkillAlias {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标准技能名：所有匹配最终归一到这个口径 */
    private String standardName;

    /** 别名/变体写法：JUC、MyBatis-Plus、Spring Data Redis 等 */
    private String alias;

    /** 通道标记：当前只用 RULE，SEMANTIC 预留（未来可按通道给不同阈值） */
    private String channel;
}
