package com.wyy.web.controller.mes;

import com.wyy.common.annotation.Log;
import com.wyy.common.constant.HttpStatus;
import com.wyy.common.core.controller.BaseController;
import com.wyy.common.core.domain.AjaxResult;
import com.wyy.common.core.page.TableDataInfo;
import com.wyy.common.enums.BusinessType;
import com.wyy.common.utils.poi.ExcelUtil;
import com.wyy.web.domain.ProductionOrderImportRow;
import com.wyy.web.service.ProductionExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** 后台生产订单、排程、任务派工和工作站班组配置接口。 */
@RestController
@RequestMapping("/mes/pro")
public class ProductionExecutionController extends BaseController
{
    @Autowired
    private ProductionExecutionService productionExecutionService;

    @PreAuthorize("@ss.hasPermi('mes:pro:productionorder:list')")
    @GetMapping("/productionorder/list")
    public TableDataInfo orderList(@RequestParam Map<String, String> params)
    {
        return tableData(productionExecutionService.listProductionOrders(new HashMap<String, Object>(params)));
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:productionorder:list')")
    @GetMapping("/productionorder/options")
    public AjaxResult options()
    {
        return AjaxResult.success(productionExecutionService.options());
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:productionorder:add')")
    @Log(title = "生产订单", businessType = BusinessType.INSERT)
    @PostMapping("/productionorder")
    public AjaxResult addOrder(@RequestBody Map<String, Object> body)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.createProductionOrder(body, getUsername()));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:productionorder:add')")
    @Log(title = "生产订单导入", businessType = BusinessType.IMPORT)
    @PostMapping("/productionorder/importData")
    public AjaxResult importOrders(@RequestParam("file") MultipartFile file) throws Exception
    {
        try
        {
            ExcelUtil<ProductionOrderImportRow> util = new ExcelUtil<>(ProductionOrderImportRow.class);
            int count = productionExecutionService.importProductionOrders(util.importExcel(file.getInputStream()), getUsername());
            return AjaxResult.success("成功导入 " + count + " 张生产订单");
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:productionorder:list')")
    @GetMapping("/productionorder/{productionOrderId}/split-options")
    public AjaxResult splitOptions(@PathVariable("productionOrderId") Long productionOrderId)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.productionOrderSplitOptions(productionOrderId));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:productionorder:edit')")
    @Log(title = "生产订单拆工单", businessType = BusinessType.INSERT)
    @PostMapping("/productionorder/{productionOrderId}/split")
    public AjaxResult splitOrder(@PathVariable("productionOrderId") Long productionOrderId, @RequestBody Map<String, Object> body)
    {
        try
        {
            Object rows = body.get("splits");
            if (!(rows instanceof List))
            {
                return AjaxResult.error("拆分数据格式不正确");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> splits = (List<Map<String, Object>>) rows;
            List<Long> ids = productionExecutionService.splitProductionOrder(productionOrderId, splits, getUsername());
            return AjaxResult.success(ids);
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:productionplan:list')")
    @GetMapping("/productionplan/list")
    public AjaxResult planList()
    {
        return AjaxResult.success(productionExecutionService.listProductionPlans());
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:productionplan:add')")
    @Log(title = "生产计划与任务生成", businessType = BusinessType.INSERT)
    @PostMapping("/productionplan")
    public AjaxResult addPlan(@RequestBody Map<String, Object> body)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.createAndReleasePlan(body, getUsername()));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workorder:list')")
    @GetMapping("/productionworkorder/list")
    public TableDataInfo productionWorkorderList(@RequestParam Map<String, String> params)
    {
        return tableData(productionExecutionService.listManagedWorkorders(new HashMap<String, Object>(params)));
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workorder:query')")
    @GetMapping("/productionworkorder/{workorderId}")
    public AjaxResult productionWorkorderDetail(@PathVariable("workorderId") Long workorderId)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.workorderDetail(workorderId));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workorder:edit')")
    @Log(title = "工单分解执行任务", businessType = BusinessType.INSERT)
    @PostMapping("/productionworkorder/{workorderId}/decompose")
    public AjaxResult decomposeWorkorder(@PathVariable("workorderId") Long workorderId, @RequestBody Map<String, Object> body)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.decomposeWorkorder(workorderId, body, getUsername()));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workorder:edit')")
    @Log(title = "生产工单撤回", businessType = BusinessType.UPDATE)
    @PostMapping("/productionworkorder/{workorderId}/revoke")
    public AjaxResult revokeWorkorder(@PathVariable("workorderId") Long workorderId)
    {
        try
        {
            productionExecutionService.revokeWorkorder(workorderId, getUsername());
            return AjaxResult.success();
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workorder:edit')")
    @Log(title = "生产工单强制完成", businessType = BusinessType.UPDATE)
    @PostMapping("/productionworkorder/{workorderId}/force-finish")
    public AjaxResult forceFinishWorkorder(@PathVariable("workorderId") Long workorderId)
    {
        try
        {
            productionExecutionService.forceFinishWorkorder(workorderId, getUsername());
            return AjaxResult.success();
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:executiontask:list')")
    @GetMapping("/executiontask/list")
    public TableDataInfo executionTaskList(@RequestParam Map<String, String> params)
    {
        return tableData(productionExecutionService.listExecutionTasks(new HashMap<String, Object>(params)));
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:executiontask:list')")
    @GetMapping("/executiontask/{taskId}")
    public AjaxResult executionTaskDetail(@PathVariable("taskId") Long taskId)
    {
        try
        {
            return AjaxResult.success(productionExecutionService.executionTaskDetail(taskId));
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 生产报表看板。 */
    @PreAuthorize("@ss.hasPermi('mes:pro:report:list')")
    @GetMapping("/report/dashboard")
    public AjaxResult reportDashboard()
    {
        return AjaxResult.success(productionExecutionService.reportDashboard());
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:dispatch:list')")
    @GetMapping("/dispatch/list")
    public TableDataInfo dispatchList(@RequestParam Map<String, String> params)
    {
        return tableData(productionExecutionService.listDispatchTasks(new HashMap<String, Object>(params)));
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:dispatch:edit')")
    @Log(title = "生产任务派工", businessType = BusinessType.UPDATE)
    @PostMapping("/dispatch/{taskId}")
    public AjaxResult dispatch(@PathVariable("taskId") Long taskId, @RequestBody Map<String, Object> body)
    {
        try
        {
            productionExecutionService.dispatchTask(taskId, body, getUsername());
            return AjaxResult.success();
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:dispatch:edit')")
    @Log(title = "生产任务撤回", businessType = BusinessType.UPDATE)
    @PostMapping("/dispatch/{taskId}/revoke")
    public AjaxResult revokeDispatch(@PathVariable("taskId") Long taskId)
    {
        try
        {
            productionExecutionService.revokeDispatch(taskId, getUsername());
            return AjaxResult.success();
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workstationteam:list')")
    @GetMapping("/workstationteam/list")
    public AjaxResult workstationTeamList()
    {
        return AjaxResult.success(productionExecutionService.listWorkstationTeams());
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workstationteam:add')")
    @Log(title = "工作站班组绑定", businessType = BusinessType.INSERT)
    @PostMapping("/workstationteam")
    public AjaxResult addWorkstationTeam(@RequestBody Map<String, Object> body)
    {
        return saveWorkstationTeam(body);
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workstationteam:edit')")
    @Log(title = "工作站班组绑定", businessType = BusinessType.UPDATE)
    @PutMapping("/workstationteam")
    public AjaxResult editWorkstationTeam(@RequestBody Map<String, Object> body)
    {
        return saveWorkstationTeam(body);
    }

    @PreAuthorize("@ss.hasPermi('mes:pro:workstationteam:remove')")
    @Log(title = "工作站班组绑定", businessType = BusinessType.DELETE)
    @DeleteMapping("/workstationteam/{recordId}")
    public AjaxResult deleteWorkstationTeam(@PathVariable("recordId") Long recordId)
    {
        productionExecutionService.deleteWorkstationTeam(recordId);
        return AjaxResult.success();
    }

    private AjaxResult saveWorkstationTeam(Map<String, Object> body)
    {
        try
        {
            productionExecutionService.saveWorkstationTeam(body, getUsername());
            return AjaxResult.success();
        }
        catch (IllegalArgumentException e)
        {
            return AjaxResult.error(e.getMessage());
        }
    }

    /** 把 {rows,total} 分页结果包装成若依标准分页响应。 */
    private TableDataInfo tableData(Map<String, Object> paged)
    {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) paged.get("rows");
        long total = ((Number) paged.get("total")).longValue();
        TableDataInfo info = new TableDataInfo();
        info.setCode(HttpStatus.SUCCESS);
        info.setMsg("查询成功");
        info.setRows(rows);
        info.setTotal(total);
        return info;
    }
}
