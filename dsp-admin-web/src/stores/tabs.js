import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useTabStore = defineStore('tabs', () => {
  const tabs = ref([
    { fullPath: '/interface', name: '接口管理' }
  ])
  const activeTab = ref('/interface')

  const cachedNames = computed(() => tabs.value.map(t => t.name))

  function addTab(route) {
    const exists = tabs.value.find(t => t.fullPath === route.fullPath)
    if (!exists) {
      tabs.value.push({
        fullPath: route.fullPath,
        name: route.name || route.path
      })
    }
    activeTab.value = route.fullPath
  }

  function removeTab(fullPath) {
    if (fullPath === '/interface') return
    const idx = tabs.value.findIndex(t => t.fullPath === fullPath)
    if (idx === -1) return
    tabs.value.splice(idx, 1)
    if (activeTab.value === fullPath) {
      const newIdx = Math.min(idx, tabs.value.length - 1)
      activeTab.value = tabs.value[newIdx]?.fullPath || '/interface'
    }
  }

  function removeOther(fullPath) {
    tabs.value = tabs.value.filter(t => t.fullPath === fullPath || t.fullPath === '/interface')
    if (!tabs.value.find(t => t.fullPath === activeTab.value)) {
      activeTab.value = fullPath
    }
  }

  function removeAll() {
    tabs.value = tabs.value.filter(t => t.fullPath === '/interface')
    activeTab.value = '/interface'
  }

  return { tabs, activeTab, cachedNames, addTab, removeTab, removeOther, removeAll }
})
