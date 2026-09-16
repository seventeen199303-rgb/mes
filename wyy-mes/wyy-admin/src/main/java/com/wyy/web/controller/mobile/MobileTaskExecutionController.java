package com.wyy.web.controller.mobile;

import com.wyy.common.annotation.Log;
import com.wyy.common.core.domain.AjaxResult;
import com.wyy.common.enums.BusinessType;
import com.wyy.common.utils.SecurityUtils;
import com.wyy.web.service.ProductionExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** APP 生产任务列表、详情、开工和完工接口。 */
@RestController
@RequestMapping("/mobile/pro/execution")
public class MobileTaskExecutionController
{
    @Autowired
    private ProductionExecutionService productionExecutionService;

    @GetMapping("/my-tasks")
    public AjaxResult myTasks()
    {
        List<Map<String, Object>> tasks = productionExecutionService.myExecutableTasks(SecurityUtils.getUserId());
        AjaxResult result = AjaxResult.success();
        result.put("items", tasks);
        result.put("total", tasks.size());
        return result;
    }

    @GetMapping("/{taskId}")
    public AjaxResult detail(@PathVariable Long taskId)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.myExecutableTask(taskId, SecurityUtils.getUserId()));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "移动端生产任务开工", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/start")
    public AjaxResult start(@PathVariable Long taskId, @RequestBody(required = false) Map<String, Object> body)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.startTask(taskId, remark(body)));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @Log(title = "移动端生产任务完工", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/finish")
    public AjaxResult finish(@PathVariable Long taskId, @RequestBody(required = false) Map<String, Object> body)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.finishTask(taskId, remark(body)));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** APP 端将任务完工动作明确呈现为“报工完成”；保留 finish 接口兼容旧版本。 */
    @Log(title = "移动端生产任务报工", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/report")
    public AjaxResult report(@PathVariable Long taskId, @RequestBody(required = false) Map<String, Object> body)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.finishTask(taskId, remark(body)));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** APP 端操作人把进行中的任务转交给其他员工。 */
    @Log(title = "移动端生产任务转交", businessType = BusinessType.UPDATE)
    @PostMapping("/{taskId}/transfer")
    public AjaxResult transfer(@PathVariable Long taskId, @RequestBody(required = false) Map<String, Object> body)
    {
        try
        {
            Long targetUserId = body == null ? null : toLong(body.get("targetUserId"));
            String remark = body == null ? "" : String.valueOf(body.getOrDefault("remark", ""));
            return AjaxResult.success(productionExecutionService.transferTask(taskId, targetUserId, remark));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    private String remark(Map<String, Object> body)
    {
        return body == null ? "" : String.valueOf(body.getOrDefault("remark", ""));
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
