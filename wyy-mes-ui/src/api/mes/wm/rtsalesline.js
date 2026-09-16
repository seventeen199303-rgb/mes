import request from '@/utils/request'

// 查询产品销售退货行列表
export function listRtsalesline(query) {
  return request({
    url: '/mes/wm/rtsalseline/list',
    method: 'get',
    params: query
  })
}

// 查询产品销售退货行详细
export function getRtsalesline(lineId) {
  return request({
    url: '/mes/wm/rtsalseline/' + lineId,
    method: 'get'
  })
}

// 查询产品销售退货行列表（带明细信息）
export function listWithDetail(query) {
  return request({
    url: '/mes/wm/rtsalseline/list',
    method: 'get',
    params: query
  })
}


// 新增产品销售退货行
export function addRtsalesline(data) {
  return request({
    url: '/mes/wm/rtsalseline',
    method: 'post',
    data: data
  })
}

// 修改产品销售退货行
export function updateRtsalesline(data) {
  return request({
    url: '/mes/wm/rtsalseline',
    method: 'put',
    data: data
  })
}

// 删除产品销售退货行
export function delRtsalesline(lineId) {
  return request({
    url: '/mes/wm/rtsalseline/' + lineId,
    method: 'delete'
  })
}
