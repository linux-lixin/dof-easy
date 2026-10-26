<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';
const props = defineProps<{ characNo: number; name?: string }>();
const emit = defineEmits<{ (e: 'close'): void; (e: 'saved'): void }>();
const state = ref<any>(null), values = ref<Record<string, number>>({}), history = ref<any[]>([]);
const busy = ref(false), error = ref(''), prepared = ref<any>(null), pending = ref<any>(null);
const operation = ref('patch'), target = ref(''), reason = ref('');
const labels: Record<string, string> = { PREVIEWED: '仅预览', PREPARED: '结果待核对', PERSISTED: '属性已保存', NOT_APPLIED: '未写入', CONFLICT: '存在第三方修改', ACKNOWLEDGED: '已保留当前值，原结果未知', NO_CHANGE: '无需写入' };
const endpoint = () => `/api/v1/charac/${props.characNo}`;
function uuid() {
  const b = crypto.getRandomValues(new Uint8Array(16)); b[6] = (b[6]! & 15) | 64; b[8] = (b[8]! & 63) | 128;
  const s = Array.from(b, x => x.toString(16).padStart(2, '0')).join('');
  return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`;
}
const requestId = ref(uuid());
function message(e: any) { return e?.response?.data?.message || e?.message || '操作失败，请保留编号并核对历史'; }
watch(() => JSON.stringify([values.value, operation.value, target.value, reason.value, state.value?.revision]), () => {
  requestId.value = uuid(); prepared.value = null; pending.value = null;
});
async function load() {
  busy.value = true; error.value = ''; state.value = null; prepared.value = null; pending.value = null;
  try {
    state.value = (await Request.get<any>(endpoint() + '/edit')).data;
    values.value = { ...state.value.values }; operation.value = 'patch'; target.value = ''; reason.value = '';
    history.value = (await Request.get<any[]>(endpoint() + '/history')).data;
  } catch (e) { error.value = message(e); }
  finally { busy.value = false; }
}
async function preview() {
  if (busy.value || !state.value?.editable) return;
  const body: any = { characNo: props.characNo, requestId: requestId.value, revision: state.value.revision, operation: operation.value };
  if (operation.value === 'patch') {
    body.values = Object.fromEntries(Object.entries(values.value).filter(([key, value]) => value !== state.value.values[key]));
  } else body.targetRequestId = target.value;
  if (operation.value === 'accept-current') body.reason = reason.value;
  busy.value = true; error.value = '';
  try { pending.value = JSON.parse(JSON.stringify(body)); prepared.value = (await Request.post<any>(endpoint() + '/preview', pending.value)).data; }
  catch (e) { prepared.value = null; error.value = message(e); }
  finally { busy.value = false; }
}
async function save() {
  if (busy.value || prepared.value?.state !== 'PREVIEWED' || !pending.value) return;
  busy.value = true; error.value = '';
  try {
    const result = (await Request.put<any>('/api/v1/charac', pending.value)).data;
    if (result.persisted) { Message.success('属性持久化结果已核对；重登后的实际效果待验证'); emit('saved'); await load(); }
    else { error.value = labels[result.state] || result.state; history.value = (await Request.get<any[]>(endpoint() + '/history')).data; }
  } catch (e) { error.value = message(e); }
  finally { busy.value = false; }
}
async function historical(row: any, action: string) {
  await load(); if (!state.value) return;
  operation.value = action; target.value = row.requestId;
}
async function reconcile(row: any) {
  if (busy.value) return; busy.value = true; error.value = '';
  try {
    const result = (await Request.post<any>(`${endpoint()}/history/${row.requestId}/reconcile`)).data;
    Message.info(labels[result.state] || result.state); await load();
  } catch (e) { error.value = message(e); }
  finally { busy.value = false; }
}
onMounted(load);
</script>

<template>
  <a-modal :visible="true" :title="`角色属性：${name || characNo}`" :width="1000" :footer="false"
    :mask-closable="false" :closable="!busy" @cancel="emit('close')">
    <a-alert v-if="error" type="error">{{ error }}</a-alert>
    <a-space style="margin: 12px 0"><a-button :loading="busy" @click="load">重新读取</a-button></a-space>
    <template v-if="state">
      <a-alert :type="state.editable ? 'info' : 'warning'">账号状态：{{ state.onlineState === 'offline' ? '离线' : state.onlineState === 'online' ? '在线，禁止修改' : '未知，禁止修改' }}。保存前再次核对离线状态；配置保存与重登后的实际效果分别验证。</a-alert>
      <p>操作编号：<code>{{ requestId }}</code></p>
      <a-alert v-if="target">{{ operation === 'restore' ? '恢复原操作涉及的字段，保留其他属性' : '保留当前属性并关闭冲突；原操作结果仍未知' }}。关联编号：{{ target }}</a-alert>
      <a-form :model="values" layout="vertical" style="margin-top: 16px">
        <a-row :gutter="16"><a-col v-for="field in state.fields" :key="field.key" :span="8">
          <a-form-item :label="field.label"><a-input-number v-model="values[field.key]" :data-field="field.key"
            :min="field.min" :max="field.max" :precision="0" :disabled="busy || !state.editable || operation !== 'patch'" /></a-form-item>
        </a-col></a-row>
      </a-form>
      <a-textarea v-if="operation === 'accept-current'" v-model="reason" :max-length="128" :disabled="busy" placeholder="填写保留当前属性的原因" />
      <a-space><a-button :disabled="busy || !state.editable" @click="preview">预览属性操作</a-button>
        <a-button type="primary" :disabled="busy || prepared?.state !== 'PREVIEWED'" @click="save">确认属性预览</a-button></a-space>
      <a-table v-if="prepared" :data="prepared.changes || []" :pagination="false" style="margin-top: 16px">
        <template #columns><a-table-column title="字段" data-index="label" /><a-table-column title="当前值" data-index="before" /><a-table-column title="写入值" data-index="after" /></template>
      </a-table>
    </template>
    <a-table :data="history" :pagination="{ pageSize: 5 }" style="margin-top: 20px" row-key="requestId">
      <template #columns>
        <a-table-column title="操作编号" data-index="requestId" /><a-table-column title="状态"><template #cell="{record}">{{ labels[record.state] || record.state }}</template></a-table-column>
        <a-table-column title="操作"><template #cell="{record}"><a-space wrap>
          <a-button v-if="record.state === 'PREPARED'" :disabled="busy" size="small" @click="reconcile(record)">核对结果</a-button>
          <a-button v-if="record.state === 'PERSISTED'" :disabled="busy" size="small" @click="historical(record, 'restore')">预览属性恢复</a-button>
          <a-button v-if="record.state === 'CONFLICT'" :disabled="busy" size="small" @click="historical(record, 'accept-current')">保留当前属性</a-button>
        </a-space></template></a-table-column>
      </template>
    </a-table>
  </a-modal>
</template>
