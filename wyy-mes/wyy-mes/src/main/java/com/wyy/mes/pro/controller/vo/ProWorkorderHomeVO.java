package com.ktg.mes.pro.controller.vo;

import java.util.List;

import com.ktg.mes.pro.domain.ProWorkorder;

/**
 * 首页展示的工单数据。
 */
public class ProWorkorderHomeVO extends ProWorkorder
{
    private static final long serialVersionUID = 1L;

    private List<ProRouteHomeVO> routeHomg;

    public List<ProRouteHomeVO> getRouteHomg()
    {
        return routeHomg;
    }

    public void setRouteHomg(List<ProRouteHomeVO> routeHomg)
    {
        this.routeHomg = routeHomg;
    }
}
