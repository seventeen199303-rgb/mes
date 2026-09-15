<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const API_BASE = import.meta.env.VITE_APP_API_BASE || 'https://kywgmes.cn/prod-api'
const APP_VERSION = '1.6.2'

const loggedIn = ref(false)
const loading = ref(false)
const loginLoading = ref(false)
const activeTab = ref('home')
const activeMenu = ref(null)
const activeMenuLoading = ref(false)
const activeMenuItems = ref([])
const activeMenuTotal = ref(0)
const taskStatusTab = ref('RELEASED')
const selectedTask = ref(null)
const taskActionLoading = ref(false)
const transferVisible = ref(false)
const transferList = ref([])
const transferKeyword = ref('')
const transferTarget = ref(null)
const transferSubmitting = ref(false)
const username = ref('')
const password = ref('')
const loginError = ref('')
const toast = ref('')
const noticeFilter = ref('全部')
const noticeDetail = ref(null)
const noticeDetailVisible = ref(false)
const noticeReadLoading = ref(false)
const updateInfo = ref(null)
const updateVisible = ref(false)

const profile = reactive({
  user: {},
  employeeNo: '',
  department: '未分配部门',
  summary: { todoTasks: 0, feedbackCount: 0, qualifiedRate: '--' }
})
const dashboard = reactive({
  metrics: {
    todoTasks: 0, feedbackCount: 0, transferCount: 0, issueCount: 0,
    returnCount: 0, receiptCount: 0, taskTotal: 0, taskFinished: 0, qualifiedRate: '--'
  },
  tasks: [], feedbacks: [], transfers: [], issues: [], returns: [], receipts: []
})
const notices = ref([])

const tabs = [
  { key: 'home', label: '首页', icon: '⌂' },
  { key: 'stats', label: '统计', icon: '▥' },
  { key: 'notice', label: '通知', icon: '♧' },
  { key: 'profile', label: '我的', icon: '◉' }
]

const productionMenus = [
  { key: 'tasks', name: '生产任务', icon: '▤', tone: 'blue', metric: 'todoTasks', hint: '待办任务' },
  { key: 'feedback', name: '扫码报工', icon: '⌁', tone: 'green', metric: 'feedbackCount', hint: '报工记录' },
  { key: 'transfer', name: '工序流转', icon: '⇢', tone: 'indigo', metric: 'transferCount', hint: '流转单据' },
  { key: 'issue', name: '生产领料', icon: '⊞', tone: 'orange', metric: 'issueCount', hint: '领料单据' },
  { key: 'return', name: '生产退料', icon: '↩', tone: 'amber', metric: 'returnCount', hint: '退料单据' },
  { key: 'receipt', name: '产品入库', icon: '▣', tone: 'cyan', metric: 'receiptCount', hint: '入库单据' }
]

const menuGroups = computed(() => [
  {
    title: '生产执行',
    menus: productionMenus.map(menu => ({ ...menu, hint: `${dashboard.metrics[menu.metric] || 0} 条${menu.hint}` }))
  },
  {
    title: '质量与仓储',
    menus: [
      { name: '质检任务', icon: '✓', tone: 'purple', hint: '质量检验' },
      { name: '检验模板', icon: '☷', tone: 'violet', hint: '标准与项目' },
      { name: '采购入库', icon: '↓', tone: 'teal', hint: '收货与入库' },
      { name: '库存查询', icon: '▦', tone: 'blue', hint: '仓库与库位' },
      { name: '条码清单', icon: '▥', tone: 'slate', hint: '追溯查询' },
      { name: '装箱管理', icon: '□', tone: 'orange', hint: '包装与发运' }
    ]
  },
  {
    title: '现场管理',
    menus: [
      { name: '设备点检', icon: '◌', tone: 'red', hint: '点检任务' },
      { name: '保养计划', icon: '◷', tone: 'rose', hint: '计划执行' },
      { name: '维修工单', icon: '⚒', tone: 'amber', hint: '维修跟踪' },
      { name: '工装台账', icon: '⌘', tone: 'indigo', hint: '工装状态' },
      { name: '班组排班', icon: '◫', tone: 'cyan', hint: '今日班次' },
      { name: '基础资料', icon: '⚙', tone: 'slate', hint: '物料、工位、SOP' }
    ]
  }
])

