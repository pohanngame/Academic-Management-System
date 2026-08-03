const ADMIN_HOME = '/dashboard/admin'
const TEACHER_HOME = '/dashboard'

export function homeRouteForRole(role) {
  return role === 'SUPER_ADMIN' ? ADMIN_HOME : TEACHER_HOME
}

export function isDashboardPathAllowed(path, role) {
  if (!path.startsWith('/dashboard')) return true
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
