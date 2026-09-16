<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch" label-width="100px">
      <el-form-item label="出库单编号" prop="salseCode">
        <el-input
          v-model="queryParams.salseCode"
          placeholder="请输入出库单编号"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="销售订单编号" prop="soCode">
        <el-input
          v-model="queryParams.soCode"
          placeholder="请输入销售订单编号"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户名称" prop="clientName">
        <el-input
          v-model="queryParams.clientName"
          placeholder="请输入客户名称"
          clearable
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="出库日期" prop="salseDate">
        <el-date-picker clearable
          v-model="queryParams.salseDate"
          type="date"
          value-format="yyyy-MM-dd"
          placeholder="请选择出库日期">
        </el-date-picker>
      </el-form-item>
      <el-form-item label="单据状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择单据状态" clearable>
          <el-option
            v-for="dict in dict.type.mes_order_status"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
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
          v-hasPermi="['mes:wm:productsalse:add']"
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
          v-hasPermi="['mes:wm:productsalse:edit']"
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
          v-hasPermi="['mes:wm:productsalse:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar :showSearch.sync="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="productsalesList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="出库单编号" width="150px" align="center" prop="salseCode" >
        <template slot-scope="scope">
          <el-button
            type="text"
            @click="handleView(scope.row)"
            v-hasPermi="['mes:wm:productsalse:query']"
          >{{scope.row.salseCode}}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="出库单名称" width="220px" align="center" prop="salseName" :show-overflow-tooltip="true" />
      <el-table-column label="出货检验单号" width="150px" align="center" prop="oqcCode" />
      <el-table-column label="销售订单编号" width="150px" align="center" prop="soCode" />
      <el-table-column label="客户编码" align="center" prop="clientCode" />
      <el-table-column label="客户名称" align="center" prop="clientName" />
      <el-table-column label="仓库" width="180px" align="center" prop="warehouseName" :show-overflow-tooltip="true" />
      <el-table-column label="库区" width="160px" align="center" prop="locationName" :show-overflow-tooltip="true" />
      <el-table-column label="库位" width="180px" align="center" prop="areaName" :show-overflow-tooltip="true" />
      <el-table-column label="出库日期" align="center" prop="salseDate" width="120">
        <template slot-scope="scope">
          <span>{{ parseTime(scope.row.salseDate, '{y}-{m}-{d}') }}</span>
        </template>
      </el-table-column>
      <el-table-column label="单据状态" align="center" prop="status">
        <template slot-scope="scope">
          <dict-tag :options="dict.type.mes_product_sales_status" :value="scope.row.status"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120px" align="center" class-name="small-padding fixed-width">
        <template slot-scope="scope">
          <el-button
            size="mini"
            type="text"
            icon="el-icon-edit"
            @click="handleUpdate(scope.row)"
            v-if="scope.row.status == 'PREPARE'"
            v-hasPermi="['mes:wm:productsalse:edit']"
          >修改</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-delete"
            @click="handleDelete(scope.row)"
            v-if="scope.row.status == 'PREPARE'"
            v-hasPermi="['mes:wm:productsalse:remove']"
          >删除</el-button>
          <el-button
            size="mini"
            type="text"
            icon="el-icon-shopping-cart-full"
            v-if="scope.row.status =='UNSTOCK'"
            @click="handleStocking(scope.row)"
            v-hasPermi="['mes:wm:productsalse:edit']"
          >执行拣货</el-button>   
          <el-button
            size="mini"
            type="text"
            icon="el-icon-truck"
            v-if="scope.row.status =='UNSHIPPING'"
            @click="handleShipping(scope.row)"
            v-hasPermi="['mes:wm:productsalse:edit']"
          >填写运单</el-button>             
          <el-button
            size="mini"
            type="text"
            icon="el-icon-video-play"
            @click="handleExecute(scope.row)"
            v-if="scope.row.status == 'UNEXECUTE'"
            v-hasPermi="['mes:wm:productsalse:edit']"
          >执行出库</el-button>

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

    <!-- 添加或修改销售出库单对话框 -->
    <el-dialog :title="title" :visible.sync="open" width="960px" append-to-body>
      <el-form ref="form" :model="form" :rules="rules" label-width="100px">
        <!-- 草稿状态下可修改的部分 -->
        <div v-if="form.status =='PREPARE'">
        <el-row>
          <el-col :span="8">
            <el-form-item label="出库单编号" prop="salseCode">
              <el-input v-model="form.salseCode" placeholder="请输入出库单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item  label-width="80">
              <el-switch v-model="autoGenFlag"
                  active-color="#13ce66"
                  active-text="自动生成"
                  @change="handleAutoGenChange(autoGenFlag)" v-if="optType != 'view' && form.status =='PREPARE'">
              </el-switch>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出库单名称" prop="salseName">
              <el-input v-model="form.salseName" placeholder="请输入出库单名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="8">
            <el-form-item label="出货检验单号" prop="oqcCode">
              <el-input v-model="form.oqcCode" placeholder="请输入出货检验单号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销售订单编号" prop="soCode">
              <el-input v-model="form.soCode" placeholder="请输入销售订单编号" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出库日期" prop="salseDate">
              <el-date-picker clearable
                v-model="form.salseDate"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择出库日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="8">
            <el-form-item label="客户编码" prop="clientCode">
              <el-input v-model="form.clientCode" placeholder="请输入客户编码" >
                <el-button slot="append" @click="handleSelectClient" icon="el-icon-search"></el-button>
              </el-input>
              <ClientSelect ref="clientSelect" @onSelected="onClientSelected" > </ClientSelect>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户名称" prop="clientName">
              <el-input v-model="form.clientName" readonly="readonly"/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户简称" prop="clientNick">
              <el-input v-model="form.clientNick" readonly="readonly"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库区" prop="locationName">
              <el-input v-model="form.locationName" placeholder="请输入库区名称" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位" prop="areaName">
              <el-input v-model="form.areaName" placeholder="请输入库位名称" />
            </el-form-item>
          </el-col>
        </el-row>
        </div>
        <div v-else>
          <el-row>
          <el-col :span="8">
            <el-form-item label="出库单编号" prop="salseCode">
              <el-input v-model="form.salseCode" readonly="readonly" />
            </el-form-item>
          </el-col>
          <el-col :span="4">

          </el-col>
          <el-col :span="12">
            <el-form-item label="出库单名称" prop="salseName">
              <el-input v-model="form.salseName" readonly="readonly" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="8">
            <el-form-item label="出货检验单号" prop="oqcCode">
              <el-input v-model="form.oqcCode" readonly="readonly" >
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="销售订单编号" prop="soCode">
              <el-input v-model="form.soCode" readonly="readonly" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="出库日期" disabled prop="salseDate">
              <el-date-picker clearable
                v-model="form.salseDate"
                type="date"
                value-format="yyyy-MM-dd"
                placeholder="请选择出库日期">
              </el-date-picker>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="8">
            <el-form-item label="客户编码" prop="clientCode">
              <el-input v-model="form.clientCode" readonly="readonly" >
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户名称" prop="clientName">
              <el-input v-model="form.clientName" readonly="readonly"/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="客户简称" prop="clientNick">
              <el-input v-model="form.clientNick" readonly="readonly"/>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="8">
            <el-form-item label="仓库" prop="warehouseName">
              <el-input v-model="form.warehouseName" readonly="readonly"/>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库区" prop="locationName">
              <el-input v-model="form.locationName" readonly="readonly" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="库位" prop="areaName">
              <el-input v-model="form.areaName" readonly="readonly" />
            </el-form-item>
          </el-col>
        </el-row>
        </div>

        <el-row>
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" type="textarea" placeholder="请输入内容" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <el-divider v-if="form.salseId !=null" content-position="center">物料信息</el-divider>
        <el-card shadow="always" v-if="form.salseId !=null && form.status =='PREPARE' " class="box-card">
          <Productsalesline ref="line" :salseId="form.salseId" :optType="optType"></Productsalesline>
        </el-card>
        <el-card shadow="always" v-if="form.salseId !=null && form.status !='PREPARE' " class="box-card">
          <ProductsalesDetail ref="line" :salseId="form.salseId" :optType="optType"></ProductsalesDetail>
        </el-card>
        <div slot="footer" class="dialog-footer">
          <el-button type="primary" @click="saveForm" v-if="(form.status =='PREPARE' || form.status =='UNSHIPPING')&& optType !='view' ">保 存</el-button>
          <el-button type="warning" @click="submitToStock" v-if="form.status =='PREPARE' && form.salseId !=null && optType !='view' ">提 交</el-button>
          <el-button type="warning" @click="submitToShipping" v-if="form.status =='UNSTOCK' && form.salseId !=null && optType !='view' ">提 交</el-button>
          <el-button type="warning" @click="submitToExecute" v-if="form.status =='UNSHIPPING' && form.salseId !=null && optType !='view' ">提 交</el-button>
          <el-button type="danger" @click="cancel"  v-if="form.status !='PREPARE' && form.status !='FINISHED' && optType !='view' " >取 消</el-button>
          <el-button @click="close">关 闭</el-button>
        </div>
    </el-dialog>
  </div>