const currentTitle = computed(() => ({ home: '工作台', stats: '生产统计', notice: '通知中心', profile: '个人中心' })[activeTab.value])
const displayName = computed(() => profile.user.nickName || profile.user.userName || '现场员工')
const avatarText = computed(() => displayName.value.slice(0, 1))
const avatarUrl = computed(() => resolveUrl(profile.user.avatar))
const filteredNotices = computed(() => noticeFilter.value === '全部'
  ? notices.value
  : notices.value.filter(item => noticeCategory(item) === noticeFilter.value))
const unreadNoticeCount = computed(() => notices.value.filter(item => item.status === 'UNREAD').length)
const taskCompletion = computed(() => dashboard.metrics.taskTotal
  ? Math.round((dashboard.metrics.taskFinished / dashboard.metrics.taskTotal) * 100)
  : 0)
const activeMenuIsProduction = computed(() => activeMenu.value && productionMenus.some(item => item.key === activeMenu.value.key))
const taskTabs = computed(() => [
  { key: 'RELEASED', label: '待开工', count: activeMenuItems.value.filter(item => item.status === 'RELEASED').length },
  { key: 'IN_PROGRESS', label: '进行中', count: activeMenuItems.value.filter(item => item.status === 'IN_PROGRESS').length },
  { key: 'FINISHED', label: '已完成', count: activeMenuItems.value.filter(item => item.status === 'FINISHED').length }
])
const visibleMenuItems = computed(() => activeMenu.value?.key === 'tasks'
  ? activeMenuItems.value.filter(item => item.status === taskStatusTab.value)
  : activeMenuItems.value)
const transferCandidates = computed(() => {
  const keyword = transferKeyword.value.trim().toLowerCase()
  if (!keyword) return transferList.value
  return transferList.value.filter(user => [user.nickName, user.userName, user.deptName]
    .some(text => text && String(text).toLowerCase().includes(keyword)))
})

function resolveUrl(path) {
  if (!path) return ''
  if (/^https?:\/\//i.test(path) || path.startsWith('data:')) return path
  return `${API_BASE}${path.startsWith('/') ? path : `/${path}`}`
}

function showToast(message) {
  toast.value = message
  window.setTimeout(() => { toast.value = '' }, 2200)
}

async function request(path, options = {}) {
  const token = localStorage.getItem('wyymes-app-token')
  const headers = { ...(options.headers || {}) }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
  const data = await response.json().catch(() => ({}))
  if (!response.ok || (data.code !== undefined && data.code !== 200)) {
    const error = new Error(data.msg || '接口请求失败，请稍后重试')
    error.status = response.status
    throw error
  }
  return data
}

async function enterApp() {
  if (!username.value.trim() || !password.value) {
    loginError.value = '请输入账号和密码'
    return
  }
  loginLoading.value = true
  loginError.value = ''
  try {
    const body = new URLSearchParams({ username: username.value.trim(), password: password.value, loginType: '1' })
    const data = await request('/mobile/login/loginByPassword', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body
    })
    if (!data.token) throw new Error('登录接口未返回访问令牌')
    localStorage.setItem('wyymes-app-token', data.token)
    loggedIn.value = true
    await loadAppData()
    showToast(`登录成功，欢迎回来，${displayName.value}`)
  } catch (error) {
    loginError.value = error.message || '登录失败，请检查账号和密码'
  } finally {
    loginLoading.value = false
  }
}

async function loadAppData() {
  loading.value = true
  try {
    const [profileData, dashboardData, noticeData, versionData] = await Promise.all([
      request('/mobile/app/profile'), request('/mobile/app/dashboard'), request('/mobile/app/notices'), request('/mobile/app/version')
    ])
    Object.assign(profile, profileData)
    Object.assign(dashboard, dashboardData)
    notices.value = Array.isArray(noticeData.data) ? noticeData.data : []
    checkForUpdate(versionData.data)
  } catch (error) {
    if (error.status === 401) logout(false)
    throw error
  } finally {
    loading.value = false
  }
}

