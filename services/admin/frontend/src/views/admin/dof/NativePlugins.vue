<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Request from '../../../api/Request'

const root = '/api/v1/admin/dof/plugins'
const catalog = ref<any>(null), state = ref<any>(null), history = ref<any[]>([])
const module = ref(''), values = ref<Record<string, string>>({}), filter = ref('')
const preview = ref<any>(null), pending = ref<any>(null), result = ref<any>(null)
const error = ref(''), busy = ref(false), imported = ref<string>()
const selected = computed(() => catalog.value?.modules?.find((m: any) => m.id === module.value))
const fields = computed(() => (state.value?.fields || []).filter((f: any) => `${f.label} ${f.key}`.includes(filter.value)))
const labels: Record<string, string> = { PREPARED: '结果待核对', PERSISTED: '文件已保存', NO_CHANGE: '无需修改', NOT_APPLIED: '未写入', RESTORED: '已恢复' }
const restoreRequests = new Map<string, any>()
function uuid() {
  const b = crypto.getRandomValues(new Uint8Array(16)); b[6] = (b[6]! & 15) | 64; b[8] = (b[8]! & 63) | 128
  const h = Array.from(b, x => x.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await action() } catch (e: any) { error.value = e.response?.data?.message || e.message || '操作失败，请核对历史后重试' }
  finally { busy.value = false }
}
function changed() { preview.value = null; pending.value = null }
async function reload() {
  const [s, h] = await Promise.all([Request.get(`${root}/${module.value}`), Request.get(`${root}/${module.value}/history`)])
  state.value = s.data; history.value = h.data
  values.value = Object.fromEntries(state.value.fields.filter((f: any) => f.available).map((f: any) => [f.key, f.value]))
  imported.value = undefined; changed()
}
function switchModule(id: string) { return run(async () => { module.value = id; state.value = null; result.value = null; changed(); await reload() }) }
function prepare() { return run(async () => {
  const patch = Object.fromEntries(state.value.fields.filter((f: any) => f.available && values.value[f.key] !== f.value).map((f: any) => [f.key, values.value[f.key]]))
  if (imported.value === undefined && !Object.keys(patch).length) throw new Error('配置没有变化')
  pending.value = { requestId: uuid(), revision: state.value.revision, ...(imported.value === undefined ? { values: patch } : { content: imported.value }) }
  preview.value = null
  preview.value = (await Request.post(`${root}/${module.value}/preview`, pending.value)).data
}) }
function save() { return run(async () => {
  if (!pending.value || !preview.value) return
  result.value = (await Request.put(`${root}/${module.value}`, pending.value)).data
  if (result.value.persisted) await reload()
  else history.value = (await Request.get(`${root}/${module.value}/history`)).data
}) }
function reconcile(row: any) { return run(async () => {
  result.value = (await Request.post(`${root}/${module.value}/history/${row.requestId}/reconcile`)).data
  await reload()
}) }
function restore(row: any) { return run(async () => {
  let request = restoreRequests.get(row.requestId)
  if (!request) { request = { requestId: uuid(), revision: (await Request.get(`${root}/${module.value}`)).data.revision }; restoreRequests.set(row.requestId, request) }
  result.value = (await Request.post(`${root}/${module.value}/history/${row.requestId}/restore`, request)).data
  await reload()
}) }
function importFile(event: Event) { return run(async () => {
  const input = event.target as HTMLInputElement, file = input.files?.[0]; input.value = ''
  if (!file) return
  if (file.size > 128 * 1024) throw new Error('配置文件不能超过 128 KiB')
  const bytes = await file.arrayBuffer(); let content: string
  try { content = new TextDecoder('utf-8', { fatal: true }).decode(bytes) }
  catch { content = new TextDecoder('gb18030', { fatal: true }).decode(bytes) }
  changed(); imported.value = content
}) }
function exportFile() {
  const link = document.createElement('a'), blob = new Blob([state.value.content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob); link.href = url; link.download = `${module.value}-config.ini`; link.click(); URL.revokeObjectURL(url)
}
onMounted(() => run(async () => {
  catalog.value = (await Request.get(root)).data
  module.value = catalog.value.modules.find((m: any) => m.available)?.id || catalog.value.modules[0]?.id || ''
  if (module.value && selected.value?.available) await reload()
}))
</script>

<template>
  <a-alert v-if="error" type="error">{{ error }}</a-alert>
  <template v-if="catalog">
    <a-space wrap class="space">
      <a-button v-for="m in catalog.modules" :key="m.id" :disabled="busy || !m.available" :type="module === m.id ? 'primary' : 'secondary'" @click="switchModule(m.id)">{{ m.title }}</a-button>
      <span>插件组合：{{ catalog.pluginMode }} · 团本部署开关：{{ catalog.raidExtension }}</span>
    </a-space>
    <a-alert v-for="m in catalog.modules.filter((m: any) => !m.available)" :key="m.id" type="warning" class="space">{{ m.title }}：{{ m.reason }}</a-alert>
    <a-alert v-if="selected && !selected.dependencyReady" type="warning" class="space">团本需要 DP2，请先通过实例的插件管理启用 DP2。配置保存不会自动开启团本。</a-alert>
  </template>
  <template v-if="state">
    <p>{{ state.title }} · {{ state.activation }}</p>
    <a-alert v-if="state.application" class="space" type="warning">
      {{state.application.configuredEnabled ? '部署配置为启用' : '部署配置为停用'}} · {{state.application.dependencyReady ? '部署依赖已配置' : '部署依赖未满足'}} · 游戏运行效果尚未验证。
      {{state.application.instruction}}
      <p v-if="state.application.command">当前 GM 刷新命令：<code>{{state.application.command}}</code></p>
    </a-alert>
    <a-space wrap>
      <a-input v-model="filter" placeholder="搜索配置名称或字段" allow-clear />
      <a-button :disabled="busy" @click="run(reload)">重新读取</a-button>
      <a-button :disabled="busy" @click="exportFile">导出当前配置</a-button>
      <label>导入配置 <input type="file" accept=".ini,.txt" :disabled="busy" @change="importFile" /></label>
    </a-space>
    <a-alert v-if="imported !== undefined" class="space">已选择导入文件，点击预览核对变更；原文件的注释和未知配置保持原样。</a-alert>
    <a-form :model="values" layout="vertical" class="space">
      <a-form-item v-for="f in fields" :key="f.key" :label="`${f.label}（${f.key}）`">
        <a-textarea v-if="f.kind === 'rewards'" v-model="values[f.key]" :auto-size="{ minRows: 3, maxRows: 10 }" :disabled="busy || !f.available || imported !== undefined" @input="changed" />
        <a-input v-else v-model="values[f.key]" :disabled="busy || !f.available || imported !== undefined" @input="changed" />
        <span v-if="!f.available">当前文件没有此项，保留原状</span>
      </a-form-item>
    </a-form>
    <a-space>
      <a-button :disabled="busy" @click="prepare">预览变更</a-button>
      <a-button type="primary" :disabled="busy || !preview" @click="save">保存本次预览</a-button>
    </a-space>
    <a-table v-if="preview" class="space" :data="preview.changes" :pagination="false" :columns="[{title:'字段',dataIndex:'key'},{title:'原值',dataIndex:'before'},{title:'新值',dataIndex:'after'}]" />
    <p v-if="pending">操作编号：{{ pending.requestId }}。连接中断时请先核对历史。</p>
    <a-alert v-if="result" class="space" :type="result.persisted ? 'success' : 'warning'">{{ labels[result.state] || result.state }}；游戏读取及实际效果待确认。</a-alert>
    <a-table class="space" :data="history" row-key="requestId" :pagination="{pageSize: 8}">
      <template #columns>
        <a-table-column title="时间" data-index="createdAt" /><a-table-column title="操作者" data-index="actor" />
        <a-table-column title="操作编号" data-index="requestId" />
        <a-table-column title="结果"><template #cell="{record}">{{ labels[record.state] || record.state }}</template></a-table-column>
        <a-table-column title="核对与恢复"><template #cell="{record}">
          <a-button v-if="record.state === 'PREPARED'" :disabled="busy" @click="reconcile(record)">核对结果</a-button>
          <a-popconfirm v-if="['PREPARED','PERSISTED'].includes(record.state)" content="恢复本次修改的字段；其他修改保留。确认恢复？" @ok="restore(record)"><a-button :disabled="busy">恢复本次修改</a-button></a-popconfirm>
        </template></a-table-column>
      </template>
    </a-table>
  </template>
</template>
<style scoped>.space { margin-top: 16px }</style>
