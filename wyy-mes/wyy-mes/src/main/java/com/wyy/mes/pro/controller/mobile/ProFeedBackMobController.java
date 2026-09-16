package com.wyy.mes.pro.controller.mobile;

import com.wyy.common.annotation.Log;
import com.wyy.common.constant.UserConstants;
import com.wyy.common.core.controller.BaseController;
import com.wyy.common.core.domain.AjaxResult;
import com.wyy.common.core.page.TableDataInfo;
import com.wyy.common.enums.BusinessType;
import com.wyy.common.utils.StringUtils;
import com.wyy.mes.md.domain.MdWorkstation;
import com.wyy.mes.md.service.IMdWorkstationService;
import com.wyy.mes.pro.domain.ProFeedback;
import com.wyy.mes.pro.service.IProFeedbackService;
import io.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Api("生产报工")
@RestController
@RequestMapping("/mobile/pro/feedback")
public class ProFeedBackMobController extends BaseController {

    @Autowired
    private IProFeedbackService proFeedbackService;

    @Autowired
    private IMdWorkstationService mdWorkstationService;


    /**
     * 新增生产报工记录
     */
    @ApiOperation("新增报工单接口")
    @PreAuthorize("@ss.hasPermi('mes:pro:feedback:add')")
    @Log(title = "生产报工记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProFeedback proFeedback)
    {
        MdWorkstation workstation = mdWorkstationService.selectMdWorkstationByWorkstationId(proFeedback.getWorkstationId());
        if(StringUtils.isNotNull(workstation)){
            proFeedback.setProcessId(workstation.getProcessId());
            proFeedback.setProcessCode(workstation.getProcessCode());
            proFeedback.setProcessName(workstation.getProcessName());
        }else {
            return AjaxResult.error("当前生产任务对应的工作站不存在！");
        }
        proFeedback.setCreateBy(getUsername());
        proFeedbackService.insertProFeedback(proFeedback);
        return AjaxResult.success(proFeedback);
    }

    /**
     * 查询生产报工记录列表
     */
    @ApiOperation("查询报工单清单-全部")
    @PreAuthorize("@ss.hasPermi('mes:pro:feedback:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProFeedback proFeedback)
    {
        List<ProFeedback> list = proFeedbackService.selectProFeedbackList(proFeedback);
        return getDataTable(list);
    }

    /**
     * 查询生产报工记录列表
     */
    @ApiOperation("查询报工单清单-未审批通过的")
    @PreAuthorize("@ss.hasPermi('mes:pro:feedback:list')")
    @GetMapping("/listUnApproved")
    public TableDataInfo listUnApproved(ProFeedback proFeedback)
    {
        List<ProFeedback> all = new ArrayList<ProFeedback>();
        proFeedback.setStatus(UserConstants.ORDER_STATUS_PREPARE);
        List<ProFeedback> list1 = proFeedbackService.selectProFeedbackList(proFeedback);
        all.addAll(list1);
        proFeedback.setStatus(UserConstants.ORDER_STATUS_APPROVING);
        List<ProFeedback> list2 = proFeedbackService.selectProFeedbackList(proFeedback);
        all.addAll(list2);
        return getDataTable(all);
    }


    /**
     * 查询生产报工记录列表
     */
    @ApiOperation("查询报工单清单-已审批通过的")
    @PreAuthorize("@ss.hasPermi('mes:pro:feedback:list')")
    @GetMapping("/listApproved")
    public TableDataInfo listApproved(ProFeedback proFeedback)
    {
        proFeedback.setStatus(UserConstants.ORDER_STATUS_FINISHED);
        List<ProFeedback> list = proFeedbackService.selectProFeedbackList(proFeedback);
        return getDataTable(list);
    }

    /**
     * 修改生产报工记录
     */
    @ApiOperation("报工修改接口")
    @PreAuthorize("@ss.hasPermi('mes:pro:feedback:edit')")
    @Log(title = "生产报工记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProFeedback proFeedback)
    {
        return toAjax(proFeedbackService.updateProFeedback(proFeedback));
    }

    /**
     * 删除生产报工记录
     */
    @ApiOperation("删除报工单")
    @PreAuthorize("@ss.hasPermi('mes:pro:feedback:remove')")
    @Log(title = "生产报工记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{recordIds}")
    public AjaxResult remove(@PathVariable Long[] recordIds)
    {
        return toAjax(proFeedbackService.deleteProFeedbackByRecordIds(recordIds));
    }
}
