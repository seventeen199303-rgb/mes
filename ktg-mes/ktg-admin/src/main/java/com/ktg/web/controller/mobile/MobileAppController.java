package com.ktg.web.controller.mobile;

import com.ktg.common.core.domain.AjaxResult;
import com.ktg.common.core.domain.entity.SysUser;
import com.ktg.common.utils.SecurityUtils;
import com.ktg.mes.pro.domain.ProFeedback;
import com.ktg.mes.pro.domain.ProTask;
import com.ktg.mes.pro.domain.ProTransOrder;
import com.ktg.mes.pro.service.IProFeedbackService;
import com.ktg.mes.pro.service.IProTaskService;
import com.ktg.mes.pro.service.IProTransOrderService;
import com.ktg.mes.wm.domain.WmIssueHeader;
import com.ktg.mes.wm.domain.WmProductRecpt;
import com.ktg.mes.wm.domain.WmRtIssue;
import com.ktg.mes.wm.service.IWmIssueHeaderService;
import com.ktg.mes.wm.service.IWmProductRecptService;
import com.ktg.mes.wm.service.IWmRtIssueService;
import com.ktg.system.domain.SysMessage;
import com.ktg.system.service.ISysConfigService;
import com.ktg.system.service.ISysMessageService;
import com.ktg.system.service.ISysUserService;
import com.ktg.web.service.ProductionExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * APP 工作台接口。
 *
 * <p>移动端只依赖这一组面向现场的稳定字段，避免直接耦合后台管理页面的列表结构。
 * 所有数据仍由现有的生产、仓储及系统消息服务查询。</p>
 */
@RestController
@RequestMapping("/mobile/app")
public class MobileAppController
{
    private static final String FINISHED = "FINISHED";

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysMessageService messageService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private IProTaskService taskService;

    @Autowired
    private ProductionExecutionService productionExecutionService;

    @Autowired
    private IProFeedbackService feedbackService;

    @Autowired
    private IProTransOrderService transOrderService;

    @Autowired
    private IWmIssueHeaderService issueHeaderService;

    @Autowired
    private IWmRtIssueService rtIssueService;

    @Autowired
    private IWmProductRecptService productRecptService;

    /** 查询当前登录人的基础资料及个人生产汇总。 */
    @GetMapping("/profile")
    public AjaxResult profile()
    {
        SysUser currentUser = SecurityUtils.getLoginUser().getUser();
        SysUser user = userService.selectUserById(currentUser.getUserId());
        List<Map<String, Object>> tasks = productionExecutionService.myExecutableTasks(currentUser.getUserId());
        List<ProFeedback> feedbacks = feedbackService.selectProFeedbackList(new ProFeedback());

        Map<String, Object> summary = new HashMap<>();
        summary.put("todoTasks", countOpenTaskMaps(tasks));
        summary.put("feedbackCount", feedbacks.size());
        summary.put("qualifiedRate", qualifiedRate(feedbacks));

        AjaxResult result = AjaxResult.success();
        result.put("user", user);
        result.put("employeeNo", user.getUserName());
        result.put("department", user.getDept() == null ? "未分配部门" : user.getDept().getDeptName());
        result.put("summary", summary);
        return result;
    }

    /** 查询首页生产执行概览。 */
    @GetMapping("/dashboard")
    public AjaxResult dashboard()
    {
        Long userId = SecurityUtils.getUserId();
        List<Map<String, Object>> tasks = productionExecutionService.myExecutableTasks(userId);
        List<ProFeedback> feedbacks = feedbackService.selectProFeedbackList(new ProFeedback());
        List<ProTransOrder> transfers = transOrderService.selectProTransOrderList(new ProTransOrder());
        List<WmIssueHeader> issues = issueHeaderService.selectWmIssueHeaderList(new WmIssueHeader());
        List<WmRtIssue> returns = rtIssueService.selectWmRtIssueList(new WmRtIssue());
        List<WmProductRecpt> receipts = productRecptService.selectWmProductRecptList(new WmProductRecpt());

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("todoTasks", countOpenTaskMaps(tasks));
        metrics.put("feedbackCount", feedbacks.size());
        metrics.put("transferCount", transfers.size());
        metrics.put("issueCount", issues.size());
        metrics.put("returnCount", returns.size());
        metrics.put("receiptCount", receipts.size());
        metrics.put("taskTotal", tasks.size());
        metrics.put("taskFinished", countFinishedTaskMaps(tasks));
        metrics.put("qualifiedRate", qualifiedRate(feedbacks));

        AjaxResult result = AjaxResult.success();
        result.put("metrics", metrics);
        result.put("tasks", limit(tasks, 8));
        result.put("feedbacks", limit(feedbacks, 8));
        result.put("transfers", limit(transfers, 8));
        result.put("issues", limit(issues, 8));
        result.put("returns", limit(returns, 8));
        result.put("receipts", limit(receipts, 8));
        return result;
    }

