package com.github.system.task.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.system.auth.util.SessionUtils;
import com.github.system.desensitized.DataDesensitizationUtil;
import com.github.system.task.dao.AutoTaskDao;
import com.github.system.task.dto.AutoTaskDto;
import com.github.system.task.dto.CheckResult;
import com.github.system.task.dto.TaskResult;
import com.github.system.task.entity.AutoIndex;
import com.github.system.task.entity.AutoTask;
import com.github.system.task.init.TaskInit;
import com.github.system.task.service.AutoIndexService;
import com.github.system.task.service.AutoTaskService;
import com.github.system.task.service.TaskRuntimeService;
import com.github.system.task.vo.AutoTaskVo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class AutoTaskServiceImpl extends ServiceImpl<AutoTaskDao, AutoTask> implements AutoTaskService {

    @Resource
    private AutoIndexService autoIndexService;
    @Resource
    private TaskRuntimeService taskRuntimeService;

    @Override
    public Page<AutoTaskDto> taskPage(Page<AutoTask> page, String indexId) throws Exception {
        // 校验indexId是否存在并且启用
        AutoIndex autoIndex = autoIndexService.getOne(new LambdaQueryWrapper<AutoIndex>()
                .eq(AutoIndex::getId, indexId)
                .eq(!SessionUtils.isAdmin(), AutoIndex::getEnable, 1));
        if (autoIndex == null) {
            throw new Exception("未找到该自动任务类型");
        }
        LambdaQueryWrapper<AutoTask> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(AutoTask::getId, AutoTask::getCode, AutoTask::getEnable, AutoTask::getName, AutoTask::getLastEndStatus, AutoTask::getLastEndTime, AutoTask::getUserInfos)
                .eq(AutoTask::getIndexId, indexId);
        Page<AutoTask> queryPage = page(page, lambdaQueryWrapper);
        return turnAutoTaskEntityPage(queryPage, true);
    }

    @Override
    public Page<AutoTaskDto> minePage(Page<AutoTask> page) throws Exception {
        LambdaQueryWrapper<AutoTask> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        List<String> settings = List.of("settings", "onlyId");
        lambdaQueryWrapper.select(AutoTask.class, i -> !settings.contains(i.getProperty()))
                .eq(AutoTask::getUserId, SessionUtils.getUserId());
        Page<AutoTask> queryPage = page(page, lambdaQueryWrapper);
        return turnAutoTaskEntityPage(queryPage, false);
    }

    @Override
    public AutoTaskDto view(AutoTask autoTask) {
        autoTask.setUserInfos(null);
        AutoTaskDto dto = BeanUtil.toBean(autoTask, AutoTaskDto.class);
        dto.setSettings(null);
        Object bean = JSONUtil.toBean(autoTask.getSettings(), TaskInit.taskSettingsClassesMap.get(autoTask.getCode()));
        DataDesensitizationUtil.desensitizationNull(bean);
        dto.setSetting(bean);
        return dto;
    }

    @Override
    public List<AutoTaskDto> turnAutoTaskEntity(List<AutoTask> autoTaskList, boolean desensitization) {
        List<AutoTaskDto> list = new ArrayList<>();
        for (Object record : autoTaskList) {
            Class<?> userInfoClass = TaskInit.userInfosClassesMap.get((String) BeanUtil.getFieldValue(record, "code"));
            AutoTaskDto dto = BeanUtil.toBean(record, AutoTaskDto.class);
            Object bean = JSONUtil.toBean((String) BeanUtil.getFieldValue(record, "userInfos"), userInfoClass);
            BeanUtil.setFieldValue(bean, "onlyId", null);
            if (desensitization) {
                DataDesensitizationUtil.desensitization(bean);
            }
            dto.setUserInfo(bean);
            dto.setUserInfos(null);
            list.add(dto);
        }
        return list;
    }

    @Override
    public Page<AutoTaskDto> turnAutoTaskEntityPage(Page<AutoTask> autoTaskPage, boolean desensitization) {
        List<AutoTaskDto> dtoList = turnAutoTaskEntity(autoTaskPage.getRecords(), desensitization);
        Page<AutoTaskDto> resPage = new Page<>();
        resPage.setRecords(dtoList);
        resPage.setPages(autoTaskPage.getPages());
        resPage.setCurrent(autoTaskPage.getCurrent());
        resPage.setSize(autoTaskPage.getSize());
        resPage.setTotal(autoTaskPage.getTotal());
        return resPage;
    }

    @Override
    public CheckResult checkOrSaveUser(Long indexId, AutoTaskVo autoTaskVo, boolean save) throws Exception {
        AutoIndex autoIndex = autoIndexService.getById(indexId);
        if (autoIndex == null || autoIndex.getEnable() == 0) {
            throw new Exception("未找到对应任务索引！");
        }
        AutoTask autoTask = new AutoTask(indexId, SessionUtils.getUserId(), autoIndex.getCode(), autoTaskVo.get_sys().getEnable(), autoTaskVo.get_sys().getName());
        autoTask.setSettings(JSONUtil.toJsonStr(autoTaskVo.getData()));
        return taskRuntimeService.checkUser(autoTask, save);
    }

    @Override
    public CheckResult checkAndUpdate(AutoTaskVo autoTaskVo, boolean save) throws Exception {
        Long id = autoTaskVo.get_sys().getId();
        AutoTask task = getSelfTask(id);
        if (autoTaskVo.get_sys().getEnable() != null) {
            task.setEnable(autoTaskVo.get_sys().getEnable());
        }
        if (StrUtil.isNotBlank(autoTaskVo.get_sys().getName())) {
            task.setName(autoTaskVo.get_sys().getName());
        }
        // 假如说前端并没有给足数据，也就是用户只修改了一部分，这时候后台就要去拿原先的数据填充前端空的那部分
        JSONObject jsonObject = JSONUtil.parseObj(task.getSettings());
        Class<?> settingsClass = TaskInit.taskSettingsClassesMap.get(task.getCode());
        autoTaskVo.getData().forEach((k, v) -> {
            // 简单判断下，防止前端乱传数据
            if (ReflectUtil.hasField(settingsClass, k) && ObjectUtil.isNotNull(v)) {
                jsonObject.set(k, v);
            }
        });

        task.setSettings(JSONUtil.toJsonStr(jsonObject));
        return taskRuntimeService.checkUser(task, save);
    }

    @Override
    public TaskResult run(Long taskId) throws Exception {
        AutoTask task = getSelfTask(taskId);
        return taskRuntimeService.doTask(task,true);
    }

    private AutoTask getSelfTask(Long taskId) throws Exception {
        AutoTask task = getById(taskId);
        if (task == null || (!task.getUserId().equals(SessionUtils.getUserId()) && !SessionUtils.isAdmin())) {
            throw new Exception("没有找到任务！");
        }
        return task;
    }

}
