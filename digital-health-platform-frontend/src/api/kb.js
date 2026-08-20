import http from '../utils/request'

// 本地知识库 AI 问答(RAG)。/api/v1/kb/** 需 JWT + 管理员角色(@RequiresRole ADMIN)。
// 共享 http 已自动带 Authorization 头、baseURL=/api/v1、并解包后端 R<T> → 直接返回 data。
// payload.history:最近若干轮对话(多轮记忆,使追问能结合上文);opts:如 { silent } 控制错误提示
// 超时单独提到 90s:作答要走嵌入(bge-m3)+ LLM(DeepSeek-V3,单次最多 60s),全局默认 15s 必超时。
export const ask = (query, payload = {}, opts = {}) =>
  http.post('/kb/ask', { query, topK: payload.topK, history: payload.history }, { timeout: 90000, ...opts })
export const ingest = (data) => http.post('/kb/ingest', data)
export const listDocs = () => http.get('/kb/docs')
export const deleteDoc = (id) => http.delete(`/kb/docs/${id}`)
export const rebuild = () => http.post('/kb/rebuild')
export const pageChatLogs = (params) => http.get('/kb/chat-logs', { params })
