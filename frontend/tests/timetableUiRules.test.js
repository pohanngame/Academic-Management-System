import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildSlotGrid,
  buildSliderTracks,
  captchaRequired,
  cellSummary,
  cellTone,
  excludedReasonLabel,
  excludedTeacherRows,
  missingImportNotice,
  secretsAfterSync,
  shouldClearSecrets,
  sliderBackgroundSource,
  sliderPayload,
  sliderPieceSource,
  sliderReady,
  sliderRequired,
  slotLabel,
  syncResultSummary,
  TIMETABLE_DAY_LABELS,
  TIMETABLE_SLOT_CODES
} from '../src/views/timetable/timetableUiRules.js'

test('six slot groups and seven days are fixed', () => {
  assert.deepEqual(TIMETABLE_SLOT_CODES, ['0102', '0304', '0506', '0708', '0910', '1112'])
  assert.equal(TIMETABLE_DAY_LABELS.length, 7)
  assert.equal(slotLabel('0102'), '第 1-2 节')
})

test('grid is 7 days by 6 slot groups with the right cells attached', () => {
  const slots = [
    { dayOfWeek: 1, slotCode: '0102', free: false, occupancies: [] },
    { dayOfWeek: 7, slotCode: '1112', free: true, occupancies: [] }
  ]

  const grid = buildSlotGrid(slots)

  assert.equal(grid.length, 7)
  assert.equal(grid[0].cells.length, 6)
  assert.equal(grid[0].cells[0].slot.free, false)
  assert.equal(grid[6].cells[5].slot.free, true)
  // 缺失的格子不能变成空闲。
  assert.equal(grid[0].cells[1].slot, null)
  assert.equal(cellTone(grid[0].cells[1].slot), 'unknown')
})

test('a missing slot never renders as free', () => {
  assert.equal(cellSummary(null), '无数据')
  assert.equal(cellTone(null), 'unknown')

  const grid = buildSlotGrid([])
  assert.equal(grid.length, 7)
  assert.equal(cellSummary(grid[0].cells[0].slot), '无数据')
})

test('busy cell lists occupying teachers with course and room', () => {
  const slot = {
    dayOfWeek: 1,
    slotCode: '0102',
    free: false,
    occupancies: [{ teacherId: 7, teacherDisplayName: '张三', courseName: '数据结构', roomName: '教一101' }]
  }

  assert.equal(cellSummary(slot), '张三：数据结构（教一101）')
  assert.equal(cellTone(slot), 'busy')
})

test('free cell renders as free', () => {
  assert.equal(cellSummary({ free: true, occupancies: [] }), '空闲')
  assert.equal(cellTone({ free: true, occupancies: [] }), 'free')
})

test('missing import is surfaced and never shown as all-free', () => {
  assert.match(missingImportNotice({ missingImport: true }), /还没有课表数据/)
  assert.equal(missingImportNotice({ missingImport: false }), '')
  assert.equal(missingImportNotice(null), '')
})

test('both excluded reasons are labelled distinctly', () => {
  assert.equal(excludedReasonLabel('NO_TIMETABLE_MAPPING'), '未建立课表映射')
  assert.equal(excludedReasonLabel('ABSENT_FROM_IMPORT'), '本学期导入中没有该教师')

  const rows = excludedTeacherRows([
    { sourceName: '李四', reason: 'NO_TIMETABLE_MAPPING' },
    { sourceName: '王五', reason: 'ABSENT_FROM_IMPORT' }
  ])
  assert.equal(rows.length, 2)
  assert.equal(rows[0].reasonLabel, '未建立课表映射')
  assert.equal(rows[1].reasonLabel, '本学期导入中没有该教师')
})

test('captcha is only requested when the backend asks for it', () => {
  assert.equal(captchaRequired({ captchaRequired: true }), true)
  assert.equal(captchaRequired({ captchaRequired: false }), false)
  assert.equal(captchaRequired(null), false)
})

test('slider images come from the two new fields only when the slider is required', () => {
  const response = {
    sliderRequired: true,
    sliderBackgroundBase64: 'QkFDSw==',
    sliderPieceBase64: 'UElFQ0U='
  }
  assert.equal(sliderRequired(response), true)
  assert.equal(sliderRequired({ sliderRequired: false }), false)
  assert.equal(sliderRequired(null), false)
  assert.equal(sliderBackgroundSource(response), 'data:image/png;base64,QkFDSw==')
  assert.equal(sliderPieceSource(response), 'data:image/png;base64,UElFQ0U=')
  // 没有图时给空串，模板里 v-if 就不会渲染破损图片。
  assert.equal(sliderBackgroundSource({}), '')
  assert.equal(sliderPieceSource(null), '')
})

test('slider tracks keep only integer a/b/c', () => {
  const tracks = buildSliderTracks([
    { a: 0, b: 0, c: 0 },
    { a: 12.7, b: -0.4, c: 33.2 }
  ])
  assert.deepEqual(tracks, [
    { a: 0, b: 0, c: 0 },
    { a: 13, b: 0, c: 33 }
  ])
  assert.equal('x' in tracks[1], false)
})

test('slider payload is submitted only when the slider is required', () => {
  const required = { sliderRequired: true }
  const samples = [
    { a: 0, b: 0, c: 0 },
    { a: 42, b: 0, c: 20 }
  ]
  const payload = sliderPayload(required, 300, samples)
  assert.equal(payload.sliderCanvasWidth, 300)
  // 位移取最后一次采样的 x。
  assert.equal(payload.sliderMove, 42)
  assert.equal(payload.sliderTracks.length, 2)
  assert.equal(sliderReady(required, 300, samples), true)
  // 没拖动过就不算就绪。
  assert.equal(sliderReady(required, 300, []), false)
  assert.equal(sliderReady(required, null, samples), false)
})

test('without the slider nothing extra is submitted', () => {
  const payload = sliderPayload({ sliderRequired: false }, 300, [{ a: 10, b: 0, c: 1 }])
  assert.deepEqual(payload, {
    sliderCanvasWidth: null,
    sliderMove: null,
    sliderTracks: null
  })
  assert.equal(sliderReady({ sliderRequired: false }, 300, [{ a: 10, b: 0, c: 1 }]), false)
  assert.deepEqual(sliderPayload(null, 300, []).sliderTracks, null)
})

test('sync summary reports saved counts and skipped unmapped names', () => {
  const summary = syncResultSummary({
    semesterCode: '2026-2027-1',
    teacherCount: 3,
    cellCount: 20,
    unmappedNames: ['李四', '王五']
  })

  assert.match(summary, /已保存 3 位教师、20 条课程记录/)
  assert.match(summary, /李四、王五/)
  assert.equal(syncResultSummary(null), '')
})

test('a failed sync keeps the password and captcha for retry', () => {
  const current = { password: 'secret', captcha: '8899' }

  assert.equal(shouldClearSecrets(false), false)
  assert.deepEqual(secretsAfterSync(false, current), { password: 'secret', captcha: '8899' })
})

test('only a successful sync clears the password and captcha', () => {
  assert.equal(shouldClearSecrets(true), true)
  assert.deepEqual(secretsAfterSync(true, { password: 'secret', captcha: '8899' }),
    { password: '', captcha: '' })
})

test('clearing is not triggered by a missing success flag', () => {
  // 报错路径可能拿不到明确的成功标记，此时必须保留。
  assert.equal(shouldClearSecrets(undefined), false)
  assert.deepEqual(secretsAfterSync(undefined, { password: 'secret', captcha: '8899' }),
    { password: 'secret', captcha: '8899' })
})
