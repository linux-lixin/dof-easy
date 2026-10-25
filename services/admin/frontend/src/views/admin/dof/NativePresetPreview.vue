<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import Request from '../../../api/Request'

type Preset = { id: string; label: string; width: number; height: number; saveEvidence: string }
const props = withDefaults(defineProps<{ presets: Preset[]; initialId?: string; title?: string; selectorLabel?: string }>(), { initialId: 'native-100', title: '原工具血槽预览', selectorLabel: '血槽预设' })
const emit = defineEmits<{ (e: 'select', id: string): void }>()
const selected = ref(props.initialId)
const imageUrl = ref('')
const busy = ref(false)
const error = ref('')
let generation = 0
const current = computed(() => props.presets.find(p => p.id === selected.value))
function clearImage() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
}
async function load() {
  const request = ++generation
  clearImage(); error.value = ''
  if (!current.value) { busy.value = false; return }
  busy.value = true
  try {
    const response = await Request.get(`/api/v1/admin/dof/ui/presets/${encodeURIComponent(selected.value)}/image`, { responseType: 'blob' })
    if (request !== generation) return
    if (!response.data.type?.startsWith('image/png')) throw new Error('预览图片格式错误')
    imageUrl.value = URL.createObjectURL(response.data)
  } catch (e: any) {
    if (request === generation) error.value = e.message || '预览读取失败'
  } finally { if (request === generation) busy.value = false }
}
watch([selected, () => props.presets], load, { immediate: true })
watch(selected, value => emit('select', value), { immediate: true })
onBeforeUnmount(() => { ++generation; clearImage() })
</script>

<template>
  <section class="native-presets">
    <h3>{{ title }}</h3>
    <a-select v-model="selected" :options="presets.map(p => ({ label: p.label, value: p.id }))" :aria-label="selectorLabel" />
    <p>以下为dof-easy 2.8 原工具内置示意图，选择预览不会修改配置。</p>
    <a-spin v-if="busy" />
    <a-alert v-if="error" type="error">{{ error }} <a-button @click="load">重试读取</a-button></a-alert>
    <img v-if="imageUrl && current" :src="imageUrl" :alt="current.label + '原工具预览'" :width="current.width" :height="current.height" />
    <p v-if="current">选择后可在下方预览设置变更并保存。原工具示意图不代表当前游戏效果，保存后需重新启动客户端核对。</p>
  </section>
</template>

<style scoped>
.native-presets { margin: 18px 0; }
.native-presets img { display: block; max-width: 100%; height: auto; }
</style>
