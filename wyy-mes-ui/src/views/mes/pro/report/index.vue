<template>
  <div class="report-dashboard">
    <!-- 顶部标题条 -->
    <div class="report-header">
      <div>
        <h2>生产报表看板</h2>
        <span class="report-sub">生产订单 · 工单 · 任务执行实时汇总</span>
      </div>
      <div class="header-actions">
        <span class="fresh-dot"><i></i>实时数据</span>
        <el-button icon="el-icon-refresh" size="mini" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- 核心指标：四张带渐变卡 -->
    <div class="kpi-grid">
      <div class="kpi-card kpi-blue">
        <div class="kpi-icon">单</div>
        <div><span class="kpi-label">生产订单</span><strong>{{ overview.orderCount }}</strong><small>张</small></div>
      </div>
      <div class="kpi-card kpi-indigo">
        <div class="kpi-icon">工</div>
        <div><span class="kpi-label">生产工单</span><strong>{{ overview.workorderCount }}</strong><small>张</small></div>
      </div>
      <div class="kpi-card kpi-teal">
        <div class="kpi-icon">任</div>
        <div><span class="kpi-label">执行任务</span><strong>{{ overview.taskCount }}</strong><small>项</small></div>
      </div>
      <div class="kpi-card kpi-green">
        <div class="kpi-icon">完</div>
        <div><span class="kpi-label">已完成任务</span><strong>{{ overview.finishedTaskCount }}</strong><small>项</small></div>
      </div>
    </div>

    <!-- 第二行：状态环图 + 当日任务 -->
    <div class="row-2">
      <div class="panel">
        <div class="panel-title"><span class="title-dot dot-blue"></span>任务状态分布</div>
        <div ref="statusChart" class="chart chart-donut"></div>
        <div class="donut-legend">
          <span><i class="lg-wait"></i>待派工 <b>{{ waitCount }}</b></span>
          <span><i class="lg-ready"></i>待开工 <b>{{ overview.releasedTaskCount || 0 }}</b></span>
          <span><i class="lg-run"></i>执行中 <b>{{ overview.inProgressTaskCount || 0 }}</b></span>
          <span><i class="lg-done"></i>已完成 <b>{{ overview.finishedTaskCount || 0 }}</b></span>
        </div>
      </div>
      <div class="panel">
        <div class="panel-title"><span class="title-dot dot-orange"></span>当日任务情况</div>
        <div class="today-grid">
          <div class="today-item"><b class="t-blue">{{ today.created }}</b><span>今日创建</span></div>
          <div class="today-item"><b class="t-orange">{{ today.waiting }}</b><span>待开工</span></div>
          <div class="today-item"><b class="t-indigo">{{ today.running }}</b><span>执行中</span></div>
          <div class="today-item"><b class="t-green">{{ today.finishedToday }}</b><span>今日完工</span></div>
          <div class="today-item"><b class="t-teal">{{ today.finishedTotal }}</b><span>累计完工</span></div>
        </div>
        <div class="progress-line">
          <div class="progress-head"><span>任务完成率</span><b>{{ completionRate }}%</b></div>
          <el-progress :percentage="completionRate" :show-text="false" :stroke-width="10" color="#2d8cf0"></el-progress>
        </div>
      </div>
    </div>

    <!-- 第三行：双趋势图 -->
    <div class="row-2">
      <div class="panel">
        <div class="panel-title"><span class="title-dot dot-teal"></span>当周任务曲线</div>
        <div ref="weekChart" class="chart"></div>
      </div>
      <div class="panel">
        <div class="panel-title"><span class="title-dot dot-purple"></span>当月执行情况</div>
        <div ref="monthChart" class="chart"></div>
      </div>
    </div>

    <!-- 第四行：班组统计 -->
    <div class="panel panel-full">
      <div class="panel-title"><span class="title-dot dot-green"></span>班组统计分析</div>
      <div ref="teamChart" class="chart chart-tall"></div>
    </div>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { reportDashboard } from '@/api/mes/pro/productionexecution'

