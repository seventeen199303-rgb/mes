<template>
  <div class="app-container">
    <el-alert title="生产工单仅由生产订单按工序拆解生成。工单分解直接创建待派工的执行任务；数量与任务时间在此维护，随后到“任务派工”选择班组或责任人。" type="info" :closable="false" show-icon class="mb8" />
    <el-form :model="queryParams" size="small" :inline="true" class="mb8">
      <el-form-item label="工单编号"><el-input v-model="queryParams.workorderCode" clearable placeholder="工单编号" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="生产订单"><el-input v-model="queryParams.productionOrderCode" clearable placeholder="订单编号" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="产品"><el-input v-model="queryParams.productName" clearable placeholder="产品名称" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="工序"><el-input v-model="queryParams.processName" clearable placeholder="工序名称" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="queryParams.status" clearable placeholder="全部" style="width: 120px"><el-option label="待分解/待派工" value="CONFIRMED" /><el-option label="已撤回" value="REVOKED" /><el-option label="已完成" value="FINISHED" /></el-select></el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" @click="load">搜索</el-button><el-button icon="el-icon-refresh" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row class="mb8"><el-button icon="el-icon-refresh" size="mini" @click="load">刷新</el-button></el-row>

    <el-table v-loading="loading" :data="rows" :header-cell-style="centerStyle" :cell-style="centerStyle">
      <el-table-column label="工单编号" prop="workorder_code" width="155" />
      <el-table-column label="生产订单" prop="production_order_code" width="150" />
      <el-table-column label="工单名称" prop="workorder_name" min-width="200" show-overflow-tooltip />
      <el-table-column label="产品编号" prop="product_code" width="130" />
      <el-table-column label="产品名称" prop="product_name" min-width="150" show-overflow-tooltip />
      <el-table-column label="工艺路线" prop="route_name" min-width="150" show-overflow-tooltip />
      <el-table-column label="工序" prop="start_process_name" width="120" />
      <el-table-column label="工单数量" width="100"><template slot-scope="scope">{{ formatQuantity(scope.row.quantity) }}</template></el-table-column>
      <el-table-column label="计划开始" width="160"><template slot-scope="scope">{{ parseTime(scope.row.plan_start_time) }}</template></el-table-column>
      <el-table-column label="计划完成" width="160"><template slot-scope="scope">{{ parseTime(scope.row.plan_end_time) }}</template></el-table-column>
      <el-table-column label="任务进度" width="100"><template slot-scope="scope">{{ scope.row.finished_task_count || 0 }} / {{ scope.row.task_count || 0 }}</template></el-table-column>
      <el-table-column label="状态" width="110"><template slot-scope="scope"><el-tag :type="statusType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" fixed="right" width="285"><template slot-scope="scope"><el-button type="text" size="mini" :disabled="!canDecompose(scope.row)" @click="openDecompose(scope.row)">工单分解</el-button><el-button type="text" size="mini" :disabled="!canRevoke(scope.row)" @click="revoke(scope.row)">撤回</el-button><el-button type="text" size="mini" @click="openDetail(scope.row)">查看</el-button><el-button type="text" size="mini" :disabled="!canForceFinish(scope.row)" @click="forceFinish(scope.row)">强制完成</el-button></template></el-table-column>
    </el-table>
    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="load" />

    <el-dialog :title="`工单分解：${current.workorder_code || ''}`" :visible.sync="decomposeDialog" width="1060px" append-to-body>
      <el-alert title="每条执行任务都需填写数量、计划开始和完成时间。生成后任务状态为“待派工”，请在任务派工页面指定班组或责任人。" type="info" :closable="false" class="mb12" />
      <el-form :model="decomposeForm" label-width="105px" inline>
        <el-form-item label="工单数量"><span>{{ formatQuantity(current.quantity) }}</span></el-form-item>
        <el-form-item label="已分解数量"><span>{{ formatQuantity(current.task_quantity) }}</span></el-form-item>
        <el-form-item label="本次可分解"><b class="remaining">{{ formatQuantity(remainingQuantity) }}</b></el-form-item>
        <el-form-item label="每任务数量"><el-input-number v-model="autoQuantity" :min="1" :precision="0" style="width: 130px" /></el-form-item>
        <el-form-item><el-button type="success" plain icon="el-icon-magic-stick" size="mini" @click="autoDecompose">按数量自动拆分</el-button></el-form-item>
      </el-form>
      <el-table :data="decomposeForm.tasks" border size="mini" :header-cell-style="centerStyle" :cell-style="centerStyle">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="执行任务数量" min-width="170"><template slot-scope="scope"><el-input-number v-model="scope.row.quantity" :min="1" :precision="0" style="width: 140px" /></template></el-table-column>
        <el-table-column label="计划开始时间" min-width="220"><template slot-scope="scope"><el-date-picker v-model="scope.row.plannedStartTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" /></template></el-table-column>
        <el-table-column label="计划完成时间" min-width="220"><template slot-scope="scope"><el-date-picker v-model="scope.row.plannedEndTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%" /></template></el-table-column>
        <el-table-column label="操作" width="90" align="center"><template slot-scope="scope"><el-button type="text" :disabled="decomposeForm.tasks.length === 1" @click="decomposeForm.tasks.splice(scope.$index, 1)">删除</el-button></template></el-table-column>
      </el-table>
      <div class="toolbar"><el-button type="primary" plain size="mini" icon="el-icon-plus" @click="addTask">增加任务</el-button></div>
      <div slot="footer"><el-button @click="decomposeDialog=false">取 消</el-button><el-button type="primary" :loading="submitting" @click="submitDecompose">确认分解</el-button></div>
    </el-dialog>

    <el-dialog :title="`工单详情：${detail.workorder ? detail.workorder.workorder_code : ''}`" :visible.sync="detailDialog" width="1080px" append-to-body>
      <el-descriptions v-if="detail.workorder" :column="3" border size="small">
        <el-descriptions-item label="生产订单">{{ detail.workorder.production_order_code || '-' }}</el-descriptions-item><el-descriptions-item label="产品">{{ detail.workorder.product_code }} - {{ detail.workorder.product_name }}</el-descriptions-item><el-descriptions-item label="工序">{{ detail.workorder.start_process_name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工单数量">{{ formatQuantity(detail.workorder.quantity) }}</el-descriptions-item><el-descriptions-item label="计划开始">{{ parseTime(detail.workorder.plan_start_time) }}</el-descriptions-item><el-descriptions-item label="计划完成">{{ parseTime(detail.workorder.plan_end_time) }}</el-descriptions-item>
        <el-descriptions-item label="任务进度" :span="3">{{ (detail.taskProgress || {}).finished_task_count || 0 }} / {{ (detail.taskProgress || {}).task_count || 0 }}</el-descriptions-item>
      </el-descriptions>
      <h4>执行任务列表</h4>
      <el-table :data="detail.tasks || []" border size="mini" max-height="300" :header-cell-style="centerStyle" :cell-style="centerStyle"><el-table-column label="任务编号" prop="task_code" min-width="150" /><el-table-column label="任务数量" width="100"><template slot-scope="scope">{{ formatQuantity(scope.row.quantity) }}</template></el-table-column><el-table-column label="计划时间" min-width="290"><template slot-scope="scope">{{ parseTime(scope.row.start_time) }} 至 {{ parseTime(scope.row.end_time) }}</template></el-table-column><el-table-column label="执行人/班组" min-width="150"><template slot-scope="scope">{{ scope.row.executor_nick || scope.row.assigned_user_nick || scope.row.team_name || '待派工' }}</template></el-table-column><el-table-column label="状态" width="110"><template slot-scope="scope">{{ taskStatusText(scope.row.status) }}</template></el-table-column></el-table>
      <div slot="footer"><el-button @click="detailDialog=false">关 闭</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { decomposeProductionWorkorder, forceFinishProductionWorkorder, getProductionWorkorder, listProductionWorkorders, revokeProductionWorkorder } from '@/api/mes/pro/productionexecution'

export default {
  name: 'ProductionWorkorder',
  data() { return { loading: false, submitting: false, rows: [], total: 0, queryParams: { pageNum: 1, pageSize: 10 }, decomposeDialog: false, detailDialog: false, current: {}, detail: {}, autoQuantity: 1, decomposeForm: { tasks: [] } } },
  computed: { remainingQuantity() { return Math.max(0, Math.round(Number(this.current.quantity || 0)) - Math.round(Number(this.current.task_quantity || 0))) } },
  created() { this.load(); window.addEventListener('mes:workorder-refresh', this.load) },
  beforeDestroy() { window.removeEventListener('mes:workorder-refresh', this.load) },
  methods: {
    centerStyle() { return { textAlign: 'center' } },
    load() { this.loading = true; listProductionWorkorders(this.queryParams).then(r => { this.rows = r.rows || []; this.total = r.total || 0 }).finally(() => { this.loading = false }) },
    resetQuery() { this.queryParams = { pageNum: 1, pageSize: 10 }; this.load() },
    formatQuantity(v) { const n = Number(v); return Number.isFinite(n) ? String(Math.round(n)) : v },
    statusText(s) { return ({ PREPARE: '待确认', CONFIRMED: '待任务/执行中', RELEASED: '已释放', IN_PROGRESS: '执行中', FINISHED: '已完成', REVOKED: '已撤回' })[s] || s },
    statusType(s) { return ({ PREPARE: 'info', CONFIRMED: 'warning', RELEASED: 'primary', IN_PROGRESS: 'primary', FINISHED: 'success', REVOKED: 'danger' })[s] || '' },
    taskStatusText(s) { return ({ PREPARE: '待派工', WAITING: '等待前序', RELEASED: '待开工', IN_PROGRESS: '执行中', FINISHED: '已完成', REVOKED: '已撤回' })[s] || s },
    canDecompose(row) { return !['REVOKED', 'FINISHED'].includes(row.status) && Math.round(Number(row.task_quantity || 0)) < Math.round(Number(row.quantity || 0)) },
    canRevoke(row) { return !['REVOKED', 'FINISHED'].includes(row.status) },
    canForceFinish(row) { return !['REVOKED', 'FINISHED'].includes(row.status) },
    taskWindow() { const start = this.current.plan_start_time ? this.parseTime(this.current.plan_start_time, '{y}-{m}-{d} {h}:{i}:{s}') : this.parseTime(new Date(), '{y}-{m}-{d} {h}:{i}:{s}'); const end = this.current.plan_end_time ? this.parseTime(this.current.plan_end_time, '{y}-{m}-{d} {h}:{i}:{s}') : this.parseTime(new Date(Date.now() + 8 * 3600 * 1000), '{y}-{m}-{d} {h}:{i}:{s}'); return { start, end } },
    addTask() { const window = this.taskWindow(); this.decomposeForm.tasks.push({ quantity: 1, plannedStartTime: window.start, plannedEndTime: window.end }) },
    openDecompose(row) { this.current = row; if (this.remainingQuantity <= 0) return this.$modal.msgWarning('该工单数量已全部分解为执行任务'); const window = this.taskWindow(); this.decomposeForm = { tasks: [{ quantity: this.remainingQuantity, plannedStartTime: window.start, plannedEndTime: window.end }] }; this.autoQuantity = 1; this.decomposeDialog = true },
    async openDetail(row) { await this.fetchDetail(row); this.detailDialog = true },
    async fetchDetail(row) { const r = await getProductionWorkorder(row.workorder_id); this.detail = r.data || {} },
    autoDecompose() { const qty = Number(this.autoQuantity); if (!Number.isInteger(qty) || qty <= 0) return this.$modal.msgWarning('每任务数量必须为正整数'); const quantities = []; for (let rest = this.remainingQuantity; rest > 0; rest -= qty) quantities.push(Math.min(rest, qty)); const window = this.taskWindow(); const start = new Date(window.start.replace(/-/g, '/')).getTime(); const end = new Date(window.end.replace(/-/g, '/')).getTime(); const segment = Math.max(1, Math.floor((end - start) / quantities.length)); this.decomposeForm.tasks = quantities.map((quantity, index) => ({ quantity, plannedStartTime: this.parseTime(new Date(start + segment * index), '{y}-{m}-{d} {h}:{i}:{s}'), plannedEndTime: this.parseTime(new Date(index === quantities.length - 1 ? end : start + segment * (index + 1)), '{y}-{m}-{d} {h}:{i}:{s}') })) },
    submitDecompose() { const tasks = this.decomposeForm.tasks || []; if (!tasks.length) return this.$modal.msgWarning('请至少设置一条执行任务'); let total = 0; for (let i = 0; i < tasks.length; i += 1) { const task = tasks[i]; if (!Number.isInteger(Number(task.quantity)) || Number(task.quantity) <= 0) return this.$modal.msgWarning(`第 ${i + 1} 条任务数量必须为正整数`); if (!task.plannedStartTime || !task.plannedEndTime || new Date(task.plannedEndTime).getTime() <= new Date(task.plannedStartTime).getTime()) return this.$modal.msgWarning(`第 ${i + 1} 条任务请填写有效计划时间`); total += Number(task.quantity) } if (total > this.remainingQuantity) return this.$modal.msgWarning('本次任务数量合计不能超过工单剩余数量'); this.submitting = true; decomposeProductionWorkorder(this.current.workorder_id, { tasks }).then(() => { this.$modal.msgSuccess('执行任务已生成，请到“任务派工”派发执行单位或责任人'); this.decomposeDialog = false; this.load() }).finally(() => { this.submitting = false }) },
    revoke(row) { this.$modal.confirm(`确认将工单 ${row.workorder_code} 撤回到订单拆解前吗？未开工任务将同步撤回。`).then(() => revokeProductionWorkorder(row.workorder_id)).then(() => { this.$modal.msgSuccess('工单已撤回'); this.load() }).catch(() => {}) },
    forceFinish(row) { this.$modal.confirm(`确认强制完成工单 ${row.workorder_code} 吗？其下未完成任务将同步强制完成。`).then(() => forceFinishProductionWorkorder(row.workorder_id)).then(() => { this.$modal.msgSuccess('工单及任务已强制完成'); this.load() }).catch(() => {}) }
  }
}
</script>

<style scoped>.mb12 { margin-bottom: 12px; }.toolbar { margin-top: 12px; display: flex; gap: 10px; }.remaining { color: #e67e22; }</style>
