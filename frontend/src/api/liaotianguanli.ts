// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 智能问答(自动创建会话) POST /chat/ask */
export async function ask(body: API.UserQuestion, options?: { [key: string]: any }) {
  return request<API.SseEmitter>('/chat/ask', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 此处后端没有提供注释 GET /chat/chat/list/${param0} */
export async function listChat(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listChatParams,
  options?: { [key: string]: any }
) {
  const { sessionId: param0, ...queryParams } = params
  return request<API.BaseResponsePageQaMessage>(`/chat/chat/list/${param0}`, {
    method: 'GET',
    params: {
      // pageSize has a default value: 5
      pageSize: '5',
      ...queryParams,
    },
    ...(options || {}),
  })
}

/** 创建会话 POST /chat/session */
export async function createSession(
  body: API.CreateSessionRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseQaSession>('/chat/session', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  })
}

/** 更新会话 PUT /chat/session/${param0} */
export async function updateSession(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.updateSessionParams,
  body: API.UpdateSessionRequest,
  options?: { [key: string]: any }
) {
  const { sessionId: param0, ...queryParams } = params
  return request<API.BaseResponseQaSession>(`/chat/session/${param0}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
    },
    params: { ...queryParams },
    data: body,
    ...(options || {}),
  })
}

/** 删除会话 DELETE /chat/session/${param0} */
export async function deleteSession(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteSessionParams,
  options?: { [key: string]: any }
) {
  const { sessionId: param0, ...queryParams } = params
  return request<API.BaseResponseBoolean>(`/chat/session/${param0}`, {
    method: 'DELETE',
    params: { ...queryParams },
    ...(options || {}),
  })
}

/** 获取会话列表 GET /chat/session/list */
export async function listSessions(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.listSessionsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResponsePageQaSession>('/chat/session/list', {
    method: 'GET',
    params: {
      // pageSize has a default value: 5
      pageSize: '5',
      ...params,
    },
    ...(options || {}),
  })
}

/** 获取回答引用溯源 GET /chat/message/${messageId}/references */
export async function listReferences(
  params: { messageId: number },
  options?: { [key: string]: any }
) {
  const { messageId, ...queryParams } = params
  return request<{ code?: number; data?: any[]; message?: string }>(`/chat/message/${messageId}/references`, {
    method: 'GET',
    params: { ...queryParams },
    ...(options || {}),
  })
}
