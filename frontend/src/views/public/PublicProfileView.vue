<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getPublicProfile, publicAvatarUrl } from '../../services/publicProfiles'

const route = useRoute()
const loading = ref(false)
const notFound = ref(false)
const profileData = ref(null)

const profile = computed(() => profileData.value?.profile || {})
const avatarUrl = computed(() => publicAvatarUrl(profile.value.avatarFileId))

const sections = computed(() => {
  if (!profileData.value) {
    return []
  }
  return [
    {
      key: 'academicQualifications',
      title: '学历',
      items: profileData.value.academicQualifications,
      formatter: formatAcademicQualification
    },
    {
      key: 'teachingAreas',
      title: '教学方向',
      items: profileData.value.teachingAreas,
      formatter: formatArea
    },
    {
      key: 'researchAreas',
      title: '研究方向',
      items: profileData.value.researchAreas,
      formatter: formatArea
    },
    {
      key: 'professionalServices',
      title: '专业服务',
      items: profileData.value.professionalServices,
      formatter: formatProfessionalService
    },
    {
      key: 'workingExperiences',
      title: '工作经历',
      items: profileData.value.workingExperiences,
      formatter: formatWorkingExperience
    },
    {
      key: 'projects',
      title: '科研项目',
      items: profileData.value.projects,
      formatter: formatProject
    },
    {
      key: 'teachingCourses',
      title: '授课',
      items: profileData.value.teachingCourses,
      formatter: formatTeachingCourse
    },
    {
      key: 'papers',
      title: '学术论文',
      items: profileData.value.papers,
      formatter: formatPaper
    },
    {
      key: 'patents',
      title: '专利',
      items: profileData.value.patents,
      formatter: formatPatent
    },
    {
      key: 'certificates',
      title: '证书',
      items: profileData.value.certificates,
      formatter: formatCertificate
    }
  ].filter((section) => section.items?.length)
})

async function loadPublicProfile() {
  loading.value = true
  notFound.value = false
  try {
    profileData.value = await getPublicProfile(route.params.slug)
  } catch (error) {
    if (error.response?.status === 404) {
      notFound.value = true
    }
  } finally {
    loading.value = false
  }
}

function formatRange(startDate, endDate) {
  if (startDate && endDate) {
    return `${startDate} - ${endDate}`
  }
  return startDate || endDate || ''
}

function details(...values) {
  return values.filter(Boolean).join(' | ')
}

function formatAcademicQualification(item) {
  return {
    title: details(item.degree, item.institution),
    meta: details(item.major, formatRange(item.startDate, item.endDate)),
    description: item.description
  }
}

function formatArea(item) {
  return {
    title: item.name,
    meta: '',
    description: item.description
  }
}

function formatProfessionalService(item) {
  return {
    title: item.title,
    meta: details(item.organization, item.role, formatRange(item.startDate, item.endDate)),
    description: item.description
  }
}

function formatWorkingExperience(item) {
  return {
    title: item.organization,
    meta: details(item.position, formatRange(item.startDate, item.endDate)),
    description: item.description
  }
}

function formatProject(item) {
  return {
    title: item.projectName,
    meta: details(item.source, item.role, item.status, formatRange(item.startDate, item.endDate)),
    description: item.description
  }
}

function formatTeachingCourse(item) {
  return {
    title: item.courseName,
    meta: details(item.semester, item.className, item.teachingTarget, item.hours ? `${item.hours} 学时` : ''),
    description: item.description
  }
}

function formatPaper(item) {
  return {
    title: item.title,
    meta: details(item.authors, item.publicationName, item.publishYear, item.doi ? `DOI: ${item.doi}` : ''),
    description: details(item.volume ? `Vol. ${item.volume}` : '', item.issue ? `No. ${item.issue}` : '', item.pages)
  }
}

function formatPatent(item) {
  return {
    title: item.patentName,
    meta: details(item.patentNumber, item.patentType, item.status, item.authorizationDate || item.applicationDate),
    description: details(item.inventors, item.description)
  }
}

function formatCertificate(item) {
  return {
    title: item.certificateName,
    meta: details(item.certificateType, item.issuingAuthority, item.issueDate),
    description: item.description
  }
}

onMounted(loadPublicProfile)
</script>

