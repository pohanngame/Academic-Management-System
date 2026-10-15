<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import { requestSyncCaptcha, runSync } from '../../services/timetable'
import {
  captchaRequired,
  secretsAfterSync,
  sliderBackgroundSource,
  sliderPayload,
  sliderPieceSource,
  sliderReady,
  sliderRequired,
  syncResultSummary
} from './timetableUiRules'

const step = ref('credentials')
const loading = ref(false)
const captchaInfo = ref(null)
const result = ref(null)

// 只在内存中保存：不写入 localStorage，不做持久化。
const form = reactive({ username: '', password: '', semesterCode: '', captcha: '' })

// 滑块只在内存里记录，不落任何存储。
const slider = reactive({ dragging: false, startX: 0, samples: [], canvasWidth: 0 })
const sliderTrackRef = ref(null)

const needsSlider = computed(() => sliderRequired(captchaInfo.value))
const needsCaptcha = () => captchaRequired(captchaInfo.value)

const backgroundSource = computed(() => sliderBackgroundSource(captchaInfo.value))
const pieceSource = computed(() => sliderPieceSource(captchaInfo.value))
// 小图跟着滑块走，最多不超过背景图宽度。
const pieceOffset = computed(() => {
  const last = slider.samples.length > 0 ? slider.samples[slider.samples.length - 1] : null
  const raw = last ? last.a : 0
  return Math.max(0, Math.min(raw, slider.canvasWidth))
})

function resetSlider() {
  slider.dragging = false
  slider.startX = 0
  slider.samples = []
  slider.canvasWidth = 0
}

function measureCanvas() {
  const element = sliderTrackRef.value
  if (element) {
    slider.canvasWidth = element.clientWidth || 0
  }
}

function beginDrag(event) {
  measureCanvas()
  slider.dragging = true
  slider.samples = []
  slider.startX = event.clientX
  slider.startY = event.clientY
  slider.lastAt = Date.now()
  // 记录起点，认证页轨迹从 a=0、b=0、c=0 开始。
  slider.samples.push({ a: 0, b: 0, c: 0 })
  if (event.currentTarget && event.currentTarget.setPointerCapture) {
    event.currentTarget.setPointerCapture(event.pointerId)
  }
}

function onDrag(event) {
  if (!slider.dragging) return
  const now = Date.now()
  const a = Math.max(0, Math.min(event.clientX - slider.startX, slider.canvasWidth))
  const b = event.clientY - slider.startY
  // c 是距离上一个轨迹点的毫秒数，不是采样序号。
  slider.samples.push({ a, b, c: now - slider.lastAt })
  slider.lastAt = now
}

function endDrag() {
  if (!slider.dragging) return
  slider.dragging = false
}

function clearSecrets() {
  // 只在同步成功后清掉口令与验证码；失败时保留，便于用户直接重试。
  const next = secretsAfterSync(true, form)
  form.password = next.password
  form.captcha = next.captcha
  captchaInfo.value = null
  resetSlider()
}

function restart() {
  clearSecrets()
  step.value = 'credentials'
  result.value = null
}

async function startSync() {
  if (!form.username || !form.password) {
    ElMessage.warning('请填写教务账号与密码')
    return
  }
  if (!form.semesterCode) {
    ElMessage.warning('请填写学期，例如 2026-2027-1')
    return
  }
  loading.value = true
  try {
    captchaInfo.value = await requestSyncCaptcha(form.username)
    step.value = 'captcha'
    resetSlider()
    if (!needsCaptcha()) {
      await submitSync()
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '无法打开教务登录页')
  } finally {
    loading.value = false
  }
}

