<template>
  <div class="app-container">
    <el-alert title="此配置是自动派工的基础：每个工作站可绑定一个或多个默认班组，计划按工序匹配工作站后会取优先级最高的启用班组。" type="warning" :closable="false" show-icon class="mb8" />
    <el-row :gutter="10" class="mb8"><el-col :span="1.5"><el-button type="primary" icon="el-icon-plus" size="mini" @click="openAdd">新增绑定</el-button></el-col><el-col :span="1.5"><el-button icon="el-icon-refresh" size="mini" @click="load">刷新</el-button></el-col></el-row>
    <el-table v-loading="loading" :data="rows">
      <el-table-column label="工作站编码" prop="workstation_code" min-width="130" />
      <el-table-column label="工作站名称" prop="workstation_name" min-width="160" />
      <el-table-column label="默认班组编码" prop="team_code" min-width="130" />
      <el-table-column label="默认班组" prop="team_name" min-width="160" />
      <el-table-column label="优先级" prop="priority" width="90" align="center" />
      <el-table-column label="启用" prop="enable_flag" width="80"><template slot-scope="scope"><el-tag :type="scope.row.enable_flag === 'Y' ? 'success' : 'info'">{{ scope.row.enable_flag === 'Y' ? '启用' : '停用' }}</el-tag></template></el-table-column>
      <el-table-column label="备注" prop="remark" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="145" fixed="right"><template slot-scope="scope"><el-button type="text" size="mini" @click="openEdit(scope.row)">修改</el-button><el-button type="text" size="mini" @click="remove(scope.row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-dialog :title="form.recordId ? '修改工作站班组绑定' : '新增工作站班组绑定'" :visible.sync="dialog" width="570px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="105px">
        <el-form-item label="工作站" prop="workstationId"><el-select v-model="form.workstationId" filterable style="width:100%"><el-option v-for="item in options.workstations" :key="item.workstation_id" :label="`${item.workstation_code} - ${item.workstation_name}`" :value="item.workstation_id" /></el-select></el-form-item>
        <el-form-item label="默认班组" prop="teamId"><el-select v-model="form.teamId" filterable style="width:100%"><el-option v-for="item in options.teams" :key="item.team_id" :label="`${item.team_code} - ${item.team_name}`" :value="item.team_id" /></el-select></el-form-item>
        <el-row><el-col :span="12"><el-form-item label="优先级"><el-input-number v-model="form.priority" :min="1" style="width:100%" /></el-form-item></el-col><el-col :span="12"><el-form-item label="是否启用"><el-switch v-model="form.enableFlag" active-value="Y" inactive-value="N" active-text="启用" inactive-text="停用" /></el-form-item></el-col></el-row>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" /></el-form-item>
      </el-form>
      <div slot="footer"><el-button @click="dialog=false">取 消</el-button><el-button type="primary" @click="submit">确 定</el-button></div>
    </el-dialog>
  </div>
</template>

<script>
import { addWorkstationTeam, delWorkstationTeam, executionOptions, listWorkstationTeams, updateWorkstationTeam } from '@/api/mes/pro/productionexecution'
export default {
  name: 'WorkstationTeam',
  data() { return { loading: false, dialog: false, rows: [], options: { workstations: [], teams: [] }, form: {}, rules: { workstationId: [{ required: true, message: '请选择工作站', trigger: 'change' }], teamId: [{ required: true, message: '请选择班组', trigger: 'change' }] } } },
  created() { this.load(); executionOptions().then(res => { this.options = res.data || this.options }) },
  methods: {
    load() { this.loading = true; listWorkstationTeams().then(res => { this.rows = res.data || [] }).finally(() => { this.loading = false }) },
    openAdd() { this.form = { priority: 1, enableFlag: 'Y', remark: '' }; this.dialog = true; this.$nextTick(() => this.$refs.form && this.$refs.form.clearValidate()) },
    openEdit(row) { this.form = { recordId: row.record_id, workstationId: row.workstation_id, teamId: row.team_id, priority: row.priority, enableFlag: row.enable_flag, remark: row.remark }; this.dialog = true },
    submit() { this.$refs.form.validate(valid => { if (!valid) return; const save = this.form.recordId ? updateWorkstationTeam : addWorkstationTeam; save(this.form).then(() => { this.$modal.msgSuccess('工作站默认班组已保存'); this.dialog = false; this.load() }) }) },
    remove(row) { this.$modal.confirm(`确认删除工作站【${row.workstation_name}】与班组【${row.team_name}】的绑定吗？`).then(() => delWorkstationTeam(row.record_id)).then(() => { this.$modal.msgSuccess('已删除'); this.load() }).catch(() => {}) }
  }
}
</script>
