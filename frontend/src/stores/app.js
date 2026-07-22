import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    appName: '个人学术信息管理与简介展示系统'
  })
})
