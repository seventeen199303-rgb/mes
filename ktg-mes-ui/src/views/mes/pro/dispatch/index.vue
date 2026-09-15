<template>
  <div class="app-container">
    <el-alert title="手动派工可将任务派给一个班组或一名责任人；仅当前工序任务会立即释放到 APP，后续工序将在前序完工后自动释放。已派工未开工的任务可撤回，已开工任务请由操作人在 APP 端转交。" type="info" :closable="false" show-icon class="mb8" />
    <el-form :model="queryParams" size="small" :inline="true" class="mb8">
      <el-form-item label="任务编号"><el-input v-model="queryParams.taskCode" clearable placeholder="任务编号" style="width:140px" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="计划"><el-input v-model="queryParams.planCode" clearable placeholder="计划编号" style="width:130px" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="工单"><el-input v-model="queryParams.workorderCode" clearable placeholder="工单编号" style="width:130px" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="产品"><el-input v-model="queryParams.itemName" clearable placeholder="产品名称" style="width:130px" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="工序"><el-input v-model="queryParams.processName" clearable placeholder="工序名称" style="width:120px" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="工作站"><el-input v-model="queryParams.workstationName" clearable placeholder="工作站" style="width:120px" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="派工对象"><el-input v-model="queryParams.dispatcher" clearable placeholder="责任人/班组" style="width:130px" @keyup.enter.native="load" /></el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="queryParams.status" size="mini" @change="load">
          <el-radio-button v-for="opt in statusOptions" :key="opt.value" :label="opt.value">{{ opt.label }}</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item><el-button type="primary" icon="el-icon-search" size="mini" @click="load">搜索</el-button><el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button></el-form-item>
    </el-form>
    <el-row class="mb8"><el-button icon="el-icon-refresh" size="mini" @click="load">刷新</el-button></el-row>
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="任务编号" prop="task_code" min-width="140" />
      <el-table-column label="计划" prop="plan_code" min-width="130" />
      <el-table-column label="工单" prop="workorder_code" min-width="130" />
      <el-table-column label="产品" prop="item_name" min-width="145" show-overflow-tooltip />
      <el-table-column label="工序" prop="process_name" min-width="100" />
      <el-table-column label="工作站" prop="workstation_name" min-width="120" />
      <el-table-column label="计划工时" prop="required_hours" width="80" align="right"><template slot-scope="scope">{{ scope.row.required_hours }}h</template></el-table-column>
      <el-table-column label="派工对象" min-width="110"><template slot-scope="scope">{{ scope.row.assigned_user_nick || scope.row.team_name || '未派工' }}</template></el-table-column>
      <el-table-column label="计划完成时间" prop="end_time" width="140" align="center"><template slot-scope="scope">{{ parseTime(scope.row.end_time) || '--' }}</template></el-table-column>
      <el-table-column label="状态" prop="status" width="95" align="center"><template slot-scope="scope"><el-tag :type="statusType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template slot-scope="scope">
          <el-button v-if="scope.row.status === 'PREPARE'" type="text" size="mini" @click="openDispatch(scope.row)">派工</el-button>
          <el-button v-else-if="scope.row.status === 'RELEASED' || scope.row.status === 'WAITING'" type="text" size="mini" class="revoke-btn" @click="revoke(scope.row)">撤回</el-button>
          <span v-else class="muted-operation">{{ scope.row.status === 'IN_PROGRESS' ? '已开工' : '—' }}</span>
        </template>
      </el-table-column>
    </el-table>
    <pagination v-show="total>0" :total="total" :page.sync="queryParams.pageNum" :limit.sync="queryParams.pageSize" @pagination="load" />
    <el-dialog :title="`任务派工：${task.task_code || ''}`" :visible.sync="dialog" width="520px" append-to-body>
      <el-form :model="form" label-width="105px"><el-form-item label="派工方式"><el-radio-group v-model="form.assignType"><el-radio label="TEAM">派给班组</el-radio><el-radio label="USER">指定责任人</el-radio><el-radio label="AUTO">按工作站默认班组</el-radio></el-radio-group></el-form-item>
        <el-form-item v-if="form.assignType === 'TEAM'" label="执行班组"><el-select v-model="form.teamId" filterable style="width:100%"><el-option v-for="item in options.teams" :key="item.team_id" :label="`${item.team_code} - ${item.team_name}`" :value="item.team_id" /></el-select></el-form-item>
        <el-form-item v-if="form.assignType === 'USER'" label="责任人"><el-select v-model="form.userId" filterable style="width:100%"><el-option v-for="item in options.users" :key="item.user_id" :label="`${item.user_name} - ${item.nick_name}`" :value="item.user_id" /></el-select></el-form-item>
        <el-form-item label="计划完成时间"><el-date-picker v-model="form.plannedEndTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" clearable placeholder="保持计划不变" style="width:100%" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="dialog=false">取 消</el-button><el-button type="primary" @click="submit">确认派工</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { dispatchTask, revokeDispatchTask, executionOptions, listDispatchTasks } from '@/api/mes/pro/productionexecution'
