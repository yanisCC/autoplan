package com.github.task.base.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.github.task.base.entity.AutoIndex;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AutoIndexDao extends BaseMapper<AutoIndex> {

    int insertBatchSomeColumn(List<AutoIndex> autoIndexListList);

}