function checkForUpdate(data) {
  updateInfo.value = data
  if (data?.version && isNewerVersion(data.version, APP_VERSION)) updateVisible.value = true
}

function manualCheckUpdate() {
  if (!updateInfo.value) {
    showToast('暂未获取到版本信息')
    return
  }
  if (isNewerVersion(updateInfo.value.version, APP_VERSION)) {
    updateVisible.value = true
  } else {
    showToast('当前已是最新版本')
  }
}

function isNewerVersion(latest, current) {
  const latestParts = String(latest).split('.').map(value => Number(value) || 0)
  const currentParts = String(current).split('.').map(value => Number(value) || 0)
  for (let index = 0; index < Math.max(latestParts.length, currentParts.length); index += 1) {
    const difference = (latestParts[index] || 0) - (currentParts[index] || 0)
    if (difference !== 0) return difference > 0
  }
  return false
}

/** 打开任务列表时同步原生返回栈。 */
async function openMenu(menu) {
  activeMenu.value = menu
  selectedTask.value = null
  activeMenuItems.value = []
  activeMenuTotal.value = 0
  taskStatusTab.value = 'RELEASED'
  syncBackStack()
  if (!productionMenus.some(item => item.key === menu.key)) return
  activeMenuLoading.value = true
  try {
    const data = await request(`/mobile/app/production/${menu.key}`)
    activeMenuItems.value = Array.isArray(data.items) ? data.items : []
    activeMenuTotal.value = data.total || 0
    if (menu.key === 'tasks' && !activeMenuItems.value.some(item => item.status === 'RELEASED')) {
      taskStatusTab.value = activeMenuItems.value.some(item => item.status === 'IN_PROGRESS') ? 'IN_PROGRESS' : 'FINISHED'
    }
  } catch (error) {
    showToast(error.message || '生产数据加载失败')
  } finally {
    activeMenuLoading.value = false
  }
}

function closeMenu() { activeMenu.value = null; activeMenuItems.value = []; selectedTask.value = null; transferVisible.value = false; syncBackStack() }
function closeTaskDetail() { selectedTask.value = null; transferVisible.value = false; syncBackStack() }
function switchTab(key) { activeTab.value = key; closeMenu() }

/** 当前页面栈深度：菜单页+任务详情各占一层，用于原生返回键路由。 */
const backStackDepth = computed(() => (activeMenu.value ? (selectedTask.value ? 2 : 1) : 0))

function syncBackStack() {
  const bridge = window.AndroidBackBridge
  if (bridge && typeof bridge.register === 'function') {
    try { bridge.register(backStackDepth.value) } catch (_) { /* 忽略桥接异常 */ }
  }
}

/** 页面返回一步：任务详情 -> 任务列表 -> 首页；由原生返回键或页面返回按钮触发。 */
function handleBackPress() {
  if (transferVisible.value) { transferVisible.value = false; return }
  if (selectedTask.value) { closeTaskDetail(); return }
  if (activeMenu.value) { closeMenu(); return }
  if (activeTab.value !== 'home') { switchTab('home'); return }
  if (window.cordova?.plugins?.app) { try { window.cordova.plugins.app.exitApp() } catch (_) { /* noop */ } }
}

onMounted(() => {
  window.__androidBackPressed = handleBackPress
  window.addEventListener('popstate', handleBackPress)
  syncBackStack()
  if (!localStorage.getItem('wyymes-app-token')) return
  loggedIn.value = true
  try { loadAppData() } catch (_) { logout(false) }
})
function noticeCategory(notice) {
  const category = notice.attr1 || '系统'
  return category === '生产执行' ? '生产' : category
}

const NOTICE_ICONS = { 生产: '▤', 质量: '✓', 仓储: '▦', 系统: '♧', 生产执行: '▤' }

function noticeIcon(notice) {
  return NOTICE_ICONS[noticeCategory(notice)] || NOTICE_ICONS[notice.messageType === 'PRO_TASK' ? '生产执行' : '系统'] || '♧'
}

/** 点击通知：打开详情并把未读的标记为已读。 */
async function openNoticeDetail(notice) {
  noticeDetail.value = notice
  noticeDetailVisible.value = true
  if (notice.status !== 'UNREAD' || noticeReadLoading.value) return
  noticeReadLoading.value = true
  try {
    await request(`/mobile/app/notices/read?messageId=${notice.messageId}`)
    notice.status = 'READ'
  } catch (_) {
    // 标记失败不打断查看
  } finally {
    noticeReadLoading.value = false
  }
}

