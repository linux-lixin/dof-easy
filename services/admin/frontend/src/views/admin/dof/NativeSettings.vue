<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Request from '../../../api/Request'
const module = ref('registration')
const state = ref<any>(null), history = ref<any[]>([]), values = ref<Record<string, boolean | number>>({})
const busy = ref(false), error = ref(''), preview = ref<any>(null), pending = ref<any>(null), result = ref<any>(null)
const root = () => `/api/v1/admin/dof/settings/${module.value}`
const restores = new Map<string, any>()
function id() {
  const bytes = crypto.getRandomValues(new Uint8Array(16)); bytes[6] = (bytes[6]! & 15) | 64; bytes[8] = (bytes[8]! & 63) | 128
  const h = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`
}
async function run(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await task() } catch (e: any) { error.value = e.response?.data?.message || e.message || '请求中断，请先核对操作记录' }
  finally { busy.value = false }
}
async function reload() {
  const [a, b] = await Promise.all([Request.get(root()), Request.get(`${root()}/history`)])
  state.value = a.data; history.value = b.data
  values.value = Object.fromEntries(a.data.fields.filter((f: any) => f.available).map((f: any) => [f.key, f.value]))
  preview.value = null
}
function changed() { preview.value = null }
function switchModule(value: string) {
  if (busy.value) return
  module.value = value; state.value = null; preview.value = null; pending.value = null; result.value = null
  return run(reload)
}
function prepare() { return run(async () => {
  const changes: Record<string, boolean | number> = {}
  for (const f of state.value.fields) if (f.available && values.value[f.key] !== f.value) changes[f.key] = values.value[f.key]!
  if (!Object.keys(changes).length) throw new Error('设置没有变化')
  const request = { requestId: id(), revision: state.value.revision, values: changes }
  preview.value = (await Request.post(`${root()}/preview`, request)).data; pending.value = request
}) }
function save() { return run(async () => {
  if (!preview.value || !pending.value) return
  result.value = (await Request.put(root(), pending.value)).data; await reload()
}) }
function reconcile(row: any) { return run(async () => {
  result.value = (await Request.post(`${root()}/history/${row.requestId}/reconcile`)).data; await reload()
}) }
function restore(row: any) { return run(async () => {
  const key = `${row.requestId}:${state.value.revision}`
  if (!restores.has(key)) restores.set(key, { requestId: id(), revision: state.value.revision })
  pending.value = restores.get(key)
  result.value = (await Request.post(`${root()}/history/${row.requestId}/restore`, pending.value)).data; await reload()
}) }
const labels: Record<string,string> = { PREPARED:'结果待核对', PARTIAL:'部分字段已写入', PERSISTED:'已持久化', NO_CHANGE:'无需修改', NOT_APPLIED:'未写入', RESTORED:'已恢复' }
onMounted(() => run(reload))
</script>
<template>
  <a-space>
    <a-button v-for="item in [{id:'registration',name:'注册与改密设置'},{id:'online-reward',name:'泡点奖励设置'},{id:'shortcuts',name:'快捷操作设置'}]" :key="item.id" :type="module === item.id ? 'primary' : 'secondary'" :disabled="busy" @click="switchModule(item.id)">{{ item.name }}</a-button>
  </a-space>
  <a-alert v-if="error" type="error" class="space">{{ error }}</a-alert>
  <template v-if="state">
    <p>隔离实例：{{ state.instanceId }} · {{ state.activation }}</p>
    <a-form :model="values" layout="vertical">
      <a-form-item v-for="f in state.fields" :key="f.key" :label="f.label">
        <a-switch v-if="f.kind === 'boolean'" :model-value="values[f.key] === true" :disabled="busy || !f.available" @update:model-value="values[f.key] = $event === true; changed()" />
        <a-select v-else-if="f.kind === 'currency'" :model-value="Number(values[f.key])" :disabled="busy || !f.available" @update:model-value="values[f.key] = Number($event); changed()">
          <a-option v-for="option in f.options" :key="option.value" :value="option.value">{{ option.label }}</a-option>
        </a-select>
        <a-input-number v-else :model-value="Number(values[f.key])" :min="f.min" :max="f.max" :precision="0" :disabled="busy || !f.available" @update:model-value="values[f.key] = Number($event); changed()" />
        <span v-if="!f.available">原配置缺失或无法识别，已保留原值</span>
        <span v-if="f.hint">{{ f.hint }}</span>
      </a-form-item>
    </a-form>
    <a-space><a-button :disabled="busy" @click="run(reload)">重新读取</a-button><a-button :disabled="busy" @click="prepare">预览变更</a-button><a-button type="primary" :disabled="busy || !preview" @click="save">保存本次预览</a-button></a-space>
    <ul v-if="preview"><li v-for="change in preview.changes" :key="change.label">{{ change.label }}：{{ String(change.before) }} → {{ String(change.after) }}</li></ul>
    <p v-if="pending">操作编号：{{ pending.requestId }}。网络中断后先核对历史，再决定下一步。</p>
    <a-alert v-if="result" :type="result.persisted ? 'success' : 'warning'">{{ labels[result.state] || result.state }}；客户端实际效果尚未确认。</a-alert>
    <a-table class="space" :data="history" :pagination="{pageSize: 8}" row-key="requestId">
      <template #columns>
        <a-table-column title="时间" data-index="createdAt" /><a-table-column title="操作者" data-index="actor" /><a-table-column title="操作编号" data-index="requestId" />
        <a-table-column title="结果"><template #cell="{record}">{{ labels[record.state] || record.state }}</template></a-table-column>
        <a-table-column title="核对与恢复"><template #cell="{record}">
          <a-button v-if="['PREPARED','PARTIAL'].includes(record.state)" :disabled="busy" @click="reconcile(record)">核对实际结果</a-button>
          <a-popconfirm v-if="['PREPARED','PARTIAL','PERSISTED'].includes(record.state)" content="只恢复本次涉及的配置，发生冲突时停止覆盖。确认恢复？" @ok="restore(record)"><a-button :disabled="busy">恢复本次设置</a-button></a-popconfirm>
        </template></a-table-column>
      </template>
    </a-table>
  </template>
</template>
<style scoped>.space {margin-top: 18px}</style>
