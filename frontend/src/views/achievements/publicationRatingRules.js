const JOURNAL_ONLY_FIELDS = [
  'issn', 'eissn', 'jcrDataYear', 'journalImpactFactor', 'jcrQuartile', 'jcrSubject',
  'metricSourceDescription', 'metricSourceUrl', 'journalMetricReferenceId', 'metricConfirmed'
]

export function sanitizePaperRatingPayload(payload, selectedCcfVenueType = '') {
  const result = { ...payload }
  if (result.ccfReferenceId && shouldClearCcfSelection(selectedCcfVenueType, result.venueType)) {
    result.ccfReferenceId = null
    result.ccfConfirmed = false
  }
  if (result.venueType === 'CONFERENCE') {
    for (const field of JOURNAL_ONLY_FIELDS) result[field] = null
  }
  if (result.journalMetricReferenceId && !result.metricConfirmed) {
    for (const field of [
      'journalMetricReferenceId', 'jcrDataYear', 'journalImpactFactor', 'jcrQuartile', 'jcrSubject',
      'metricSourceDescription', 'metricSourceUrl'
    ]) result[field] = null
  }
  if (!result.ccfReferenceId && !result.manualCcfRank) result.ccfConfirmed = false
  return result
}

export function shouldClearCcfSelection(selectedVenueType, nextVenueType) {
  return Boolean(selectedVenueType && nextVenueType && selectedVenueType !== nextVenueType)
}

export function isJournalFieldVisible(venueType) {
  return venueType === 'JOURNAL'
}

export function requiresCandidateConfirmation(candidate) {
  return Boolean(candidate?.confirmationRequired || candidate?.matchMethod === 'FUZZY_CANDIDATE')
}

export function isValidImpactFactor(value) {
  if (value === null || value === undefined || value === '') return true
  const text = String(value)
  const number = Number(text)
  return Number.isFinite(number) && number >= 0 && /^\d{1,4}(?:\.\d{1,6})?$/.test(text)
}

export function isValidMetricYear(value, currentYear = new Date().getFullYear()) {
  if (value === null || value === undefined || value === '') return true
  return Number.isInteger(Number(value)) && Number(value) >= 1900 && Number(value) <= currentYear
}

export function canMarkSuggestedMetricConfirmed(suggestion, userConfirmed) {
  return Boolean(suggestion && userConfirmed)
}

export function filterBulkConfirmSelection(items) {
  return items.filter((item) => item?.paperId && item?.referenceId && item.status !== 'ALREADY_CONFIRMED')
}

export function findBulkSelectedReference(selections, paperId) {
  return selections.find((item) => item?.paperId === paperId)?.referenceId ?? null
}
