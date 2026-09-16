import request from '@/utils/request'

// 查询产品销售退货单列表
export function listRtsales(query) {
  return request({
    url: '/mes/wm/rtsalse/list',
    method: 'get',
    params: query
  })
}

// 查询产品销售退货单详细
export function getRtsales(rtId) {
  return request({
    url: '/mes/wm/rtsalse/' + rtId,
    method: 'get'
  })
}

// 新增产品销售退货单
export function addRtsales(data) {
  return request({
    url: '/mes/wm/rtsalse',
    method: 'post',
    data: data
  })
}

// 修改产品销售退货单
export function updateRtsales(data) {
  return request({
    url: '/mes/wm/rtsalse',
    method: 'put',
    data: data
  })
}

// 删除产品销售退货单
export function delRtsales(rtId) {
  return request({
    url: '/mes/wm/rtsalse/' + rtId,
    method: 'delete'
  })
}

export function execute(rtId){
  return request({
    url: '/mes/wm/rtsalse/'+rtId,
    method: 'put'
  })
}
