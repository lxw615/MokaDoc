// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 获取文档详情 根据ID获取文档信息 GET /document/${param0} */
export async function getById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getByIdParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.BaseResponseDocumentVO>(`/document/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 更新文档 更新文档名称和描述 PUT /document/${param0} */
export async function update(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateParams,
  body: API.DocumentUpdateRequest,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.BaseResponseDocumentVO>(`/document/${param0}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  })
}

/** 删除文档 逻辑删除文档 DELETE /document/${param0} */
export async function deleteUsingDelete(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteUsingDELETEParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.BaseResponseBoolean>(`/document/${param0}`, {
    method: 'DELETE',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** MD5查重 检查当前用户是否已上传过相同MD5的文件 GET /document/check-md5 */
export async function checkMd5(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.checkMd5Params,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/document/check-md5', {
    method: 'GET',
    params: {
      ...params,
    },
    ...(options || {}),
  })
}

/** 文档列表 获取当前用户的所有文档 GET /document/list */
export async function list(options?: { [key: string]: any }) {
  return request<API.BaseResponseListDocumentVO>('/document/list', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 上传文档 上传文件，自动MD5去重 POST /document/upload */
export async function upload(body: File | FormData, options?: { [key: string]: any }) {
  const formData = body instanceof FormData ? body : new FormData()
  if (body instanceof File) {
    formData.append('file', body)
  }
  return request<API.BaseResponseDocumentVO>('/document/upload', {
    method: 'POST',
    data: formData,
    ...(options || {}),
  })
}
