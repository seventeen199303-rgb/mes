package com.wyy.mes.dv.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.wyy.common.constant.UserConstants;
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
import com.wyy.mes.dv.domain.DvSubject;
import com.wyy.mes.dv.service.IDvSubjectService;
import com.wyy.common.utils.poi.ExcelUtil;
import com.wyy.common.core.page.TableDataInfo;

/**
 * 设备点检保养项目Controller
 * 
 * @author yinjinlu
 * @date 2022-06-16
 */
@RestController
@RequestMapping("/mes/dv/dvsubject")
public class DvSubjectController extends BaseController
{
    @Autowired
    private IDvSubjectService dvSubjectService;

    /**
     * 查询设备点检保养项目列表
     */
    @PreAuthorize("@ss.hasPermi('mes:dv:dvsubject:list')")
    @GetMapping("/list")
    public TableDataInfo list(DvSubject dvSubject)
    {
        startPage();
        List<DvSubject> list = dvSubjectService.selectDvSubjectList(dvSubject);
        return getDataTable(list);
    }

    /**
     * 导出设备点检保养项目列表
     */
    @PreAuthorize("@ss.hasPermi('mes:dv:dvsubject:export')")
    @Log(title = "设备点检保养项目", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DvSubject dvSubject)
    {
        List<DvSubject> list = dvSubjectService.selectDvSubjectList(dvSubject);
        ExcelUtil<DvSubject> util = new ExcelUtil<DvSubject>(DvSubject.class);
        util.exportExcel(response, list, "设备点检保养项目数据");
    }

    /**
     * 获取设备点检保养项目详细信息
     */
    @PreAuthorize("@ss.hasPermi('mes:dv:dvsubject:query')")
    @GetMapping(value = "/{subjectId}")
    public AjaxResult getInfo(@PathVariable("subjectId") Long subjectId)
    {
        return AjaxResult.success(dvSubjectService.selectDvSubjectBySubjectId(subjectId));
    }

    /**
     * 新增设备点检保养项目
     */
    @PreAuthorize("@ss.hasPermi('mes:dv:dvsubject:add')")
    @Log(title = "设备点检保养项目", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DvSubject dvSubject)
    {
        if(UserConstants.NOT_UNIQUE.equals(dvSubjectService.checkSubjectCodeUnique(dvSubject))){
            return AjaxResult.error("项目编码已存在！");
        }
        return toAjax(dvSubjectService.insertDvSubject(dvSubject));
    }

    /**
     * 修改设备点检保养项目
     */
    @PreAuthorize("@ss.hasPermi('mes:dv:dvsubject:edit')")
    @Log(title = "设备点检保养项目", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DvSubject dvSubject)
    {
        if(UserConstants.NOT_UNIQUE.equals(dvSubjectService.checkSubjectCodeUnique(dvSubject))){
            return AjaxResult.error("项目编码已存在！");
        }
        return toAjax(dvSubjectService.updateDvSubject(dvSubject));
    }

    /**
     * 删除设备点检保养项目
     */
    @PreAuthorize("@ss.hasPermi('mes:dv:dvsubject:remove')")
    @Log(title = "设备点检保养项目", businessType = BusinessType.DELETE)
	@DeleteMapping("/{subjectIds}")
    public AjaxResult remove(@PathVariable Long[] subjectIds)
    {
        return toAjax(dvSubjectService.deleteDvSubjectBySubjectIds(subjectIds));
    }
}
