<template>
  <div class="dashboard-container">
    <!-- Row 1: 关键指标 KPI -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card blue" shadow="hover">
          <div class="stat-icon-bg"><i class="el-icon-document"></i></div>
          <h4>本月订单进度</h4>
          <div class="stat-value">32/45 <span class="stat-unit">单</span></div>
          <div class="stat-desc">
            完成率: <span class="highlight blue">71.1%</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card green" shadow="hover">
          <div class="stat-icon-bg"><i class="el-icon-s-order"></i></div>
          <h4>今日生产任务</h4>
          <div class="stat-value">1280 <span class="stat-unit">件</span></div>
          <div class="stat-desc">
            已完成: <span class="highlight green">850</span> (66.4%)
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card orange" shadow="hover">
          <div class="stat-icon-bg"><i class="el-icon-data-line"></i></div>
          <h4>昨日计划兑现率</h4>
          <div class="stat-value warning">92.5%</div>
          <div class="stat-desc">
            环比: <span class="highlight orange">↑ 1.2%</span>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card red" shadow="hover">
          <div class="stat-icon-bg"><i class="el-icon-s-check"></i></div>
          <h4>待审批单据</h4>
          <div class="stat-value">5 <span class="stat-unit">个</span></div>
          <div class="stat-desc">
            包含计划调整、异常单
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Row 2: 图表区域 -->
    <el-row :gutter="20" class="charts-row">
      <el-col :xs="24" :md="16">
        <el-card class="chart-card" shadow="hover">
          <div slot="header" class="card-header">
            <span class="card-title"><i class="el-icon-data-analysis" style="color:#2563eb;"></i> 近7天生产计划达成趋势</span>
            <el-select v-model="chartFilter" size="mini" style="width: 100px;">
              <el-option label="全厂" value="all"></el-option>
              <el-option label="一车间" value="1"></el-option>
              <el-option label="二车间" value="2"></el-option>
            </el-select>
          </div>
          <div id="lineChart" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="8">
        <el-card class="chart-card" shadow="hover">
          <div slot="header" class="card-header">
            <span class="card-title"><i class="el-icon-warning-outline" style="color:#fa8c16;"></i> 本月异常类型分布</span>
          </div>
          <div id="pieChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Row 3: 信息流区域 -->
    <el-row :gutter="20" class="info-row">
      <el-col :xs="24" :md="12">
        <el-card class="notice-card" shadow="hover">
          <div slot="header" class="card-header">
            <span class="card-title">
              <i class="el-icon-bell" style="color:#ff4d4f;"></i> 实时异常预警
              <span class="blinking-dot"></span>
            </span>
            <el-link type="info" :underline="false">查看全部</el-link>
          </div>
          <div class="alert-list">
            <div class="notice-item" v-for="(item, index) in alertList" :key="index">
              <el-tag :type="item.type" size="small">{{ item.tag }}</el-tag>
              <span class="notice-content">{{ item.content }}</span>
              <span class="notice-status">{{ item.status }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="notice-card" shadow="hover">
          <div slot="header" class="card-header">
            <span class="card-title"><i class="el-icon-message-solid" style="color:#2563eb;"></i> 通知公告</span>
            <el-link type="primary" :underline="false" @click="goToNoticeList">更多</el-link>
          </div>
          <div class="notice-list">
            <div class="notice-item clickable" v-for="(item, index) in noticeList" :key="index" @click="showNoticeDetail(item.noticeId)">
              <el-tag :type="item.tagType" size="small">{{ item.tag }}</el-tag>
              <span class="notice-content">{{ item.content }}</span>
              <span class="notice-date">{{ item.date }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 通知详情弹窗 -->
    <el-dialog :title="noticeDetail.noticeTitle" :visible.sync="noticeDialogVisible" width="600px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="公告类型">
          <el-tag :type="noticeDetail.noticeType === '1' ? 'warning' : 'primary'" size="small">
            {{ noticeDetail.noticeType === '1' ? '通知' : '公告' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ noticeDetail.createTime }}</el-descriptions-item>
        <el-descriptions-item label="内容">
          <div v-html="noticeDetail.noticeContent" class="notice-content-html"></div>
        </el-descriptions-item>
      </el-descriptions>
      <div slot="footer" class="dialog-footer">
        <el-button @click="noticeDialogVisible = false">关 闭</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { listNotice, getNotice } from '@/api/system/notice'

export default {
  name: 'Dashboard',
  data() {
    return {
      chartFilter: 'all',
      alertList: [
        { tag: '设备', type: 'danger', content: 'CNC-01 主轴温度过高报警 - 10:23', status: '未处理' },
        { tag: '缺料', type: 'warning', content: '总装线 螺栓M8*30 缺料预警 - 09:45', status: '处理中' },
        { tag: '质量', type: 'warning', content: '首检不合格：批次20231024-05 - 09:10', status: '已复核' },
        { tag: '设备', type: 'danger', content: '注塑机-03 压力不稳定 - 08:55', status: '维修中' }
      ],
      noticeList: [],
      lineChart: null,
      pieChart: null,
      // 通知详情弹窗
      noticeDialogVisible: false,
      noticeDetail: {
        noticeTitle: '',
        noticeType: '',
        createTime: '',
        noticeContent: ''
      }
    }
  },
  created() {
    this.getNoticeList()
  },
  mounted() {
    this.initCharts()
  },
  methods: {
    /** 获取通知公告列表 */
    getNoticeList() {
      listNotice({ pageNum: 1, pageSize: 5, status: '0' }).then(response => {
        const notices = response.rows || []
        this.noticeList = notices.map(item => {
          // noticeType: 1-通知 2-公告
          const tagMap = {
            '1': { tag: '通知', tagType: 'warning' },
            '2': { tag: '公告', tagType: 'primary' }
          }
          const tagInfo = tagMap[item.noticeType] || { tag: '公告', tagType: 'primary' }
          // 格式化日期
          let date = ''
          if (item.createTime) {
            const d = new Date(item.createTime)
            date = (d.getMonth() + 1).toString().padStart(2, '0') + '-' + d.getDate().toString().padStart(2, '0')
          }
          return {
            noticeId: item.noticeId,
            tag: tagInfo.tag,
            tagType: tagInfo.tagType,
            content: item.noticeTitle,
            date: date
          }
        })
      })
    },
    /** 跳转到通知公告列表页 */
    goToNoticeList() {
      this.$router.push('/system/notice')
    },
    /** 显示通知详情 */
    showNoticeDetail(noticeId) {
      getNotice(noticeId).then(response => {
        const data = response.data
        this.noticeDetail = {
          noticeTitle: data.noticeTitle,
          noticeType: data.noticeType,
          createTime: data.createTime,
          noticeContent: data.noticeContent
        }
        this.noticeDialogVisible = true
      })
    },
    initCharts() {
      // 折线图
      this.lineChart = echarts.init(document.getElementById('lineChart'))
      const lineOption = {
        tooltip: {
          trigger: 'axis'
        },
        legend: {
          data: ['计划产量', '实际产量'],
          bottom: 0
        },
        grid: {
          left: '3%',
          right: '4%',
          bottom: '15%',
          top: '10%',
          containLabel: true
        },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: ['10-18', '10-19', '10-20', '10-21', '10-22', '10-23', '10-24']
        },
        yAxis: {
          type: 'value'
        },
        series: [
          {
            name: '计划产量',
            type: 'line',
            smooth: true,
            data: [1200, 1300, 1250, 1400, 1350, 1450, 1280],
            lineStyle: { color: '#2563eb' },
            itemStyle: { color: '#2563eb' },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(37, 99, 235, 0.3)' },
                { offset: 1, color: 'rgba(37, 99, 235, 0.05)' }
              ])
            }
          },
          {
            name: '实际产量',
            type: 'line',
            smooth: true,
            data: [1150, 1280, 1200, 1380, 1320, 1420, 850],
            lineStyle: { color: '#52c41a' },
            itemStyle: { color: '#52c41a' },
            areaStyle: {
              color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                { offset: 0, color: 'rgba(82, 196, 26, 0.3)' },
                { offset: 1, color: 'rgba(82, 196, 26, 0.05)' }
              ])
            }
          }
        ]
      }
      this.lineChart.setOption(lineOption)

      // 饼图
      this.pieChart = echarts.init(document.getElementById('pieChart'))
      const pieOption = {
        tooltip: {
          trigger: 'item',
          formatter: '{b}: {c} ({d}%)'
        },
        legend: {
          orient: 'vertical',
          right: '5%',
          top: 'center'
        },
        series: [
          {
            type: 'pie',
            radius: ['40%', '70%'],
            center: ['35%', '50%'],
            avoidLabelOverlap: false,
            label: {
              show: false
            },
            emphasis: {
              label: {
                show: true,
                fontSize: '14',
                fontWeight: 'bold'
              }
            },
            labelLine: {
              show: false
            },
            data: [
              { value: 35, name: '设备异常', itemStyle: { color: '#ff4d4f' } },
              { value: 28, name: '质量异常', itemStyle: { color: '#fa8c16' } },
              { value: 20, name: '物料异常', itemStyle: { color: '#eb2f96' } },
              { value: 17, name: '其他', itemStyle: { color: '#2563eb' } }
            ]
          }
        ]
      }
      this.pieChart.setOption(pieOption)

      // 响应窗口大小变化
      window.addEventListener('resize', () => {
        this.lineChart.resize()
        this.pieChart.resize()
      })
    }
  }
}
</script>

