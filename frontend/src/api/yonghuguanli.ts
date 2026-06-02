// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 获取用户信息 根据用户ID获取用户详细信息 GET /user/${param0} */
export async function getUserById(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.getUserByIdParams,
  options?: { [key: string]: any }
) {
  const { id: param0, ...queryParams } = params
  return request<API.BaseResponseUserVO>(`/user/${param0}`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 获取当前用户信息 获取当前登录用户的详细信息 GET /user/current */
export async function getCurrentUser(options?: { [key: string]: any }) {
  return request<API.BaseResponseUserVO>('/user/current', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 更新当前用户资料 PUT /user/profile */
export async function updateProfile(
  body: API.UserProfileUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseUserVO>('/user/profile', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 修改当前用户密码 PUT /user/password */
export async function updatePassword(
  body: API.PasswordUpdateRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseBoolean>('/user/password', {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 获取当前用户操作日志 GET /user/logs */
export async function listOperationLogs(options?: { [key: string]: any }) {
  return request<API.BaseResponseOperationLogList>('/user/logs', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 获取当前用户存储摘要 GET /user/storage */
export async function getStorageSummary(options?: { [key: string]: any }) {
  return request<API.BaseResponseStorageSummary>('/user/storage', {
    method: 'GET',
    ...(options || {}),
  })
}

/** 用户登录 使用用户名/邮箱和密码登录 POST /user/login */
export async function userLogin(body: API.UserLoginRequest, options?: { [key: string]: any }) {
  return request<API.BaseResponseLoginVO>('/user/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 用户注销 退出当前登录会话 POST /user/logout */
export async function userLogout(options?: { [key: string]: any }) {
  return request<API.BaseResponseBoolean>('/user/logout', {
    method: 'POST',
    ...(options || {}),
  })
}

/** 用户注册 新用户注册账号 POST /user/register */
export async function userRegister(
  body: API.UserRegisterRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseLong>('/user/register', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}
