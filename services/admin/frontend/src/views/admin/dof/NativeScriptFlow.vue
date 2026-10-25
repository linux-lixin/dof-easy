<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import Request from '../../../api/Request'
const emit = defineEmits<{ (event: 'select', taskId: string): void }>()
type Upload = { uploadId: string; state: string; bytes: number; sha256?: string; createdAt: string; error?: string }
type Task = { requestId: string; uploadId: string; revision: string; state: string; progress: number; error?: string; sha256?: string; bytes: number }
const root = '/api/v1/admin/dof/script'
const status = ref<any>(null), uploads = ref<Upload[]>([]), tasks = ref<Task[]>([])
const busy = ref(false), error = ref(''), file = ref<File | null>(null), uploaded = ref(0)
const pendingUpload = ref<string | null>(null)
const pendingTask = ref<{ requestId: string; uploadId: string; revision: string } | null>(null)
let disposed = false, timer: ReturnType<typeof setTimeout> | undefined
const labels: Record<string, string> = { UPLOADING: '上传中', READY: '原始文件检查通过', QUEUED: '等待生成', RUNNING: '正在加密', READY_FOR_TEST: '加密文件已生成，游戏效果待验证', FAILED: '处理失败', INTERRUPTED: '处理已中断' }
function id() {
  const b = crypto.getRandomValues(new Uint8Array(16)); b[6] = (b[6]! & 15) | 64; b[8] = (b[8]! & 63) | 128
  const h = Array.from(b, v => v.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`
}
async function run(action: () => Promise<void>) {
  if (busy.value || disposed) return
  busy.value = true; error.value = ''
  try { await action() } catch (e: any) { if (!disposed) error.value = e.response?.data?.message || e.message || '请求失败，请刷新核对原任务' }
  finally { busy.value = false }
}
async function refresh() {
  if (timer) clearTimeout(timer)
  const [u, t] = await Promise.all([Request.get(`${root}/uploads`, { timeout: 1800000 }), Request.get(`${root}/tasks`, { timeout: 1800000 })])
  if (disposed) return
  uploads.value = u.data; tasks.value = t.data
  if (pendingUpload.value && uploads.value.some(x => x.uploadId === pendingUpload.value && x.state !== 'UPLOADING')) pendingUpload.value = null
  if (pendingTask.value && tasks.value.some(x => x.requestId === pendingTask.value?.requestId)) pendingTask.value = null
  if (tasks.value.some(x => ['QUEUED', 'RUNNING'].includes(x.state))) schedule()
}
function schedule() { timer = setTimeout(() => { if (disposed) return; if (busy.value) schedule(); else void run(refresh) }, 3000) }
async function load() { status.value = (await Request.get(root)).data; if (status.value.enabled) await refresh() }
function choose(event: Event) { const input = event.target as HTMLInputElement; file.value = input.files?.[0] || null; input.value = ''; uploaded.value = 0 }
function upload() { return run(async () => {
  if (!file.value || pendingUpload.value) return
  if (file.value.size < 80 || file.value.size > status.value.maxSourceBytes) throw new Error('文件长度超出当前客户端支持范围')
  const requestId = id(); pendingUpload.value = requestId
  // Browser sends the File directly. No base64 or whole-file ArrayBuffer copy.
  await Request.post(`${root}/uploads`, file.value, {
    params: { requestId, revision: status.value.revision }, headers: { 'Content-Type': 'application/octet-stream' }, timeout: 1800000,
    onUploadProgress: e => { uploaded.value = Math.min(100, Math.floor(e.loaded * 100 / file.value!.size)) },
  })
  await refresh()
}) }
function importPaired() { return run(async () => {
  if (pendingUpload.value) return
  const requestId = id(); pendingUpload.value = requestId
  await Request.post(`${root}/uploads/paired`, { requestId, revision: status.value.revision }, { timeout: 1800000 })
  await refresh()
}) }
function generate(u: Upload) { return run(async () => {
  if (!pendingTask.value) pendingTask.value = { requestId: id(), uploadId: u.uploadId, revision: status.value.revision }
  await Request.post(`${root}/tasks`, pendingTask.value, { timeout: 1800000 }); await refresh()
}) }
onMounted(() => run(load))
onBeforeUnmount(() => { disposed = true; if (timer) clearTimeout(timer) })
</script>

<template>
  <a-card title="1. 上传并加密原始 Script.pvf" class="space">
    <a-alert>生成当前配套晴空的不压缩加密文件，随后可选择任务生成含 PVF 的登录器配套包。生成不会修改隔离服参数。</a-alert>
    <a-alert v-if="error" type="error" class="space">{{ error }}</a-alert>
    <a-button class="space" :loading="busy" @click="run(load)">刷新来源和任务</a-button>
    <template v-if="status?.enabled">
      <p>环境：隔离测试 · 实例：{{ status.instanceId }}</p>
      <a-alert v-if="!status.applicationEnabled" type="warning">{{ status.applicationBlocker }}。当前产物供开发核对，尚不能作为已通过登录验收的交付包。</a-alert>
      <p v-if="status.pairedSourceAvailable">
        <a-button :disabled="busy || !!pendingUpload" @click="importPaired">使用当前服务端 PVF</a-button>
        从当前实例的只读配套文件创建独立任务副本，保留服务器原件；也可上传本地文件。
      </p>
      <p><input type="file" accept=".pvf" :disabled="busy || !!pendingUpload" aria-label="选择原始 Script.pvf" @change="choose" /></p>
      <p v-if="file">{{ file.name }} · {{ (file.size / 1024 / 1024).toFixed(2) }} MiB</p>
      <a-button :disabled="busy || !file || !!pendingUpload" @click="upload">上传原始 PVF</a-button>
      <p v-if="busy && pendingUpload">上传传输 {{ uploaded }}% · 传输结束后仍需检查索引和完整性。</p>
      <p v-if="pendingUpload">待核对上传编号：{{ pendingUpload }}。连接失败后先刷新记录，不覆盖原上传。</p>
      <p v-if="pendingTask">待核对生成编号：{{ pendingTask.requestId }}</p>
      <a-table class="space" :data="uploads" row-key="uploadId" :pagination="{ pageSize: 5 }">
        <template #columns>
          <a-table-column title="上传编号" data-index="uploadId" />
          <a-table-column title="状态"><template #cell="{record}">{{ labels[record.state] || record.state }}<p v-if="record.error">{{ record.error }}</p></template></a-table-column>
          <a-table-column title="生成"><template #cell="{record}"><a-button v-if="record.state === 'READY'" :disabled="busy || (!!pendingTask && pendingTask.uploadId !== record.uploadId) || tasks.some(t => ['QUEUED','RUNNING'].includes(t.state))" @click="generate(record)">{{ pendingTask ? '核对原生成请求' : '生成加密 PVF' }}</a-button></template></a-table-column>
        </template>
      </a-table>
      <a-table class="space" :data="tasks" row-key="requestId" :pagination="{ pageSize: 5 }">
        <template #columns>
          <a-table-column title="加密批次" data-index="requestId" />
          <a-table-column title="状态"><template #cell="{record}">{{ labels[record.state] || record.state }}<span v-if="record.state === 'RUNNING'"> · {{ record.progress }}%</span><p v-if="record.error">{{ record.error }}</p></template></a-table-column>
          <a-table-column title="配套包"><template #cell="{record}"><a-button v-if="record.state === 'READY_FOR_TEST'" :disabled="busy" @click="emit('select', record.requestId)">选择此批次生成登录器配套包</a-button></template></a-table-column>
        </template>
      </a-table>
    </template>
    <p v-else-if="status">隔离后台尚未配置配套文件来源。</p>
  </a-card>
</template>
<style scoped>.space { margin-top: 18px; }</style>
