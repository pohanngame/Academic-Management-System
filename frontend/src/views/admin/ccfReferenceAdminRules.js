export function shouldShowCcfReferenceAdmin(role) {
  return role === 'SUPER_ADMIN'
}

export function canSubmitCcfImport(file, currentPassword, loading) {
  return Boolean(
    !loading &&
    file &&
    file.size > 0 &&
    file.size <= 5 * 1024 * 1024 &&
    file.name?.toLowerCase().endsWith('.json') &&
    currentPassword
  )
}

export function sanitizeCcfImportError(message) {
  if (!message || /[A-Za-z]:\\|\/app\/|\/tmp\//.test(message)) {
    return 'CCF 参考数据导入失败'
  }
  return message
}
