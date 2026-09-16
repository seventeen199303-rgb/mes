package com.wyy.mes.report.controller;

import java.util.Collections;

import com.wyy.common.core.domain.AjaxResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页图表看板兼容接口。
 *
 * 当前后端分支未包含图形报表模块及其数据表，但前端首页会加载该模块。
 * 返回空列表使图表作为可选内容处理，避免影响首页的工单进度和其他看板。
 */
@RestController
@RequestMapping("/mes/report/chart")
public class DashboardChartController
{
    @GetMapping("/getMyCharts")
    public AjaxResult getMyCharts()
    {
        return AjaxResult.success(Collections.emptyList());
    }
}