export default {
  name: 'ProductionReport',
  data() {
    return {
      overview: {},
      today: {},
      charts: {}
    }
  },
  computed: {
    waitCount() {
      const base = (this.overview.taskCount || 0) - (this.overview.releasedTaskCount || 0)
        - (this.overview.inProgressTaskCount || 0) - (this.overview.finishedTaskCount || 0)
      return Math.max(0, base)
    },
    completionRate() {
      const total = this.overview.taskCount || 0
      if (!total) return 0
      return Math.round((this.overview.finishedTaskCount || 0) / total * 100)
    }
  },
  mounted() {
    this.load()
    window.addEventListener('resize', this.resizeCharts)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeCharts)
    Object.values(this.charts).forEach(chart => chart && chart.dispose())
  },
  methods: {
    load() {
      reportDashboard().then(res => {
        const data = res.data || {}
        this.overview = data.overview || {}
        this.today = data.today || {}
        this.$nextTick(() => {
          this.renderStatusChart()
          this.renderTrend('weekChart', data.weekTrend || [], ['创建任务', '完成任务'])
          this.renderTrend('monthChart', data.monthExecution || [], ['创建任务', '完成任务'])
          this.renderTeamChart(data.teamStats || [])
        })
      })
    },
    resizeCharts() {
      Object.values(this.charts).forEach(chart => chart && chart.resize())
    },
    renderStatusChart() {
      const chart = echarts.init(this.$refs.statusChart)
      this.charts.status = chart
      chart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} 项 ({d}%)' },
        legend: { show: false },
        series: [{
          type: 'pie',
          radius: ['46%', '72%'],
          center: ['50%', '46%'],
          avoidLabelOverlap: true,
          itemStyle: { borderRadius: 5, borderColor: '#fff', borderWidth: 2 },
          label: { show: false },
          emphasis: { label: { show: true, fontSize: 16, fontWeight: 'bold', formatter: '{b}\n{c} 项' } },
          data: [
            { name: '待派工', value: this.waitCount },
            { name: '待开工', value: this.overview.releasedTaskCount || 0 },
            { name: '执行中', value: this.overview.inProgressTaskCount || 0 },
            { name: '已完成', value: this.overview.finishedTaskCount || 0 }
          ]
        }],
        color: ['#c8cdd4', '#ffb648', '#2d8cf0', '#19be6b']
      })
    },
    renderTrend(refName, data, names) {
      const chart = echarts.init(this.$refs[refName])
      this.charts[refName] = chart
      chart.setOption({
        tooltip: { trigger: 'axis' },
        legend: { top: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 6, textStyle: { color: '#606266' } },
        grid: { left: 38, right: 16, top: 34, bottom: 24 },
        xAxis: {
          type: 'category',
          data: data.map(item => item.dateLabel),
          axisLine: { lineStyle: { color: '#dcdfe6' } },
          axisLabel: { color: '#909399' },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value', minInterval: 1,
          splitLine: { lineStyle: { color: '#f0f2f5' } },
          axisLabel: { color: '#909399' }
        },
        series: [
          { name: names[0], type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: data.map(item => item.created), itemStyle: { color: '#2d8cf0' }, lineStyle: { width: 2.5 }, areaStyle: { opacity: 0.08 } },
          { name: names[1], type: 'line', smooth: true, symbol: 'circle', symbolSize: 6, data: data.map(item => item.finished), itemStyle: { color: '#19be6b' }, lineStyle: { width: 2.5 }, areaStyle: { opacity: 0.08 } }
        ]
      })
    },
    renderTeamChart(teamStats) {
      const chart = echarts.init(this.$refs.teamChart)
      this.charts.team = chart
      chart.setOption({
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: { top: 0, icon: 'roundRect', itemWidth: 12, itemHeight: 6, textStyle: { color: '#606266' } },
        grid: { left: 92, right: 24, top: 34, bottom: 24 },
        xAxis: {
          type: 'value', minInterval: 1,
          splitLine: { lineStyle: { color: '#f0f2f5' } },
          axisLabel: { color: '#909399' }
        },
        yAxis: {
          type: 'category',
          data: teamStats.map(item => item.teamName).reverse(),
          axisLine: { show: false },
          axisTick: { show: false },
          axisLabel: { color: '#606266' }
        },
        series: [
          { name: '已完成', type: 'bar', stack: 'total', barWidth: 14, data: teamStats.map(item => item.finished).reverse(), itemStyle: { color: '#19be6b', borderRadius: [0, 3, 3, 0] } },
          { name: '执行中', type: 'bar', stack: 'total', barWidth: 14, data: teamStats.map(item => item.running).reverse(), itemStyle: { color: '#2d8cf0', borderRadius: [0, 3, 3, 0] } },
          { name: '待处理', type: 'bar', stack: 'total', barWidth: 14, data: teamStats.map(item => Math.max(0, item.total - item.finished - item.running)).reverse(), itemStyle: { color: '#c8cdd4', borderRadius: [0, 3, 3, 0] } }
        ]
      })
    }
  }
}
</script>

