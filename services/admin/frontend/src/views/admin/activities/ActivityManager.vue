<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';
type Field = { name: string; label: string; type: string; group: string; min: number; max: number; readonly: boolean; default: any };
type Section = { key: string; title: string; primaryKey: string | null; autoId: boolean; writable?: boolean; deleteAllowed?: boolean; notice?: string; fields: Field[] };
type Row = { revision: string; values: Record<string, any> | null };
type Edit = Row & { requestId: string; operation: 'upsert' | 'delete' | 'restore' | 'accept-current'; targetRequestId?: string; reason?: string };
const sections = ref<Section[]>([]), active = ref('signin'), rows = ref<Row[]>([]);
const loading = ref(false), saving = ref(false), error = ref(''), notice = ref('');
const edit = ref<Edit | null>(null), history = ref<any[]>([]), showHistory = ref(false), prepared = ref<any>(null), pending = ref<Edit | null>(null);
const imported = ref('');
const section = computed(() => sections.value.find(s => s.key === active.value));
const groups = computed(() => [...new Set(section.value?.fields.map(f => f.group) || [])]);
const fields = (group: string) => section.value?.fields.filter(f => f.group === group) || [];
const summaries = computed(() => section.value?.fields.filter(f => ['lv','id','box_id','Dname','item_id','name','d_Name','start_time','end_time','end_date','te_date_end','d_on','d_off','item1','item_code1','item2','item_code2','item3','item_code3'].includes(f.name)) || []);
const endpoint = () => `/api/v1/admin/activities/${active.value}`;
const labels: Record<string, string> = { PREVIEWED: '仅预览', PREPARED: '结果待核对', PERSISTED: '配置已保存', NOT_APPLIED: '未写入', CONFLICT: '当前值与前后快照均不同', ACKNOWLEDGED: '已确认保留当前值，原结果未知', NO_CHANGE: '无需写入' };
function uuid() {
  const bytes = crypto.getRandomValues(new Uint8Array(16)); bytes[6] = (bytes[6]! & 15) | 64; bytes[8] = (bytes[8]! & 63) | 128;
  const text = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return `${text.slice(0,8)}-${text.slice(8,12)}-${text.slice(12,16)}-${text.slice(16,20)}-${text.slice(20)}`;
}
function message(e: any) { return e?.response?.data?.message || e?.message || '操作失败，请先核对历史'; }
watch(() => JSON.stringify(edit.value && { revision: edit.value.revision, values: edit.value.values, operation: edit.value.operation, reason: edit.value.reason, targetRequestId: edit.value.targetRequestId }), () => {
  prepared.value = null; pending.value = null; if (edit.value) edit.value.requestId = uuid();
});
async function load() {
  loading.value = true; error.value = ''; rows.value = []; edit.value = null; prepared.value = null; pending.value = null;
  try { const r = await Request.get<any>(endpoint()); rows.value = r.data.rows; notice.value = [r.data.moduleNotice, r.data.notice].filter(Boolean).join(' '); }
  catch (e) { error.value = message(e); }
  finally { loading.value = false; }
}
function begin(row?: Row, operation: Edit['operation'] = 'upsert') {
  imported.value = ''; prepared.value = null; pending.value = null;
  if (row) edit.value = { ...JSON.parse(JSON.stringify(row)), requestId: uuid(), operation };
  else {
    const values: Record<string, any> = {};section.value?.fields.forEach(f => { values[f.name] = f.default; });
    edit.value = { revision: 'new', values, requestId: uuid(), operation };
  }
}
async function preview() {
  if (!edit.value || saving.value) return;
  saving.value = true; error.value = '';
  try {
    pending.value = JSON.parse(JSON.stringify(edit.value));
    const body: any = { ...pending.value };if (body.operation === 'restore' || body.operation === 'accept-current') delete body.values;
    prepared.value = (await Request.post(endpoint() + '/validate', body)).data;
  } catch (e) { prepared.value = null; error.value = message(e); }
  finally { saving.value = false; }
}
async function save() {
  if (!pending.value || prepared.value?.state !== 'PREVIEWED' || saving.value) return;
  saving.value = true; error.value = '';
  try {
    const body: any = { ...pending.value };let result: any;
    if (body.operation === 'restore' || body.operation === 'accept-current') {
      delete body.values;result = (await Request.post(`${endpoint()}/history/${body.targetRequestId}/${body.operation}`, body)).data;
    } else if (body.operation === 'delete') result = (await Request.delete(endpoint(), { data: body })).data;
    else result = (await Request.put(endpoint(), body)).data;
    if (result.persisted) { Message.success('操作已核对，配置持久化结果明确；游戏效果仍需验证');await load(); }
    else { error.value = labels[result.state] || '结果尚未确认，请核对历史';await openHistory(); }
  } catch (e) { error.value = message(e); }
  finally { saving.value = false; }
}
async function historyAction(row: any, operation: 'restore' | 'accept-current') {
  if (saving.value || row.legacy) return;
  saving.value = true; error.value = '';
  try {
    const current = (await Request.get<any>(`${endpoint()}/history/${row.requestId}/current`)).data;
    showHistory.value = false;edit.value = { ...current, requestId: uuid(), operation, targetRequestId: row.requestId, ...(operation === 'accept-current' ? { reason: '' } : {}) };
  } catch (e) { error.value = message(e); }
  finally { saving.value = false; }
}
async function reconcile(row: any) {
  if (saving.value) return;saving.value = true;
  try { const result = (await Request.post<any>(`${endpoint()}/history/${row.requestId}/reconcile`)).data;Message.info(labels[result.state] || result.state);await openHistory(); }
  catch (e) { error.value = message(e); }
  finally { saving.value = false; }
}
function download(name: string, value: any) {
  const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }));
  const a = document.createElement('a'); a.href = url; a.download = name; a.click(); URL.revokeObjectURL(url);
}
function downloadBefore(record: any) {
  try { download(`${active.value}-before-${record.id}.json`, JSON.parse(record.before_json)); }
  catch { Message.error('历史快照格式损坏，已停止导出；请保留原审计记录核查'); }
}
function previewImport() {
  try {
    const value = JSON.parse(imported.value), data = value.values || value;
    const names = section.value!.fields.map(f => f.name).sort();
    if (JSON.stringify(Object.keys(data).sort()) !== JSON.stringify(names)) throw new Error('快照字段不匹配');
    if (edit.value?.operation !== 'upsert' || !edit.value.values) return;
    for (const f of section.value!.fields) if (!f.readonly && f.name !== section.value!.primaryKey) edit.value.values[f.name] = data[f.name];
    Message.info('已填入表单，请预览核对后保存');
  } catch (e) { Message.error(message(e)); }
}
async function openHistory() {
  try { history.value = (await Request.get<any[]>(endpoint() + '/history')).data;showHistory.value = true; }
  catch (e) { error.value = message(e); }
}
onMounted(async () => {
  loading.value = true;
  try { sections.value = (await Request.get<Section[]>('/api/v1/admin/activities')).data;await load(); }
  catch (e) { error.value = message(e); }
  finally { loading.value = false; }
});
</script>

