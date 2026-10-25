<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import Request from '../../../api/Request'
const props = defineProps<{ bundleId?: string }>()
const root = '/api/v1/admin/dof/client/releases'
type Edit = { requestId: string; revision: string; bundleId?: string }
type Row = { requestId: string; bundleId?: string; state: string; operation: string; createdAt: string }
const status = ref<any>(null), preview = ref<any>(null), rows = ref<Row[]>([])
const pending = ref<Edit | null>(null), restoreTarget = ref<string | null>(null)
const busy = ref(false), error = ref('')
const submitted = ref(false)
const labels: Record<string, string> = { PREPARED: '结果待核对', PARTIAL: '部分写入，需恢复或核对', PERSISTED: '参数已保存并回读，游戏效果待验证', RESTORED: '本次参数已恢复', NOT_APPLIED: '核对确认未应用' }
function id() {
  const b = crypto.getRandomValues(new Uint8Array(16)); b[6] = (b[6]! & 15) | 64; b[8] = (b[8]! & 63) | 128
  const h = Array.from(b, v => v.toString(16).padStart(2, '0')).join('')
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`
}
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await action() } catch (e: any) { error.value = e.response?.data?.message || e.message || '连接失败，请核对发布记录' }
  finally { busy.value = false }
}
async function reload() {
  const [a, b] = await Promise.all([Request.get(`${root}/status`), Request.get(root)])
  status.value = a.data; rows.value = b.data
  if (pending.value && rows.value.some(r => r.requestId === pending.value?.requestId)) { pending.value = null; restoreTarget.value = null; preview.value = null }
}
function inspect() { return run(async () => {
  if (!props.bundleId || pending.value) return
  await reload()
  const edit: Edit = { requestId: id(), revision: status.value.revision, bundleId: props.bundleId }
  preview.value = (await Request.post(`${root}/preview`, edit, { timeout: 1800000 })).data
  pending.value = edit; restoreTarget.value = null; submitted.value = false
}) }
function apply() { return run(async () => {
  if (!pending.value) return
  const url = restoreTarget.value ? `${root}/${restoreTarget.value}/restore` : root
  submitted.value = true
  await Request.post(url, pending.value, { timeout: 1800000 }); await reload()
}) }
function restore(row: Row) { return run(async () => {
  if (pending.value) return
  await reload(); pending.value = { requestId: id(), revision: status.value.revision }; restoreTarget.value = row.requestId; submitted.value = false
}) }
function reconcile(row: Row) { return run(async () => { await Request.post(`${root}/${row.requestId}/reconcile`); await reload() }) }
watch(() => props.bundleId, () => { if (!pending.value) preview.value = null })
onMounted(() => run(reload))
</script>
<template>
  <a-card title="3. 应用或恢复隔离服参数" class="space">
    <a-alert>仅应用所选配套包的两处解密参数。保留原生更新设置、服务器 PVF、身份密钥及玩家数据。保存成功后仍需重新启动独立客户端验证实际登录。</a-alert>
    <a-alert v-if="error" type="error" class="space">{{ error }}</a-alert>
    <p v-if="status">隔离实例：{{ status.instanceId }} · {{ status.parameterCopiesEqual ? '两处参数一致' : '两处历史参数不同，将分别保存恢复前值' }}</p>
    <a-button :loading="busy" @click="run(reload)">刷新配置与历史</a-button>
    <p v-if="bundleId">所选配套包：{{ bundleId }}</p>
    <a-button class="space" :disabled="busy || !bundleId || !!pending || !status?.sourceConfigured || !status?.manualModeCompatible" @click="inspect">核对配套包并预览应用</a-button>
    <p v-if="status && !status.sourceConfigured">尚未绑定隔离游戏 PVF 的只读来源，不能应用。</p>
    <p v-if="status && !status.manualModeCompatible">当前启用了原生校验或更新；本阶段不会自动关闭，不能应用手动配套包。</p>
    <template v-if="pending">
      <p v-if="restoreTarget">恢复操作：{{ restoreTarget }}。仅恢复其修改的参数，第三方后续修改将阻止覆盖。</p>
      <p v-else-if="preview">已核对客户端和服务器 PVF 来源一致，将保存两处解密参数。配套包：{{ pending.bundleId }}</p>
      <p>操作编号：{{ pending.requestId }}。响应不明时先刷新记录核对。</p>
      <a-button type="primary" :disabled="busy" @click="apply">{{ restoreTarget ? '执行定向恢复' : '应用此配套包参数' }}</a-button>
      <a-button v-if="!submitted" :disabled="busy" @click="pending = null; preview = null; restoreTarget = null">取消本次预览</a-button>
    </template>
    <a-table class="space" :data="rows" row-key="requestId" :pagination="{ pageSize: 5 }">
      <template #columns>
        <a-table-column title="操作编号" data-index="requestId" />
        <a-table-column title="配套包" data-index="bundleId" />
        <a-table-column title="结果"><template #cell="{record}">{{ labels[record.state] || record.state }}</template></a-table-column>
        <a-table-column title="核对与恢复"><template #cell="{record}"><a-button v-if="['PREPARED','PARTIAL'].includes(record.state)" :disabled="busy" @click="reconcile(record)">核对实际结果</a-button><a-button v-if="['PERSISTED','PREPARED','PARTIAL'].includes(record.state)" :disabled="busy || !!pending" @click="restore(record)">准备定向恢复</a-button></template></a-table-column>
      </template>
    </a-table>
  </a-card>
</template>
<style scoped>.space { margin-top: 18px; }</style>
