package com.ktg.mes.pro.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ktg.common.constant.UserConstants;
import com.ktg.common.core.domain.AjaxResult;
import com.ktg.common.utils.DateUtils;
import com.ktg.common.utils.StringUtils;
import com.ktg.mes.dv.domain.DvMachineryType;
import com.ktg.mes.pro.controller.vo.ProRouteHomeVO;
import com.ktg.mes.pro.controller.vo.ProWorkorderHomeVO;
import com.ktg.mes.pro.domain.ProFeedback;
import com.ktg.mes.pro.domain.ProRouteProcess;
import com.ktg.mes.pro.domain.ProRouteProduct;
import com.ktg.mes.pro.service.IProFeedbackService;
import com.ktg.mes.pro.service.IProRouteProcessService;
import com.ktg.mes.pro.service.IProRouteProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import com.ktg.mes.pro.mapper.ProWorkorderMapper;
import com.ktg.mes.pro.domain.ProWorkorder;
import com.ktg.mes.pro.service.IProWorkorderService;

/**
 * 生产工单Service业务层处理
 * 
 * @author yinjinlu
 * @date 2022-05-09
 */
@Service
public class ProWorkorderServiceImpl implements IProWorkorderService 
{
    @Autowired
    private ProWorkorderMapper proWorkorderMapper;

    @Autowired
    private IProRouteProductService proRouteProductService;

    @Autowired
    private IProRouteProcessService proRouteProcessService;

    @Autowired
    private IProFeedbackService proFeedbackService;

    /**
     * 查询生产工单
     * 
     * @param workorderId 生产工单主键
     * @return 生产工单
     */
    @Override
    public ProWorkorder selectProWorkorderByWorkorderId(Long workorderId)
    {
        return proWorkorderMapper.selectProWorkorderByWorkorderId(workorderId);
    }

    /**
     * 查询生产工单列表
     * 
     * @param proWorkorder 生产工单
     * @return 生产工单
     */
    @Override
    public List<ProWorkorder> selectProWorkorderList(ProWorkorder proWorkorder)
    {
        return proWorkorderMapper.selectProWorkorderList(proWorkorder);
    }

