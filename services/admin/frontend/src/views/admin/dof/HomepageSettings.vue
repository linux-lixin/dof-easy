<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Request from '../../../api/Request'
const root = '/api/v1/admin/dof/home'
const state = ref<any>(null), values = ref<Record<string, string | null>>({})
const history = ref<any[]>([]), busy = ref(false), error = ref(''), preview = ref<any>(null), pending = ref<any>(null), result = ref<any>(null)
const restoreRequests = new Map<string, any>()
function change(key: string, value: string) { values.value[key] = value; preview.value = null }
function id() {
  const bytes = crypto.getRandomValues(new Uint8Array(16)); bytes[6] = (bytes[6]! & 15) | 64; bytes[8] = (bytes[8]! & 63) | 128
  const h = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`
}
async function run(task: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await task() } catch (e: any) { error.value = e.response?.data?.message || e.message || '连接中断，请核对操作记录' }
  finally { busy.value = false }
}
async function reload() {
  const [current, records] = await Promise.all([Request.get(root), Request.get(`${root}/history`)])
  state.value = current.data; values.value = { ...current.data.values }; history.value = records.data; preview.value = null
}
function prepare() { return run(async () => {
  const request = { requestId: id(), revision: state.value.revision, values: { ...values.value } }
  preview.value = (await Request.post(`${root}/preview`, request)).data; pending.value = request
}) }
function save() { return run(async () => {
  if (!preview.value || !pending.value) return
  result.value = (await Request.put(root, pending.value)).data; await reload()
}) }
function restore(row: any) { return run(async () => {
  const key = `${row.requestId}:${state.value.revision}`
  if (!restoreRequests.has(key)) restoreRequests.set(key, { requestId: id(), revision: state.value.revision })
  pending.value = restoreRequests.get(key)
  result.value = (await Request.post(`${root}/history/${row.requestId}/restore`, pending.value)).data; await reload()
}) }
function reconcile(row: any) { return run(async () => {
  result.value = (await Request.post(`${root}/history/${row.requestId}/reconcile`)).data; await reload()
}) }
onMounted(() => run(reload))
</script>

<template>
  <a-alert>管理dof-easy原生首页文字和入口。修改注册链接不会改变现有注册限制。客户端刷新时机仍待等价验证。</a-alert>
  <a-alert v-if="error" type="error" class="space">{{ error }}</a-alert>
  <template v-if="state">
    <p>{{ state.activation }}</p>
    <a-form :model="values" layout="vertical">
      <a-form-item v-for="field in state.fields" :key="field.key" :label="field.label">
        <a-textarea v-if="field.multiline" :model-value="values[field.key] ?? undefined" :disabled="busy" :auto-size="{ minRows: 3, maxRows: 10 }" @update:model-value="change(field.key, $event)" />
        <a-input v-else :model-value="values[field.key] ?? undefined" :disabled="busy" @update:model-value="change(field.key, $event)" />
      </a-form-item>
    </a-form>
    <a-space>
      <a-button :disabled="busy" @click="run(reload)">重新读取</a-button>
      <a-button :disabled="busy" @click="prepare">预览变更</a-button>
      <a-button type="primary" :disabled="busy || !preview" @click="save">保存本次预览</a-button>
    </a-space>
    <p v-if="preview">本次变更：{{ preview.changedFields.length ? state.fields.filter((f: any) => preview.changedFields.includes(f.key)).map((f: any) => f.label).join('、') : '无变更' }}</p>
    <p v-if="pending">操作编号：{{ pending.requestId }}。连接中断后先查历史，不重复创建请求。</p>
    <a-alert v-if="result" :type="result.persisted ? 'success' : 'warning'">{{ result.persisted ? '已确认持久化，客户端效果待确认' : '已核对操作状态' }} · {{ result.state }}</a-alert>
    <a-table class="space" :data="history" :pagination="{ pageSize: 5 }" row-key="requestId">
      <template #columns>
        <a-table-column title="时间" data-index="createdAt" />
        <a-table-column title="操作者" data-index="actor" />
        <a-table-column title="操作编号" data-index="requestId" />
        <a-table-column title="结果"><template #cell="{record}">{{ ({PERSISTED:'已持久化',NO_CHANGE:'无需修改',PREPARED:'结果待核对',NOT_APPLIED:'未写入'} as Record<string,string>)[record.state] || record.state }}</template></a-table-column>
        <a-table-column title="核对与恢复"><template #cell="{record}">
          <a-button v-if="record.state === 'PREPARED'" :disabled="busy" @click="reconcile(record)">核对实际结果</a-button>
          <a-popconfirm v-else-if="record.state === 'PERSISTED'" content="仅恢复本次首页文字修改，版本冲突时拒绝覆盖。确认恢复？" @ok="restore(record)">
            <a-button :disabled="busy || record.afterRevision !== state.revision">恢复修改前设置</a-button>
          </a-popconfirm>
        </template></a-table-column>
      </template>
    </a-table>
  </template>
</template>
<style scoped>.space { margin-top: 18px; }</style>
