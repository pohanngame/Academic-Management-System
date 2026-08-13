export function normalizeSourceTeacherSelection(ids, selfTeacherId) {
  const unique = [...new Set((ids || []).filter((id) => Number.isInteger(id) && id > 0))]
  return unique.length === 1 && unique[0] === selfTeacherId ? [] : unique
}

export function canSelectAllSources(teachers) {
  return (teachers || []).length <= 50
}

export function selectAllSourceIds(teachers) {
  return [...new Set((teachers || []).map((teacher) => teacher.teacherId))]
}

export function reconcileSourceTeacherSelection(ids, teachers) {
  const available = new Set((teachers || []).map((teacher) => teacher.teacherId))
  return [...new Set((ids || []).filter((id) => available.has(id)))]
}

export function sourceTeacherOptionLabel(teacher) {
  const identity = `${teacher.displayName}${teacher.self ? '（本人）' : ''} · ${teacher.majorName || '未分配专业'}`
  if (teacher.self) {
    return identity
  }
  return `${identity} · 可用资料 ${teacher.availableResourceCount || 0} · 附件 ${teacher.availableAttachmentCount || 0}`
}
