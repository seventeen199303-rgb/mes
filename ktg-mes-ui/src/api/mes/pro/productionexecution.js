import request from '@/utils/request'

export function listProductionOrders(params) {
  return request({ url: '/mes/pro/productionorder/list', method: 'get', params })
}

export function executionOptions() {
  return request({ url: '/mes/pro/productionorder/options', method: 'get' })
}

export function addProductionOrder(data) {
  return request({ url: '/mes/pro/productionorder', method: 'post', data })
}

export function getProductionOrderSplitOptions(id) {
  return request({ url: `/mes/pro/productionorder/${id}/split-options`, method: 'get' })
}

export function splitProductionOrder(id, data) {
  return request({ url: `/mes/pro/productionorder/${id}/split`, method: 'post', data })
}

export function listProductionPlans() {
  return request({ url: '/mes/pro/productionplan/list', method: 'get' })
}

export function addProductionPlan(data) {
  return request({ url: '/mes/pro/productionplan', method: 'post', data })
}

export function listProductionWorkorders(params) {
  return request({ url: '/mes/pro/productionworkorder/list', method: 'get', params })
}

export function getProductionWorkorder(workorderId) {
  return request({ url: `/mes/pro/productionworkorder/${workorderId}`, method: 'get' })
}

export function decomposeProductionWorkorder(workorderId, data) {
  return request({ url: `/mes/pro/productionworkorder/${workorderId}/decompose`, method: 'post', data })
}

export function revokeProductionWorkorder(workorderId) {
  return request({ url: `/mes/pro/productionworkorder/${workorderId}/revoke`, method: 'post' })
}

export function forceFinishProductionWorkorder(workorderId) {
  return request({ url: `/mes/pro/productionworkorder/${workorderId}/force-finish`, method: 'post' })
}

export function listExecutionTasks(params) {
  return request({ url: '/mes/pro/executiontask/list', method: 'get', params })
}

export function getExecutionTask(taskId) {
  return request({ url: `/mes/pro/executiontask/${taskId}`, method: 'get' })
}

export function listDispatchTasks(params) {
  return request({ url: '/mes/pro/dispatch/list', method: 'get', params })
}

export function dispatchTask(taskId, data) {
  return request({ url: `/mes/pro/dispatch/${taskId}`, method: 'post', data })
}

export function revokeDispatchTask(taskId) {
  return request({ url: `/mes/pro/dispatch/${taskId}/revoke`, method: 'post' })
}

export function listWorkstationTeams() {
  return request({ url: '/mes/pro/workstationteam/list', method: 'get' })
}

export function addWorkstationTeam(data) {
  return request({ url: '/mes/pro/workstationteam', method: 'post', data })
}

export function updateWorkstationTeam(data) {
  return request({ url: '/mes/pro/workstationteam', method: 'put', data })
}

export function delWorkstationTeam(recordId) {
  return request({ url: `/mes/pro/workstationteam/${recordId}`, method: 'delete' })
}

export function reportDashboard() {
  return request({ url: '/mes/pro/report/dashboard', method: 'get' })
}
