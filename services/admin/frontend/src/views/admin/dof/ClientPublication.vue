<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import Request from '../../../api/Request'
import NativeScriptFlow from './NativeScriptFlow.vue'
import NativeScriptRelease from './NativeScriptRelease.vue'

type Job = { requestId: string; state: string; progress: number; createdAt: string; error?: string; sha256?: string; bytes: number; endpoint: string; scriptTaskId?: string; clientFormatVersion: number }
const root = '/api/v1/admin/dof/client'
const status = ref<any>(null)
const jobs = ref<Job[]>([])
const endpoint = ref('')
const background = ref<string | undefined>()
const backgroundName = ref('')
const busy = ref(false)
const error = ref('')
const scriptTaskId = ref<string | undefined>()
const releaseBundleId = ref<string | undefined>()
const pending = ref<{ requestId: string; revision: string; endpoint: string; backgroundBase64?: string; scriptTaskId?: string } | null>(null)
let disposed = false
let timer: ReturnType<typeof setTimeout> | undefined
const labels: Record<string,string> = { QUEUED: '等待生成', RUNNING: '正在生成', READY_FOR_TEST: '测试包已生成，运行效果待验', FAILED: '生成失败', INTERRUPTED: '执行中断，旧包未改动' }
function id() {
  const b = crypto.getRandomValues(new Uint8Array(16)); b[6] = (b[6]! & 15) | 64; b[8] = (b[8]! & 63) | 128
  const h = Array.from(b, v => v.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`
}
async function reload() {
  const response = (await Request.get(root)).data
  if (disposed) return
  status.value = response
  if (!endpoint.value) endpoint.value = response.endpoint || ''
  if (response.enabled) await refreshJobs()
}
async function refreshJobs() {
  if (timer) clearTimeout(timer)
  const rows = (await Request.get(`${root}/tasks`)).data
  if (disposed) return
  jobs.value = rows
  if (pending.value && rows.some((j: Job) => j.requestId === pending.value?.requestId)) pending.value = null
  if (rows.some((j: Job) => ['QUEUED', 'RUNNING'].includes(j.state))) scheduleRefresh()
}
function scheduleRefresh() {
  timer = setTimeout(() => {
    if (disposed) return
    if (busy.value) scheduleRefresh()
    else run(refreshJobs)
  }, 3000)
}
async function run(action: () => Promise<void>) {
  if (busy.value || disposed) return
  busy.value = true; error.value = ''
  try { await action() } catch (e: any) { if (!disposed) error.value = e.response?.data?.message || e.message || '连接失败，请核对任务记录' }
  finally { busy.value = false }
}
function create() { return run(async () => {
  if (!pending.value) pending.value = { requestId: id(), revision: status.value.revision, endpoint: endpoint.value, backgroundBase64: background.value, scriptTaskId: scriptTaskId.value }
  // A failed HTTP response retains this exact request until task history resolves it.
  await Request.post(`${root}/tasks`, pending.value, { timeout: 1800000 })
  await refreshJobs()
}) }
function chooseBackground(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  return run(async () => {
    if (file.size > 2 * 1024 * 1024) throw new Error('背景图片不能超过 2 MiB')
    const bytes = new Uint8Array(await file.arrayBuffer())
    let binary = ''
    for (let i = 0; i < bytes.length; i += 16384) binary += String.fromCharCode(...bytes.subarray(i, i + 16384))
    background.value = btoa(binary); backgroundName.value = file.name
  })
}
function download(job: Job) { return run(async () => {
  const response = await Request.get(`${root}/tasks/${job.requestId}/download`, { responseType: 'blob', timeout: 600000 })
  if (disposed) return
  if (!response.data.type?.startsWith('application/zip')) throw new Error('下载内容不是客户端测试包')
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a'); link.href = url; link.download = `dof-isolated-${job.requestId}.zip`
  document.body.appendChild(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}) }
onMounted(() => run(reload))
onBeforeUnmount(() => { disposed = true; if (timer) clearTimeout(timer) })
</script>

<template>
  <a-alert type="warning">这里生成当前测试实例的 dof-easy 登录器配套包。选择加密批次后包含对应 Script.pvf；不包含完整游戏素材，也不执行自动更新。生成成功不代表游戏登录验收通过。</a-alert>
  <NativeScriptFlow @select="id => { if (!busy && !pending) scriptTaskId = id }" />
  <a-alert v-if="error" class="space" type="error">{{ error }}</a-alert>
  <a-button class="space" :loading="busy" @click="run(reload)">刷新发布来源与任务</a-button>
  <a-alert v-if="status && !status.enabled" class="space">{{ status.blocker || '当前实例尚未导入客户端发布材料。' }}</a-alert>
  <template v-if="status?.enabled">
    <a-form class="space" :model="{ endpoint }" layout="vertical">
      <p>2. 生成 dof-easy 登录器{{ scriptTaskId ? '与加密 PVF 配套包' : '配置包' }} · 格式 v{{ status.clientFormatVersion }}</p>
      <p>配套文件：dof-launcher.exe、dof-client.dll、ijl15.dll。使用同一批次的文件安装。</p>
      <p v-if="scriptTaskId">已选择加密批次：{{ scriptTaskId }} <a-button :disabled="busy || !!pending" @click="scriptTaskId = undefined">改为仅生成登录器配置</a-button></p>
      <a-form-item label="隔离客户端连接地址"><a-input v-model="endpoint" :max-length="253" :disabled="busy || !!pending" /></a-form-item>
      <a-form-item label="登录器背景（可选）">
        <input type="file" accept="image/png,image/gif" :disabled="busy || !!pending" aria-label="选择登录器背景" @change="chooseBackground" />
        <p v-if="backgroundName">{{ backgroundName }} <a-button :disabled="busy || !!pending" @click="background = undefined; backgroundName = ''">保留原背景</a-button></p>
      </a-form-item>
      <p>不选择图片时保留原背景。新图片须为 PNG 或 GIF，最大 2 MiB、4096×4096，实际显示效果待客户端核对。</p>
      <a-button type="primary" :disabled="busy || !endpoint || jobs.some(j => ['QUEUED','RUNNING'].includes(j.state))" @click="create">{{ pending ? '用原操作编号核对或重试' : '生成 dof-easy 配套测试包' }}</a-button>
    </a-form>
    <p v-if="pending">连接结果尚待核对，操作编号 {{ pending.requestId }}。请刷新任务记录，勿重复创建。</p>
    <a-table class="space" :data="jobs" row-key="requestId" :pagination="{ pageSize: 10 }" :scroll="{ x: 1000 }">
      <template #columns>
        <a-table-column title="创建时间" data-index="createdAt" />
        <a-table-column title="操作编号" data-index="requestId" />
        <a-table-column title="连接地址" data-index="endpoint" />
        <a-table-column title="加密 PVF 批次"><template #cell="{record}">{{ record.scriptTaskId || '仅登录器配置' }}</template></a-table-column>
        <a-table-column title="状态"><template #cell="{record}">{{ labels[record.state] || record.state }}<p v-if="record.error">{{ record.error }}</p><p v-if="record.state === 'RUNNING'">{{ record.progress }}%</p></template></a-table-column>
        <a-table-column title="产物"><template #cell="{record}"><template v-if="record.state === 'READY_FOR_TEST' && record.clientFormatVersion === status.clientFormatVersion"><a-button v-if="record.scriptTaskId" :disabled="busy" @click="releaseBundleId = record.requestId">选择并配置隔离参数</a-button><a-button :disabled="busy" @click="download(record)">下载测试包</a-button><p>{{ (record.bytes / 1024 / 1024).toFixed(2) }} MiB</p><code class="hash">SHA-256 {{ record.sha256 }}</code></template><p v-else-if="record.state === 'READY_FOR_TEST'">配套格式版本已改变，请重新生成。</p></template></a-table-column>
      </template>
    </a-table>
    <NativeScriptRelease :bundle-id="releaseBundleId" />
  </template>
</template>

<style scoped>
.space { margin-top: 18px; }
.hash { overflow-wrap: anywhere; }
</style>