<template>
  <main class="public-profile-page" v-loading="loading">
    <section v-if="notFound" class="empty-state">
      <h1>未找到公开简介</h1>
      <p>该教师简介不存在，或当前未开启公开展示。</p>
    </section>

    <template v-else-if="profileData">
      <header class="profile-header">
        <div class="header-inner">
          <div class="avatar-wrap">
            <img v-if="avatarUrl" :src="avatarUrl" :alt="`${profile.displayName}头像`" />
            <span v-else>{{ profile.displayName?.slice(0, 1) || '师' }}</span>
          </div>
          <div class="identity">
            <h1>{{ profile.displayName }}</h1>
            <div class="identity-line">
              <span v-if="profile.title">{{ profile.title }}</span>
              <span v-if="profile.department">{{ profile.department }}</span>
            </div>
            <dl class="contact-grid">
              <div v-if="profile.phone">
                <dt>联系电话</dt>
                <dd>{{ profile.phone }}</dd>
              </div>
              <div v-if="profile.office">
                <dt>办公室</dt>
                <dd>{{ profile.office }}</dd>
              </div>
              <div v-if="profile.profileEmail">
                <dt>电子信箱</dt>
                <dd>{{ profile.profileEmail }}</dd>
              </div>
            </dl>
          </div>
        </div>
      </header>

      <div class="content-shell">
        <section v-if="profile.biography" class="content-section">
          <h2>个人简介</h2>
          <p class="biography">{{ profile.biography }}</p>
        </section>

        <section
          v-for="section in sections"
          :key="section.key"
          class="content-section"
        >
          <h2>{{ section.title }}</h2>
          <ul class="item-list">
            <li v-for="item in section.items" :key="item.id">
              <h3>{{ section.formatter(item).title }}</h3>
              <p v-if="section.formatter(item).meta" class="item-meta">
                {{ section.formatter(item).meta }}
              </p>
              <p v-if="section.formatter(item).description" class="item-desc">
                {{ section.formatter(item).description }}
              </p>
            </li>
          </ul>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.public-profile-page {
  min-height: 100vh;
  background: #f4f6f8;
  color: #1f2937;
}

.profile-header {
  background: #0f3a5f;
  color: #ffffff;
  border-bottom: 5px solid #c9a227;
}

.header-inner {
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 40px 0 34px;
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 28px;
  align-items: center;
}

.avatar-wrap {
  width: 150px;
  aspect-ratio: 1;
  overflow: hidden;
  border: 4px solid rgba(255, 255, 255, 0.85);
  background: #d8dee6;
  display: grid;
  place-items: center;
  font-size: 56px;
  font-weight: 700;
  color: #0f3a5f;
}

.avatar-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.identity h1 {
  margin: 0 0 10px;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0;
}

.identity-line {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  font-size: 17px;
  color: #e5edf5;
}

.contact-grid {
  margin: 24px 0 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 20px;
}

.contact-grid div {
  min-width: 0;
}

.contact-grid dt {
  font-size: 13px;
  color: #b9c8d7;
  margin-bottom: 5px;
}

.contact-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.content-shell {
  width: min(1120px, calc(100% - 32px));
  margin: 28px auto 56px;
  display: grid;
  gap: 18px;
}

.content-section {
  background: #ffffff;
  border: 1px solid #dfe5eb;
  padding: 24px 28px;
}

.content-section h2 {
  margin: 0 0 18px;
  padding-left: 12px;
  border-left: 4px solid #c9a227;
  font-size: 21px;
  line-height: 1.25;
}

.biography {
  margin: 0;
  line-height: 1.8;
  white-space: pre-wrap;
}

.item-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.item-list li {
  padding: 15px 0;
  border-top: 1px solid #edf1f5;
}

.item-list li:first-child {
  border-top: 0;
  padding-top: 0;
}

.item-list li:last-child {
  padding-bottom: 0;
}

.item-list h3 {
  margin: 0;
  font-size: 16px;
  line-height: 1.5;
}

.item-meta,
.item-desc {
  margin: 7px 0 0;
  color: #526071;
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.empty-state {
  width: min(720px, calc(100% - 32px));
  margin: 80px auto;
  padding: 32px;
  background: #ffffff;
  border: 1px solid #dfe5eb;
  text-align: center;
}

.empty-state h1 {
  margin: 0 0 12px;
  font-size: 26px;
}

.empty-state p {
  margin: 0;
  color: #6b7280;
}

@media (max-width: 760px) {
  .header-inner {
    grid-template-columns: 1fr;
    justify-items: start;
    padding: 28px 0;
  }

  .avatar-wrap {
    width: 118px;
  }

  .identity h1 {
    font-size: 28px;
  }

  .contact-grid {
    grid-template-columns: 1fr;
  }

  .content-section {
    padding: 20px;
  }
}
</style>