async function submitSync() {
  if (needsSlider.value && !sliderReady(captchaInfo.value, slider.canvasWidth, slider.samples)) {
    ElMessage.warning('请先拖动滑块完成验证')
    return
  }
  if (needsCaptcha() && !form.captcha) {
    ElMessage.warning('请输入验证码')
    return
  }
  const sliderFields = needsSlider.value
    ? sliderPayload(captchaInfo.value, slider.canvasWidth, slider.samples)
    : {}
  loading.value = true
  try {
    result.value = await runSync({
      attemptId: captchaInfo.value.attemptId,
      semesterCode: form.semesterCode,
      password: form.password,
      captcha: needsCaptcha() ? form.captcha : null,
      ...sliderFields
    })
    ElMessage.success('课表同步完成')
    step.value = 'done'
    // 仅成功时清空：失败时保留已填写的口令与验证码，方便直接重试。
    clearSecrets()
  } catch (error) {
    // 校验失败时后端不会登录：保留滑块轨迹，但要求重新拖动。
    ElMessage.error(error.response?.data?.message || '同步失败')
    resetSlider()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="timetable-sync">
    <h2>课表同步</h2>
    <p class="hint">
      使用教务账号同步一个学期的全校课表。账号密码只用于本次请求，不会被保存、记录或写入备份。
    </p>

    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="notice"
      title="教师姓名与系统的对应关系在「专业与业务角色」页面维护，本页只负责导入。"
    />

    <el-form label-width="100px" class="sync-form" @submit.prevent>
      <el-form-item label="教务账号">
        <el-input v-model="form.username" autocomplete="off" style="width: 260px" />
      </el-form-item>
      <el-form-item label="教务密码">
        <el-input
          v-model="form.password"
          type="password"
          show-password
          autocomplete="new-password"
          style="width: 260px"
        />
      </el-form-item>
      <el-form-item label="学期">
        <el-input v-model="form.semesterCode" placeholder="2026-2027-1" style="width: 200px" />
      </el-form-item>

      <template v-if="needsCaptcha()">
        <el-form-item v-if="!needsSlider" label="验证码">
          <el-input v-model="form.captcha" style="width: 160px" />
        </el-form-item>

        <el-form-item v-else label="滑块验证">
          <div
            ref="sliderTrackRef"
            class="slider-track"
            :class="{ dragging: slider.dragging }"
            @pointerdown="beginDrag"
            @pointermove="onDrag"
            @pointerup="endDrag"
            @pointercancel="endDrag"
            @pointerleave="endDrag"
          >
            <img v-if="backgroundSource" :src="backgroundSource" alt="滑块背景" class="slider-bg" />
            <img
              v-if="pieceSource"
              :src="pieceSource"
              alt="滑块"
              class="slider-piece"
              :style="{ transform: `translateX(${pieceOffset}px)` }"
            />
          </div>
          <div class="slider-hint">按住小图向右拖动，与缺口对齐后提交。</div>
        </el-form-item>
      </template>

      <el-form-item>
        <el-button
          v-if="step === 'credentials'"
          type="primary"
          :loading="loading"
          @click="startSync"
        >
          开始同步
        </el-button>
        <template v-else>
          <el-button type="primary" :loading="loading" @click="submitSync">提交同步</el-button>
          <el-button :disabled="loading" @click="restart">重新填写</el-button>
        </template>
      </el-form-item>
    </el-form>

    <el-alert
      v-if="result"
      :title="syncResultSummary(result)"
      type="success"
      show-icon
      :closable="false"
      class="notice"
    />
  </div>
</template>

<style scoped>
.timetable-sync {
  padding: 16px;
}
.hint {
  color: #6b7280;
  margin-bottom: 16px;
}
.notice {
  margin-bottom: 16px;
}
.sync-form {
  max-width: 720px;
}
.slider-track {
  position: relative;
  width: 300px;
  height: 150px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  cursor: grab;
  user-select: none;
  touch-action: none;
}
.slider-track.dragging {
  cursor: grabbing;
}
.slider-bg {
  display: block;
  width: 100%;
  height: 100%;
}
.slider-piece {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  pointer-events: none;
}
.slider-hint {
  color: #6b7280;
  font-size: 12px;
  margin-top: 6px;
}
</style>
