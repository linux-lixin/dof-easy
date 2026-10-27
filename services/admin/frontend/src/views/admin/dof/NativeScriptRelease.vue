<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Request from '../../../api/Request'
const props = defineProps<{ bundleId?: string }>()
const root = '/api/v1/admin/dof/client/releases', storageKey = 'script.release.pending.v1', protocol = 'script-release-preview-v1'
type Edit = { protocol: string; requestId: string; revision: string; bundleId?: string }
type Cancel = { requestId: string; revision: string; reason: string }
type Row = { requestId: string; state: string; operation: string; createdAt: string; bundleId?: string; targetRequestId?: string; receiptRevision?: string; request?: Edit; cancelRequest?: Cancel; requestReplayAvailable?: boolean }
const status = ref<any>(null), preview = ref<Row | null>(null), rows = ref<Row[]>([])
const pending = ref<Edit | null>(null), pendingId = ref(''), restoreTarget = ref<string | null>(null)
const cancellation = ref<Cancel | null>(null), cancelId = ref(''), cancelState = ref('')
const busy = ref(false), error = ref(''), submitted = ref(false), missingPreview = ref(false)
const labels: Record<string, string> = { PREVIEWED: '预览已记录，尚未应用', PREPARED: '结果待核对', CONFLICT: '结果冲突，需核对或定向恢复', PARTIAL: '部分写入，需核对或恢复', PERSISTED: '操作已保存并回读，游戏效果待验证', RESTORED: '本次参数已恢复', ACKNOWLEDGED: '来源问题已有恢复记录', NOT_APPLIED: '确认未应用' }
function id() {
  const b = crypto.getRandomValues(new Uint8Array(16)); b[6] = (b[6]! & 15) | 64; b[8] = (b[8]! & 63) | 128
  const h = Array.from(b, v => v.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20)}`
}
function checkpoint() {
  if (pendingId.value) sessionStorage.setItem(storageKey, JSON.stringify({ requestId: pendingId.value, submitted: submitted.value, cancelId: cancelId.value }))
  else sessionStorage.removeItem(storageKey)
}
function clear() { pending.value = null; pendingId.value = ''; restoreTarget.value = null; preview.value = null; cancellation.value = null; cancelId.value = ''; cancelState.value = ''; submitted.value = false; missingPreview.value = false; checkpoint() }
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await action() } catch (e: any) { error.value = e.response?.data?.message || e.message || '连接失败，请保留原编号核对发布记录' }
  finally { busy.value = false }
}
async function reload() {
  const [a, b] = await Promise.all([Request.get(`${root}/status`), Request.get(root)])
  status.value = a.data; rows.value = b.data
  if (!pendingId.value) return
  if (cancelId.value) {
    try {
      const control: Row = (await Request.get(`${root}/${cancelId.value}`)).data
      cancelState.value = control.state; cancellation.value = control.cancelRequest || null
      if (['PERSISTED', 'NOT_APPLIED'].includes(control.state)) { cancelId.value = ''; cancellation.value = null; cancelState.value = '' }
    } catch (e: any) { if (e.response?.status !== 404) throw e; cancelState.value = 'MISSING'; cancellation.value = null }
  }
  let row: Row
  try { row = (await Request.get(`${root}/${pendingId.value}`)).data }
  catch (e: any) { pending.value = null; missingPreview.value = e.response?.status === 404 && !submitted.value && !cancelId.value; throw e }
  missingPreview.value = false; preview.value = row
  pending.value = row.requestReplayAvailable && row.request ? { ...row.request } : null
  restoreTarget.value = row.operation === 'restore' ? row.targetRequestId || null : null
  if (['PERSISTED','RESTORED','ACKNOWLEDGED','NOT_APPLIED','CONFLICT','PARTIAL'].includes(row.state) && !cancelId.value) clear()
  checkpoint()
}
function inspect() { return run(async () => {
  if (!props.bundleId || pendingId.value) return
  await reload(); const edit: Edit = { protocol, requestId: id(), revision: status.value.revision, bundleId: props.bundleId }
  pendingId.value = edit.requestId; pending.value = { ...edit }; restoreTarget.value = null; submitted.value = false; checkpoint()
  preview.value = (await Request.post(`${root}/preview`, edit, { timeout: 1800000 })).data; await reload()
}) }
function apply() { return run(async () => {
  if (!pending.value || preview.value?.state !== 'PREVIEWED' || cancelId.value) return
  submitted.value = true; checkpoint(); const url = restoreTarget.value ? `${root}/${restoreTarget.value}/restore` : root
  await Request.post(url, { ...pending.value }, { timeout: 1800000 }); await reload()
}) }
function restore(row: Row) { return run(async () => {
  if (pendingId.value) return
  await reload(); const edit: Edit = { protocol, requestId: id(), revision: status.value.revision }
  pendingId.value = edit.requestId; pending.value = { ...edit }; restoreTarget.value = row.requestId; submitted.value = false; checkpoint()
  preview.value = (await Request.post(`${root}/${row.requestId}/restore/preview`, edit, { timeout: 1800000 })).data; await reload()
}) }
function recover(row: Row) { return run(async () => { if (pendingId.value) return; pendingId.value = row.requestId; submitted.value = row.state !== 'PREVIEWED'; checkpoint(); await reload() }) }
function reconcile(row: Row) { return run(async () => { await Request.post(`${root}/${row.requestId}/reconcile`); await reload() }) }
function cancel() { return run(async () => {
  if (!pendingId.value || preview.value?.state !== 'PREVIEWED') return
  if (!cancelId.value) { cancelId.value = id(); checkpoint() }
  if (!cancellation.value) {
    cancellation.value = { requestId: cancelId.value, revision: preview.value.receiptRevision!, reason: '管理员取消尚未提交的配套参数预览' }; checkpoint()
    await Request.post(`${root}/${pendingId.value}/cancellations/preview`, { ...cancellation.value }); cancelState.value = 'PREVIEWED'
  }
  if (!cancellation.value) throw new Error('请先刷新核对取消记录')
  await Request.post(`${root}/${pendingId.value}/cancellations`, { ...cancellation.value }); await reload()
}) }
onMounted(() => run(async () => {
  const saved = sessionStorage.getItem(storageKey)
  if (saved) { const value = JSON.parse(saved); if (!/^[0-9a-f-]{36}$/.test(value.requestId) || (value.cancelId && !/^[0-9a-f-]{36}$/.test(value.cancelId))) throw new Error('本地待核对编号无效'); pendingId.value = value.requestId; submitted.value = value.submitted === true; cancelId.value = value.cancelId || '' }
  await reload()
}))
</script>
<template>
  <a-card title="3. 应用或恢复隔离服参数" class="space">
    <a-alert>仅应用所选配套包的两处解密参数。保留原生更新设置、服务器 PVF、身份密钥及玩家数据。保存和回读仅确认数据库状态；网关、插件读取时机及客户端登录效果仍待验。</a-alert>
    <a-alert v-if="error" type="error" class="space">{{ error }}</a-alert>
    <a-alert v-if="status && status.protocol !== protocol" type="warning">服务器发布协议尚未更新，请同步更新管理页面与后端后再操作。</a-alert>
    <p v-if="status">隔离实例：{{ status.instanceId }} · {{ status.parameterCopiesEqual ? '两处参数一致' : '两处历史参数不同，将分别保存恢复前值' }}</p>
    <a-button :loading="busy" @click="run(reload)">刷新配置与历史</a-button>
    <p v-if="bundleId">所选配套包：{{ bundleId }}</p>
    <a-button class="space" :disabled="busy || status?.protocol !== protocol || !bundleId || !!pendingId || !status?.sourceConfigured || !status?.manualModeCompatible" @click="inspect">核对配套包并预览应用</a-button>
    <p v-if="status && !status.sourceConfigured">尚未绑定隔离游戏 PVF 的只读来源，不能应用。</p>
    <p v-if="status && !status.manualModeCompatible">当前启用了原生校验或更新；本阶段不会自动关闭，不能应用手动配套包。</p>
    <template v-if="pendingId">
      <p v-if="restoreTarget">恢复操作：{{ restoreTarget }}。仅恢复其修改的参数，第三方后续修改将阻止覆盖。</p>
      <p v-else-if="preview?.state === 'PREVIEWED'">已核对客户端和服务器 PVF 来源一致，将保存两处解密参数。配套包：{{ pending?.bundleId }}</p>
      <p>操作编号：{{ pendingId }}。响应不明时先刷新记录核对。</p>
      <a-button v-if="pending && preview?.state === 'PREVIEWED'" type="primary" :disabled="busy || !!cancelId" @click="apply">{{ restoreTarget ? '执行定向恢复' : '应用此配套包参数' }}</a-button>
      <a-button v-if="preview?.state === 'PREVIEWED' && (!cancelId || ['PREVIEWED','MISSING'].includes(cancelState))" :disabled="busy" @click="cancel">取消本次预览</a-button>
      <p v-if="cancelId">取消操作编号：{{ cancelId }}。先核对原编号，不新建应用。</p>
      <a-button v-if="cancelId && cancelState === 'PREPARED'" :disabled="busy" @click="reconcile({ requestId: cancelId } as Row)">核对取消结果</a-button>
      <a-button v-if="missingPreview" :disabled="busy" @click="clear">清除尚未找到的未提交预览编号</a-button>
    </template>
    <a-table class="space" :data="rows" row-key="requestId" :pagination="{ pageSize: 5 }">
      <template #columns>
        <a-table-column title="操作编号" data-index="requestId" />
        <a-table-column title="配套包" data-index="bundleId" />
        <a-table-column title="结果"><template #cell="{record}">{{ record.operation === 'cancel-preview' && record.state === 'PERSISTED' ? '取消已记录，未更改游戏配置' : labels[record.state] || record.state }}</template></a-table-column>
        <a-table-column title="核对与恢复"><template #cell="{record}"><a-button v-if="record.state === 'PREVIEWED' && record.operation !== 'cancel-preview'" :disabled="busy || !!pendingId" @click="recover(record)">恢复预览</a-button><a-button v-if="['PREPARED','PARTIAL','CONFLICT'].includes(record.state)" :disabled="busy" @click="reconcile(record)">核对实际结果</a-button><a-button v-if="record.operation === 'apply' && ['PERSISTED','PREPARED','PARTIAL','CONFLICT'].includes(record.state)" :disabled="busy || status?.protocol !== protocol || !!pendingId" @click="restore(record)">准备定向恢复</a-button></template></a-table-column>
      </template>
    </a-table>
  </a-card>
</template>
<style scoped>.space { margin-top: 18px; }</style>
