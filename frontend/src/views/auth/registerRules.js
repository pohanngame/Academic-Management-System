const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const messages = {
  username: '用户名需为 3–64 个字符',
  email: '请输入合法邮箱，最多 128 个字符',
  password: '密码需为 8–64 个字符',
  confirmPassword: '两次输入的密码不一致',
  displayName: '请输入姓名，最多 128 个字符',
  department: '部门最多 128 个字符',
  title: '职称最多 128 个字符'
}

export function registrationFieldError(field, value, form = {}) {
  const text = typeof value === 'string' ? value : ''
  const normalized = field === 'password' || field === 'confirmPassword' ? text : text.trim()
  if (field === 'username' && (normalized.length < 3 || normalized.length > 64)) return messages.username
  if (field === 'email' && (!EMAIL_PATTERN.test(normalized) || normalized.length > 128)) return messages.email
  if (field === 'password' && (text.length < 8 || text.length > 64)) return messages.password
  if (field === 'confirmPassword' && (!text || text !== form.password)) return messages.confirmPassword
  if (field === 'displayName' && (!normalized || normalized.length > 128)) return messages.displayName
  if (field === 'department' && normalized.length > 128) return messages.department
  if (field === 'title' && normalized.length > 128) return messages.title
  return ''
}

export function normalizeRegistrationPayload(form) {
  return {
    ...form,
    username: form.username.trim(),
    email: form.email.trim(),
    displayName: form.displayName.trim(),
    department: form.department.trim(),
    title: form.title.trim()
  }
}

export function shouldSubmitRegistration({ valid, loading }) {
  return valid && !loading
}