<template>
  <a-card title="活动管理" style="margin: 24px">
    <a-alert style="margin-bottom: 16px">签到、战令与收集箱沿用配套游戏插件。此处修改活动规则，保留玩家领取和收集进度。活动到期不自动重置玩家记录。</a-alert>
    <a-select v-model="active" @change="load" :disabled="saving" style="width: 360px"><a-option v-for="s in sections" :key="s.key" :value="s.key">{{ s.title }}{{ s.writable === false ? "（只读）" : "" }}</a-option></a-select>
    <a-alert v-if="error" type="error">{{ error }}</a-alert>
    <a-space style="margin: 16px 0" wrap>
      <a-button @click="load" :loading="loading" :disabled="saving">刷新</a-button>
      <a-button type="primary" v-if="section && section.writable !== false && !error && (section.primaryKey || !rows.length)" :disabled="loading || saving" @click="begin()">新增配置</a-button>
      <a-button :disabled="loading || !!error" @click="download(`${active}-config.json`, { section: active, rows })">导出配置</a-button>
      <a-button :disabled="loading || !!error" @click="openHistory">修改记录与快照</a-button>
    </a-space>
    <a-table :data="rows" :loading="loading" :pagination="{ pageSize: 10 }" :scroll="{ x: 1000 }" row-key="revision">
      <template #columns>
        <a-table-column v-for="f in summaries" :key="f.name" :title="f.label">
          <template #cell="{ record }">{{ record.values[f.name] ?? '未设置' }}</template>
        </a-table-column>
        <a-table-column title="操作" :width="180" fixed="right">
          <template #cell="{ record }">
            <a-space><a-button size="small" :disabled="saving" @click="begin(record)">{{ section?.writable === false ? "查看" : "编辑" }}</a-button>
              <a-button v-if="section?.primaryKey && section.writable !== false && section.deleteAllowed !== false" size="small" status="danger" :disabled="saving" @click="begin(record, 'delete')">删除</a-button>
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>
    <p style="color: var(--color-text-3)">{{ notice }}</p>
  </a-card>
  <a-modal :visible="!!edit" :title="section?.title" :width="1000" :footer="false" :mask-closable="false" :closable="!saving" @cancel="edit = null">
    <template v-if="edit && section">
      <a-alert v-if="error" type="error">{{error}}</a-alert>
      <p>操作编号：{{edit.requestId}} · {{edit.operation}}。连接中断时保留编号并核对历史。</p>
      <a-alert v-if="edit.operation === 'accept-current'" type="warning">仅保留当前配置并关闭结果冲突，不认定原操作成功或失败，不改写原生配置。</a-alert>
      <a-input v-if="edit.operation === 'accept-current'" v-model="edit.reason" :disabled="saving" :max-length="128" placeholder="填写保留当前配置并关闭冲突的原因" />
      <a-form v-if="edit.values" :model="edit.values" layout="vertical">
        <a-collapse :default-active-key="groups.slice(0, 1)">
          <a-collapse-item v-for="group in groups" :key="group" :header="group">
            <a-row :gutter="16">
              <a-col v-for="f in fields(group)" :key="f.name" :span="8">
                <a-form-item :label="f.label">
                  <a-input-number v-if="f.type === 'int'" v-model="edit.values[f.name]" :min="f.min" :max="f.max" :precision="0" :disabled="saving || edit.operation !== 'upsert' || section.writable === false || f.readonly || (f.name === section.primaryKey && (section.autoId || edit.revision !== 'new'))" />
                  <a-input v-else v-model="edit.values[f.name]" :max-length="255" :disabled="saving || edit.operation !== 'upsert' || section.writable === false || f.readonly" :placeholder="f.type === 'date' ? 'YYYY-MM-DD' : f.type === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : ''" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-item>
          <a-collapse-item v-if="section.writable !== false && edit.operation === 'upsert'" key="restore" header="从单条配置快照恢复到表单">
            <a-textarea v-model="imported" placeholder="粘贴修改记录中的单条配置 JSON；保留当前编号，核对表单后再保存。" :auto-size="{ minRows: 3, maxRows: 8 }" :disabled="saving" />
            <a-button @click="previewImport" :disabled="saving">填入表单</a-button>
          </a-collapse-item>
        </a-collapse>
      </a-form>
      <a-table v-if="prepared" :data="prepared.changes" :pagination="false" :columns="[{title:'字段',dataIndex:'label'},{title:'原值',dataIndex:'before'},{title:'新值',dataIndex:'after'}]" />
      <a-alert v-if="prepared && prepared.state !== 'PREVIEWED'" type="warning">{{labels[prepared.state] || prepared.state}}，请先查看操作历史。</a-alert>
      <a-space style="margin-top: 20px">
        <a-button v-if="section.writable !== false" :disabled="saving" @click="preview">预览操作</a-button>
        <a-button v-if="section.writable !== false" type="primary" :disabled="saving || prepared?.state !== 'PREVIEWED'" :loading="saving" @click="save">确认本次预览</a-button>
        <a-button :disabled="saving" @click="edit = null">取消</a-button>
      </a-space>
    </template>
  </a-modal>
  <a-modal v-model:visible="showHistory" title="操作历史与旧版快照" :width="900" :footer="false">
    <a-alert>预览不写原生数据。结果待核对时不要更换编号重试；恢复只处理原操作字段。旧版 pending 保留现场，不自动重试。</a-alert>
    <a-table :data="history" :pagination="{ pageSize: 10 }" row-key="id">
      <template #columns>
        <a-table-column title="时间" data-index="created_at" />
        <a-table-column title="管理员" data-index="actor" />
        <a-table-column title="操作" data-index="operation_name" />
        <a-table-column title="状态"><template #cell="{record}">{{labels[record.state] || record.state}}{{record.legacy ? '（旧版）' : ''}}</template></a-table-column>
        <a-table-column title="操作编号" data-index="requestId" />
        <a-table-column title="原因" data-index="reason" />
        <a-table-column title="核对与恢复"><template #cell="{record}">
          <a-button v-if="!record.legacy && record.state === 'PREPARED'" :disabled="saving" @click="reconcile(record)">核对结果</a-button>
          <a-button v-if="!record.legacy && record.state === 'PERSISTED'" :disabled="saving" @click="historyAction(record, 'restore')">预览恢复</a-button>
          <a-button v-if="!record.legacy && record.state === 'CONFLICT'" :disabled="saving" @click="historyAction(record, 'accept-current')">保留当前值并关闭冲突</a-button>
        </template></a-table-column>
        <a-table-column title="快照"><template #cell="{ record }">
          <a-button size="small" @click="download(`${active}-audit-${record.id}.json`, record)">下载</a-button>
          <a-button size="small" :disabled="record.before_json === 'null'" @click="downloadBefore(record)">修改前配置</a-button>
        </template></a-table-column>
      </template>
    </a-table>
  </a-modal>
</template>
