<template>
  <div class="app-container">
    <el-alert title="生产计划负责工序工单的排程。计划创建后，请在生产工单中按数量分解执行任务；自动派工依赖“工作站班组绑定”。" type="warning" :closable="false" show-icon class="mb8" />
    <el-row :gutter="10" class="mb8"><el-col :span="1.5"><el-button type="primary" icon="el-icon-plus" size="mini" @click="openAdd">新建排程计划</el-button></el-col><el-col :span="1.5"><el-button icon="el-icon-refresh" size="mini" @click="load">刷新</el-button></el-col></el-row>
    <el-table v-loading="loading" :data="rows" :header-cell-style="centerStyle" :cell-style="centerStyle">
      <el-table-column label="计划编号" prop="plan_code" min-width="150" />
      <el-table-column label="生产工单" prop="workorder_code" min-width="145" />
      <el-table-column label="工艺路线" prop="route_name" min-width="160" show-overflow-tooltip />
      <el-table-column label="计划数量" prop="quantity" width="95"><template slot-scope="scope">{{ formatQuantity(scope.row.quantity) }}</template></el-table-column>
      <el-table-column label="计划开始" prop="planned_start_time" width="160"><template slot-scope="scope">{{ parseTime(scope.row.planned_start_time) }}</template></el-table-column>
      <el-table-column label="计划完成" prop="planned_end_time" width="160"><template slot-scope="scope">{{ parseTime(scope.row.planned_end_time) }}</template></el-table-column>
      <el-table-column label="派工方式" prop="dispatch_mode" width="100"><template slot-scope="scope">{{ dispatchText(scope.row.dispatch_mode) }}</template></el-table-column>
      <el-table-column label="任务进度" min-width="100"><template slot-scope="scope">{{ scope.row.finished_task_count || 0 }} / {{ scope.row.task_count || 0 }}</template></el-table-column>
      <el-table-column label="状态" prop="status" width="100"><template slot-scope="scope"><el-tag :type="scope.row.status === 'FINISHED' ? 'success' : scope.row.status === 'IN_PROGRESS' ? 'warning' : 'info'">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
    </el-table>

    <el-dialog title="新建生产计划" :visible.sync="dialog" width="760px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="生产工单" prop="workorderId"><el-select v-model="form.workorderId" filterable placeholder="选择已拆解工单" style="width:100%" @change="fillWorkorder"><el-option v-for="item in options.workorders" :key="item.workorder_id" :label="`${item.workorder_code} - ${item.product_name}`" :value="item.workorder_id" /></el-select></el-form-item>
        <el-form-item label="工艺路线" prop="routeId"><el-select v-model="form.routeId" filterable placeholder="选择工艺路线" style="width:100%"><el-option v-for="item in availableRoutes" :key="item.route_id" :label="`${item.route_code} - ${item.route_name}`" :value="item.route_id" /></el-select></el-form-item>
        <el-row><el-col :span="12"><el-form-item label="计划数量" prop="quantity"><el-input-number v-model="form.quantity" :min="1" :step="1" :precision="0" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item label="默认派工方式" prop="dispatchMode"><el-select v-model="form.dispatchMode" style="width:100%"><el-option label="自动按工作站班组派工" value="AUTO" /><el-option label="指定班组" value="TEAM" /><el-option label="指定责任人" value="USER" /><el-option label="暂不派工" value="MANUAL" /></el-select></el-form-item></el-col></el-row>
        <el-form-item v-if="form.dispatchMode === 'TEAM'" label="执行班组" prop="teamId"><el-select v-model="form.teamId" filterable style="width:100%"><el-option v-for="item in options.teams" :key="item.team_id" :label="`${item.team_code} - ${item.team_name}`" :value="item.team_id" /></el-select></el-form-item>
        <el-form-item v-if="form.dispatchMode === 'USER'" label="责任人" prop="userId"><el-select v-model="form.userId" filterable style="width:100%"><el-option v-for="item in options.users" :key="item.user_id" :label="`${item.user_name} - ${item.nick_name}`" :value="item.user_id" /></el-select></el-form-item>
        <el-row><el-col :span="12"><el-form-item label="计划开始" prop="plannedStartTime"><el-date-picker v-model="form.plannedStartTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item label="计划完成" prop="plannedEndTime"><el-date-picker v-model="form.plannedEndTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width:100%" /></el-form-item></el-col></el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="dialog=false">取 消</el-button><el-button type="primary" @click="submit">创建生产计划</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { addProductionPlan, executionOptions, listProductionPlans } from '@/api/mes/pro/productionexecution'

