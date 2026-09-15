<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="工艺路线编号" prop="routeCode">
        <el-input
          v-model="queryParams.routeCode"
          placeholder="请输入工艺路线编号"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="工艺路线名称" prop="routeName">
        <el-input
          v-model="queryParams.routeName"
          placeholder="请输入工艺路线名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="是否启用" prop="enableFlag">
        <el-input
          v-model="queryParams.enableFlag"
          placeholder="请输入是否启用"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" size="mini" @click="handleQuery">搜索</el-button>
        <el-button icon="el-icon-refresh" size="mini" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="el-icon-plus"
          size="mini"
          @click="handleAdd"
          v-hasPermi="['mes:pro:proroute:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="el-icon-edit"
          size="mini"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['mes:pro:proroute:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="el-icon-delete"
          size="mini"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['mes:pro:proroute:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="el-icon-download"
          size="mini"
          @click="handleExport"
          v-hasPermi="['mes:pro:proroute:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="prorouteList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="工艺路线编号" align="center" prop="routeCode" >
        <template slot-scope="scope">
          <el-button
            type="text"
            @click="handleView(scope.row)"
            v-hasPermi="['mes:pro:proroute:query']"
          >{{scope.row.routeCode}}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="工艺路线名称" align="center" prop="routeName" />
      <el-table-column label="工艺路线说明" align="center" prop="routeDesc" />
      <el-table-column label="是否启用" align="center" prop="enableFlag">
        <template slot-scope="scope">
          <el-switch
            v-model="scope.row.enableFlag"
            active-text="是"
            inactive-text="否"
            active-value="Y"
            inactive-value="N"
            @change="handleEnableFlagChange(scope.row)"
          ></el-switch>
        </template>
      </el-table-column>
      <el-table-column label="备注" align="center" prop="remark" />
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            v-if="scope.row.enableFlag == 'N'"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['mes:pro:proroute:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            v-if="scope.row.enableFlag == 'N'"
            @click="handleDelete(scope.row)"
            v-hasPermi="['mes:pro:proroute:remove']"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <pagination
      v-show="total>0"
      :total="total"
      :page.sync="queryParams.pageNum"
      :limit.sync="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改工艺路线对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="1080px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="8">
            <el-form-item label="编号" prop="routeCode">
              <el-input v-model="form.routeCode" placeholder="请输入工艺路线编号" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item  label-width="80">
              <el-switch v-model="autoGenFlag"
                  active-color="#13ce66"
                  active-text="自动生成"
                  @change="handleAutoGenChange(autoGenFlag)" v-if="optType != 'view'">
              </el-switch>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="名称" prop="routeName">
              <el-input v-model="form.routeName" placeholder="请输入工艺路线名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="说明" prop="routeDesc">
              <el-input v-model="form.routeDesc" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div v-if="form.routeId == null && optType !== 'view'" class="draft-processes">
        <el-divider content-position="center">组成工序</el-divider>
        <el-form label-width="100px">
          <el-form-item label="选择工序">
            <el-select v-model="draftProcessIds" multiple filterable clearable placeholder="可直接选择组成工序，选择顺序即工艺顺序" style="width:100%">
              <el-option v-for="item in processOptions" :key="item.processId" :label="`${item.processCode} - ${item.processName}`" :value="item.processId" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-alert title="保存时会先创建工艺路线，再按选择顺序保存工序组成；最后一道工序默认设为关键工序，可在后续修改中调整。" type="info" :closable="false" />
      </div>
      <el-tabs type="border-card" v-if="form.routeId != null">
        <el-tab-pane label="组成工序">
          <Routeprocess v-if="form.routeId !=null" :optType="optType" :routeId="form.routeId"></Routeprocess>
        </el-tab-pane>
        <el-tab-pane label="关联产品">
          <Routeproduct v-if="form.routeId !=null" :optType="optType" :routeId="form.routeId"></Routeproduct>
        </el-tab-pane>
      </el-tabs>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="submitForm" v-if="optType !='view'">保 存</el-button>
        <el-button @click="cancel">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { listProroute, getProroute, delProroute, addProroute, updateProroute } from "@/api/mes/pro/proroute";
import Routeprocess from "./routeprocess";
import Routeproduct from "./product";
import {genCode} from "@/api/system/autocode/rule"
import { listAllProcess } from "@/api/mes/pro/process";
import { addRouteprocess } from "@/api/mes/pro/routeprocess";
export default {
  name: "Proroute",
  dicts: ['sys_yes_no'],
  components: {Routeprocess,Routeproduct},
  data() {
    return {
      //自动生成编码
      autoGenFlag:false,
      optType: undefined,
      // 遮罩层
      loading: true,
      // 选中数组
      ids: [],
      // 非单个禁用
      single: true,
      // 非多个禁用
      multiple: true,
      // 显示搜索条件
      showSearch: true,
      // 总条数
      total: 0,
      // 工艺路线表格数据
      prorouteList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        routeCode: null,
        routeName: null,
        routeDesc: null,
        enableFlag: null,
      },
      // 表单参数
      form: {},
      // 新建工艺路线时直接选择的组成工序
      draftProcessIds: [],
      processOptions: [],
      // 表单校验
      rules: {
        routeCode: [
          { required: true, message: "工艺路线编号不能为空", trigger: "blur" },
          { max: 64, message: "字段过长", trigger: "blur" }
        ],
        routeName: [
          { required: true, message: "工艺路线名称不能为空", trigger: "blur" },
          { max: 100, message: "字段过长", trigger: "blur" }
        ],
        enableFlag: [
          { required: true, message: "是否启用不能为空", trigger: "blur" }
        ],
        remark: [
          { max: 250, message: '长度必须小于250个字符', trigger: 'blur' }
        ],
        routeDesc: [
          { max: 250, message: '字段过长', trigger: 'blur' }
        ]
      }
    };
  },
  created() {
    this.getList();
    this.getProcessOptions();
  },
  methods: {
    /** 查询工艺路线列表 */
    getList() {
      this.loading = true;
      listProroute(this.queryParams).then(response => {
        this.prorouteList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 取消按钮
    cancel() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        routeId: null,
        routeCode: null,
        routeName: null,
        routeDesc: null,
        enableFlag: 'N',
        remark: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null
      };
      this.autoGenFlag = false;
      this.draftProcessIds = [];
      this.resetForm("form");
    },
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
    // 多选框选中数据
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.routeId)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加工艺路线";
      this.optType = "add";
    },
    // 查询明细按钮操作
    handleView(row){
      this.reset();
      const routeId = row.routeId || this.ids;
      getProroute(routeId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "查看工艺线路信息";
        this.optType = "view";
      });
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const routeId = row.routeId || this.ids
      getProroute(routeId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改工艺路线";
        this.optType = "edit";
      });
    },
    /** 提交按钮 */
    submitForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.routeId != null) {
            updateProroute(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addProroute(this.form).then(response => {
              return this.saveDraftRouteProcesses(response.data);
            }).then(() => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            }).catch(error => {
              this.$modal.msgError((error && error.message) || '工艺路线或组成工序保存失败');
            });
          }
        }
      });
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const routeIds = row.routeId || this.ids;
      this.$modal.confirm('是否确认删除工艺路线编号为"' + routeIds + '"的数据项？').then(function() {
        return delProroute(routeIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /**
     * 启用状态变更
     * @param row
     */
     handleEnableFlagChange(row){
      let text = row.enableFlag === "N" ? "停用" : "启用";
      this.$modal.confirm('确认要"' + text + '""' + row.routeName + '"工艺吗？').then(function() {
        return updateProroute(row);
      }).then(() => {
        this.$modal.msgSuccess(text + "成功");
      }).catch(function() {
        row.enableFlag = row.enableFlag === "N" ? "Y" : "N";
      });
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('pro/proroute/export', {
        ...this.queryParams
      }, `proroute_${new Date().getTime()}.xlsx`)
    },
    //自动生成编码
    handleAutoGenChange(autoGenFlag){
      if(autoGenFlag){
        genCode('ROUTE_CODE').then(response =>{
          this.form.routeCode = response;
        });
      }else{
        this.form.routeCode = null;
      }
    },
    getProcessOptions() {
      listAllProcess().then(response => {
        this.processOptions = response.data || [];
      });
    },
    saveDraftRouteProcesses(routeId) {
      if (!routeId || !this.draftProcessIds.length) return Promise.resolve();
      const ids = this.draftProcessIds.slice();
      return ids.reduce((chain, processId, index) => chain.then(() => addRouteprocess({
        routeId,
        processId,
        orderNum: index + 1,
        linkType: 'FS',
        defaultPreTime: 0,
        defaultSufTime: 0,
        colorCode: '#00AEF3',
        keyFlag: index === ids.length - 1 ? 'Y' : 'N',
        isCheck: 'N',
        remark: ''
      })), Promise.resolve());
    }
  }
};
</script>
