import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  WORD_TEMPLATE_ERROR_MESSAGES,
  WORD_TEMPLATE_TYPES,
  afterDeletingTemplate,
  defaultWordTemplateSelection,
  downloadErrorMessage,
  normalizeWordTemplateSelection,
  runCustomTemplateUpload,
  taskWordTemplateLabel,
  templateValidationMessage
} from '../src/views/ai/aiWordTemplateRules.js'

import {
  canSelectAllSources,
  normalizeSourceTeacherSelection,
  reconcileSourceTeacherSelection,
  sourceTeacherOptionLabel
} from '../src/views/ai/aiSourceTeacherRules.js'

test('defaults to the recommended system template and keeps four modes exclusive', () => {
  assert.deepEqual(defaultWordTemplateSelection(), {
    templateType: 'SYSTEM_GENERAL',
    templateFileId: null
  })
  assert.equal(WORD_TEMPLATE_TYPES.length, 4)
  assert.deepEqual(normalizeWordTemplateSelection('SIMPLE_REPORT', 99), {
    templateType: 'SIMPLE_REPORT',
    templateFileId: null
  })
  assert.deepEqual(normalizeWordTemplateSelection('CUSTOM', 99), {
    templateType: 'CUSTOM',
    templateFileId: 99
  })
})

test('missing placeholder is cancelled without a formal upload', async () => {
  const calls = []
  const outcome = await runCustomTemplateUpload('file', async (_file, append) => {
    calls.push(append)
    return { file: null, validation: { code: 'PLACEHOLDER_MISSING' } }
  }, async () => false)

  assert.equal(outcome.cancelled, true)
  assert.deepEqual(calls, [false])
})

test('confirmation performs exactly one normalized upload', async () => {
  const calls = []
  const outcome = await runCustomTemplateUpload('file', async (_file, append) => {
    calls.push(append)
    return append
      ? { file: { id: 7 }, validation: { code: 'VALID_EXACT_PLACEHOLDER', autoAppended: true } }
      : { file: null, validation: { code: 'PLACEHOLDER_MISSING' } }
  }, async () => true)

  assert.equal(outcome.cancelled, false)
  assert.equal(outcome.response.file.id, 7)
  assert.deepEqual(calls, [false, true])
})

test('deleting the selected custom template returns to system general', () => {
  assert.deepEqual(afterDeletingTemplate({ templateType: 'CUSTOM', templateFileId: 9 }, 9),
    defaultWordTemplateSelection())
})

test('task labels preserve built-in and historical custom snapshots', () => {
  assert.equal(taskWordTemplateLabel({ templateType: 'TEACHER_ACHIEVEMENT' }), '教师成果汇总模板')
  assert.equal(taskWordTemplateLabel({ templateFileId: 8, templateFileName: 'mine.docx' }), 'mine.docx')
})

test('all backend validation codes have actionable Chinese mappings', () => {
  const expected = [
    'VALID_EXACT_PLACEHOLDER', 'PLACEHOLDER_MISSING', 'MULTIPLE_PLACEHOLDERS',
    'UNSUPPORTED_PLACEHOLDER_LOCATION', 'UNKNOWN_TEMPLATE_TAG', 'INVALID_DOCX',
    'ENCRYPTED_DOCX', 'FILE_TOO_LARGE', 'UNSUPPORTED_FILE_TYPE'
  ]
  assert.deepEqual(Object.keys(WORD_TEMPLATE_ERROR_MESSAGES).sort(), expected.sort())
  for (const code of expected) {
    assert.notEqual(templateValidationMessage(code), 'Word 模板验证失败')
  }
})

test('template page keeps single-flight guards, validation details and advanced help collapsed', () => {
  const source = readFileSync(
    new URL('../src/views/ai/AiGenerationView.vue', import.meta.url),
    'utf8'
  )
  assert.match(source, /if \(templateUploading\.value\)/)
  assert.match(source, /if \(templateTesting\.value\)/)
  assert.match(source, /:disabled="!form\.templateFileId"/)
  assert.match(source, /有效占位符：\{\{ templateValidation\.validPlaceholderCount \}\}/)
  assert.match(source, /<el-collapse class="advanced-template-help">/)
  assert.doesNotMatch(source, /<el-collapse[^>]+v-model=/)
  assert.match(source, /selectedAttachmentIds: form\.selectedAttachmentIds/)
  assert.doesNotMatch(source, /getAllTeacherDirectory/)
  assert.match(source, /if \(wordExporting\.value\)/)
  assert.match(source, /if \(pdfExporting\.value\)/)
  assert.match(source, /资料来源人员（留空表示仅使用本人）/)
})

test('AI source selection is deduplicated and select-all is capped at 50', () => {
  assert.deepEqual(normalizeSourceTeacherSelection([7, 7], 7), [])
  assert.deepEqual(normalizeSourceTeacherSelection([7, 8, 8], 7), [7, 8])
  assert.equal(canSelectAllSources(Array.from({ length: 50 }, (_, i) => ({ teacherId: i + 1 }))), true)
  assert.equal(canSelectAllSources(Array.from({ length: 51 }, (_, i) => ({ teacherId: i + 1 }))), false)
})

test('revoked AI sources are removed and remaining grant counts are visible', () => {
  const available = [
    { teacherId: 7, displayName: '本人', self: true, majorName: '计算机', availableResourceCount: 0, availableAttachmentCount: 0 },
    { teacherId: 8, displayName: '教师B', self: false, majorName: '软件工程', availableResourceCount: 2, availableAttachmentCount: 1 }
  ]

  assert.deepEqual(reconcileSourceTeacherSelection([7, 8, 9], available), [7, 8])
  assert.match(sourceTeacherOptionLabel(available[1]), /可用资料 2/)
  assert.match(sourceTeacherOptionLabel(available[1]), /附件 1/)
})

test('blob download errors prefer one safe backend message', async () => {
  const error = { response: { data: new Blob([JSON.stringify({ message: '系统 Word 模板生成结果无效' })]) } }
  assert.equal(await downloadErrorMessage(error, '导出 Word 失败'), '系统 Word 模板生成结果无效')
  assert.equal(await downloadErrorMessage({ response: { data: new Blob(['not-json']) } }, '导出 Word 失败'), '导出 Word 失败')
})
