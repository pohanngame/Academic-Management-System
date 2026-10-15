const ADMIN_HOME = '/dashboard/admin'
const TEACHER_HOME = '/dashboard'

// 教师与超级管理员都能访问的公共路径（如课表同步与公共空闲查询）。
const SHARED_DASHBOARD_PATHS = [
  '/dashboard/timetable-sync',
  '/dashboard/timetable-common-free'
]

export function homeRouteForRole(role) {
  return role === 'SUPER_ADMIN' ? ADMIN_HOME : TEACHER_HOME
}

function isSharedDashboardPath(path) {
  return SHARED_DASHBOARD_PATHS.some(
    (shared) => path === shared || path.startsWith(`${shared}/`))
}

export function isDashboardPathAllowed(path, role) {
  if (!path.startsWith('/dashboard')) return true
  if (isSharedDashboardPath(path)) return true
  const isAdminPath = path === ADMIN_HOME || path.startsWith(`${ADMIN_HOME}/`)
  return role === 'SUPER_ADMIN' ? isAdminPath : !isAdminPath
}

export function safePostLoginTarget(redirect, role) {
  if (typeof redirect !== 'string'
      || !redirect.startsWith('/')
      || redirect.startsWith('//')
      || !isDashboardPathAllowed(redirect, role)) {
    return homeRouteForRole(role)
  }
  return redirect
}
