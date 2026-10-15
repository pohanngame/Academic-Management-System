const SLOT_LABELS = {
  '0102': '第 1-2 节',
  '0304': '第 3-4 节',
  '0506': '第 5-6 节',
  '0708': '第 7-8 节',
  '0910': '第 9-10 节',
  '1112': '第 11-12 节'
}

// 显式固定顺序：数字样式的字符串键会被 JS 当作整数下标提前，
// 直接 Object.keys(SLOT_LABELS) 会把 '1112' 排到最前面。
const SLOT_ORDER = ['0102', '0304', '0506', '0708', '0910', '1112']

const DAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

const REASON_LABELS = {
  NO_TIMETABLE_MAPPING: '未建立课表映射',
  ABSENT_FROM_IMPORT: '本学期导入中没有该教师'
}

export const TIMETABLE_SLOT_CODES = SLOT_ORDER
export const TIMETABLE_DAY_LABELS = DAY_LABELS

export function slotLabel(slotCode) {
  return SLOT_LABELS[slotCode] || slotCode
}

export function dayLabel(dayOfWeek) {
  return DAY_LABELS[dayOfWeek - 1] || `第 ${dayOfWeek} 天`
}

export function excludedReasonLabel(reason) {
  return REASON_LABELS[reason] || reason
}

/**
 * 把后端返回的 42 格铺成 7 天 x 6 节次组的表格。
 * 缺格按“不空闲”显示，避免把缺数据显示成空闲。
 */
export function buildSlotGrid(slots = [], dayLabels = DAY_LABELS) {
  const byKey = new Map()
  for (const slot of slots || []) {
    byKey.set(`${slot.dayOfWeek}-${slot.slotCode}`, slot)
  }
  return dayLabels.map((label, index) => ({
    dayOfWeek: index + 1,
    label,
    cells: SLOT_ORDER.map((slotCode) => ({
      slotCode,
      slot: byKey.get(`${index + 1}-${slotCode}`) || null
    }))
  }))
}

/** 单元格展示文案：空闲 / 有课（列出占用教师与课程）。 */
export function cellSummary(slot) {
  if (!slot) return '无数据'
  if (slot.free) return '空闲'
  const occupancies = slot.occupancies || []
  if (occupancies.length === 0) return '不可用'
  return occupancies
    .map((entry) => {
      const teacher = entry.teacherDisplayName || `教师 ${entry.teacherId}`
      const course = entry.courseName || '有课'
      const room = entry.roomName ? `（${entry.roomName}）` : ''
      return `${teacher}：${course}${room}`
    })
    .join('；')
}

export function cellTone(slot) {
  if (!slot) return 'unknown'
  return slot.free ? 'free' : 'busy'
}

/** 没有该学期数据时给出一条明确提示，绝不能让用户以为是全员空闲。 */
export function missingImportNotice(response) {
  if (!response || !response.missingImport) return ''
  return '该学期还没有课表数据，全部时段都不能判定为空闲，请先完成课表同步。'
}

/** 同步第二步是否需要验证码输入框。 */
export function captchaRequired(captchaResponse) {
  return Boolean(captchaResponse && captchaResponse.captchaRequired)
}

/** 后端是否要求滑块验证。 */
export function sliderRequired(captchaResponse) {
  return Boolean(captchaResponse && captchaResponse.sliderRequired)
}

function imageSource(base64) {
  if (!base64) return ''
  return `data:image/png;base64,${base64}`
}

/** 滑块背景图，直接用作 <img src>。 */
export function sliderBackgroundSource(captchaResponse) {
  return imageSource(captchaResponse && captchaResponse.sliderBackgroundBase64)
}

/** 滑块小图（要拖动的那块）。 */
export function sliderPieceSource(captchaResponse) {
  return imageSource(captchaResponse && captchaResponse.sliderPieceBase64)
}

/**
 * 滑动过程中的轨迹点，字段名与认证页滑块脚本一致。
 * a 是水平位移，b 是纵向位移，c 是相对上一个点的毫秒数。
 * 只保留整数，避免浮点噪声让教务判为机器轨迹。
 */
export function buildSliderTracks(samples = []) {
  // Math.round(-0.4) 会得到 -0；归一成 0，避免序列化出怪异值。
  const round = (value) => {
    const rounded = Math.round(value || 0)
    return rounded === 0 ? 0 : rounded
  }
  return (samples || []).map((sample) => ({
    a: round(sample.a),
    b: round(sample.b),
    c: round(sample.c)
  }))
}

/**
 * 组装第二步提交体里的滑块字段。
 * 不需要滑块时三者都不提交（保持原有流程）。
 * 拖动距离取最后一次采样的 x，而不是浏览器事件里的坐标。
 */
export function sliderPayload(captchaResponse, canvasWidth, samples = []) {
  if (!sliderRequired(captchaResponse)) {
    return { sliderCanvasWidth: null, sliderMove: null, sliderTracks: null }
  }
  const tracks = buildSliderTracks(samples)
  const last = tracks.length > 0 ? tracks[tracks.length - 1] : null
  return {
    sliderCanvasWidth: canvasWidth == null ? null : Math.round(canvasWidth),
    sliderMove: last ? last.a : null,
    sliderTracks: tracks
  }
}

/** 滑块已具备提交条件：宽度、位移都给且至少有 1 个轨迹点。 */
export function sliderReady(captchaResponse, canvasWidth, samples = []) {
  const payload = sliderPayload(captchaResponse, canvasWidth, samples)
  return payload.sliderCanvasWidth != null
    && payload.sliderMove != null
    && payload.sliderTracks.length > 0
}

/** 同步结果摘要。unmappedNames 是本学期总表里没有映射的教师。 */
export function syncResultSummary(result) {
  if (!result) return ''
  const names = result.unmappedNames || []
  const base = `学期 ${result.semesterCode}：已保存 ${result.teacherCount} 位教师、${result.cellCount} 条课程记录`
  if (names.length === 0) return base
  return `${base}；另有 ${names.length} 位教师未建立映射，已跳过：${names.join('、')}`
}

export function excludedTeacherRows(excludedTeachers = []) {
  return (excludedTeachers || []).map((entry) => ({
    sourceName: entry.sourceName,
    reason: entry.reason,
    reasonLabel: excludedReasonLabel(entry.reason)
  }))
}

/**
 * 同步结束后是否清空口令与验证码。
 * 只有成功才清；失败要保留，便于用户直接重试。
 */
export function shouldClearSecrets(succeeded) {
  return succeeded === true
}

/** 清空后的敏感字段值（失败时保持原值不动）。 */
export function secretsAfterSync(succeeded, current) {
  if (!shouldClearSecrets(succeeded)) {
    return { password: current.password, captcha: current.captcha }
  }
  return { password: '', captcha: '' }
}
