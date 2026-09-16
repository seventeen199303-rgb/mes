package com.ktg.web.service;

import com.ktg.common.constant.UserConstants;
import com.ktg.common.core.domain.entity.SysUser;
import com.ktg.common.utils.SecurityUtils;
import com.ktg.system.domain.SysMessage;
import com.ktg.system.service.ISysMessageService;
import com.ktg.system.service.ISysUserService;
import com.ktg.system.strategy.AutoCodeUtil;
import com.ktg.web.domain.ProductionOrderImportRow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单驱动生产执行的编排服务。
 *
 * <p>它只负责生产订单、计划、任务派工和现场执行状态，实际数量报工、库存和质检仍沿用既有模块，
 * 避免在开工、完工操作中重复记账。</p>
 */
@Service
public class ProductionExecutionService
{
    private static final String STATUS_PREPARE = "PREPARE";
    private static final String STATUS_ALL_SPLIT = "ALL_SPLIT";
    private static final String STATUS_WAITING = "WAITING";
    private static final String STATUS_RELEASED = "RELEASED";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String STATUS_REVOKED = "REVOKED";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AutoCodeUtil autoCodeUtil;

    @Autowired
    private ISysMessageService messageService;

    @Autowired
    private ISysUserService userService;

    public List<Map<String, Object>> listProductionOrders()
    {
        return (List<Map<String, Object>>) listProductionOrders(Collections.emptyMap()).get("rows");
    }

    /** 生产订单查询。所有条件均为可选，供订单列表的组合筛选使用。 */
    public Map<String, Object> listProductionOrders(Map<String, Object> query)
    {
        StringBuilder from = new StringBuilder("FROM pro_production_order o "
                + "LEFT JOIN pro_production_order_line l ON l.production_order_id=o.production_order_id "
                + "LEFT JOIN pro_workorder w ON w.production_order_line_id=l.production_order_line_id ");
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendLike(where, args, "o.production_order_code", text(query, "productionOrderCode"));
        appendLike(where, args, "o.production_order_name", text(query, "productionOrderName"));
        appendLike(where, args, "o.client_code", text(query, "clientCode"));
        appendLike(where, args, "o.client_name", text(query, "clientName"));
        appendLike(where, args, "l.item_code", text(query, "productCode"));
        appendLike(where, args, "l.item_name", text(query, "productName"));
        appendEquals(where, args, "o.status", text(query, "status"));
        Timestamp requestDateStart = timestamp(query.get("requestDateStart"));
        Timestamp requestDateEnd = timestamp(query.get("requestDateEnd"));
        if (requestDateStart != null) { appendWhere(where); where.append(" o.request_date>=?"); args.add(requestDateStart); }
        if (requestDateEnd != null) { appendWhere(where); where.append(" o.request_date<=?"); args.add(requestDateEnd); }
        String orderBy = " ORDER BY o.production_order_id DESC";
        String select = "SELECT o.*, COUNT(l.production_order_line_id) line_count, COALESCE(SUM(l.quantity),0) total_quantity, "
                + "COUNT(DISTINCT w.workorder_id) workorder_count, "
                + "COALESCE(SUM(CASE WHEN w.status<>'REVOKED' THEN 1 ELSE 0 END),0) split_process_count ";
        return paged(select + from.toString() + where.toString() + " GROUP BY o.production_order_id",
                "SELECT COUNT(DISTINCT o.production_order_id) " + from.toString() + where.toString(),
                args, query, orderBy);
    }

    /** 工单统一由生产订单的工序拆解产生，列表附带计划与任务进度。 */
    public Map<String, Object> listManagedWorkorders(Map<String, Object> query)
    {
        StringBuilder from = new StringBuilder("FROM pro_workorder w "
                + "LEFT JOIN pro_production_order o ON o.production_order_id=w.production_order_id "
                + "LEFT JOIN pro_production_plan p ON p.workorder_id=w.workorder_id AND p.status<>'REVOKED' "
                + "LEFT JOIN pro_task t ON t.workorder_id=w.workorder_id ");
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendLike(where, args, "w.workorder_code", text(query, "workorderCode"));
        appendLike(where, args, "w.workorder_name", text(query, "workorderName"));
        appendLike(where, args, "o.production_order_code", text(query, "productionOrderCode"));
        appendLike(where, args, "w.product_code", text(query, "productCode"));
        appendLike(where, args, "w.product_name", text(query, "productName"));
        appendLike(where, args, "w.process_name", text(query, "processName"));
        appendEquals(where, args, "w.status", text(query, "status"));
        String orderBy = " ORDER BY w.workorder_id DESC";
        String select = "SELECT w.*,o.production_order_code,o.production_order_name, "
                + "COUNT(DISTINCT p.plan_id) plan_count,COUNT(DISTINCT t.task_id) task_count,"
                + "COALESCE(SUM(CASE WHEN t.status='FINISHED' THEN 1 ELSE 0 END),0) finished_task_count,"
                + "COALESCE(SUM(CASE WHEN t.status<>'REVOKED' THEN t.quantity ELSE 0 END),0) task_quantity ";
        return paged(select + from.toString() + where.toString() + " GROUP BY w.workorder_id",
                "SELECT COUNT(DISTINCT w.workorder_id) " + from.toString() + where.toString(),
                args, query, orderBy);
    }

    public Map<String, Object> workorderDetail(Long workorderId)
    {
        Map<String, Object> workorder = one("SELECT w.*,o.production_order_code,o.production_order_name FROM pro_workorder w "
                + "LEFT JOIN pro_production_order o ON o.production_order_id=w.production_order_id WHERE w.workorder_id=?", workorderId);
        if (workorder == null) throw new IllegalArgumentException("生产工单不存在");
        Map<String, Object> result = new HashMap<>();
        result.put("workorder", workorder);
        result.put("plans", jdbcTemplate.queryForList("SELECT * FROM pro_production_plan WHERE workorder_id=? ORDER BY plan_id DESC", workorderId));
        result.put("tasks", jdbcTemplate.queryForList(taskSelect() + " WHERE t.workorder_id=? ORDER BY t.task_id DESC", workorderId));
        result.put("taskProgress", one("SELECT COUNT(*) task_count,COALESCE(SUM(CASE WHEN status='FINISHED' THEN 1 ELSE 0 END),0) finished_task_count "
                + "FROM pro_task WHERE workorder_id=?", workorderId));
        return result;
    }

    public Map<String, Object> listExecutionTasks(Map<String, Object> query)
    {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();
        appendLike(where, args, "t.task_code", text(query, "taskCode"));
        appendLike(where, args, "t.task_name", text(query, "taskName"));
        appendLike(where, args, "t.workorder_code", text(query, "workorderCode"));
        appendLike(where, args, "o.production_order_code", text(query, "productionOrderCode"));
        appendLike(where, args, "t.item_code", text(query, "productCode"));
        appendLike(where, args, "t.item_name", text(query, "productName"));
        appendLike(where, args, "t.process_name", text(query, "processName"));
        appendEquals(where, args, "t.status", text(query, "status"));
        String orderBy = " ORDER BY t.task_id DESC";
        return paged(taskSelect() + where.toString(),
                "SELECT COUNT(*) FROM pro_task t "
                + "LEFT JOIN pro_production_plan p ON p.plan_id=t.plan_id "
                + "LEFT JOIN pro_workorder w ON w.workorder_id=t.workorder_id "
                + "LEFT JOIN pro_production_order o ON o.production_order_id=COALESCE(p.production_order_id,w.production_order_id) "
                + where.toString(),
                args, query, orderBy);
    }

    public Map<String, Object> executionTaskDetail(Long taskId)
    {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(taskSelect() + " WHERE t.task_id=?", taskId);
        if (rows.isEmpty()) throw new IllegalArgumentException("生产任务不存在");
        Map<String, Object> result = new HashMap<>();
        result.put("task", rows.get(0));
        result.put("executions", jdbcTemplate.queryForList("SELECT * FROM pro_task_execution WHERE task_id=? ORDER BY execution_id DESC", taskId));
        result.put("operationSteps", operationSteps(nullableLong(rows.get(0).get("process_id"))));
        return result;
    }

    public List<Map<String, Object>> listProductionPlans()
    {
        return jdbcTemplate.queryForList("SELECT p.*, COUNT(t.task_id) task_count, "
                + "COALESCE(SUM(CASE WHEN t.status='FINISHED' THEN 1 ELSE 0 END),0) finished_task_count "
                + "FROM pro_production_plan p LEFT JOIN pro_task t ON t.plan_id=p.plan_id "
                + "GROUP BY p.plan_id ORDER BY p.plan_id DESC");
    }

    public Map<String, Object> listDispatchTasks(Map<String, Object> query)
    {
        StringBuilder select = new StringBuilder("SELECT t.task_id,t.task_code,t.task_name,t.plan_id,t.workorder_code,t.workorder_name,"
                + "t.item_code,t.item_name,t.specification,t.quantity,t.unit_of_measure,t.process_sequence,t.process_name,"
                + "t.workstation_name,t.team_id,t.team_name,t.assigned_user_id,t.assigned_user_nick,t.executor_nick,"
                + "t.status,t.start_time,t.end_time,t.actual_start_time,t.actual_end_time,t.dispatch_time,"
                + "ROUND(COALESCE(t.duration,0)/60,1) required_hours,p.plan_code,p.plan_name "
                + "FROM pro_task t LEFT JOIN pro_production_plan p ON p.plan_id=t.plan_id ");
        StringBuilder where = new StringBuilder("WHERE t.status<>'REVOKED'");
        List<Object> args = new ArrayList<>();
        appendLike(where, args, "t.task_code", text(query, "taskCode"));
        appendLike(where, args, "t.task_name", text(query, "taskName"));
        appendLike(where, args, "p.plan_code", text(query, "planCode"));
        appendLike(where, args, "t.workorder_code", text(query, "workorderCode"));
        appendLike(where, args, "t.item_name", text(query, "itemName"));
        appendLike(where, args, "t.process_name", text(query, "processName"));
        appendLike(where, args, "t.workstation_name", text(query, "workstationName"));
        appendEquals(where, args, "t.status", text(query, "status"));
        String dispatcher = text(query, "dispatcher");
        if (!isBlank(dispatcher))
        {
            appendWhere(where);
            where.append(" (t.assigned_user_nick LIKE ? OR t.team_name LIKE ? OR t.executor_nick LIKE ?)");
            String like = "%" + dispatcher.trim() + "%";
            args.add(like);
            args.add(like);
            args.add(like);
        }
        String orderBy = " ORDER BY t.start_time DESC,t.process_sequence ASC,t.task_id ASC";
        return paged(select.toString() + where.toString(),
                "SELECT COUNT(*) FROM pro_task t LEFT JOIN pro_production_plan p ON p.plan_id=t.plan_id "
                + where.toString(),
                args, query, orderBy);
    }

