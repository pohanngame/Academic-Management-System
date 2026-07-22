import { createRouter, createWebHistory } from 'vue-router'

import DashboardLayout from '../layouts/DashboardLayout.vue'
import { useAuthStore } from '../stores/auth'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/auth/LoginView.vue'
import RegisterView from '../views/auth/RegisterView.vue'
import AcademicRecordListView from '../views/achievements/AcademicRecordListView.vue'
import AiGenerationView from '../views/ai/AiGenerationView.vue'
import BibtexImportView from '../views/bibtex/BibtexImportView.vue'
import DashboardHomeView from '../views/dashboard/DashboardHomeView.vue'
import ExportConfigView from '../views/export/ExportConfigView.vue'
import OcrTaskView from '../views/ocr/OcrTaskView.vue'
import ProfileBlockListView from '../views/profileBlocks/ProfileBlockListView.vue'
import PublicProfileView from '../views/public/PublicProfileView.vue'
import ProfileEditView from '../views/teacher/ProfileEditView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true }
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { guestOnly: true }
    },
    {
      path: '/profiles/:slug',
      name: 'public-profile',
      component: PublicProfileView
    },
    {
      path: '/dashboard',
      component: DashboardLayout,
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'dashboard',
          component: DashboardHomeView
        },
        {
          path: 'profile',
          name: 'teacher-profile',
          component: ProfileEditView
        },
        {
          path: 'academic-qualifications',
          name: 'academic-qualifications',
          component: ProfileBlockListView,
          meta: { blockType: 'academicQualifications' }
        },
        {
          path: 'teaching-areas',
          name: 'teaching-areas',
          component: ProfileBlockListView,
          meta: { blockType: 'teachingAreas' }
        },
        {
          path: 'research-areas',
          name: 'research-areas',
          component: ProfileBlockListView,
          meta: { blockType: 'researchAreas' }
        },
        {
          path: 'professional-services',
          name: 'professional-services',
          component: ProfileBlockListView,
          meta: { blockType: 'professionalServices' }
        },
        {
          path: 'working-experiences',
          name: 'working-experiences',
          component: ProfileBlockListView,
          meta: { blockType: 'workingExperiences' }
        },
        {
          path: 'projects',
          name: 'projects',
          component: AcademicRecordListView,
          meta: { recordType: 'projects' }
        },
        {
          path: 'teaching-courses',
          name: 'teaching-courses',
          component: AcademicRecordListView,
          meta: { recordType: 'teachingCourses' }
        },
        {
          path: 'papers',
          name: 'papers',
          component: AcademicRecordListView,
          meta: { recordType: 'papers' }
        },
        {
          path: 'bibtex-import',
          name: 'bibtex-import',
          component: BibtexImportView
        },
        {
          path: 'ocr',
          name: 'ocr',
          component: OcrTaskView
        },
        {
          path: 'patents',
          name: 'patents',
          component: AcademicRecordListView,
          meta: { recordType: 'patents' }
        },
        {
          path: 'certificates',
          name: 'certificates',
          component: AcademicRecordListView,
          meta: { recordType: 'certificates' }
        },
        {
          path: 'export',
          name: 'export',
          component: ExportConfigView
        },
        {
          path: 'ai-generation',
          name: 'ai-generation',
          component: AiGenerationView
        }
      ]
    }
  ]
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth || to.matched.some((record) => record.meta.requiresAuth)) {
    if (!authStore.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (!authStore.user) {
      try {
        await authStore.fetchCurrentUser()
      } catch {
        authStore.clearSession()
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    }
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { name: 'dashboard' }
  }

  return true
})

export default router