    @Override
    public String checkWorkorderCodeUnique(ProWorkorder proWorkorder) {
        ProWorkorder workorder = proWorkorderMapper.checkWorkorderCodeUnique(proWorkorder);
        Long workorderId = proWorkorder.getWorkorderId() == null? -1L: proWorkorder.getWorkorderId();
        if(StringUtils.isNotNull(workorder) && workorder.getWorkorderId().longValue() != workorderId.longValue()){
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }


    /**
     * 新增生产工单
     * 
     * @param proWorkorder 生产工单
     * @return 结果
     */
    @Override
    public int insertProWorkorder(ProWorkorder proWorkorder)
    {
        if(proWorkorder.getParentId()!= null){
            ProWorkorder parent = proWorkorderMapper.selectProWorkorderByWorkorderId(proWorkorder.getParentId());
            if(StringUtils.isNotNull(parent)){
                proWorkorder.setAncestors(parent.getAncestors()+","+parent.getParentId());
            }
        }

        proWorkorder.setCreateTime(DateUtils.getNowDate());
        return proWorkorderMapper.insertProWorkorder(proWorkorder);
    }

    /**
     * 修改生产工单
     * 
     * @param proWorkorder 生产工单
     * @return 结果
     */
    @Override
    public int updateProWorkorder(ProWorkorder proWorkorder)
    {
        proWorkorder.setUpdateTime(DateUtils.getNowDate());
        return proWorkorderMapper.updateProWorkorder(proWorkorder);
    }

    /**
     * 批量删除生产工单
     * 
     * @param workorderIds 需要删除的生产工单主键
     * @return 结果
     */
    @Override
    public int deleteProWorkorderByWorkorderIds(Long[] workorderIds)
    {
        return proWorkorderMapper.deleteProWorkorderByWorkorderIds(workorderIds);
    }

    /**
     * 删除生产工单信息
     * 
     * @param workorderId 生产工单主键
     * @return 结果
     */
    @Override
    public int deleteProWorkorderByWorkorderId(Long workorderId)
    {
        return proWorkorderMapper.deleteProWorkorderByWorkorderId(workorderId);
    }

    /**
     * 首页工单进度。当前分支的首页前端已经依赖该接口，故在此按工单、工艺路线及报工记录组装数据。
     */
    @Override
    public AjaxResult getHomeList(ProWorkorder proWorkorder)
    {
        List<ProWorkorder> workorders = proWorkorderMapper.selectProWorkorderList(proWorkorder);
        if (workorders == null || workorders.isEmpty())
        {
            return AjaxResult.success(Collections.emptyList());
        }

        List<ProWorkorderHomeVO> homeWorkorders = new ArrayList<>();
        Map<Long, ProWorkorderHomeVO> workorderMap = new HashMap<>();
        Map<Long, List<ProWorkorderHomeVO>> childrenMap = new HashMap<>();

        for (ProWorkorder workorder : workorders)
        {
            ProWorkorderHomeVO homeWorkorder = new ProWorkorderHomeVO();
            BeanUtils.copyProperties(workorder, homeWorkorder);
            homeWorkorder.setRouteHomg(buildRouteProgress(workorder));
            homeWorkorders.add(homeWorkorder);
            if (homeWorkorder.getWorkorderId() != null)
            {
                workorderMap.put(homeWorkorder.getWorkorderId(), homeWorkorder);
            }
            if (homeWorkorder.getParentId() != null && homeWorkorder.getParentId() != 0L)
            {
                childrenMap.computeIfAbsent(homeWorkorder.getParentId(), key -> new ArrayList<>())
                        .add(homeWorkorder);
            }
        }

        List<ProWorkorderHomeVO> roots = new ArrayList<>();
        for (ProWorkorderHomeVO homeWorkorder : homeWorkorders)
        {
            List<ProWorkorderHomeVO> children = childrenMap.get(homeWorkorder.getWorkorderId());
            if (children != null)
            {
                homeWorkorder.setChildren(children);
            }

            Long parentId = homeWorkorder.getParentId();
            if (parentId == null || parentId == 0L || !workorderMap.containsKey(parentId))
            {
                roots.add(homeWorkorder);
            }
        }
        return AjaxResult.success(roots);
    }

    private List<ProRouteHomeVO> buildRouteProgress(ProWorkorder workorder)
    {
        if (workorder.getProductId() == null)
        {
            return Collections.emptyList();
        }

        ProRouteProduct routeProductQuery = new ProRouteProduct();
        routeProductQuery.setItemId(workorder.getProductId());
        List<ProRouteProduct> routeProducts = proRouteProductService.selectProRouteProductList(routeProductQuery);
        if (routeProducts == null || routeProducts.isEmpty())
        {
            return Collections.emptyList();
        }

        Map<Long, BigDecimal> feedbackTotals = getFeedbackTotals(workorder.getWorkorderId());
        BigDecimal total = workorder.getQuantity() == null ? BigDecimal.ZERO : workorder.getQuantity();
        List<ProRouteHomeVO> progress = new ArrayList<>();

        for (ProRouteProduct routeProduct : routeProducts)
        {
            if (routeProduct.getRouteId() == null)
            {
                continue;
            }

            ProRouteProcess routeProcessQuery = new ProRouteProcess();
            routeProcessQuery.setRouteId(routeProduct.getRouteId());
            List<ProRouteProcess> routeProcesses = proRouteProcessService.selectProRouteProcessList(routeProcessQuery);
            if (routeProcesses == null)
            {
                continue;
            }

            for (ProRouteProcess routeProcess : routeProcesses)
            {
                ProRouteHomeVO routeHome = new ProRouteHomeVO();
                BeanUtils.copyProperties(routeProcess, routeHome);
                BigDecimal complete = feedbackTotals.getOrDefault(routeProcess.getProcessId(), BigDecimal.ZERO);
                routeHome.setTotal(total);
                routeHome.setCompleteNumber(complete);
                routeHome.setIncompleteNumber(total.subtract(complete));
                progress.add(routeHome);
            }
        }
        return progress;
    }

    private Map<Long, BigDecimal> getFeedbackTotals(Long workorderId)
    {
        if (workorderId == null)
        {
            return Collections.emptyMap();
        }

        ProFeedback feedbackQuery = new ProFeedback();
        feedbackQuery.setWorkorderId(workorderId);
        List<ProFeedback> feedbacks = proFeedbackService.selectProFeedbackList(feedbackQuery);
        if (feedbacks == null || feedbacks.isEmpty())
        {
            return Collections.emptyMap();
        }

        Map<Long, BigDecimal> totals = new HashMap<>();
        for (ProFeedback feedback : feedbacks)
        {
            if (feedback.getProcessId() == null)
            {
                continue;
            }
            BigDecimal quantity = feedback.getQuantityFeedback() == null ? BigDecimal.ZERO : feedback.getQuantityFeedback();
            totals.merge(feedback.getProcessId(), quantity, BigDecimal::add);
        }
        return totals;
    }
}
