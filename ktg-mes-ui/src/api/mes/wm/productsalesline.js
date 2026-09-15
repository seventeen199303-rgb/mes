import request from '@/utils/request'

// 查询产品销售出库行列表
export function listProductsalesline(query) {
  return request({
    url: '/mes/wm/productsalseline/list',
    method: 'get',
    params: query
  })
}

export function listWithDetail(query) {
  return request({
    url: '/mes/wm/productsalseline/list',
    method: 'get',
    params: query
  })
}

// 查询产品销售出库行详细
export function getProductsalesline(lineId) {
  return request({
    url: '/mes/wm/productsalseline/' + lineId,
    method: 'get'
  })
}

// 新增产品销售出库行
export function addProductsalesline(data) {
  return request({
    url: '/mes/wm/productsalseline',
    method: 'post',
    data: data
  })
}

// 修改产品销售出库行
export function updateProductsalesline(data) {
  return request({
    url: '/mes/wm/productsalseline',
    method: 'put',
    data: data
  })
}

// 删除产品销售出库行
export function delProductsalesline(lineId) {
  return request({
    url: '/mes/wm/productsalseline/' + lineId,
    method: 'delete'
  })
}