/** 全部通知标记为已读。 */
async function readAllNotices() {
  if (!unreadNoticeCount.value || noticeReadLoading.value) return
  noticeReadLoading.value = true
  try {
    await request('/mobile/app/notices/read')
    notices.value.forEach(item => { item.status = 'READ' })
    showToast('全部通知已标记为已读')
  } catch (error) {
    showToast(error.message || '操作失败')
  } finally {
    noticeReadLoading.value = false
  }
}

function formatNoticeTime(time) {
  if (!time) return ''
  const date = new Date(time)
  if (Number.isNaN(date.getTime())) return String(time)
  const today = new Date()
  return date.toDateString() === today.toDateString()
    ? date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    : `${date.getMonth() + 1}-${date.getDate()}`
}

function productionItemTitle(item, key) {
  return ({ tasks: item.taskCode || item.taskName, feedback: item.feedbackCode || item.taskCode, transfer: item.transOrderCode, issue: item.issueCode, return: item.rtCode, receipt: item.recptCode })[key] || item.itemName || '业务单据'
}

const BIZ_STATUS_TEXT = {
  PREPARE: '待处理', CONFIRMED: '已确认', APPROVING: '待审核', APPROVED: '已审核',
  FINISHED: '已完成', CANCELED: '已取消', CANCELLED: '已取消', ACTIVE: '处理中',
  RESOLVED: '已解决', NORMAL: '正常', WAITING: '等待前序', RELEASED: '待开工',
  IN_PROGRESS: '执行中', REVOKED: '已撤回', UNREAD: '未读', READ: '已读',
  STORE: '在库', ISSUE: '被领用', REPARE: '维修中', REPAIR: '维修中', SCRAP: '报废',
  STOP: '停机', WORKING: '生产中', EXECUTING: '执行中', SPLIT: '部分拆解',
  ALL_SPLIT: '已全部拆解'
}

function bizStatusText(status) {
  if (!status) return ''
  return BIZ_STATUS_TEXT[String(status).trim()] || status
}

function productionItemText(item, key) {
  if (key === 'tasks') return [item.itemName, item.processName, item.workstationName].filter(Boolean).join(' · ')
  if (key === 'feedback') return [item.itemName, item.processName, item.quantityFeedback && `报工 ${item.quantityFeedback}${item.unitOfMeasure || ''}`].filter(Boolean).join(' · ')
  if (key === 'transfer') return [item.itemName, item.processName, item.workstationName].filter(Boolean).join(' · ')
  if (key === 'issue' || key === 'return') return [item.workorderCode, item.workstationName, bizStatusText(item.status)].filter(Boolean).join(' · ')
  if (key === 'receipt') return [item.workorderCode, item.warehouseName, bizStatusText(item.status)].filter(Boolean).join(' · ')
  return ''
}

function productionItemProgress(item, key) { return key === 'tasks' ? `${item.quantityProduced || 0}/${item.quantity || 0}${item.unitOfMeasure || ''}` : bizStatusText(item.status) }
function taskStatusText(status) { return ({ RELEASED: '待开工', IN_PROGRESS: '执行中', FINISHED: '已完成', WAITING: '等待前序', PREPARE: '待派工' })[status] || status || '--' }
function displayTime(value) {
  if (!value) return '--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}
async function openTaskDetail(item) {
  taskActionLoading.value = true
  try {
    const data = await request(`/mobile/pro/execution/${item.taskId}`)
    selectedTask.value = data.data
    syncBackStack()
  } catch (error) {
    showToast(error.message || '任务详情加载失败')
  } finally {
    taskActionLoading.value = false
  }
}
async function operateTask(action) {
  if (!selectedTask.value || taskActionLoading.value) return
  taskActionLoading.value = true
  try {
    const data = await request(`/mobile/pro/execution/${selectedTask.value.taskId}/${action}`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({})
    })
    selectedTask.value = data.data
    const taskList = await request('/mobile/app/production/tasks')
    activeMenuItems.value = Array.isArray(taskList.items) ? taskList.items : []
    activeMenuTotal.value = taskList.total || 0
    await loadAppData()
    showToast(action === 'start' ? '已记录开工时间，开始作业' : '已完成报工，下一工序将自动释放')
  } catch (error) {
    showToast(error.message || '任务操作失败')
  } finally {
    taskActionLoading.value = false
  }
}
function openUpdate() { if (updateInfo.value?.downloadUrl) window.open(updateInfo.value.downloadUrl, '_blank') }

