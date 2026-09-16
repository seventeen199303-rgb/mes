package com.wyy.mes.pro.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.wyy.mes.md.domain.MdProductSop;
import com.wyy.mes.md.service.IMdProductSopService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wyy.common.annotation.Log;
import com.wyy.common.core.controller.BaseController;
import com.wyy.common.core.domain.AjaxResult;
import com.wyy.common.enums.BusinessType;
import com.wyy.mes.pro.domain.ProTaskIssue;
import com.wyy.mes.pro.service.IProTaskIssueService;
import com.wyy.common.utils.poi.ExcelUtil;
import com.wyy.common.core.page.TableDataInfo;

/**
 * 生产任务投料Controller
 * 
 * @author yinjinlu
 * @date 2022-07-22
 */
@RestController
@RequestMapping("/mes/pro/taskissue")
public class ProTaskIssueController extends BaseController
{
    @Autowired
    private IProTaskIssueService proTaskIssueService;


    /**
     * 查询生产任务投料列表
     */
    @PreAuthorize("@ss.hasPermi('mes:pro:taskissue:list')")
    @GetMapping("/list")
    public TableDataInfo list(ProTaskIssue proTaskIssue)
    {
        startPage();
        List<ProTaskIssue> list = proTaskIssueService.selectProTaskIssueList(proTaskIssue);
        return getDataTable(list);
    }

    /**
     * 导出生产任务投料列表
     */
    @PreAuthorize("@ss.hasPermi('mes:pro:taskissue:export')")
    @Log(title = "生产任务投料", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, ProTaskIssue proTaskIssue)
    {
        List<ProTaskIssue> list = proTaskIssueService.selectProTaskIssueList(proTaskIssue);
        ExcelUtil<ProTaskIssue> util = new ExcelUtil<ProTaskIssue>(ProTaskIssue.class);
        util.exportExcel(response, list, "生产任务投料数据");
    }

    /**
     * 获取生产任务投料详细信息
     */
    @PreAuthorize("@ss.hasPermi('mes:pro:taskissue:query')")
    @GetMapping(value = "/{recordId}")
    public AjaxResult getInfo(@PathVariable("recordId") Long recordId)
    {
        return AjaxResult.success(proTaskIssueService.selectProTaskIssueByRecordId(recordId));
    }

    /**
     * 新增生产任务投料
     */
    @PreAuthorize("@ss.hasPermi('mes:pro:taskissue:add')")
    @Log(title = "生产任务投料", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody ProTaskIssue proTaskIssue)
    {
        return toAjax(proTaskIssueService.insertProTaskIssue(proTaskIssue));
    }

    /**
     * 修改生产任务投料
     */
    @PreAuthorize("@ss.hasPermi('mes:pro:taskissue:edit')")
    @Log(title = "生产任务投料", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody ProTaskIssue proTaskIssue)
    {
        return toAjax(proTaskIssueService.updateProTaskIssue(proTaskIssue));
    }

    /**
     * 删除生产任务投料
     */
    @PreAuthorize("@ss.hasPermi('mes:pro:taskissue:remove')")
    @Log(title = "生产任务投料", businessType = BusinessType.DELETE)
	@DeleteMapping("/{recordIds}")
    public AjaxResult remove(@PathVariable Long[] recordIds)
    {
        return toAjax(proTaskIssueService.deleteProTaskIssueByRecordIds(recordIds));
    }
}
