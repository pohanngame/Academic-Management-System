import test from 'node:test'
import assert from 'node:assert/strict'

import {
  canMarkSuggestedMetricConfirmed,
  findBulkSelectedReference,
  filterBulkConfirmSelection,
  isJournalFieldVisible,
  isValidImpactFactor,
  isValidMetricYear,
  requiresCandidateConfirmation,
  sanitizePaperRatingPayload,
  shouldClearCcfSelection
} from '../src/views/achievements/publicationRatingRules.js'

test('conference payload cannot submit hidden journal metrics', () => {
  const payload = sanitizePaperRatingPayload({ venueType: 'CONFERENCE', issn: 'x', jcrQuartile: 'Q1', journalImpactFactor: 1 })
  assert.equal(payload.issn, null)
  assert.equal(payload.jcrQuartile, null)
  assert.equal(payload.journalImpactFactor, null)
  assert.equal(isJournalFieldVisible('CONFERENCE'), false)
})

test('changing publication venue type invalidates the selected CCF reference', () => {
  assert.equal(shouldClearCcfSelection('JOURNAL', 'CONFERENCE'), true)
  assert.equal(shouldClearCcfSelection('JOURNAL', 'JOURNAL'), false)
  assert.equal(shouldClearCcfSelection('', 'CONFERENCE'), false)
  const payload = sanitizePaperRatingPayload(
    { venueType: 'CONFERENCE', ccfReferenceId: 12, ccfConfirmed: true },
    'JOURNAL'
  )
  assert.equal(payload.ccfReferenceId, null)
  assert.equal(payload.ccfConfirmed, false)
})

test('journal keeps manually entered values and marks them as journal fields', () => {
  const payload = sanitizePaperRatingPayload({ venueType: 'JOURNAL', issn: '1234-5678', jcrQuartile: 'Q2' })
  assert.equal(payload.issn, '1234-5678')
  assert.equal(isJournalFieldVisible('JOURNAL'), true)
})

test('fuzzy and conflicting candidates require explicit selection', () => {
  assert.equal(requiresCandidateConfirmation({ matchMethod: 'FUZZY_CANDIDATE' }), true)
  assert.equal(requiresCandidateConfirmation({ confirmationRequired: true }), true)
  assert.equal(requiresCandidateConfirmation({ matchMethod: 'EXACT_NAME' }), false)
})

test('unconfirmed suggestion cannot become a verified metric', () => {
  assert.equal(canMarkSuggestedMetricConfirmed({ referenceId: 1 }, false), false)
  assert.equal(canMarkSuggestedMetricConfirmed({ referenceId: 1 }, true), true)
  const payload = sanitizePaperRatingPayload({
    venueType: 'JOURNAL', journalMetricReferenceId: 1, metricConfirmed: false,
    journalImpactFactor: 2.5, jcrQuartile: 'Q1'
  })
  assert.equal(payload.journalMetricReferenceId, null)
  assert.equal(payload.journalImpactFactor, null)
})

test('impact factor and year validation rejects unsafe numeric forms', () => {
  assert.equal(isValidImpactFactor('12.345678'), true)
  assert.equal(isValidImpactFactor('-1'), false)
  assert.equal(isValidImpactFactor('Infinity'), false)
  assert.equal(isValidImpactFactor('1.1234567'), false)
  assert.equal(isValidImpactFactor('10000'), false)
  assert.equal(isValidMetricYear(1900, 2026), true)
  assert.equal(isValidMetricYear(2027, 2026), false)
})

test('batch confirmation excludes already confirmed or incomplete rows', () => {
  assert.deepEqual(filterBulkConfirmSelection([
    { paperId: 1, referenceId: 2, status: 'EXACT' },
    { paperId: 2, referenceId: 3, status: 'ALREADY_CONFIRMED' },
    { paperId: 3 }
  ]), [{ paperId: 1, referenceId: 2, status: 'EXACT' }])
  assert.equal(findBulkSelectedReference([
    { paperId: 1, referenceId: 2 },
    { paperId: 4, referenceId: 9 }
  ], 4), 9)
  assert.equal(findBulkSelectedReference([], 4), null)
})