    public List<Map<String, Object>> listWorkstationTeams()
    {
        return jdbcTemplate.queryForList("SELECT x.record_id,x.workstation_id,w.workstation_code,w.workstation_name,"
                + "x.team_id,t.team_code,t.team_name,x.priority,x.enable_flag,x.remark,x.create_time "
                + "FROM md_workstation_team x "
                + "LEFT JOIN md_workstation w ON w.workstation_id=x.workstation_id "
                + "LEFT JOIN cal_team t ON t.team_id=x.team_id "
                + "ORDER BY w.workstation_code,x.priority,x.record_id");
    }

    public Map<String, Object> options()
    {
        Map<String, Object> options = new HashMap<>();
        options.put("products", jdbcTemplate.queryForList("SELECT item_id,item_code,item_name,specification,unit_of_measure "
                + "FROM md_item WHERE enable_flag='Y' AND item_or_product IN ('PRODUCT','PRODUCTS') ORDER BY item_code"));
        options.put("clients", jdbcTemplate.queryForList("SELECT client_id,client_code,client_name,client_nick "
                + "FROM md_client WHERE enable_flag='Y' ORDER BY client_code"));
        options.put("workorders", jdbcTemplate.queryForList("SELECT workorder_id,workorder_code,workorder_name,product_id,product_code,"
                + "product_name,product_spc,unit_of_measure,quantity,request_date,plan_end_time,production_order_id,route_id,route_code,route_name,"
                + "start_process_id,start_process_name,start_process_order,end_process_id,end_process_name,end_process_order "
                + "FROM pro_workorder WHERE status NOT IN ('FINISHED','REVOKED') ORDER BY workorder_id DESC"));
        options.put("routes", jdbcTemplate.queryForList("SELECT route_id,route_code,route_name,route_desc FROM pro_route "
                + "WHERE enable_flag='Y' ORDER BY route_code"));
        options.put("routeProducts", jdbcTemplate.queryForList("SELECT route_id,item_id FROM pro_route_product"));
        options.put("teams", jdbcTemplate.queryForList("SELECT team_id,team_code,team_name,calendar_type FROM cal_team ORDER BY team_code"));
        options.put("users", jdbcTemplate.queryForList("SELECT user_id,user_name,nick_name,phonenumber FROM sys_user "
                + "WHERE status='0' AND del_flag='0' ORDER BY user_name"));
        options.put("workstations", jdbcTemplate.queryForList("SELECT workstation_id,workstation_code,workstation_name,process_id,process_name "
                + "FROM md_workstation WHERE enable_flag='Y' ORDER BY workstation_code"));
        return options;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createProductionOrder(Map<String, Object> body, String username)
    {
        Long itemId = requiredLong(body, "itemId", "请选择产品");
        BigDecimal quantity = requiredPositiveInteger(body, "quantity", "生产数量必须为正整数");
        Map<String, Object> item = one("SELECT item_id,item_code,item_name,specification,unit_of_measure FROM md_item "
                + "WHERE item_id=? AND enable_flag='Y'", itemId);
        if (item == null)
        {
            throw new IllegalArgumentException("所选产品不存在或已停用");
        }

        String code = text(body, "productionOrderCode");
        if (isBlank(code))
        {
            code = autoCodeUtil.genSerialCode("PRODUCTION_ORDER_CODE", null);
        }
        if (exists("SELECT 1 FROM pro_production_order WHERE production_order_code=?", code))
        {
            throw new IllegalArgumentException("生产订单编号已存在");
        }
        String itemName = string(item.get("item_name"));
        final String productionOrderCode = code;
        String name = defaultText(text(body, "productionOrderName"), itemName + "生产订单");
        Timestamp requestDate = timestamp(body.get("requestDate"));
        if (requestDate == null)
        {
            throw new IllegalArgumentException("请选择需求交期");
        }
        Long clientId = requiredLong(body, "clientId", "请选择客户");
        Map<String, Object> client = one("SELECT client_id,client_code,client_name FROM md_client WHERE client_id=? AND enable_flag='Y'", clientId);
        if (client == null)
        {
            throw new IllegalArgumentException("所选客户不存在或已停用");
        }
        String clientCode = string(client.get("client_code"));
        String clientName = string(client.get("client_name"));
        String remark = text(body, "remark");

        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO pro_production_order "
                    + "(production_order_code,production_order_name,order_type,status,client_id,client_code,client_name,request_date,remark,create_by,create_time) "
                    + "VALUES (?,?, 'MAKE',?,?,?,?,?,?,?,NOW())", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, productionOrderCode);
            statement.setString(2, name);
            statement.setString(3, STATUS_PREPARE);
            setNullableLong(statement, 4, clientId);
            statement.setString(5, clientCode);
            statement.setString(6, clientName);
            statement.setTimestamp(7, requestDate);
            statement.setString(8, defaultText(remark, ""));
            statement.setString(9, username);
            return statement;
        }, holder);
        Long orderId = holder.getKey().longValue();
        jdbcTemplate.update("INSERT INTO pro_production_order_line "
                        + "(production_order_id,item_id,item_code,item_name,specification,unit_of_measure,quantity,workorder_quantity,status,remark,create_by,create_time) "
                        + "VALUES (?,?,?,?,?,?,?,0,?,?,?,NOW())",
                orderId, itemId, string(item.get("item_code")), itemName, string(item.get("specification")),
                string(item.get("unit_of_measure")), quantity, STATUS_PREPARE, defaultText(remark, ""), username);
        return orderId;
    }

    /** 按静态模板导入；每个非空数据行创建一张生产订单。 */
    @Transactional(rollbackFor = Exception.class)
    public int importProductionOrders(List<ProductionOrderImportRow> rows, String username)
    {
        int imported = 0;
        for (int index = 0; index < rows.size(); index++)
        {
            ProductionOrderImportRow row = rows.get(index);
            if (row == null || (isBlank(row.getProductionOrderName()) && isBlank(row.getClientCode())
                    && isBlank(row.getProductCode()) && row.getQuantity() == null && row.getRequestDate() == null && isBlank(row.getRemark())))
            {
                continue;
            }
            int lineNumber = index + 2;
            Map<String, Object> client = one("SELECT client_id,client_code,client_name FROM md_client WHERE client_code=? AND enable_flag='Y'",
                    defaultText(row.getClientCode(), ""));
            if (client == null)
            {
                throw new IllegalArgumentException("第 " + lineNumber + " 行客户编码不存在或未启用：" + defaultText(row.getClientCode(), "（空）"));
            }
            Map<String, Object> item = one("SELECT item_id FROM md_item WHERE item_code=? AND enable_flag='Y' AND item_or_product IN ('PRODUCT','PRODUCTS')",
                    defaultText(row.getProductCode(), ""));
            if (item == null)
            {
                throw new IllegalArgumentException("第 " + lineNumber + " 行产品编码不存在或未启用：" + defaultText(row.getProductCode(), "（空）"));
            }
            if (!isPositiveInteger(row.getQuantity()))
            {
                throw new IllegalArgumentException("第 " + lineNumber + " 行生产数量必须为正整数");
            }
            if (row.getRequestDate() == null)
            {
                throw new IllegalArgumentException("第 " + lineNumber + " 行需求交期不能为空，格式为 yyyy-MM-dd");
            }
            Map<String, Object> body = new HashMap<>();
            body.put("itemId", item.get("item_id"));
            body.put("clientId", client.get("client_id"));
            body.put("productionOrderName", row.getProductionOrderName());
            body.put("quantity", row.getQuantity());
            body.put("requestDate", row.getRequestDate());
            body.put("remark", row.getRemark());
            createProductionOrder(body, username);
            imported++;
        }
        if (imported == 0)
        {
            throw new IllegalArgumentException("未读取到可导入的生产订单数据");
        }
        return imported;
    }

    /** 返回订单行可用的产品工艺路线及其有序工序，供按工序拆工单使用。 */
    public Map<String, Object> productionOrderSplitOptions(Long productionOrderId)
    {
        Map<String, Object> order = one("SELECT * FROM pro_production_order WHERE production_order_id=?", productionOrderId);
        if (order == null)
        {
            throw new IllegalArgumentException("生产订单不存在");
        }
        List<Map<String, Object>> lines = jdbcTemplate.queryForList("SELECT l.* FROM pro_production_order_line l "
                + "WHERE l.production_order_id=? ORDER BY l.production_order_line_id", productionOrderId);
        for (Map<String, Object> line : lines)
        {
            List<Map<String, Object>> routes = jdbcTemplate.queryForList("SELECT r.route_id,r.route_code,r.route_name,r.route_desc "
                    + "FROM pro_route_product rp JOIN pro_route r ON r.route_id=rp.route_id "
                    + "WHERE rp.item_id=? AND r.enable_flag='Y' ORDER BY r.route_code", line.get("item_id"));
            for (Map<String, Object> route : routes)
            {
                List<Map<String, Object>> processes = jdbcTemplate.queryForList("SELECT record_id,process_id,process_code,process_name,order_num "
                        + "FROM pro_route_process WHERE route_id=? ORDER BY order_num,record_id", route.get("route_id"));
                for (Map<String, Object> process : processes)
                {
                    boolean split = exists("SELECT 1 FROM pro_workorder WHERE production_order_line_id=? AND route_id=? "
                                    + "AND start_process_id=? AND end_process_id=? AND status<>'REVOKED'",
                            line.get("production_order_line_id"), route.get("route_id"), process.get("process_id"), process.get("process_id"));
                    process.put("split", split);
                }
                route.put("processes", processes);
            }
            line.put("routes", routes);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("order", order);
        result.put("lines", lines);
        return result;
    }

    /**
     * 订单只按工艺路线中的工序拆成工单；每个工序工单承接订单整件数量。
     * 数量拆分只在后续“工单分解”为执行任务时发生。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> splitProductionOrder(Long productionOrderId, List<Map<String, Object>> splits, String username)
    {
        Map<String, Object> order = one("SELECT * FROM pro_production_order WHERE production_order_id=? FOR UPDATE", productionOrderId);
        if (order == null)
        {
            throw new IllegalArgumentException("生产订单不存在");
        }
        if (STATUS_FINISHED.equals(string(order.get("status"))))
        {
            throw new IllegalArgumentException("已完成的生产订单不能再拆解工单");
        }
        if (splits == null || splits.isEmpty())
        {
            throw new IllegalArgumentException("请至少设置一张要生成的生产工单");
        }
        List<Map<String, Object>> lines = jdbcTemplate.queryForList("SELECT * FROM pro_production_order_line WHERE production_order_id=? "
                + "ORDER BY production_order_line_id FOR UPDATE", productionOrderId);
        Map<Long, Map<String, Object>> lineMap = new HashMap<>();
        for (Map<String, Object> line : lines)
        {
            lineMap.put(longValue(line.get("production_order_line_id")), line);
        }

        List<Long> workorderIds = new ArrayList<>();
        for (int index = 0; index < splits.size(); index++)
        {
            Map<String, Object> split = splits.get(index);
            Long lineId = requiredLong(split, "productionOrderLineId", "第 " + (index + 1) + " 行缺少订单产品");
            Map<String, Object> line = lineMap.get(lineId);
            if (line == null)
            {
                throw new IllegalArgumentException("第 " + (index + 1) + " 行的订单产品不属于当前生产订单");
            }
            Long routeId = requiredLong(split, "routeId", "第 " + (index + 1) + " 行请选择工艺路线");
            Map<String, Object> route = one("SELECT r.route_id,r.route_code,r.route_name FROM pro_route r "
                    + "JOIN pro_route_product rp ON rp.route_id=r.route_id WHERE r.route_id=? AND rp.item_id=? AND r.enable_flag='Y'",
                    routeId, line.get("item_id"));
            if (route == null)
            {
                throw new IllegalArgumentException("产品【" + string(line.get("item_code")) + "】未关联所选工艺路线，请先维护产品制程关系");
            }
            Long processId = requiredLong(split, "processId", "第 " + (index + 1) + " 行请选择工序");
            Map<String, Object> process = one("SELECT process_id,process_code,process_name,order_num FROM pro_route_process WHERE route_id=? AND process_id=?",
                    routeId, processId);
            if (process == null)
            {
                throw new IllegalArgumentException("第 " + (index + 1) + " 行选择的工序不属于所选工艺路线");
            }
            if (exists("SELECT 1 FROM pro_workorder WHERE production_order_line_id=? AND route_id=? "
                    + "AND start_process_id=? AND end_process_id=? AND status<>'REVOKED'", lineId, routeId, processId, processId))
            {
                throw new IllegalArgumentException("工序【" + string(process.get("process_name")) + "】已经拆分过生产工单");
            }
            Timestamp planStartTime = timestamp(split.get("planStartTime"));
            Timestamp planEndTime = timestamp(split.get("planEndTime"));
            if (planStartTime == null || planEndTime == null)
            {
                throw new IllegalArgumentException("第 " + (index + 1) + " 行请完整填写工单计划开始和完成时间");
            }
            if (!planEndTime.after(planStartTime))
            {
                throw new IllegalArgumentException("第 " + (index + 1) + " 行工单计划完成时间必须晚于开始时间");
            }
            workorderIds.add(insertSplitWorkorder(order, line, route, process, planStartTime, planEndTime, username));
            jdbcTemplate.update("UPDATE pro_production_order_line SET status='SPLIT',update_by=?,update_time=NOW() WHERE production_order_line_id=?",
                    username, lineId);
        }
        updateProductionOrderSplitStatus(productionOrderId, username);
        return workorderIds;
    }

    private Long insertSplitWorkorder(Map<String, Object> order, Map<String, Object> line, Map<String, Object> route,
            Map<String, Object> process, Timestamp planStartTime, Timestamp planEndTime, String username)
    {
        String workorderCode = autoCodeUtil.genSerialCode(UserConstants.WORKORDER_CODE, null);
        String productName = string(line.get("item_name"));
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            String sql = "INSERT INTO pro_workorder "
                    + "(workorder_code,workorder_name,workorder_type,order_source,source_code,production_order_id,production_order_line_id,"
                    + "route_id,route_code,route_name,start_process_id,start_process_code,start_process_name,start_process_order,"
                    + "end_process_id,end_process_code,end_process_name,end_process_order,product_id,product_code,product_name,product_spc,unit_of_measure,"
                    + "quantity,quantity_produced,quantity_changed,quantity_scheduled,client_id,client_code,client_name,request_date,plan_start_time,plan_end_time,"
                    + "parent_id,ancestors,status,remark,create_by,create_time) VALUES ("
                    + String.join(",", Collections.nCopies(38, "?")) + ",NOW())";
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            int p = 1;
            statement.setString(p++, workorderCode);
            statement.setString(p++, productName + "-" + string(process.get("process_name")) + "工序工单");
            statement.setString(p++, "SELF");
            statement.setString(p++, "ORDER");
            statement.setString(p++, string(order.get("production_order_code")));
            statement.setLong(p++, longValue(order.get("production_order_id")));
            statement.setLong(p++, longValue(line.get("production_order_line_id")));
            statement.setLong(p++, longValue(route.get("route_id")));
            statement.setString(p++, string(route.get("route_code")));
            statement.setString(p++, string(route.get("route_name")));
            statement.setLong(p++, longValue(process.get("process_id")));
            statement.setString(p++, string(process.get("process_code")));
            statement.setString(p++, string(process.get("process_name")));
            statement.setInt(p++, optionalInt(process.get("order_num"), 1));
            statement.setLong(p++, longValue(process.get("process_id")));
            statement.setString(p++, string(process.get("process_code")));
            statement.setString(p++, string(process.get("process_name")));
            statement.setInt(p++, optionalInt(process.get("order_num"), 1));
            statement.setLong(p++, longValue(line.get("item_id")));
            statement.setString(p++, string(line.get("item_code")));
            statement.setString(p++, productName);
            statement.setString(p++, string(line.get("specification")));
            statement.setString(p++, string(line.get("unit_of_measure")));
            statement.setBigDecimal(p++, decimal(line.get("quantity")));
            statement.setBigDecimal(p++, BigDecimal.ZERO);
            statement.setBigDecimal(p++, BigDecimal.ZERO);
            statement.setBigDecimal(p++, BigDecimal.ZERO);
            setNullableLong(statement, p++, nullableLong(order.get("client_id")));
            statement.setString(p++, string(order.get("client_code")));
            statement.setString(p++, string(order.get("client_name")));
            statement.setTimestamp(p++, timestamp(order.get("request_date")));
            statement.setTimestamp(p++, planStartTime);
            statement.setTimestamp(p++, planEndTime);
            statement.setLong(p++, 0L);
            statement.setString(p++, "0");
            statement.setString(p++, "CONFIRMED");
            statement.setString(p++, defaultText(string(order.get("remark")), ""));
            statement.setString(p++, username);
            return statement;
        }, holder);
        return holder.getKey().longValue();
    }

    /** 创建工序工单的生产计划；执行任务由工单分解动作单独生成。 */
    @Transactional(rollbackFor = Exception.class)
    public Long createAndReleasePlan(Map<String, Object> body, String username)
    {
        Long workorderId = requiredLong(body, "workorderId", "请选择生产工单");
        Long routeId = requiredLong(body, "routeId", "请选择工艺路线");
        Map<String, Object> workorder = one("SELECT * FROM pro_workorder WHERE workorder_id=?", workorderId);
        Map<String, Object> route = one("SELECT * FROM pro_route WHERE route_id=? AND enable_flag='Y'", routeId);
        if (workorder == null)
        {
            throw new IllegalArgumentException("生产工单不存在");
        }
        if (route == null)
        {
            throw new IllegalArgumentException("工艺路线不存在或未启用");
        }
        if (!exists("SELECT 1 FROM pro_route_product WHERE route_id=? AND item_id=?", routeId, workorder.get("product_id")))
        {
            throw new IllegalArgumentException("所选工艺路线未关联当前工单产品，请先维护工艺路线的产品制程");
        }
        Long lockedRouteId = nullableLong(workorder.get("route_id"));
        if (lockedRouteId != null && !lockedRouteId.equals(routeId))
        {
            throw new IllegalArgumentException("该工单已按产品工艺路线拆分，排程必须使用工单指定的工艺路线");
        }
        BigDecimal quantity = requiredPositiveInteger(body, "quantity", "计划数量必须为正整数");
        if (quantity.compareTo(decimal(workorder.get("quantity"))) > 0)
        {
            throw new IllegalArgumentException("计划数量不能超过工单数量");
        }
        Timestamp plannedStart = timestamp(body.get("plannedStartTime"));
        Timestamp plannedEnd = timestamp(body.get("plannedEndTime"));
        if (plannedEnd == null)
        {
            plannedEnd = timestamp(workorder.get("plan_end_time"));
        }
        if (plannedStart == null)
        {
            plannedStart = Timestamp.valueOf(LocalDateTime.now().withSecond(0).withNano(0));
        }
        if (plannedEnd == null)
        {
            plannedEnd = Timestamp.valueOf(plannedStart.toLocalDateTime().plusHours(8));
        }
        if (!plannedEnd.after(plannedStart))
        {
            throw new IllegalArgumentException("计划完成时间必须晚于计划开始时间");
        }
        String dispatchMode = defaultText(text(body, "dispatchMode"), "AUTO").toUpperCase();
        if (!("AUTO".equals(dispatchMode) || "TEAM".equals(dispatchMode) || "USER".equals(dispatchMode) || "MANUAL".equals(dispatchMode)))
        {
            throw new IllegalArgumentException("派工方式不正确");
        }
        Long teamId = optionalLong(body, "teamId");
        Long userId = optionalLong(body, "userId");
        if ("TEAM".equals(dispatchMode) && teamId == null)
        {
            throw new IllegalArgumentException("按班组派工时必须选择班组");
        }
        if ("USER".equals(dispatchMode) && userId == null)
        {
            throw new IllegalArgumentException("按人员派工时必须选择责任人");
        }

        String planCode = autoCodeUtil.genSerialCode("PRODUCTION_PLAN_CODE", null);
        KeyHolder holder = new GeneratedKeyHolder();
        Timestamp finalPlannedStart = plannedStart;
        Timestamp finalPlannedEnd = plannedEnd;
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO pro_production_plan "
                            + "(plan_code,plan_name,production_order_id,workorder_id,workorder_code,route_id,route_code,route_name,quantity,"
                            + "planned_start_time,planned_end_time,dispatch_mode,status,remark,create_by,create_time) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'PREPARE',?,?,NOW())",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, planCode);
            statement.setString(2, string(workorder.get("workorder_name")) + "生产计划");
            setNullableLong(statement, 3, nullableLong(workorder.get("production_order_id")));
            statement.setLong(4, workorderId);
            statement.setString(5, string(workorder.get("workorder_code")));
            statement.setLong(6, routeId);
            statement.setString(7, string(route.get("route_code")));
            statement.setString(8, string(route.get("route_name")));
            statement.setBigDecimal(9, quantity);
            statement.setTimestamp(10, finalPlannedStart);
            statement.setTimestamp(11, finalPlannedEnd);
            statement.setString(12, dispatchMode);
            statement.setString(13, defaultText(text(body, "remark"), ""));
            statement.setString(14, username);
            return statement;
        }, holder);
        Long planId = holder.getKey().longValue();
        jdbcTemplate.update("UPDATE pro_workorder SET quantity_scheduled=quantity_scheduled+?,status='CONFIRMED',update_by=?,update_time=NOW() WHERE workorder_id=?",
                quantity, username, workorderId);
        return planId;
    }

    /** 按数量把单一工序工单拆成多个可派发的执行任务。 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> decomposeWorkorder(Long workorderId, Map<String, Object> body, String username)
    {
        Object rawTasks = body.get("tasks");
        if (!(rawTasks instanceof List) || ((List<?>) rawTasks).isEmpty())
        {
            throw new IllegalArgumentException("请至少填写一条执行任务");
        }
        Map<String, Object> workorder = one("SELECT * FROM pro_workorder WHERE workorder_id=? FOR UPDATE", workorderId);
        if (workorder == null) throw new IllegalArgumentException("生产工单不存在");
        if (STATUS_REVOKED.equals(string(workorder.get("status"))) || STATUS_FINISHED.equals(string(workorder.get("status"))))
        {
            throw new IllegalArgumentException("已撤回或已完成的工单不能再分解执行任务");
        }
        Long startProcessId = nullableLong(workorder.get("start_process_id"));
        Long endProcessId = nullableLong(workorder.get("end_process_id"));
        if (startProcessId == null || endProcessId == null || !startProcessId.equals(endProcessId))
        {
            throw new IllegalArgumentException("历史工单包含多个工序，请按新流程从订单重新按工序拆解后再生成任务");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> taskRows = (List<Map<String, Object>>) rawTasks;
        BigDecimal requested = BigDecimal.ZERO;
        for (int index = 0; index < taskRows.size(); index++)
        {
            Map<String, Object> row = taskRows.get(index);
            requested = requested.add(requiredPositiveInteger(row, "quantity", "第 " + (index + 1) + " 条任务数量必须为正整数"));
            Timestamp start = timestamp(row.get("plannedStartTime"));
            Timestamp end = timestamp(row.get("plannedEndTime"));
            if (start == null || end == null || !end.after(start))
            {
                throw new IllegalArgumentException("第 " + (index + 1) + " 条任务请填写有效的计划开始和完成时间");
            }
        }
        BigDecimal existing = decimal(one("SELECT COALESCE(SUM(quantity),0) quantity FROM pro_task WHERE workorder_id=? AND status<>'REVOKED'", workorderId).get("quantity"));
        if (existing.add(requested).compareTo(decimal(workorder.get("quantity"))) > 0)
        {
            throw new IllegalArgumentException("本次任务数量加上已生成任务数量不能超过工单数量");
        }
        Map<String, Object> route = one("SELECT * FROM pro_route WHERE route_id=? AND enable_flag='Y'", workorder.get("route_id"));
        if (route == null) throw new IllegalArgumentException("工艺路线不存在或已停用");
        List<Long> taskIds = new ArrayList<>();
        for (Map<String, Object> row : taskRows)
        {
            BigDecimal quantity = requiredPositiveInteger(row, "quantity", "任务数量必须为正整数");
            generateRouteTasks(null, workorder, route, quantity, timestamp(row.get("plannedStartTime")),
                    timestamp(row.get("plannedEndTime")), "MANUAL", null, null, username,
                    optionalInt(workorder.get("start_process_order"), Integer.MIN_VALUE), optionalInt(workorder.get("end_process_order"), Integer.MAX_VALUE));
            Map<String, Object> created = one("SELECT task_id FROM pro_task WHERE workorder_id=? ORDER BY task_id DESC LIMIT 1", workorderId);
            if (created != null) taskIds.add(longValue(created.get("task_id")));
        }
        jdbcTemplate.update("UPDATE pro_workorder SET status='CONFIRMED',update_by=?,update_time=NOW() WHERE workorder_id=?", username, workorderId);
        return taskIds;
    }

    /** 撤销尚未执行的工单及其计划、任务，订单对应工序重新恢复可拆。 */
    @Transactional(rollbackFor = Exception.class)
    public void revokeWorkorder(Long workorderId, String username)
    {
        Map<String, Object> workorder = one("SELECT * FROM pro_workorder WHERE workorder_id=? FOR UPDATE", workorderId);
        if (workorder == null) throw new IllegalArgumentException("生产工单不存在");
        if (STATUS_REVOKED.equals(string(workorder.get("status")))) return;
        if (exists("SELECT 1 FROM pro_task WHERE workorder_id=? AND status IN ('IN_PROGRESS','FINISHED')", workorderId))
        {
            throw new IllegalArgumentException("工单已有开工或完成的执行任务，不能撤回");
        }
        jdbcTemplate.update("UPDATE pro_task SET status='REVOKED',update_by=?,update_time=NOW() WHERE workorder_id=? AND status<>'REVOKED'", username, workorderId);
        jdbcTemplate.update("UPDATE pro_production_plan SET status='REVOKED',update_by=?,update_time=NOW() WHERE workorder_id=? AND status<>'REVOKED'", username, workorderId);
        jdbcTemplate.update("UPDATE pro_workorder SET status='REVOKED',update_by=?,update_time=NOW() WHERE workorder_id=?", username, workorderId);
        Long orderId = nullableLong(workorder.get("production_order_id"));
        if (orderId != null) updateProductionOrderSplitStatus(orderId, username);
    }

    /** 强制完成工单时同步结束其未结束的执行任务与生产计划。 */
    @Transactional(rollbackFor = Exception.class)
    public void forceFinishWorkorder(Long workorderId, String username)
    {
        Map<String, Object> workorder = one("SELECT * FROM pro_workorder WHERE workorder_id=? FOR UPDATE", workorderId);
        if (workorder == null) throw new IllegalArgumentException("生产工单不存在");
        if (STATUS_REVOKED.equals(string(workorder.get("status")))) throw new IllegalArgumentException("已撤回的工单不能强制完成");
        jdbcTemplate.update("UPDATE pro_task SET status='FINISHED',actual_start_time=COALESCE(actual_start_time,NOW()),actual_end_time=NOW(),"
                        + "actual_duration=COALESCE(actual_duration,0),update_by=?,update_time=NOW() WHERE workorder_id=? AND status<>'REVOKED' AND status<>'FINISHED'",
                username, workorderId);
        jdbcTemplate.update("UPDATE pro_production_plan SET status='FINISHED',update_by=?,update_time=NOW() WHERE workorder_id=? AND status<>'REVOKED'", username, workorderId);
        jdbcTemplate.update("UPDATE pro_workorder SET status='FINISHED',quantity_produced=quantity,update_by=?,update_time=NOW() WHERE workorder_id=?", username, workorderId);
        if (nullableLong(workorder.get("plan_id")) == null)
        {
            Map<String, Object> completedTask = one("SELECT * FROM pro_task WHERE workorder_id=? ORDER BY task_id LIMIT 1", workorderId);
            if (completedTask != null)
            {
                releaseNextTask(completedTask, username);
            }
        }
        finishProductionOrderIfReady(nullableLong(workorder.get("production_order_id")), username);
    }

    @Transactional(rollbackFor = Exception.class)
    public void dispatchTask(Long taskId, Map<String, Object> body, String username)
    {
        Map<String, Object> task = one("SELECT * FROM pro_task WHERE task_id=? FOR UPDATE", taskId);
        if (task == null)
        {
            throw new IllegalArgumentException("生产任务不存在");
        }
        if (STATUS_FINISHED.equals(string(task.get("status"))) || STATUS_IN_PROGRESS.equals(string(task.get("status"))))
        {
            throw new IllegalArgumentException("已开工或已完成的任务不能重新派工");
        }
        String assignType = defaultText(text(body, "assignType"), "TEAM").toUpperCase();
        Long teamId = optionalLong(body, "teamId");
        Long userId = optionalLong(body, "userId");
        Assignment assignment = resolveAssignment(assignType, teamId, userId, nullableLong(task.get("workstation_id")));
        boolean predecessorFinished = predecessorFinished(task);
        String status = predecessorFinished ? STATUS_RELEASED : STATUS_WAITING;
        jdbcTemplate.update("UPDATE pro_task SET assign_type=?,team_id=?,team_code=?,team_name=?,assigned_user_id=?,assigned_user_name=?,"
                        + "assigned_user_nick=?,dispatch_time=NOW(),status=?,update_by=?,update_time=NOW() WHERE task_id=?",
                assignment.type, assignment.teamId, assignment.teamCode, assignment.teamName, assignment.userId,
                assignment.userName, assignment.userNick, status, username, taskId);
        String plannedEndTime = text(body, "plannedEndTime");
        if (!isBlank(plannedEndTime))
        {
            jdbcTemplate.update("UPDATE pro_task SET end_time=?,update_by=?,update_time=NOW() WHERE task_id=?",
                    plannedEndTime.trim(), username, taskId);
        }
        if (STATUS_RELEASED.equals(status))
        {
            task = one("SELECT * FROM pro_task WHERE task_id=?", taskId);
            notifyTaskRecipients(task, "生产任务已派发");
        }
    }

    /** 撤回已派工但尚未开工的任务，任务回到待派工状态并清空派工对象。 */
    @Transactional(rollbackFor = Exception.class)
    public void revokeDispatch(Long taskId, String username)
    {
        Map<String, Object> task = one("SELECT * FROM pro_task WHERE task_id=? FOR UPDATE", taskId);
        if (task == null)
        {
            throw new IllegalArgumentException("生产任务不存在");
        }
        String status = string(task.get("status"));
        if (STATUS_FINISHED.equals(status))
        {
            throw new IllegalArgumentException("已完成的任务不能撤回");
        }
        if (task.get("actual_start_time") != null || task.get("executor_id") != null)
        {
            throw new IllegalArgumentException("任务已开工，不能撤回；如需变更执行人请由操作人在 APP 端转交");
        }
        if (!STATUS_RELEASED.equals(status) && !STATUS_WAITING.equals(status) && !STATUS_PREPARE.equals(status))
        {
            throw new IllegalArgumentException("当前任务状态不允许撤回");
        }
        jdbcTemplate.update("UPDATE pro_task SET status=?,assign_type='MANUAL',team_id=NULL,team_code=NULL,team_name=NULL,"
                        + "assigned_user_id=NULL,assigned_user_name=NULL,assigned_user_nick=NULL,dispatch_time=NULL,"
                        + "update_by=?,update_time=NOW() WHERE task_id=?",
                STATUS_PREPARE, username, taskId);
    }

    /** APP 端操作人将进行中的任务转交给其他员工，任务与责任人同步变更。 */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferTask(Long taskId, Long targetUserId, String remark)
    {
        Long userId = SecurityUtils.getUserId();
        Map<String, Object> task = one("SELECT * FROM pro_task WHERE task_id=? FOR UPDATE", taskId);
        assertMobileAccess(task, userId);
        if (!STATUS_IN_PROGRESS.equals(string(task.get("status"))))
        {
            throw new IllegalArgumentException("只有已开工的任务才能转交");
        }
        if (!userId.equals(nullableLong(task.get("executor_id"))))
        {
            throw new IllegalArgumentException("只有当前实际开工的员工可以转交此任务");
        }
        if (targetUserId == null)
        {
            throw new IllegalArgumentException("请选择接手人");
        }
        if (userId.equals(targetUserId))
        {
            throw new IllegalArgumentException("不能转交给自己");
        }
        SysUser target = userService.selectUserById(targetUserId);
        if (target == null || !"0".equals(target.getStatus()) || !"0".equals(target.getDelFlag()))
        {
            throw new IllegalArgumentException("接手人不存在或已停用");
        }
        jdbcTemplate.update("UPDATE pro_task SET assign_type='USER',assigned_user_id=?,assigned_user_name=?,assigned_user_nick=?,"
                        + "executor_id=?,executor_name=?,executor_nick=?,team_id=NULL,team_code=NULL,team_name=NULL,"
                        + "update_by=?,update_time=NOW() WHERE task_id=?",
                target.getUserId(), target.getUserName(), target.getNickName(),
                target.getUserId(), target.getUserName(), target.getNickName(), target.getUserName(), taskId);
        SysUser operator = userService.selectUserById(userId);
        insertExecution(taskId, "TRANSFER", operator, defaultText(remark, "") + " 转交给 " + target.getNickName());
        Map<String, Object> updated = one("SELECT * FROM pro_task WHERE task_id=?", taskId);
        notifyTaskRecipients(updated, "生产任务已转交给您");
        return myExecutableTask(taskId, targetUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveWorkstationTeam(Map<String, Object> body, String username)
    {
        Long workstationId = requiredLong(body, "workstationId", "请选择工作站");
        Long teamId = requiredLong(body, "teamId", "请选择班组");
        Long recordId = optionalLong(body, "recordId");
        int priority = optionalInt(body, "priority", 1);
        String enabled = "N".equalsIgnoreCase(text(body, "enableFlag")) ? "N" : "Y";
        if (one("SELECT workstation_id FROM md_workstation WHERE workstation_id=?", workstationId) == null
                || one("SELECT team_id FROM cal_team WHERE team_id=?", teamId) == null)
        {
            throw new IllegalArgumentException("工作站或班组不存在");
        }
        if (recordId == null)
        {
            if (exists("SELECT 1 FROM md_workstation_team WHERE workstation_id=? AND team_id=?", workstationId, teamId))
            {
                throw new IllegalArgumentException("该工作站已绑定此班组");
            }
            jdbcTemplate.update("INSERT INTO md_workstation_team(workstation_id,team_id,priority,enable_flag,remark,create_by,create_time) "
                    + "VALUES (?,?,?,?,?,?,NOW())", workstationId, teamId, priority, enabled, defaultText(text(body, "remark"), ""), username);
        }
        else
        {
            jdbcTemplate.update("UPDATE md_workstation_team SET workstation_id=?,team_id=?,priority=?,enable_flag=?,remark=?,update_by=?,update_time=NOW() "
                    + "WHERE record_id=?", workstationId, teamId, priority, enabled, defaultText(text(body, "remark"), ""), username, recordId);
        }
    }

    /** 在职员工列表（供 APP 转交弹窗选择接手人），不含排除的用户。 */
    public List<Map<String, Object>> activeUsers(Long excludeUserId)
    {
        return jdbcTemplate.queryForList("SELECT user_id userId,user_name userName,nick_name nickName,phonenumber, "
                + "(SELECT dept_name FROM sys_dept d WHERE d.dept_id=u.dept_id) deptName "
                + "FROM sys_user u WHERE status='0' AND del_flag='0' AND user_id<>? ORDER BY user_name", excludeUserId);
    }

    /** 生产报表看板：订单/工单/任务概况、当日任务、近7天曲线、当月执行、班组统计。 */
    public Map<String, Object> reportDashboard()
    {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> overview = new HashMap<>();
        overview.put("orderCount", number(jdbcTemplate.queryForList(
                "SELECT COUNT(*) c FROM pro_production_order").get(0).get("c")));
        overview.put("workorderCount", number(jdbcTemplate.queryForList(
                "SELECT COUNT(*) c FROM pro_workorder").get(0).get("c")));
        overview.put("taskCount", number(jdbcTemplate.queryForList(
                "SELECT COUNT(*) c FROM pro_task WHERE status<>'REVOKED'").get(0).get("c")));
        overview.put("finishedTaskCount", number(jdbcTemplate.queryForList(
                "SELECT COUNT(*) c FROM pro_task WHERE status='FINISHED'").get(0).get("c")));
        overview.put("inProgressTaskCount", number(jdbcTemplate.queryForList(
                "SELECT COUNT(*) c FROM pro_task WHERE status='IN_PROGRESS'").get(0).get("c")));
        overview.put("releasedTaskCount", number(jdbcTemplate.queryForList(
                "SELECT COUNT(*) c FROM pro_task WHERE status='RELEASED'").get(0).get("c")));
        result.put("overview", overview);

        result.put("today", jdbcTemplate.queryForList("SELECT "
                + "(SELECT COUNT(*) FROM pro_task WHERE status<>'REVOKED' AND DATE(start_time)=CURDATE()) created,"
                + "(SELECT COUNT(*) FROM pro_task WHERE status='RELEASED') waiting,"
                + "(SELECT COUNT(*) FROM pro_task WHERE status='IN_PROGRESS') running,"
                + "(SELECT COUNT(*) FROM pro_task WHERE status='FINISHED' AND DATE(actual_end_time)=CURDATE()) finishedToday,"
                + "(SELECT COUNT(*) FROM pro_task WHERE status='FINISHED') finishedTotal").get(0));

        List<Map<String, Object>> weekTrend = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(d.day,'%m-%d') dateLabel,"
                + "COALESCE(SUM(CASE WHEN t.status<>'REVOKED' AND DATE(t.start_time)=d.day THEN 1 ELSE 0 END),0) created,"
                + "COALESCE(SUM(CASE WHEN t.status='FINISHED' AND DATE(t.actual_end_time)=d.day THEN 1 ELSE 0 END),0) finished "
                + "FROM (SELECT CURDATE() - INTERVAL 6 DAY + INTERVAL n DAY AS day FROM "
                + "(SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6) x) d "
                + "LEFT JOIN pro_task t ON DATE(t.start_time)=d.day OR DATE(t.actual_end_time)=d.day "
                + "GROUP BY d.day ORDER BY d.day");
        for (Map<String, Object> row : weekTrend)
        {
            row.put("created", number(row.get("created")));
            row.put("finished", number(row.get("finished")));
        }
        result.put("weekTrend", weekTrend);

        List<Map<String, Object>> monthExecution = jdbcTemplate.queryForList(
                "SELECT DATE_FORMAT(d.day,'%m-%d') dateLabel,"
                + "COALESCE(SUM(CASE WHEN t.status<>'REVOKED' AND DATE(t.start_time)=d.day THEN 1 ELSE 0 END),0) created,"
                + "COALESCE(SUM(CASE WHEN t.status='FINISHED' AND DATE(t.actual_end_time)=d.day THEN 1 ELSE 0 END),0) finished "
                + "FROM (SELECT DATE_FORMAT(CURDATE(),'%Y-%m-01') + INTERVAL n DAY AS day FROM "
                + "(SELECT 0 n UNION SELECT 1 UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 "
                + "UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 "
                + "UNION SELECT 14 UNION SELECT 15 UNION SELECT 16 UNION SELECT 17 UNION SELECT 18 UNION SELECT 19 UNION SELECT 20 "
                + "UNION SELECT 21 UNION SELECT 22 UNION SELECT 23 UNION SELECT 24 UNION SELECT 25 UNION SELECT 26 UNION SELECT 27 "
                + "UNION SELECT 28 UNION SELECT 29 UNION SELECT 30) x) d "
                + "LEFT JOIN pro_task t ON DATE(t.start_time)=d.day OR DATE(t.actual_end_time)=d.day "
                + "WHERE d.day<=CURDATE() "
                + "GROUP BY d.day ORDER BY d.day");
        for (Map<String, Object> row : monthExecution)
        {
            row.put("created", number(row.get("created")));
            row.put("finished", number(row.get("finished")));
        }
        result.put("monthExecution", monthExecution);

        List<Map<String, Object>> teamStats = jdbcTemplate.queryForList(
                "SELECT t.team_name teamName,"
                + "COUNT(*) total,"
                + "COALESCE(SUM(CASE WHEN t.status='FINISHED' THEN 1 ELSE 0 END),0) finished,"
                + "COALESCE(SUM(CASE WHEN t.status='IN_PROGRESS' THEN 1 ELSE 0 END),0) running "
                + "FROM pro_task t WHERE t.status<>'REVOKED' AND t.team_name IS NOT NULL AND t.team_name<>'' "
                + "GROUP BY t.team_name "
                + "ORDER BY total DESC LIMIT 10");
        for (Map<String, Object> row : teamStats)
        {
            row.put("total", number(row.get("total")));
            row.put("finished", number(row.get("finished")));
            row.put("running", number(row.get("running")));
        }
        result.put("teamStats", teamStats);
        return result;
    }

    public void deleteWorkstationTeam(Long recordId)
    {
        jdbcTemplate.update("DELETE FROM md_workstation_team WHERE record_id=?", recordId);
    }

    /** 返回当前人可见的生产任务：班组任务仅由班组长接收和执行。 */
    public List<Map<String, Object>> myExecutableTasks(Long userId)
    {
        return jdbcTemplate.queryForList(mobileTaskSelect() + " WHERE (t.assigned_user_id=? OR t.team_id IN "
                + "(SELECT team_id FROM cal_team WHERE leader_user_id=?)) "
                + "AND (t.status IN ('RELEASED','IN_PROGRESS') OR (t.status='FINISHED' AND t.executor_id=?)) "
                + "AND (t.executor_id IS NULL OR t.executor_id=?) "
                + "ORDER BY CASE t.status WHEN 'RELEASED' THEN 1 WHEN 'IN_PROGRESS' THEN 2 ELSE 3 END,t.end_time ASC,t.task_id DESC", userId, userId, userId, userId);
    }

    public Map<String, Object> myExecutableTask(Long taskId, Long userId)
    {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(mobileTaskSelect() + " WHERE t.task_id=? "
                + "AND (t.assigned_user_id=? OR t.team_id IN (SELECT team_id FROM cal_team WHERE leader_user_id=?)) "
                + "AND (t.executor_id IS NULL OR t.executor_id=?)", taskId, userId, userId, userId);
        if (list.isEmpty())
        {
            throw new IllegalArgumentException("该任务未派发给当前人员，或已由其他员工领取");
        }
        Map<String, Object> result = list.get(0);
        result.put("operationSteps", operationSteps(nullableLong(result.get("processId"))));
        result.put("executions", jdbcTemplate.queryForList("SELECT operation_type operationType,operator_nick operatorNick,operation_time operationTime,remark "
                + "FROM pro_task_execution WHERE task_id=? ORDER BY execution_id DESC", taskId));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> startTask(Long taskId, String remark)
    {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userService.selectUserById(userId);
        Map<String, Object> task = one("SELECT * FROM pro_task WHERE task_id=? FOR UPDATE", taskId);
        assertMobileAccess(task, userId);
        if (!STATUS_RELEASED.equals(string(task.get("status"))))
        {
            throw new IllegalArgumentException("当前任务不是待开工状态");
        }
        jdbcTemplate.update("UPDATE pro_task SET status='IN_PROGRESS',executor_id=?,executor_name=?,executor_nick=?,actual_start_time=NOW(),"
                        + "update_by=?,update_time=NOW() WHERE task_id=?",
                userId, user.getUserName(), user.getNickName(), user.getUserName(), taskId);
        insertExecution(taskId, "START", user, remark);
        Long planId = nullableLong(task.get("plan_id"));
        if (planId != null)
        {
            jdbcTemplate.update("UPDATE pro_production_plan SET status='IN_PROGRESS',update_by=?,update_time=NOW() "
                    + "WHERE plan_id=? AND status='RELEASED'", user.getUserName(), planId);
        }
        return myExecutableTask(taskId, userId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> finishTask(Long taskId, String remark)
    {
        Long userId = SecurityUtils.getUserId();
        SysUser user = userService.selectUserById(userId);
        Map<String, Object> task = one("SELECT * FROM pro_task WHERE task_id=? FOR UPDATE", taskId);
        assertMobileAccess(task, userId);
        if (!STATUS_IN_PROGRESS.equals(string(task.get("status"))) || !userId.equals(nullableLong(task.get("executor_id"))))
        {
            throw new IllegalArgumentException("只有实际开工的员工可以完工此任务");
        }
        jdbcTemplate.update("UPDATE pro_task SET status='FINISHED',actual_end_time=NOW(),"
                        + "actual_duration=GREATEST(1,TIMESTAMPDIFF(MINUTE,actual_start_time,NOW())),update_by=?,update_time=NOW() WHERE task_id=?",
                user.getUserName(), taskId);
        insertExecution(taskId, "REPORT", user, remark);
        finishPlanAndWorkorderIfReady(task, user.getUserName());
        releaseNextTask(task, user.getUserName());
        return one(mobileTaskSelect() + " WHERE t.task_id=?", taskId);
    }

    private void generateRouteTasks(Long planId, Map<String, Object> workorder, Map<String, Object> route, BigDecimal quantity,
            Timestamp plannedStart, Timestamp plannedEnd, String dispatchMode, Long teamId, Long userId, String username,
            int startProcessOrder, int endProcessOrder)
    {
        List<Map<String, Object>> routeProcesses = jdbcTemplate.queryForList("SELECT rp.record_id,rp.process_id,rp.process_code,rp.process_name,"
                + "rp.order_num,rp.default_pre_time,rp.default_suf_time,rp.color_code,"
                + "(SELECT w.workstation_id FROM md_workstation w WHERE w.process_id=rp.process_id AND w.enable_flag='Y' "
                + "ORDER BY w.workstation_id LIMIT 1) workstation_id "
                + "FROM pro_route_process rp WHERE rp.route_id=? AND rp.order_num>=? AND rp.order_num<=? ORDER BY rp.order_num,rp.record_id",
                route.get("route_id"), startProcessOrder, endProcessOrder);
        if (routeProcesses.isEmpty())
        {
            throw new IllegalArgumentException("所选工艺路线没有维护工序，无法生成执行任务");
        }
        for (Map<String, Object> process : routeProcesses)
        {
            if (process.get("workstation_id") == null)
            {
                throw new IllegalArgumentException("工序【" + process.get("process_name") + "】未配置启用的工作站，无法排程");
            }
        }
        long windowMinutes = Math.max(1, (plannedEnd.getTime() - plannedStart.getTime()) / 60000L);
        long unitMinutes = Math.max(1, windowMinutes / routeProcesses.size());
        LocalDateTime taskStart = plannedStart.toLocalDateTime();
        for (int index = 0; index < routeProcesses.size(); index++)
        {
            Map<String, Object> process = routeProcesses.get(index);
            Map<String, Object> workstation = one("SELECT workstation_id,workstation_code,workstation_name FROM md_workstation WHERE workstation_id=?",
                    longValue(process.get("workstation_id")));
            Assignment assignment = resolveAssignment(dispatchMode, teamId, userId, nullableLong(workstation.get("workstation_id")));
            long duration = Math.max(1, unitMinutes + optionalLong(process.get("default_pre_time"), 0L) + optionalLong(process.get("default_suf_time"), 0L));
            LocalDateTime taskEnd = taskStart.plusMinutes(duration);
            boolean first = index == 0;
            boolean predecessorReady = planId != null || predecessorWorkordersFinished(workorder);
            String taskStatus = first
                    ? (predecessorReady ? (assignment.isAssigned() ? STATUS_RELEASED : STATUS_PREPARE) : STATUS_WAITING)
                    : STATUS_WAITING;
            String taskCode = autoCodeUtil.genSerialCode(UserConstants.TASK_CODE, null);
            KeyHolder holder = new GeneratedKeyHolder();
            LocalDateTime currentStart = taskStart;
            LocalDateTime currentEnd = taskEnd;
            int sequence = optionalInt(process, "order_num", index + 1);
            jdbcTemplate.update(connection -> {
                String taskInsertSql = "INSERT INTO pro_task "
                                + "(task_code,task_name,plan_id,route_id,route_process_id,process_sequence,workorder_id,workorder_code,workorder_name,"
                                + "workstation_id,workstation_code,workstation_name,process_id,process_code,process_name,item_id,item_code,item_name,specification,"
                                + "unit_of_measure,quantity,quantity_produced,quantity_quanlify,quantity_unquanlify,quantity_changed,client_id,client_code,client_name,client_nick,"
                                + "start_time,duration,end_time,color_code,request_date,status,assign_type,team_id,team_code,team_name,assigned_user_id,assigned_user_name,"
                                + "assigned_user_nick,dispatch_time,remark,create_by,create_time) "
                                + "VALUES (" + String.join(",", Collections.nCopies(46, "?")) + ")";
                PreparedStatement statement = connection.prepareStatement(taskInsertSql, Statement.RETURN_GENERATED_KEYS);
                int p = 1;
                statement.setString(p++, taskCode);
                statement.setString(p++, string(process.get("process_name")) + " - " + string(workorder.get("product_name")));
                setNullableLong(statement, p++, planId);
                statement.setLong(p++, longValue(route.get("route_id")));
                statement.setLong(p++, longValue(process.get("record_id")));
                statement.setInt(p++, sequence);
                statement.setLong(p++, longValue(workorder.get("workorder_id")));
                statement.setString(p++, string(workorder.get("workorder_code")));
                statement.setString(p++, string(workorder.get("workorder_name")));
                statement.setLong(p++, longValue(workstation.get("workstation_id")));
                statement.setString(p++, string(workstation.get("workstation_code")));
                statement.setString(p++, string(workstation.get("workstation_name")));
                statement.setLong(p++, longValue(process.get("process_id")));
                statement.setString(p++, string(process.get("process_code")));
                statement.setString(p++, string(process.get("process_name")));
                statement.setLong(p++, longValue(workorder.get("product_id")));
                statement.setString(p++, string(workorder.get("product_code")));
                statement.setString(p++, string(workorder.get("product_name")));
                statement.setString(p++, string(workorder.get("product_spc")));
                statement.setString(p++, string(workorder.get("unit_of_measure")));
                statement.setBigDecimal(p++, quantity);
                statement.setBigDecimal(p++, BigDecimal.ZERO);
                statement.setBigDecimal(p++, BigDecimal.ZERO);
                statement.setBigDecimal(p++, BigDecimal.ZERO);
                statement.setBigDecimal(p++, BigDecimal.ZERO);
                setNullableLong(statement, p++, nullableLong(workorder.get("client_id")));
                statement.setString(p++, string(workorder.get("client_code")));
                statement.setString(p++, string(workorder.get("client_name")));
                statement.setString(p++, string(workorder.get("client_name")));
                statement.setTimestamp(p++, Timestamp.valueOf(currentStart));
                statement.setLong(p++, duration);
                statement.setTimestamp(p++, Timestamp.valueOf(currentEnd));
                statement.setString(p++, defaultText(string(process.get("color_code")), "#00AEF3"));
                statement.setTimestamp(p++, timestamp(workorder.get("request_date")));
                statement.setString(p++, taskStatus);
                statement.setString(p++, assignment.type);
                setNullableLong(statement, p++, assignment.teamId);
                statement.setString(p++, assignment.teamCode);
                statement.setString(p++, assignment.teamName);
                setNullableLong(statement, p++, assignment.userId);
                statement.setString(p++, assignment.userName);
                statement.setString(p++, assignment.userNick);
                if (assignment.isAssigned()) statement.setTimestamp(p++, new Timestamp(System.currentTimeMillis())); else statement.setNull(p++, java.sql.Types.TIMESTAMP);
                statement.setString(p++, "");
                statement.setString(p++, username);
                statement.setTimestamp(p++, new Timestamp(System.currentTimeMillis()));
                return statement;
            }, holder);
            Long taskId = holder.getKey().longValue();
            if (STATUS_RELEASED.equals(taskStatus))
            {
                notifyTaskRecipients(one("SELECT * FROM pro_task WHERE task_id=?", taskId), "生产任务已自动派发");
            }
            taskStart = taskEnd;
        }
    }

    private Assignment resolveAssignment(String assignType, Long teamId, Long userId, Long workstationId)
    {
        String type = defaultText(assignType, "MANUAL").toUpperCase();
        if ("AUTO".equals(type))
        {
            Map<String, Object> mapping = one("SELECT x.team_id,t.team_code,t.team_name,t.leader_user_id FROM md_workstation_team x "
                    + "JOIN cal_team t ON t.team_id=x.team_id WHERE x.workstation_id=? AND x.enable_flag='Y' "
                    + "ORDER BY x.priority,x.record_id LIMIT 1", workstationId);
            if (mapping == null)
            {
                return new Assignment("MANUAL");
            }
            if (nullableLong(mapping.get("leader_user_id")) == null)
            {
                throw new IllegalArgumentException("工作站默认班组未设置班组长，请先在班组设置中维护班组长");
            }
            return new Assignment("AUTO", longValue(mapping.get("team_id")), string(mapping.get("team_code")), string(mapping.get("team_name")), null, null, null);
        }
        if ("TEAM".equals(type))
        {
            Map<String, Object> team = one("SELECT team_id,team_code,team_name,leader_user_id FROM cal_team WHERE team_id=?", teamId);
            if (team == null)
            {
                throw new IllegalArgumentException("所选班组不存在");
            }
            if (nullableLong(team.get("leader_user_id")) == null)
            {
                throw new IllegalArgumentException("所选班组未设置班组长，请先在班组设置中维护班组长");
            }
            return new Assignment("TEAM", longValue(team.get("team_id")), string(team.get("team_code")), string(team.get("team_name")), null, null, null);
        }
        if ("USER".equals(type))
        {
            Map<String, Object> user = one("SELECT user_id,user_name,nick_name FROM sys_user WHERE user_id=? AND status='0' AND del_flag='0'", userId);
            if (user == null)
            {
                throw new IllegalArgumentException("所选责任人不存在或已停用");
            }
            return new Assignment("USER", null, null, null, longValue(user.get("user_id")), string(user.get("user_name")), string(user.get("nick_name")));
        }
        if ("MANUAL".equals(type))
        {
            return new Assignment("MANUAL");
        }
        throw new IllegalArgumentException("派工方式不正确");
    }

    private boolean predecessorFinished(Map<String, Object> task)
    {
        Long planId = nullableLong(task.get("plan_id"));
        if (planId == null)
        {
            Map<String, Object> workorder = one("SELECT * FROM pro_workorder WHERE workorder_id=?", task.get("workorder_id"));
            return workorder != null && predecessorWorkordersFinished(workorder);
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pro_task WHERE plan_id=? AND process_sequence<? AND status<>'FINISHED'",
                Integer.class, planId, optionalInt(task, "process_sequence", 0));
        return count == null || count == 0;
    }

    private void releaseNextTask(Map<String, Object> completedTask, String username)
    {
        Long planId = nullableLong(completedTask.get("plan_id"));
        if (planId == null)
        {
            Map<String, Object> workorder = one("SELECT * FROM pro_workorder WHERE workorder_id=?", completedTask.get("workorder_id"));
            if (workorder == null || !STATUS_FINISHED.equals(string(workorder.get("status"))))
            {
                return;
            }
            List<Map<String, Object>> candidates = jdbcTemplate.queryForList("SELECT t.* FROM pro_task t JOIN pro_workorder w ON w.workorder_id=t.workorder_id "
                    + "WHERE w.production_order_line_id=? AND w.route_id=? AND w.start_process_order>? AND t.status='WAITING' "
                    + "ORDER BY w.start_process_order,t.task_id", workorder.get("production_order_line_id"), workorder.get("route_id"),
                    workorder.get("start_process_order"));
            if (candidates.isEmpty())
            {
                return;
            }
            int nextProcessOrder = optionalInt(candidates.get(0), "process_sequence", Integer.MAX_VALUE);
            for (Map<String, Object> task : candidates)
            {
                if (optionalInt(task, "process_sequence", Integer.MAX_VALUE) != nextProcessOrder) break;
                String status = (task.get("team_id") != null || task.get("assigned_user_id") != null) ? STATUS_RELEASED : STATUS_PREPARE;
                jdbcTemplate.update("UPDATE pro_task SET status=?,update_by=?,update_time=NOW() WHERE task_id=?", status, username, task.get("task_id"));
                if (STATUS_RELEASED.equals(status))
                {
                    notifyTaskRecipients(one("SELECT * FROM pro_task WHERE task_id=?", task.get("task_id")), "上一工序已完成，请开始执行");
                }
            }
            return;
        }
        List<Map<String, Object>> next = jdbcTemplate.queryForList("SELECT * FROM pro_task WHERE plan_id=? AND process_sequence>? "
                + "AND status='WAITING' ORDER BY process_sequence,task_id LIMIT 1", planId, optionalInt(completedTask, "process_sequence", 0));
        if (next.isEmpty())
        {
            return;
        }
        Map<String, Object> task = next.get(0);
        String status = (task.get("team_id") != null || task.get("assigned_user_id") != null) ? STATUS_RELEASED : STATUS_PREPARE;
        jdbcTemplate.update("UPDATE pro_task SET status=?,update_by=?,update_time=NOW() WHERE task_id=?", status, username, task.get("task_id"));
        if (STATUS_RELEASED.equals(status))
        {
            notifyTaskRecipients(one("SELECT * FROM pro_task WHERE task_id=?", task.get("task_id")), "上一工序已完成，请开始执行");
        }
    }

    private void finishPlanAndWorkorderIfReady(Map<String, Object> task, String username)
    {
        Long planId = nullableLong(task.get("plan_id"));
        if (planId == null)
        {
            Long workorderId = nullableLong(task.get("workorder_id"));
            Integer unfinishedTasks = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pro_task WHERE workorder_id=? AND status<>'FINISHED'", Integer.class, workorderId);
            if (unfinishedTasks != null && unfinishedTasks == 0)
            {
                jdbcTemplate.update("UPDATE pro_workorder SET status='FINISHED',quantity_produced=quantity,update_by=?,update_time=NOW() WHERE workorder_id=?",
                        username, workorderId);
                Map<String, Object> workorder = one("SELECT * FROM pro_workorder WHERE workorder_id=?", workorderId);
                finishProductionOrderIfReady(nullableLong(workorder.get("production_order_id")), username);
            }
            return;
        }
        Integer notFinished = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pro_task WHERE plan_id=? AND status<>'FINISHED'", Integer.class, planId);
        if (notFinished != null && notFinished == 0)
        {
            jdbcTemplate.update("UPDATE pro_production_plan SET status='FINISHED',update_by=?,update_time=NOW() WHERE plan_id=?", username, planId);
            Map<String, Object> plan = one("SELECT * FROM pro_production_plan WHERE plan_id=?", planId);
            Long workorderId = nullableLong(plan.get("workorder_id"));
            Integer unfinishedPlan = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pro_production_plan WHERE workorder_id=? AND status<>'FINISHED'", Integer.class, workorderId);
            if (unfinishedPlan != null && unfinishedPlan == 0)
            {
                jdbcTemplate.update("UPDATE pro_workorder SET status='FINISHED',update_by=?,update_time=NOW() WHERE workorder_id=?", username, workorderId);
                finishProductionOrderIfReady(nullableLong(plan.get("production_order_id")), username);
            }
        }
    }

    /** 同一订单产品的后续工序必须等待所有前序工序工单完成。 */
    private boolean predecessorWorkordersFinished(Map<String, Object> workorder)
    {
        Long lineId = nullableLong(workorder.get("production_order_line_id"));
        Long routeId = nullableLong(workorder.get("route_id"));
        Integer processOrder = optionalInt(workorder.get("start_process_order"), 0);
        if (lineId == null || routeId == null || processOrder <= 1)
        {
            return true;
        }
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pro_workorder WHERE production_order_line_id=? AND route_id=? "
                + "AND start_process_order<? AND status NOT IN ('FINISHED','REVOKED')", Integer.class, lineId, routeId, processOrder);
        return count == null || count == 0;
    }

    private void finishProductionOrderIfReady(Long orderId, String username)
    {
        if (orderId == null)
        {
            return;
        }
        Integer openWorkorders = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM pro_workorder WHERE production_order_id=? "
                + "AND status NOT IN ('FINISHED','REVOKED')", Integer.class, orderId);
        if (openWorkorders != null && openWorkorders == 0)
        {
            jdbcTemplate.update("UPDATE pro_production_order SET status='FINISHED',update_by=?,update_time=NOW() WHERE production_order_id=?", username, orderId);
        }
    }

    private void assertMobileAccess(Map<String, Object> task, Long userId)
    {
        if (task == null)
        {
            throw new IllegalArgumentException("生产任务不存在");
        }
        boolean direct = userId.equals(nullableLong(task.get("assigned_user_id")));
        boolean teamLeader = false;
        Long teamId = nullableLong(task.get("team_id"));
        if (teamId != null)
        {
            teamLeader = exists("SELECT 1 FROM cal_team WHERE team_id=? AND leader_user_id=?", teamId, userId);
        }
        if (!direct && !teamLeader)
        {
            throw new IllegalArgumentException("该任务未派发给当前人员");
        }
        Long executor = nullableLong(task.get("executor_id"));
        if (executor != null && !executor.equals(userId))
        {
            throw new IllegalArgumentException("该班组任务已由其他员工开工");
        }
    }

    private void updateProductionOrderSplitStatus(Long productionOrderId, String username)
    {
        List<Map<String, Object>> lines = jdbcTemplate.queryForList("SELECT * FROM pro_production_order_line WHERE production_order_id=?", productionOrderId);
        boolean allSplit = !lines.isEmpty();
        boolean anySplit = false;
        for (Map<String, Object> line : lines)
        {
            Map<String, Object> route = one("SELECT route_id FROM pro_workorder WHERE production_order_line_id=? AND status<>'REVOKED' "
                    + "ORDER BY workorder_id LIMIT 1", line.get("production_order_line_id"));
            boolean lineComplete = route != null && !exists("SELECT 1 FROM pro_route_process rp WHERE rp.route_id=? AND NOT EXISTS "
                    + "(SELECT 1 FROM pro_workorder w WHERE w.production_order_line_id=? AND w.route_id=rp.route_id "
                    + "AND w.start_process_id=rp.process_id AND w.end_process_id=rp.process_id AND w.status<>'REVOKED')",
                    route == null ? 0L : route.get("route_id"), line.get("production_order_line_id"));
            boolean lineHasSplit = route != null;
            jdbcTemplate.update("UPDATE pro_production_order_line SET status=?,update_by=?,update_time=NOW() WHERE production_order_line_id=?",
                    lineComplete ? STATUS_ALL_SPLIT : (lineHasSplit ? "SPLIT" : STATUS_PREPARE), username, line.get("production_order_line_id"));
            allSplit = allSplit && lineComplete;
            anySplit = anySplit || lineHasSplit;
        }
        jdbcTemplate.update("UPDATE pro_production_order SET status=?,update_by=?,update_time=NOW() WHERE production_order_id=?",
                allSplit ? STATUS_ALL_SPLIT : (anySplit ? "SPLIT" : STATUS_PREPARE), username, productionOrderId);
    }

    /** 执行任务列表和详情共用的可追溯查询。 */
    private String taskSelect()
    {
        return "SELECT t.*,p.plan_code,p.plan_name,p.planned_start_time,p.planned_end_time,p.dispatch_mode,"
                + "w.production_order_id,w.product_code workorder_product_code,w.product_name workorder_product_name,"
                + "o.production_order_code,o.production_order_name,o.client_name production_client_name,"
                + "(SELECT COUNT(*) FROM pro_task_execution e WHERE e.task_id=t.task_id) execution_count "
                + "FROM pro_task t LEFT JOIN pro_production_plan p ON p.plan_id=t.plan_id "
                + "LEFT JOIN pro_workorder w ON w.workorder_id=t.workorder_id "
                + "LEFT JOIN pro_production_order o ON o.production_order_id=COALESCE(p.production_order_id,w.production_order_id)";
    }

    private String mobileTaskSelect()
    {
        return "SELECT t.task_id taskId,t.task_code taskCode,t.task_name taskName,t.status,t.process_id processId,t.workorder_code workorderCode,"
                + "t.item_code itemCode,t.item_name itemName,t.specification,t.quantity,t.unit_of_measure unitOfMeasure,"
                + "t.process_name processName,t.process_sequence processSequence,t.workstation_name workstationName,"
                + "t.team_name teamName,t.assigned_user_nick assignedUserNick,t.executor_nick executorNick,"
                + "t.start_time plannedStartTime,t.end_time plannedEndTime,t.duration requiredMinutes,"
                + "ROUND(COALESCE(t.duration,0)/60,1) requiredHours,t.actual_start_time actualStartTime,t.actual_end_time actualEndTime,"
                + "t.actual_duration actualDuration,p.plan_code planCode,p.plan_name planName,COALESCE(p.route_code,w.route_code) routeCode,COALESCE(p.route_name,w.route_name) routeName,"
                + "o.production_order_code productionOrderCode,o.production_order_name productionOrderName,o.request_date productionOrderDueDate "
                + "FROM pro_task t LEFT JOIN pro_production_plan p ON p.plan_id=t.plan_id "
                + "LEFT JOIN pro_workorder w ON w.workorder_id=t.workorder_id "
                + "LEFT JOIN pro_production_order o ON o.production_order_id=COALESCE(p.production_order_id,w.production_order_id) ";
    }

    private List<Map<String, Object>> operationSteps(Long processId)
    {
        if (processId == null)
        {
            return Collections.emptyList();
        }
        return jdbcTemplate.queryForList("SELECT content_id contentId,order_num orderNum,content_text contentText,device,material,doc_url docUrl "
                + "FROM pro_process_content WHERE process_id=? ORDER BY order_num,content_id", processId);
    }

    private void insertExecution(Long taskId, String operationType, SysUser user, String remark)
    {
        jdbcTemplate.update("INSERT INTO pro_task_execution(task_id,operation_type,operator_id,operator_name,operator_nick,operation_time,remark,create_by,create_time) "
                + "VALUES (?,?,?,?,?,NOW(),?,?,NOW())", taskId, operationType, user.getUserId(), user.getUserName(), user.getNickName(),
                defaultText(remark, ""), user.getUserName());
    }

    private void notifyTaskRecipients(Map<String, Object> task, String title)
    {
        if (task == null)
        {
            return;
        }
        List<Map<String, Object>> users = new ArrayList<>();
        Long userId = nullableLong(task.get("assigned_user_id"));
        if (userId != null)
        {
            users.addAll(jdbcTemplate.queryForList("SELECT user_id,user_name,nick_name FROM sys_user WHERE user_id=?", userId));
        }
        else if (task.get("team_id") != null)
        {
            users.addAll(jdbcTemplate.queryForList("SELECT u.user_id,u.user_name,u.nick_name FROM cal_team t "
                    + "JOIN sys_user u ON u.user_id=t.leader_user_id WHERE t.team_id=? AND u.status='0' AND u.del_flag='0'", task.get("team_id")));
        }
        for (Map<String, Object> recipient : users)
        {
            SysMessage message = new SysMessage();
            message.setMessageType("PRO_TASK");
            message.setMessageLevel("INFO");
            message.setMessageTitle(title);
            message.setMessageContent("任务 " + string(task.get("task_code")) + "：" + string(task.get("process_name"))
                    + " / " + string(task.get("item_name")) + "，计划完成：" + string(task.get("end_time")));
            message.setRecipientId(longValue(recipient.get("user_id")));
            message.setRecipientName(string(recipient.get("user_name")));
            message.setRecipientNick(string(recipient.get("nick_name")));
            message.setStatus("UNREAD");
            message.setDeletedFlag("N");
            message.setCallBack("/mobile/pro/execution/" + task.get("task_id"));
            message.setAttr1("生产执行");
            message.setCreateBy("system");
            messageService.insertSysMessage(message);
        }
    }

    private Map<String, Object> one(String sql, Object... args)
    {
        List<Map<String, Object>> list = jdbcTemplate.queryForList(sql, args);
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean exists(String sql, Object... args)
    {
        try
        {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM (" + sql + ") q", Integer.class, args);
            return count != null && count > 0;
        }
        catch (EmptyResultDataAccessException e)
        {
            return false;
        }
    }

    private static void appendLike(StringBuilder sql, List<Object> args, String column, String value)
    {
        if (!isBlank(value))
        {
            appendWhere(sql);
            sql.append(' ').append(column).append(" LIKE ?");
            args.add("%" + value.trim() + "%");
        }
    }

    private static void appendEquals(StringBuilder sql, List<Object> args, String column, String value)
    {
        if (!isBlank(value))
        {
            appendWhere(sql);
            sql.append(' ').append(column).append("=?");
            args.add(value.trim());
        }
    }

    private static void appendWhere(StringBuilder sql)
    {
        String content = sql.toString().toUpperCase();
        boolean hasWhere = content.contains(" WHERE ") || content.startsWith("WHERE ");
        sql.append(hasWhere ? " AND " : " WHERE ");
    }

    private static Long requiredLong(Map<String, Object> body, String key, String message)
    {
        Long value = optionalLong(body, key);
        if (value == null)
        {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static Long optionalLong(Map<String, Object> body, String key)
    {
        return nullableLong(body.get(key));
    }

    private static Long optionalLong(Object value, Long defaultValue)
    {
        Long result = nullableLong(value);
        return result == null ? defaultValue : result;
    }

    private static Long nullableLong(Object value)
    {
        if (value == null || "".equals(String.valueOf(value).trim())) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        return Long.valueOf(String.valueOf(value));
    }

    private static long longValue(Object value)
    {
        Long result = nullableLong(value);
        if (result == null) throw new IllegalArgumentException("数据缺少必填ID");
        return result;
    }

    /** COUNT(*) 聚合值转成数字，便于前端图表直接使用。 */
    private static Object number(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        if (value == null)
        {
            return 0L;
        }
        try
        {
            return Long.valueOf(String.valueOf(value));
        }
        catch (NumberFormatException e)
        {
            return value;
        }
    }

    /**
     * 手动分页执行查询：对查询 SQL 追加 LIMIT，并用 countSql 统计总条数。
     * countSql 必须携带与查询 SQL 相同的 WHERE 条件，返回 {rows, total}。
     */
    private Map<String, Object> paged(String querySql, String countSql, List<Object> args, Map<String, Object> query, String orderBy)
    {
        int pageNum = Math.max(1, optionalInt(query, "pageNum", 1));
        int pageSize = Math.min(200, Math.max(1, optionalInt(query, "pageSize", 10)));
        long total = 0L;
        try
        {
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, args.toArray());
            total = count == null ? 0L : count.longValue();
        }
        catch (Exception ignored)
        {
            total = 0L;
        }
        String pageSql = querySql + orderBy + " LIMIT " + ((pageNum - 1) * pageSize) + "," + pageSize;
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(pageSql, args.toArray());
        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("total", total);
        return result;
    }

    private static BigDecimal requiredPositive(Map<String, Object> body, String key, String message)
    {
        BigDecimal result = optionalDecimal(body, key, BigDecimal.ZERO);
        if (result.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(message);
        return result;
    }

    /** 生产订单和拆单按件管理，不允许录入小数数量。 */
    private static BigDecimal requiredPositiveInteger(Map<String, Object> body, String key, String message)
    {
        BigDecimal result = requiredPositive(body, key, message);
        if (!isPositiveInteger(result)) throw new IllegalArgumentException(message);
        return result.setScale(0, RoundingMode.UNNECESSARY);
    }

    private static boolean isPositiveInteger(BigDecimal value)
    {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 && value.stripTrailingZeros().scale() <= 0;
    }

    private static BigDecimal optionalDecimal(Map<String, Object> body, String key, BigDecimal defaultValue)
    {
        Object value = body.get(key);
        if (value == null || "".equals(String.valueOf(value).trim())) return defaultValue;
        return decimal(value);
    }

    private static BigDecimal decimal(Object value)
    {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(String.valueOf(value));
    }

    private static int optionalInt(Map<String, Object> body, String key, int defaultValue)
    {
        return optionalInt(body.get(key), defaultValue);
    }

    private static int optionalInt(Object value, int defaultValue)
    {
        if (value == null || "".equals(String.valueOf(value).trim())) return defaultValue;
        return ((Number) (value instanceof Number ? value : Integer.valueOf(String.valueOf(value)))).intValue();
    }

    private static String text(Map<String, Object> body, String key)
    {
        return body == null ? null : string(body.get(key));
    }

    private static String string(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private static String defaultText(String value, String defaultValue)
    {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private static Timestamp timestamp(Object value)
    {
        if (value == null || "".equals(String.valueOf(value).trim())) return null;
        if (value instanceof Timestamp) return (Timestamp) value;
        if (value instanceof java.util.Date) return new Timestamp(((java.util.Date) value).getTime());
        String text = String.valueOf(value).trim().replace('T', ' ');
        if (text.length() == 10) text += " 00:00:00";
        if (text.length() == 16) text += ":00";
        return Timestamp.valueOf(LocalDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    private static void setNullableLong(PreparedStatement statement, int index, Long value) throws java.sql.SQLException
    {
        if (value == null) statement.setNull(index, java.sql.Types.BIGINT); else statement.setLong(index, value);
    }

    private static class Assignment
    {
        private final String type;
        private final Long teamId;
        private final String teamCode;
        private final String teamName;
        private final Long userId;
        private final String userName;
        private final String userNick;

        Assignment(String type)
        {
            this(type, null, null, null, null, null, null);
        }

        Assignment(String type, Long teamId, String teamCode, String teamName, Long userId, String userName, String userNick)
        {
            this.type = type;
            this.teamId = teamId;
            this.teamCode = teamCode;
            this.teamName = teamName;
            this.userId = userId;
            this.userName = userName;
            this.userNick = userNick;
        }

        boolean isAssigned()
        {
            return teamId != null || userId != null;
        }
    }
}
