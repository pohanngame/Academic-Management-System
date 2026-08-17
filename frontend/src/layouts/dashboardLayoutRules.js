export const SIDEBAR_COLLAPSED_KEY = 'academic_profile_sidebar_collapsed'

export function readSidebarCollapsed(value) {
  return value === 'true'
}

export function saveSidebarCollapsed(collapsed, setItem = localStorage.setItem.bind(localStorage)) {
  setItem(SIDEBAR_COLLAPSED_KEY, String(Boolean(collapsed)))
}

export function sidebarWidth(collapsed) {
  return collapsed ? '64px' : '248px'
}