<style scoped>
.dashboard-container {
  padding: 20px;
  background: #f5f5f5;
  min-height: calc(100vh - 84px);
}

/* KPI Cards */
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  position: relative;
  overflow: hidden;
  border-radius: 10px;
  margin-bottom: 20px;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
}

.stat-card.blue::before { background: #2563eb; }
.stat-card.green::before { background: #52c41a; }
.stat-card.orange::before { background: #fa8c16; }
.stat-card.red::before { background: #C41230; }

.stat-icon-bg {
  position: absolute;
  right: 15px;
  top: 15px;
  font-size: 40px;
  opacity: 0.1;
  color: #333;
}

.stat-card h4 {
  font-size: 14px;
  color: #666;
  margin-bottom: 12px;
  font-weight: normal;
}

.stat-card .stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #333;
  font-family: 'DIN', 'Roboto', sans-serif;
}

.stat-card .stat-value.warning {
  color: #fa8c16;
}

.stat-unit {
  font-size: 14px;
  color: #666;
}

.stat-desc {
  font-size: 12px;
  color: #666;
  margin-top: 5px;
}

.highlight.blue { color: #2563eb; font-weight: bold; }
.highlight.green { color: #52c41a; font-weight: bold; }
.highlight.orange { color: #fa8c16; }

/* Charts */
.charts-row {
  margin-bottom: 20px;
}

.chart-card {
  border-radius: 10px;
  height: 350px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chart-container {
  height: 260px;
}

/* Notice Cards */
.info-row {
  margin-bottom: 20px;
}

.notice-card {
  border-radius: 10px;
  height: 300px;
}

.blinking-dot {
  width: 8px;
  height: 8px;
  background-color: #ff4d4f;
  border-radius: 50%;
  display: inline-block;
  animation: blink 1s infinite;
}

@keyframes blink {
  0% { opacity: 1; }
  50% { opacity: 0.4; }
  100% { opacity: 1; }
}

.alert-list, .notice-list {
  height: 200px;
  overflow-y: auto;
}

.notice-item {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px dashed #f0f0f0;
  cursor: pointer;
  transition: all 0.2s;
}

.notice-item:hover {
  background: #fafafa;
  padding-left: 10px;
}

.notice-content {
  flex: 1;
  font-size: 13px;
  color: #333;
  margin-left: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.notice-status, .notice-date {
  font-size: 12px;
  color: #999;
  margin-left: 10px;
}

/* 通知项点击样式 */
.notice-item.clickable {
  cursor: pointer;
}

.notice-item.clickable:hover {
  background: #e6f7ff;
}

/* 通知详情弹窗内容样式 */
.notice-content-html {
  max-height: 400px;
  overflow-y: auto;
  line-height: 1.8;
}

.notice-content-html img {
  max-width: 100%;
}
</style>
