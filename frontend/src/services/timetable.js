import { http } from './http'

/** 第一步：打开教务认证页并判断是否需要验证码。 */
export async function requestSyncCaptcha(username) {
  const { data } = await http.post('/timetable/sync/captcha', { username })
  return data.data
}

/**
 * 第二步：提交同步。密码只在本次请求体内传递，前端不落任何存储。
 * 需要滑块时额外带上 sliderCanvasWidth、sliderMove、sliderTracks。
 * 绝不能把密码、Cookie、Session、票据写入 localStorage 或日志。
 */
export async function runSync({
  attemptId,
  semesterCode,
  password,
  captcha,
  sliderCanvasWidth = null,
  sliderMove = null,
  sliderTracks = null
}) {
  const body = { attemptId, semesterCode, password, captcha }
  // 不需要滑块时不提交这三个字段，保持原有流程。
  if (sliderCanvasWidth != null && sliderMove != null && sliderTracks != null) {
    body.sliderCanvasWidth = sliderCanvasWidth
    body.sliderMove = sliderMove
    body.sliderTracks = sliderTracks
  }
  // 全校总表约 4MB，实测下载和解析超过默认 15 秒。
  const { data } = await http.post('/timetable/sync', body, { timeout: 120000 })
  return data.data
}

export async function getCommonFreeOptions() {
  const { data } = await http.get('/timetable/common-free/options')
  return data.data
}

export async function queryCommonFree({ semesterCode, week, majorId, teacherIds }) {
  const params = { semesterCode, week }
  if (majorId !== undefined && majorId !== null && majorId !== '') {
    params.majorId = majorId
  } else if (Array.isArray(teacherIds) && teacherIds.length > 0) {
    params.teacherIds = teacherIds.join(',')
  }
  const { data } = await http.get('/timetable/common-free', { params })
  return data.data
}

/**
 * 导出当前查询的 Excel，使用与查询完全相同的筛选参数。
 * 走带鉴权的 blob 下载，避免把 token 拼到 URL 上。
 */
export async function downloadCommonFreeExcel({ semesterCode, week, majorId, teacherIds }) {
  const params = { semesterCode, week }
  if (majorId !== undefined && majorId !== null && majorId !== '') {
    params.majorId = majorId
  } else if (Array.isArray(teacherIds) && teacherIds.length > 0) {
    params.teacherIds = teacherIds.join(',')
  }
  const response = await http.get('/timetable/common-free/excel', {
    params,
    responseType: 'blob'
  })
  const blob = new Blob([response.data], {
    type: response.headers['content-type'] || 'application/octet-stream'
  })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `timetable-common-free-${semesterCode}-week${week}.xlsx`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}
