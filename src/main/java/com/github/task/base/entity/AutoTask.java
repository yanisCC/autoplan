package com.github.task.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

@ApiModel("个人自动任务表")
@TableName("auto_task")
@Data
public class AutoTask {

    @ApiModelProperty("主键id")
    private Integer id;

    @ApiModelProperty("自动任务id")
    private Integer indexId;

    @ApiModelProperty("用户id")
    private Integer userId;

    @ApiModelProperty("任务唯一id标识，用于判断任务是否存在")
    private String onlyId;

    @ApiModelProperty("自动任务类型")
    private String code;

    @ApiModelProperty("是否开启，0不开启 1开启")
    private Integer enable;

    @ApiModelProperty("名称")
    private String name;

    @ApiModelProperty("配置属性JSON字符串")
    private String settings;

    @ApiModelProperty("任务信息储存JSON字符串（通常用于前端展示UID等级等信息）")
    private String userInfos;

    @ApiModelProperty("最后任务完成时间")
    private Date lastEndTime;

    @ApiModelProperty("最后任务状态")
    private Integer lastEndStatus;


}