export default {
  name: 'ProductionDispatch',
  data() {
    return {
      loading: false,
      dialog: false,
      rows: [],
      total: 0,
      queryParams: { status: '', pageNum: 1, pageSize: 10 },
      statusOptions: [
        { value: '', label: '全部' },
        { value: 'PREPARE', label: '待派工' },
        { value: 'WAITING', label: '等待前序' },
        { value: 'RELEASED', label: '待开工' },
        { value: 'IN_PROGRESS', label: '执行中' },
        { value: 'FINISHED', label: '已完成' }
      ],
      task: {},
      options: { teams: [], users: [] },
      form: {}
    }
  },
  created() { this.load(); executionOptions().then(res => { this.options = res.data || this.options }) },
  methods: {
    load() {
      this.loading = true
      const params = {}
      Object.keys(this.queryParams).forEach(key => {
        const value = this.queryParams[key]
        if (value !== null && value !== undefined && value !== '') params[key] = value
      })
      listDispatchTasks(params).then(res => { this.rows = res.rows || []; this.total = res.total || 0 }).finally(() => { this.loading = false })
    },
    resetQuery() { this.queryParams = { status: '', pageNum: 1, pageSize: 10 }; this.load() },
    openDispatch(row) {
      this.task = row
      this.form = { assignType: row.assigned_user_id ? 'USER' : row.team_id ? 'TEAM' : 'TEAM', teamId: row.team_id || null, userId: row.assigned_user_id || null, plannedEndTime: row.end_time || null }
      this.dialog = true
    },
    revoke(row) {
      this.$modal.confirm(`确认撤回任务「${row.task_code}」的派工吗？撤回后任务回到待派工状态。`).then(() => {
        revokeDispatchTask(row.task_id).then(() => { this.$modal.msgSuccess('任务已撤回'); this.load() })
      }).catch(() => {})
    },
    submit() {
      if (this.form.assignType === 'TEAM' && !this.form.teamId) return this.$modal.msgError('请选择执行班组')
      if (this.form.assignType === 'USER' && !this.form.userId) return this.$modal.msgError('请选择责任人')
      const payload = { assignType: this.form.assignType, teamId: this.form.teamId, userId: this.form.userId }
      if (this.form.plannedEndTime) payload.plannedEndTime = this.form.plannedEndTime
      dispatchTask(this.task.task_id, payload).then(() => { this.$modal.msgSuccess('任务已派发'); this.dialog = false; this.load() })
    },
    statusText(value) { return ({ PREPARE: '待派工', WAITING: '等待前序', RELEASED: '待开工', IN_PROGRESS: '执行中', FINISHED: '已完成' })[value] || value },
    statusType(value) { return ({ PREPARE: 'info', WAITING: 'info', RELEASED: 'warning', IN_PROGRESS: 'primary', FINISHED: 'success' })[value] || '' }
  }
}
</script>

<style scoped>
.revoke-btn { color: #f56c6c; }
.muted-operation { color: #c0c4cc; font-size: 12px; }
</style>