<style scoped>
.report-dashboard { min-height: 100%; padding: 16px 20px 26px; background: #f7f9fc; }
.report-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.report-header h2 { margin: 0; color: #1f2d3d; font-size: 20px; font-weight: 600; }
.report-sub { display: block; margin-top: 4px; color: #98a1ab; font-size: 12px; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.fresh-dot { color: #98a1ab; font-size: 12px; }
.fresh-dot i { display: inline-block; width: 7px; height: 7px; margin-right: 5px; border-radius: 50%; background: #19be6b; }

.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin-bottom: 14px; }
.kpi-card { display: flex; align-items: center; gap: 14px; padding: 18px 16px; border-radius: 10px; color: #fff; box-shadow: 0 6px 16px rgba(31,45,61,.08); }
.kpi-blue { background: linear-gradient(120deg, #2d8cf0, #5cadff); }
.kpi-indigo { background: linear-gradient(120deg, #6c5ce7, #a29bfe); }
.kpi-teal { background: linear-gradient(120deg, #0e9d8f, #35c3b0); }
.kpi-green { background: linear-gradient(120deg, #19be6b, #5ed898); }
.kpi-icon { width: 42px; height: 42px; flex: 0 0 42px; display: grid; place-items: center; border-radius: 10px; background: rgba(255,255,255,.22); font-size: 18px; font-weight: 700; }
.kpi-label { display: block; color: rgba(255,255,255,.85); font-size: 12px; }
.kpi-card strong { font-size: 26px; font-weight: 700; line-height: 1.1; }
.kpi-card small { margin-left: 4px; color: rgba(255,255,255,.8); font-size: 11px; }

.row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 14px; }
.panel { padding: 14px 16px; border-radius: 10px; background: #fff; box-shadow: 0 2px 10px rgba(31,45,61,.05); }
.panel-full { margin-bottom: 0; }
.panel-title { display: flex; align-items: center; margin-bottom: 6px; color: #1f2d3d; font-size: 14px; font-weight: 600; }
.title-dot { width: 4px; height: 14px; margin-right: 8px; border-radius: 2px; }
.dot-blue { background: #2d8cf0; }
.dot-orange { background: #ff9900; }
.dot-teal { background: #0e9d8f; }
.dot-purple { background: #6c5ce7; }
.dot-green { background: #19be6b; }

.chart { height: 260px; }
.chart-donut { height: 190px; }
.chart-tall { height: 300px; }
.donut-legend { display: flex; justify-content: center; gap: 18px; padding-top: 4px; }
.donut-legend span { color: #8a939e; font-size: 12px; }
.donut-legend i { display: inline-block; width: 8px; height: 8px; margin-right: 4px; border-radius: 50%; }
.donut-legend b { color: #1f2d3d; font-weight: 600; }
.lg-wait { background: #c8cdd4; }
.lg-ready { background: #ffb648; }
.lg-run { background: #2d8cf0; }
.lg-done { background: #19be6b; }

.today-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; }
.today-item { padding: 14px 6px; border: 1px solid #eef1f6; border-radius: 8px; text-align: center; }
.today-item b { display: block; margin-bottom: 6px; font-size: 22px; font-weight: 700; }
.today-item span { color: #8a939e; font-size: 12px; }
.t-blue { color: #2d8cf0; }
.t-orange { color: #ff9900; }
.t-indigo { color: #6c5ce7; }
.t-green { color: #19be6b; }
.t-teal { color: #0e9d8f; }
.progress-line { margin-top: 16px; }
.progress-head { display: flex; justify-content: space-between; margin-bottom: 6px; color: #8a939e; font-size: 12px; }
.progress-head b { color: #2d8cf0; font-size: 14px; }
</style>