    /**
     * 每次打开生产执行入口时重新查询对应业务数据，确保现场端看到的是最新数据。
     */
    @GetMapping("/production/{module}")
    public AjaxResult production(@PathVariable String module)
    {
        AjaxResult result = AjaxResult.success();
        if ("tasks".equals(module))
        {
            List<Map<String, Object>> list = productionExecutionService.myExecutableTasks(SecurityUtils.getUserId());
            result.put("total", list.size());
            result.put("items", limit(list, 30));
        }
        else if ("feedback".equals(module))
        {
            List<ProFeedback> list = feedbackService.selectProFeedbackList(new ProFeedback());
            result.put("total", list.size());
            result.put("items", limit(list, 30));
        }
        else if ("transfer".equals(module))
        {
            List<ProTransOrder> list = transOrderService.selectProTransOrderList(new ProTransOrder());
            result.put("total", list.size());
            result.put("items", limit(list, 30));
        }
        else if ("issue".equals(module))
        {
            List<WmIssueHeader> list = issueHeaderService.selectWmIssueHeaderList(new WmIssueHeader());
            result.put("total", list.size());
            result.put("items", limit(list, 30));
        }
        else if ("return".equals(module))
        {
            List<WmRtIssue> list = rtIssueService.selectWmRtIssueList(new WmRtIssue());
            result.put("total", list.size());
            result.put("items", limit(list, 30));
        }
        else if ("receipt".equals(module))
        {
            List<WmProductRecpt> list = productRecptService.selectWmProductRecptList(new WmProductRecpt());
            result.put("total", list.size());
            result.put("items", limit(list, 30));
        }
        else
        {
            return AjaxResult.error("不支持的生产执行模块");
        }
        return result;
    }

    /** 查询可接收转交任务的在职员工列表（不含当前登录人）。 */
    @GetMapping("/colleagues")
    public AjaxResult colleagues()
    {
        List<Map<String, Object>> users = productionExecutionService.activeUsers(SecurityUtils.getUserId());
        AjaxResult result = AjaxResult.success();
        result.put("items", users);
        result.put("total", users.size());
        return result;
    }

    /** 查询当前用户的持久化消息。 */
    @GetMapping("/notices")
    public AjaxResult notices()
    {
        SysMessage query = new SysMessage();
        query.setRecipientId(SecurityUtils.getUserId());
        List<SysMessage> messages = messageService.selectSysMessageList(query);
        return AjaxResult.success(limit(messages, 50));
    }

    /** 通知标记已读：messageId 传单条，不传则全部标记为已读。 */
    @GetMapping("/notices/read")
    public AjaxResult readNotice(Long messageId)
    {
        Long userId = SecurityUtils.getUserId();
        if (messageId != null)
        {
            SysMessage message = messageService.selectSysMessageByMessageId(messageId);
            if (message == null || !userId.equals(message.getRecipientId()))
            {
                return AjaxResult.error("通知不存在或无权操作");
            }
            if (!"READ".equals(message.getStatus()))
            {
                message.setStatus("READ");
                message.setProcessTime(new java.util.Date());
                messageService.updateSysMessage(message);
            }
            return AjaxResult.success(messageService.selectSysMessageByMessageId(messageId));
        }
        SysMessage query = new SysMessage();
        query.setRecipientId(userId);
        query.setStatus("UNREAD");
        for (SysMessage message : messageService.selectSysMessageList(query))
        {
            message.setStatus("READ");
            message.setProcessTime(new java.util.Date());
            messageService.updateSysMessage(message);
        }
        return AjaxResult.success();
    }

    /**
     * 查询 Android 最新版本。版本号、下载地址与更新说明维护在系统参数中，升级不需要重新发版后端。
     */
    @GetMapping("/version")
    public AjaxResult version()
    {
        Map<String, Object> version = new HashMap<>();
        version.put("version", config("app.android.version", "1.1.0"));
        version.put("downloadUrl", config("app.android.download-url", "https://kywgmes.cn/download/wyymes-android.apk"));
        version.put("description", config("app.android.update-description", "优化移动端生产执行、通知和个人中心数据同步。"));
        version.put("forceUpdate", config("app.android.force-update", "N"));
        return AjaxResult.success(version);
    }

    private String config(String key, String defaultValue)
    {
        String value = configService.selectConfigByKey(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    private int countUnfinishedTasks(List<ProTask> tasks)
    {
        int count = 0;
        for (ProTask task : tasks)
        {
            if (!FINISHED.equals(task.getStatus()))
            {
                count++;
            }
        }
        return count;
    }

    private int countFinishedTasks(List<ProTask> tasks)
    {
        int count = 0;
        for (ProTask task : tasks)
        {
            if (FINISHED.equals(task.getStatus()))
            {
                count++;
            }
        }
        return count;
    }

    private int countOpenTaskMaps(List<Map<String, Object>> tasks)
    {
        int count = 0;
        for (Map<String, Object> task : tasks)
        {
            if (!FINISHED.equals(String.valueOf(task.get("status"))))
            {
                count++;
            }
        }
        return count;
    }

    private int countFinishedTaskMaps(List<Map<String, Object>> tasks)
    {
        int count = 0;
        for (Map<String, Object> task : tasks)
        {
            if (FINISHED.equals(String.valueOf(task.get("status"))))
            {
                count++;
            }
        }
        return count;
    }

    private String qualifiedRate(List<ProFeedback> feedbacks)
    {
        BigDecimal qualified = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (ProFeedback feedback : feedbacks)
        {
            if (feedback.getQuantityQualified() != null)
            {
                qualified = qualified.add(feedback.getQuantityQualified());
            }
            if (feedback.getQuantityFeedback() != null)
            {
                total = total.add(feedback.getQuantityFeedback());
            }
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0)
        {
            return "--";
        }
        return qualified.multiply(BigDecimal.valueOf(100)).divide(total, 1, BigDecimal.ROUND_HALF_UP).stripTrailingZeros().toPlainString();
    }

    private <T> List<T> limit(List<T> source, int size)
    {
        if (source == null || source.isEmpty())
        {
            return Collections.emptyList();
        }
        return new ArrayList<>(source.subList(0, Math.min(source.size(), size)));
    }
}