async function openTransfer() {
  if (!selectedTask.value || transferSubmitting.value) return
  transferVisible.value = true
  transferKeyword.value = ''
  transferTarget.value = null
  if (!transferList.value.length) {
    try {
      const data = await request('/mobile/app/colleagues')
      transferList.value = Array.isArray(data.items) ? data.items : []
    } catch (error) {
      showToast(error.message || '员工列表加载失败')
    }
  }
}

function selectTransferTarget(user) { transferTarget.value = user }

async function confirmTransfer() {
  if (!selectedTask.value || transferSubmitting.value) return
  if (!transferTarget.value) {
    showToast('请选择接手人')
    return
  }
  transferSubmitting.value = true
  try {
    await request(`/mobile/pro/execution/${selectedTask.value.taskId}/transfer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ targetUserId: transferTarget.value.userId })
    })
    transferVisible.value = false
    showToast(`任务已转交给 ${transferTarget.value.nickName || transferTarget.value.userName}`)
    selectedTask.value = null
    const taskList = await request('/mobile/app/production/tasks')
    activeMenuItems.value = Array.isArray(taskList.items) ? taskList.items : []
    activeMenuTotal.value = taskList.total || 0
    await loadAppData()
  } catch (error) {
    showToast(error.message || '任务转交失败')
  } finally {
    transferSubmitting.value = false
  }
}

function logout(withMessage = true) {
  localStorage.removeItem('wyymes-app-token')
  loggedIn.value = false
  password.value = ''
  updateVisible.value = false
  if (withMessage) showToast('已退出登录')
}

onMounted(async () => {
  if (!localStorage.getItem('wyymes-app-token')) return
  loggedIn.value = true
  try { await loadAppData() } catch (_) { logout(false) }
})
</script>

<template>
  <main class="app-shell">
    <section v-if="!loggedIn" class="login-page">
      <div class="login-hero"><div class="brand-mark"><span>W</span></div><div><p class="eyebrow">WANG YOU YONG MES</p><h1>王有用MES</h1></div></div>
      <form class="login-card" @submit.prevent="enterApp">
        <div class="login-heading"><h2>欢迎登录</h2><p>使用平台账号登录现场端</p></div>
        <label class="field-label" for="account">账号</label><input id="account" v-model="username" autocomplete="username" placeholder="请输入平台账号" />
        <label class="field-label" for="password">密码</label><input id="password" v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        <p v-if="loginError" class="login-error">{{ loginError }}</p>
        <button class="primary-button" type="submit" :disabled="loginLoading">{{ loginLoading ? '登录中…' : '登录' }}</button>
      </form>
      <p class="login-foot">南京王有用智能制造有限公司 · MES 现场端</p>
    </section>

    <template v-else>
      <header class="topbar"><div><p class="topbar-subtitle">王有用MES管理平台</p><h1>{{ currentTitle }}</h1></div><button class="avatar-button" aria-label="个人中心" @click="switchTab('profile')"><img v-if="avatarUrl" :src="avatarUrl" alt="头像" @error="$event.target.style.display = 'none'" /><span>{{ avatarText }}</span></button></header>

      <section v-if="activeTab === 'home'" class="page page-home">
        <div class="welcome-card"><div><p>你好，{{ displayName }}</p><h2>生产执行数据已同步</h2><span>{{ profile.department }} · {{ profile.employeeNo || '未维护工号' }}</span></div><div class="live-dot"><i></i>实时数据</div></div>
        <div class="metric-row"><article><strong>{{ dashboard.metrics.todoTasks }}</strong><span>待办任务</span></article><article><strong>{{ dashboard.metrics.feedbackCount }}</strong><span>报工记录</span></article><article><strong>{{ dashboard.metrics.receiptCount }}</strong><span>产品入库</span></article></div>
        <template v-for="group in menuGroups" :key="group.title"><div class="section-title"><h2>{{ group.title }}</h2><span>{{ group.title === '生产执行' ? '实时查询' : '业务入口' }}</span></div><div class="menu-grid"><button v-for="menu in group.menus" :key="menu.name" class="menu-card" @click="openMenu(menu)"><span :class="['menu-icon', menu.tone]">{{ menu.icon }}</span><b>{{ menu.name }}</b><small>{{ menu.hint }}</small></button></div></template>
      </section>

      <section v-else-if="activeTab === 'stats'" class="page stats-page">
        <div class="yield-card"><div><span>生产任务完成率</span><strong>{{ taskCompletion }}<em>%</em></strong><small>数据来自当前生产任务</small></div><div class="ring"><span>已完成<br /><b>{{ dashboard.metrics.taskFinished }}</b></span></div></div>
        <div class="stat-grid"><article><span>生产任务</span><b>{{ dashboard.metrics.taskTotal }}</b><small>条</small></article><article><span>已完成任务</span><b>{{ dashboard.metrics.taskFinished }}</b><small>条</small></article><article><span>生产报工</span><b>{{ dashboard.metrics.feedbackCount }}</b><small>条</small></article><article><span>报工合格率</span><b>{{ dashboard.metrics.qualifiedRate }}</b><small v-if="dashboard.metrics.qualifiedRate !== '--'">%</small></article></div>
        <div class="panel"><div class="panel-head"><h2>生产执行概览</h2><span>实时查询</span></div><div class="summary-lines"><p><b>工序流转</b><span>{{ dashboard.metrics.transferCount }} 单</span></p><p><b>生产领料</b><span>{{ dashboard.metrics.issueCount }} 单</span></p><p><b>生产退料</b><span>{{ dashboard.metrics.returnCount }} 单</span></p><p><b>产品入库</b><span>{{ dashboard.metrics.receiptCount }} 单</span></p></div></div>
      </section>

      <section v-else-if="activeTab === 'notice'" class="page notice-page">
        <div class="notice-toolbar">
          <div class="notice-filter"><button v-for="filter in ['全部', '生产', '质量', '仓储', '系统']" :key="filter" :class="{ active: noticeFilter === filter }" @click="noticeFilter = filter">{{ filter }}</button></div>
          <button v-if="unreadNoticeCount" class="notice-all-read" @click="readAllNotices">全部已读</button>
        </div>
        <article v-for="notice in filteredNotices" :key="notice.messageId" class="notice-item" :class="{ read: notice.status !== 'UNREAD' }" @click="openNoticeDetail(notice)"><span :class="['notice-type', noticeCategory(notice)]">{{ noticeIcon(notice) }}</span><div><h2>{{ notice.messageTitle }}</h2><p>{{ notice.messageContent }}</p><small>{{ formatNoticeTime(notice.createTime) }} · {{ notice.status === 'UNREAD' ? '未读' : '已读' }}</small></div><i v-if="notice.status === 'UNREAD'"></i></article>
        <div v-if="!filteredNotices.length" class="empty-page">暂无{{ noticeFilter === '全部' ? '' : noticeFilter }}通知</div>
      </section>

      <section v-else-if="activeTab === 'profile'" class="page profile-page">
        <div class="profile-head"><div class="profile-avatar"><img v-if="avatarUrl" :src="avatarUrl" alt="头像" @error="$event.target.style.display = 'none'" /><span>{{ avatarText }}</span></div><div><h2>{{ displayName }}</h2><p>{{ profile.department }}</p><span>工号：{{ profile.employeeNo || profile.user.userName || '--' }}</span></div></div>
        <div class="profile-summary"><div><b>{{ profile.summary.todoTasks }}</b><span>待办任务</span></div><div><b>{{ profile.summary.feedbackCount }}</b><span>报工记录</span></div><div><b>{{ profile.summary.qualifiedRate }}<small v-if="profile.summary.qualifiedRate !== '--'">%</small></b><span>合格率</span></div></div>
        <div class="profile-list"><button @click="switchTab('notice')"><span>我的通知</span><b>{{ unreadNoticeCount || '›' }}</b></button><button @click="loadAppData().then(() => showToast('资料已同步'))"><span>刷新账号与生产数据</span><b>›</b></button><button @click="manualCheckUpdate"><span>检查版本更新</span><b>›</b></button></div>
        <button class="logout" @click="logout()">退出登录</button><p class="version">王有用MES APP · v{{ APP_VERSION }}</p>
      </section>

      <div v-if="noticeDetailVisible && noticeDetail" class="update-backdrop" @click.self="noticeDetailVisible = false"><section class="update-dialog notice-detail-dialog"><span :class="['notice-type', noticeCategory(noticeDetail)]">{{ noticeIcon(noticeDetail) }}</span><h2>{{ noticeDetail.messageTitle }}</h2><p class="notice-detail-meta">{{ formatNoticeTime(noticeDetail.createTime) }} · {{ noticeCategory(noticeDetail) }}通知</p><p class="notice-detail-content">{{ noticeDetail.messageContent }}</p><button class="primary-button" @click="noticeDetailVisible = false">知道了</button></section></div>

      <nav class="tabbar"><button v-for="tab in tabs" :key="tab.key" :class="{ active: activeTab === tab.key }" @click="switchTab(tab.key)"><span class="tab-icon">{{ tab.icon }}<i v-if="tab.key === 'notice' && unreadNoticeCount">{{ unreadNoticeCount > 99 ? '99+' : unreadNoticeCount }}</i></span><b>{{ tab.label }}</b></button></nav>

      <div v-if="activeMenu" class="menu-page">
        <header class="menu-page-head"><button class="menu-back" @click="selectedTask ? closeTaskDetail() : closeMenu()">‹ {{ selectedTask ? '任务列表' : '返回' }}</button><span v-if="selectedTask" class="menu-page-status">{{ taskStatusText(selectedTask.status) }}</span></header>
        <div v-if="!selectedTask" class="menu-page-title"><span :class="['menu-icon', activeMenu.tone]">{{ activeMenu.icon }}</span><div><h2>{{ activeMenu.name }}</h2><p>{{ activeMenuIsProduction ? `已从 MES 后端查询到 ${activeMenuTotal} 条数据` : activeMenu.hint }}</p></div></div>
        <div class="menu-page-body">
        <template v-if="!selectedTask">
        <div v-if="activeMenu.key === 'tasks'" class="task-status-tabs"><button v-for="tab in taskTabs" :key="tab.key" :class="{ active: taskStatusTab === tab.key }" @click="taskStatusTab = tab.key">{{ tab.label }}<b>{{ tab.count }}</b></button></div>
        <div v-if="activeMenuIsProduction" class="business-list"><p v-if="activeMenuLoading" class="sheet-loading">正在查询最新生产数据…</p><template v-else><article v-for="item in visibleMenuItems" :key="item.taskId || item.recordId || item.transOrderId || item.issueId || item.rtId || item.recptId" :class="{ clickable: activeMenu.key === 'tasks' }" @click="activeMenu.key === 'tasks' && openTaskDetail(item)"><div><b>{{ productionItemTitle(item, activeMenu.key) }}</b><small>{{ productionItemText(item, activeMenu.key) || '暂无补充信息' }}</small></div><span>{{ activeMenu.key === 'tasks' ? taskStatusText(item.status) : productionItemProgress(item, activeMenu.key) }}</span></article><p v-if="!visibleMenuItems.length" class="sheet-loading">当前状态下没有可显示的数据</p></template></div>
        <div v-else class="empty-state"><span>移动端入口已就绪</span><small>该业务模块将继续按 MES 现场业务逐步接入。</small></div>
        </template>
        <template v-else><h2 class="task-detail-title">{{ selectedTask.processName }}</h2><p class="task-detail-subtitle">{{ selectedTask.taskCode }} · {{ selectedTask.workstationName }}</p><div class="task-detail-grid"><p><span>生产订单号</span><b>{{ selectedTask.productionOrderCode || '--' }}</b></p><p><span>生产工单号</span><b>{{ selectedTask.workorderCode }}</b></p><p><span>产品编号</span><b>{{ selectedTask.itemCode }}</b></p><p><span>产品名称</span><b>{{ selectedTask.itemName }}</b></p><p class="full"><span>产品描述</span><b>{{ selectedTask.specification || '未维护' }}</b></p><p><span>生产数量</span><b>{{ selectedTask.quantity }}{{ selectedTask.unitOfMeasure }}</b></p><p><span>计划结束时间</span><b>{{ displayTime(selectedTask.plannedEndTime) }}</b></p><p><span>工艺名称</span><b>{{ selectedTask.routeName || '--' }}</b></p><p><span>所需工时</span><b>{{ selectedTask.requiredHours || 0 }} 小时</b></p><p><span>责任人</span><b>{{ selectedTask.assignedUserNick || selectedTask.teamName || '--' }}</b></p><p v-if="selectedTask.actualStartTime"><span>实际开工时间</span><b>{{ displayTime(selectedTask.actualStartTime) }}</b></p><p v-if="selectedTask.actualEndTime"><span>实际完工时间</span><b>{{ displayTime(selectedTask.actualEndTime) }}</b></p></div><section v-if="selectedTask.operationSteps && selectedTask.operationSteps.length" class="operation-steps"><h3>工序操作步骤</h3><ol><li v-for="step in selectedTask.operationSteps" :key="step.contentId"><b>{{ step.orderNum }}. {{ step.contentText }}</b><small v-if="step.device || step.material">{{ [step.device && `设备：${step.device}`, step.material && `物料：${step.material}`].filter(Boolean).join(' · ') }}</small></li></ol></section><button v-if="selectedTask.status === 'RELEASED'" class="primary-button task-action" :disabled="taskActionLoading" @click="operateTask('start')">{{ taskActionLoading ? '提交中…' : '开工' }}</button><div v-else-if="selectedTask.status === 'IN_PROGRESS'" class="task-actions-row"><button class="transfer-button" :disabled="taskActionLoading" @click="openTransfer">转交</button><button class="finish-button task-action" :disabled="taskActionLoading" @click="operateTask('report')">{{ taskActionLoading ? '提交中…' : '报工完成' }}</button></div><div v-else class="task-finished">此任务已完成，报工记录已保存。</div></template>
        </div>
        <div v-if="transferVisible" class="transfer-backdrop" @click.self="transferVisible = false"><section class="transfer-dialog"><h2>转交任务</h2><p class="transfer-sub">选择接手人，确认后任务与责任人将变更为对方</p><input v-model="transferKeyword" class="transfer-search" placeholder="搜索姓名 / 账号 / 部门" /><div class="transfer-list"><button v-for="user in transferCandidates" :key="user.userId" :class="['transfer-user', { selected: transferTarget && transferTarget.userId === user.userId }]" @click="selectTransferTarget(user)"><span class="transfer-avatar">{{ (user.nickName || user.userName || '?').slice(0, 1) }}</span><div><b>{{ user.nickName || user.userName }}</b><small>{{ user.userName }}{{ user.deptName ? ` · ${user.deptName}` : '' }}</small></div><i v-if="transferTarget && transferTarget.userId === user.userId">✓</i></button><p v-if="!transferCandidates.length" class="transfer-empty">没有匹配的员工</p></div><div class="transfer-actions"><button class="transfer-cancel" @click="transferVisible = false">取消</button><button class="primary-button transfer-confirm" :disabled="transferSubmitting" @click="confirmTransfer">{{ transferSubmitting ? '转交中…' : '确认转交' }}</button></div></section></div>
      </div>

      <div v-if="updateVisible && updateInfo" class="update-backdrop"><section class="update-dialog"><span class="update-mark">↑</span><h2>发现新版本 v{{ updateInfo.version }}</h2><p>{{ updateInfo.description }}</p><button class="primary-button" @click="openUpdate">立即更新</button><button v-if="updateInfo.forceUpdate !== 'Y'" class="later-button" @click="updateVisible = false">稍后再说</button></section></div>
    </template>

    <div v-if="loading" class="loading-mask">正在同步 MES 数据…</div>
    <transition name="fade"><div v-if="toast" class="toast">{{ toast }}</div></transition>
  </main>
</template>
