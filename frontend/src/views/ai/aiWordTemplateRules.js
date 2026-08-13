export const WORD_TEMPLATE_TYPES = Object.freeze([
  'SYSTEM_GENERAL',
  'TEACHER_ACHIEVEMENT',
  'SIMPLE_REPORT',
  'CUSTOM'
])

export const WORD_TEMPLATE_LABELS = Object.freeze({
  SYSTEM_GENERAL: '系统通用模板（推荐）',
  TEACHER_ACHIEVEMENT: '教师成果汇总模板',
  SIMPLE_REPORT: '简洁报告模板',
  CUSTOM: '自定义 Word 模板'
})

export const WORD_TEMPLATE_ERROR_MESSAGES = Object.freeze({
  VALID_EXACT_PLACEHOLDER: '格式正常，已找到正文插入位置',
  PLACEHOLDER_MISSING: '未检测到正文插入位置',
  MULTIPLE_PLACEHOLDERS: '检测到多个正文插入位置，请只保留一个 {{aiContent}}',
  UNSUPPORTED_PLACEHOLDER_LOCATION: '正文插入位置不能位于页眉、页脚、文本框、批注、修订、内容控件或复杂域',
  UNKNOWN_TEMPLATE_TAG: '模板中存在不支持的占位标签，仅支持 {{aiContent}}',
  INVALID_DOCX: '文件不是可解析的 DOCX，或文件结构已损坏',
  ENCRYPTED_DOCX: '不支持加密、需要打开密码或旧版 DOC 文件',
  FILE_TOO_LARGE: 'Word 模板超过允许的上传大小',
  UNSUPPORTED_FILE_TYPE: '仅支持 DOCX 文件，不支持 DOC、DOCM、RTF 或 HTML'
})

export function defaultWordTemplateSelection() {
  return { templateType: 'SYSTEM_GENERAL', templateFileId: null }
}

export function normalizeWordTemplateSelection(templateType, templateFileId) {
  const type = WORD_TEMPLATE_TYPES.includes(templateType) ? templateType : 'SYSTEM_GENERAL'
  return {
    templateType: type,
    templateFileId: type === 'CUSTOM' ? templateFileId ?? null : null
  }
}

export function templateValidationMessage(code, fallback = 'Word 模板验证失败') {
  return WORD_TEMPLATE_ERROR_MESSAGES[code] || fallback
}

export function taskWordTemplateLabel(task) {
  const type = task?.templateType || (task?.templateFileId ? 'CUSTOM' : 'SYSTEM_GENERAL')
  if (type !== 'CUSTOM') {
    return WORD_TEMPLATE_LABELS[type] || WORD_TEMPLATE_LABELS.SYSTEM_GENERAL
  }
  return `${task.templateFileName || `模板 #${task.templateFileId}`}${task.templateDeleted ? '（已删除）' : ''}`
}

export function afterDeletingTemplate(selection, deletedFileId) {
  if (selection.templateType === 'CUSTOM' && selection.templateFileId === deletedFileId) {
    return defaultWordTemplateSelection()
  }
  return normalizeWordTemplateSelection(selection.templateType, selection.templateFileId)
}

export async function runCustomTemplateUpload(file, upload, confirmAppend) {
  const inspected = await upload(file, false)
  if (inspected.validation?.code !== 'PLACEHOLDER_MISSING') {
    return { cancelled: false, response: inspected }
  }
  const confirmed = await confirmAppend()
  if (!confirmed) {
    return { cancelled: true, response: inspected }
  }
  const saved = await upload(file, true)
  return { cancelled: false, response: saved }
}

export async function downloadErrorMessage(error, fallback) {
  const data = error?.response?.data
  if (data instanceof Blob) {
    try {
      const payload = JSON.parse(await data.text())
      return payload.message || fallback
    } catch {
      return fallback
    }
  }
  return data?.message || fallback
}