</template>

<script>
import { listProductsales, getProductsales, delProductsales, addProductsales, updateProductsales, checkQuantity, execute } from "@/api/mes/wm/productsales";
import Productsalesline from "./line.vue"
import ProductsalesDetail from "./detail.vue"
import ClientSelect from "@/components/clientSelect/single.vue";
import {genCode} from "@/api/system/autocode/rule"
export default {
  name: "Productsales",
  dicts: ['mes_product_sales_status'],
  components: {Productsalesline,ProductsalesDetail,ClientSelect},
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
      // 销售出库单表格数据
      productsalesList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        salseCode: null,
        salseName: null,
        oqcId: null,
        oqcCode: null,
        soCode: null,
        clientId: null,
        clientCode: null,
        clientName: null,
        clientNick: null,
        salseDate: null,
        status: null,
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
        salseCode: [
          { required: true, message: "出库单编号不能为空", trigger: "blur" },
          { max: 64, message: "字段过长", trigger: "blur" }
        ],
        salseName: [
          { required: true, message: "出库单名称不能为空", trigger: "blur" },
          { max: 100, message: "字段过长", trigger: "blur" }
        ],
        clientCode: [
          { required: true, message: "请指定客户", trigger: "blur" }
        ],
        salseDate: [
          { required: true, message: "请选择出库日期", trigger: "blur" }
        ],
        remark: [
          { max: 250, message: '长度必须小于250个字符', trigger: 'blur' }
        ]
      }
    };
  },
  created() {
    this.getList();
  },
  methods: {
    /** 查询销售出库单列表 */
    getList() {
      this.loading = true;
      listProductsales(this.queryParams).then(response => {
        this.productsalesList = response.rows;
        this.total = response.total;
        this.loading = false;
      });
    },
    // 关闭按钮
    close() {
      this.open = false;
      this.reset();
    },
    // 表单重置
    reset() {
      this.form = {
        salseId: null,
        salseCode: null,
        salseName: null,
        oqcId: null,
        oqcCode: null,
        soCode: null,
        clientId: null,
        clientCode: null,
        clientName: null,
        clientNick: null,
        warehouseId: null,
        warehouseCode: null,
        warehouseName: null,
        locationId: null,
        locationCode: null,
        locationName: null,
        areaId: null,
        areaCode: null,
        areaName: null,
        salseDate: new Date(),
        status: "PREPARE",
        remark: null,
        attr1: null,
        attr2: null,
        attr3: null,
        attr4: null,
        createBy: null,
        createTime: null,
        updateBy: null,
        updateTime: null
      };
      this.autoGenFlag = false;
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
      this.ids = selection.map(item => item.salseId)
      this.single = selection.length!==1
      this.multiple = !selection.length
    },
    // 查询明细按钮操作
    handleView(row){
      this.reset();
      const salseId = row.salseId
      getProductsales(salseId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "查看出库单信息";
        this.optType = "view";
      });
    },
    /** 新增按钮操作 */
    handleAdd() {
      this.reset();
      this.open = true;
      this.title = "添加销售出库单";
      this.optType = "add";
    },
    /** 修改按钮操作 */
    handleUpdate(row) {
      this.reset();
      const salseId = row.salseId || this.ids
      getProductsales(salseId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "修改销售出库单";
        this.optType = "edit";
      });
    },

    /**
     * 拣货按钮操作
     */
     handleStocking(row){
      this.reset();
      const salseId = row.salseId || this.ids
      getProductsales(salseId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "出库单拣货";
        this.optType = "edit";
      });
    },

    /**
     * 填写运单信息按钮操作
     */
     handleShipping(row){
      this.reset();
      const salseId = row.salseId || this.ids
      getProductsales(salseId).then(response => {
        this.form = response.data;
        this.open = true;
        this.title = "出库单填写运单信息";
        this.optType = "shipping";
      });
    },

    /** 保存按钮 */
    saveForm() {
      this.$refs["form"].validate(valid => {
        if (valid) {
          if (this.form.salseId != null) {
            updateProductsales(this.form).then(response => {
              this.$modal.msgSuccess("修改成功");
              this.open = false;
              this.getList();
            });
          } else {
            addProductsales(this.form).then(response => {
              this.$modal.msgSuccess("新增成功");
              this.open = false;
              this.getList();
            });
          }
        }
      });
    },

    //提交按钮(提交到待拣货状态)
    submitToStock(){
      this.$refs["form"].validate(valid => {
        if (valid) {
          this.form.status = 'UNSTOCK';
          updateProductsales(this.form).then(response => {
            this.$modal.msgSuccess("提交成功");
            this.open = false;
            this.getList();
          } 
         ).catch(() => {
            this.form.status = 'PREPARE';
         });;          
        }
      });
    },

    submitToShipping(){
      let that = this;
      checkQuantity(this.form.salseId).then( response =>{
        if(response.data){
            that.form.status = 'UNSHIPPING';
            updateProductsales(that.form).then(response => {
              that.$modal.msgSuccess("提交成功");
              that.open = false;
              that.getList();
            } 
            ).catch(() => {
              that.form.status = 'UNSTOCK';
            });    
          }else{
            this.$modal.msgError("拣货数量与出库数量不一致!");
          }
        }
      );   
    },

    //提交按钮(提交到待执行出库状态)
    submitToExecute(){
      this.$refs["form"].validate(valid => {
        if (valid) {
          this.form.status = 'UNEXECUTE';
          updateProductsales(this.form).then(response => {
            this.$modal.msgSuccess("提交成功");
            this.open = false;
            this.getList();
          } 
         ).catch(() => {
            this.form.status = 'UNSHIPPING';
         });;          
        }
      });
    },

    //取消
    cancel(){
      let that = this;
      this.$modal.confirm('确认撤销出库单？').then(function() {        
        const oldStatus = that.form.status;
        that.form.status = 'CANCELED';
        updateProductsales(that.form).then(response => {
            that.$modal.msgSuccess("撤销成功");
            that.open = false;
            that.getList();
          } 
          ).catch(() => {
            that.form.status = oldStatus;
          });
          return true;
      }).catch(() => {});
    },
    /** 删除按钮操作 */
    handleDelete(row) {
      const salseIds = row.salseId || this.ids;
      this.$modal.confirm('是否确认删除销售出库单编号为"' + salseIds + '"的数据项？').then(function() {
        return delProductsales(salseIds);
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("删除成功");
      }).catch(() => {});
    },
    /** 导出按钮操作 */
    handleExport() {
      this.download('wm/productsales/export', {
        ...this.queryParams
      }, `productsales_${new Date().getTime()}.xlsx`)
    },
    //执行出库
    handleExecute(row){
      const salseIds = row.salseId || this.ids;
      this.$modal.confirm('确认执行出库？').then(function() {
        return execute(salseIds)//执行入库
      }).then(() => {
        this.getList();
        this.$modal.msgSuccess("出库成功");
      }).catch(() => {});
    },
    handleSelectClient(){
      this.$refs["form"].clearValidate()
      this.$refs.clientSelect.handleOpen(this.form.clientId)
    },
    //客户选择弹出框
    onClientSelected(obj){
        if(obj != undefined && obj != null){
          this.form.clientId = obj.clientId;
          this.form.clientCode = obj.clientCode;
          this.form.clientName = obj.clientName;
          this.form.clientNick = obj.clientNick;
        }
    },
    //自动生成编码
    handleAutoGenChange(autoGenFlag){
      if(autoGenFlag){
        genCode('PRODUCTSALSE_CODE').then(response =>{
          this.form.salseCode = response;
        });
      }else{
        this.form.salseCode = null;
      }
    }
  }
};
</script>