export default {
  name: 'ProductionPlan',
  data() { return { loading: false, dialog: false, rows: [], options: { workorders: [], routes: [], teams: [], users: [] }, form: {}, rules: { workorderId: [{ required: true, message: '请选择生产工单', trigger: 'change' }], routeId: [{ required: true, message: '请选择工艺路线', trigger: 'change' }], quantity: [{ required: true, message: '请输入计划数量', trigger: 'blur' }], plannedStartTime: [{ required: true, message: '请选择计划开始时间', trigger: 'change' }], plannedEndTime: [{ required: true, message: '请选择计划完成时间', trigger: 'change' }] } } },
  computed: {
    availableRoutes() {
      const order = (this.options.workorders || []).find(item => item.workorder_id === this.form.workorderId)
      if (!order) return this.options.routes || []
      if (order.route_id) return (this.options.routes || []).filter(item => item.route_id === order.route_id)
      const routeIds = (this.options.routeProducts || []).filter(item => item.item_id === order.product_id).map(item => item.route_id)
      return (this.options.routes || []).filter(item => routeIds.includes(item.route_id))
    }
  },
  created() { this.load(); this.loadOptions() },
  methods: {
    load() { this.loading = true; listProductionPlans().then(res => { this.rows = res.data || [] }).finally(() => { this.loading = false }) },
    loadOptions() { executionOptions().then(res => { this.options = res.data || this.options }) },
    openAdd() { const start = new Date(); const end = new Date(start.getTime() + 8 * 3600 * 1000); this.form = { dispatchMode: 'AUTO', plannedStartTime: this.parseTime(start, '{y}-{m}-{d} {h}:{i}:{s}'), plannedEndTime: this.parseTime(end, '{y}-{m}-{d} {h}:{i}:{s}'), quantity: 1, teamId: null, userId: null, remark: '' }; this.dialog = true; this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate()) },
    fillWorkorder(id) { const order = (this.options.workorders || []).find(item => item.workorder_id === id); if (!order) return; this.$set(this.form, 'quantity', Number(order.quantity || 1)); const routes = order.route_id ? (this.options.routes || []).filter(item => item.route_id === order.route_id) : (this.options.routes || []).filter(item => (this.options.routeProducts || []).some(relation => relation.route_id === item.route_id && relation.item_id === order.product_id)); this.$set(this.form, 'routeId', routes.length ? routes[0].route_id : null); if (order.plan_end_time) this.$set(this.form, 'plannedEndTime', this.parseTime(order.plan_end_time, '{y}-{m}-{d} {h}:{i}:{s}')) },
    submit() { this.$refs.form.validate(valid => { if (!valid) return; if (!Number.isInteger(Number(this.form.quantity)) || Number(this.form.quantity) <= 0) return this.$modal.msgError('计划数量必须为正整数'); if (this.form.dispatchMode === 'TEAM' && !this.form.teamId) return this.$modal.msgError('请选择执行班组'); if (this.form.dispatchMode === 'USER' && !this.form.userId) return this.$modal.msgError('请选择责任人'); addProductionPlan(this.form).then(() => { this.$modal.msgSuccess('生产计划已创建，请到生产工单执行工单分解'); this.dialog = false; this.load(); this.loadOptions() }) }) },
    centerStyle() { return { textAlign: 'center' } },
    formatQuantity(value) { const number = Number(value); return Number.isFinite(number) ? String(Math.round(number)) : value },
    dispatchText(value) { return ({ AUTO: '自动班组', TEAM: '指定班组', USER: '指定人员', MANUAL: '手动派工' })[value] || value },
    statusText(value) { return ({ PREPARE: '待派工', RELEASED: '已释放', IN_PROGRESS: '执行中', FINISHED: '已完成' })[value] || value }
  }
}
</script>
