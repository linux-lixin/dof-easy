<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';
type Field = { name: string; label: string; type: string; group: string; min: number; max: number; readonly: boolean; hint?: string; default: any };
type Section = { key: string; title: string; primaryKey: string | null; autoId: boolean; writable?: boolean; deleteAllowed?: boolean; notice?: string; fields: Field[] };
type Row = { revision: string; values: Record<string, any> | null };
type Edit = Row & { requestId: string; operation: 'upsert' | 'delete' | 'restore' | 'accept-current'; targetRequestId?: string; reason?: string };
const sections = ref<Section[]>([]), active = ref('signin'), rows = ref<Row[]>([]);
const loading = ref(false), saving = ref(false), error = ref(''), notice = ref('');
type Context={edit:Edit|null;prepared:any;pending:Edit|null;exact:boolean;submitted:boolean;cancelPending:any;cancelReceipt:any;cancelReason:string;last:any;controls:any[];controlSource:string};
const contexts=reactive<Record<string,Context>>({});
const context=()=>contexts[active.value]||(contexts[active.value]={edit:null,prepared:null,pending:null,exact:false,submitted:false,cancelPending:null,cancelReceipt:null,cancelReason:'',last:null,controls:[],controlSource:''});
const edit=computed({get:()=>context().edit,set:(v:Edit|null)=>{context().edit=v}}),prepared=computed({get:()=>context().prepared,set:v=>{context().prepared=v}}),pending=computed({get:()=>context().pending,set:(v:Edit|null)=>{context().pending=v}});
const history=ref<any[]>([]),showHistory=ref(false),lookup=ref('');
const savedKey='activity.pending.v1';let saved:Record<string,{requestId:string;submitted:boolean;cancelRequestId?:string}>={};
try{const raw=JSON.parse(sessionStorage.getItem(savedKey)||'{}');if(raw&&typeof raw==='object'&&!Array.isArray(raw))for(const [k,v] of Object.entries(raw) as [string,any][]){if(/^[a-z0-9-]{1,50}$/.test(k)&&v&&typeof v.requestId==='string'&&/^[0-9a-f-]{36}$/i.test(v.requestId))saved[k]={requestId:v.requestId,submitted:v.submitted===true,...(typeof v.cancelRequestId==='string'&&/^[0-9a-f-]{36}$/i.test(v.cancelRequestId)?{cancelRequestId:v.cancelRequestId}:{})}}}catch{saved={}}
const hasPending=computed(()=>!!pending.value),formFrozen=computed(()=>!!pending.value&&pending.value.requestId===edit.value?.requestId);
const exact=computed(()=>context().exact),cancelPending=computed(()=>context().cancelPending),cancelReceipt=computed(()=>context().cancelReceipt);
const cancelReason=computed({get:()=>context().cancelReason,set:v=>{context().cancelReason=v}});
const terminal=(state:string)=>['PERSISTED','NOT_APPLIED','NO_CHANGE','ACKNOWLEDGED'].includes(state);
function remember(){const c=context();if(c.pending)saved[active.value]={requestId:c.pending.requestId,submitted:c.submitted,...(c.cancelPending?{cancelRequestId:c.cancelPending.requestId}:saved[active.value]?.cancelRequestId?{cancelRequestId:saved[active.value]!.cancelRequestId}:{})};else delete saved[active.value];sessionStorage.setItem(savedKey,JSON.stringify(saved))}
function clearOperation(){const c=context();c.edit=null;c.pending=null;c.prepared=null;c.exact=false;c.submitted=false;c.cancelPending=null;c.cancelReceipt=null;c.cancelReason='';remember()}
function body(value:Edit){const result:any=JSON.parse(JSON.stringify(value));if(['restore','accept-current'].includes(result.operation))delete result.values;return result}
async function settled(result:any){context().last=result;if(terminal(result.state)){clearOperation();await load();return true}return false}

const imported = ref('');
const legacyPage = ref(1), legacyEdit = ref<any>(null), legacyReason = ref(''), legacyPrepared = ref<any>(null), legacyPending = ref<any>(null), legacyRequestId = ref('');
const assessments: Record<string,string> = { MATCHES_AFTER: '当前值等于写后快照；不代表实际业务效果已经通过', MATCHES_BEFORE: '当前值等于写前快照；不据此推断历史操作从未执行', CONFLICT: '当前值与前后快照均不同，原结果未知', UNKNOWN: '旧自动编号未记录，无法确定新增目标或原结果' };
watch(legacyReason, () => { legacyRequestId.value = uuid();legacyPrepared.value = null;legacyPending.value = null; });
async function openLegacy(row: any) {
  if (saving.value) return;saving.value = true;error.value = '';
  try { legacyEdit.value = (await Request.get<any>(`${endpoint()}/legacy/${row.id}`)).data;legacyReason.value = '';legacyRequestId.value = uuid();legacyPrepared.value = null;legacyPending.value = null; }
  catch(e) { error.value = message(e); } finally { saving.value = false; }
}
async function reviewLegacy(confirm = false) {
  if (saving.value || !legacyEdit.value || confirm && legacyPrepared.value?.state !== 'PREVIEWED') return;
  saving.value = true;error.value = '';
  try {
    if (!confirm) legacyPending.value = { requestId: legacyRequestId.value, revision: legacyEdit.value.revision, reason: legacyReason.value };
    const result = (await Request.post<any>(`${endpoint()}/legacy/${legacyEdit.value.legacyId}/${confirm ? 'confirm' : 'preview'}`, legacyPending.value)).data;
    legacyPrepared.value = result;
    if (confirm && result.state === 'NO_CHANGE') { Message.success('已记录核对结论并保留当前配置；没有执行旧操作');legacyEdit.value = null;await openHistory(false); }
  } catch(e) { error.value = message(e); } finally { saving.value = false; }
}
async function reconcileLegacy(row: any) {
  if (saving.value) return;saving.value = true;
  try { const r = (await Request.post<any>(`${endpoint()}/legacy/${row.id}/operations/${row.resolution.requestId}/reconcile`)).data;Message.info(labels[r.state] || r.state);await openHistory(false); }
  catch(e) { error.value = message(e); } finally { saving.value = false; }
}

const section = computed(() => sections.value.find(s => s.key === active.value));
const groups = computed(() => [...new Set(section.value?.fields.map(f => f.group) || [])]);
const fields = (group: string) => section.value?.fields.filter(f => f.group === group) || [];
const summaries = computed(() => section.value?.fields.filter(f => ['code','restitution','Return_quantity','differ_lv','codenum','announcement','extra_consumption_id','extra_consumption_num','lv','id','box_id','Dname','item_id','name','d_Name','start_time','end_time','end_date','te_date_end','d_on','d_off','item1','item_code1','item2','item_code2','item3','item_code3'].includes(f.name)) || []);
const endpoint = () => `/api/v1/admin/activities/${active.value}`;
const labels: Record<string, string> = { PREVIEWED: '仅预览', PREPARED: '结果待核对', PERSISTED: '配置已保存', NOT_APPLIED: '未写入', CONFLICT: '当前值与前后快照均不同', ACKNOWLEDGED: '已确认保留当前值，原结果未知', NO_CHANGE: '无需写入', UNKNOWN:'提交响应不明，保留原编号', SUBMITTING:'正在提交原编号', PREVIEW_UNKNOWN:'预览响应不明，重读原编号', MISSING_PREVIEW:'仅预览请求未找到，未提交原生写入' };
function uuid() {
  const bytes = crypto.getRandomValues(new Uint8Array(16)); bytes[6] = (bytes[6]! & 15) | 64; bytes[8] = (bytes[8]! & 63) | 128;
  const text = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return `${text.slice(0,8)}-${text.slice(8,12)}-${text.slice(12,16)}-${text.slice(16,20)}-${text.slice(20)}`;
}
function message(e: any) { return e?.response?.data?.message || e?.message || '操作失败，请先核对历史'; }
async function readPinned(id:string,open=true){
  const c=context();if(c.pending&&c.pending.requestId!==id)throw new Error('此活动仍有原编号待核对，不能用另一编号替换');
  const controlId=saved[active.value]?.cancelRequestId;const r=(await Request.get<any>(`${endpoint()}/history/${id}`)).data;lookup.value=id;
  c.controls=(await Request.get<any[]>(`${endpoint()}/history/${id}/cancellations`)).data;c.controlSource=id;
  if(await settled(r))return;
  if(!c.pending||c.pending.requestId!==id||!c.exact&&r.requestReplayAvailable){c.pending=r.requestReplayAvailable?r.request:{requestId:id,revision:r.revision,values:null,operation:r.operation,targetRequestId:r.targetRequestId};c.exact=!!r.requestReplayAvailable;c.submitted=saved[active.value]?.submitted===true||r.state!=='PREVIEWED'}
  c.prepared=r;if(open)c.edit=JSON.parse(JSON.stringify(c.pending));remember();
  const controls=c.controls;
  if(controlId){const row=controls.find(x=>x.requestId===controlId);if(row){c.cancelReceipt=row;if(row.state==='NOT_APPLIED'){c.cancelPending=null;delete saved[active.value]!.cancelRequestId}else c.cancelPending={requestId:row.requestId,revision:row.revision,reason:row.reason};remember()}else c.cancelReceipt={requestId:controlId,state:'UNKNOWN'}}
}
async function load(){
  loading.value=true;error.value='';rows.value=[];history.value=[];const c=context(),old=saved[active.value],recover=!!old&&!c.pending;
  if(recover){c.pending={requestId:old!.requestId,revision:'new',values:null,operation:'upsert'};c.submitted=old!.submitted;c.exact=false;c.prepared={state:old!.submitted?'UNKNOWN':'PREVIEW_UNKNOWN'}}
  try{const r=(await Request.get<any>(endpoint())).data;rows.value=r.rows;notice.value=[r.moduleNotice,r.notice].filter(Boolean).join(' ');if(!c.pending){c.edit=null;c.prepared=null}}
  catch(e){error.value=message(e)}
  if(recover)try{await readPinned(old!.requestId,false)}catch(e:any){if(e.response?.status===404&&!c.submitted)c.prepared={state:'MISSING_PREVIEW'};error.value=message(e)}
  loading.value=false;
}
function begin(row?:Row,operation:Edit['operation']='upsert'){
  if(hasPending.value){error.value='此活动已有保留的原操作编号，请先核对或取消未提交预览';return}
  imported.value='';prepared.value=null;
  if(row)edit.value={...JSON.parse(JSON.stringify(row)),requestId:uuid(),operation};
  else{const values:Record<string,any>={};section.value?.fields.forEach(f=>{values[f.name]=f.default});edit.value={revision:'new',values,requestId:uuid(),operation}}
}
async function preview(){
  if(!edit.value||saving.value)return;saving.value=true;error.value='';
  try{const c=context(),ack=edit.value.operation==='accept-current'&&prepared.value?.state==='CONFLICT'&&edit.value.targetRequestId===pending.value?.requestId;
    if(!pending.value||ack){pending.value=JSON.parse(JSON.stringify(edit.value));c.exact=true;c.submitted=false;remember()}
    if(!c.exact)throw new Error('原请求无法精确重建，请核对或取消预览，不使用近似内容重提');
    prepared.value={state:'PREVIEW_UNKNOWN'};const r=(await Request.post<any>(endpoint()+'/validate',body(pending.value!))).data;prepared.value=r;await settled(r)
  }catch(e){error.value=message(e)}finally{saving.value=false}
}
async function save(){
  if(!pending.value||!exact.value||prepared.value?.state!=='PREVIEWED'||saving.value)return;saving.value=true;error.value='';
  try{const c=context();c.submitted=true;remember();const value=body(pending.value);prepared.value={state:'SUBMITTING'};let result:any;
    if(['restore','accept-current'].includes(value.operation))result=(await Request.post<any>(`${endpoint()}/history/${value.targetRequestId}/${value.operation}`,value)).data;
    else if(value.operation==='delete')result=(await Request.delete<any>(endpoint(),{data:value})).data;
    else result=(await Request.put<any>(endpoint(),value)).data;
    prepared.value=result;if(await settled(result))Message.success('操作已核对，配置持久化结果明确；游戏效果仍需验证')
  }catch(e){prepared.value={state:'UNKNOWN'};error.value=message(e)}finally{saving.value=false}
}
async function readOriginal(){if(saving.value||loading.value)return;saving.value=true;error.value='';try{await readPinned(lookup.value||pending.value!.requestId)}catch(e){error.value=message(e)}finally{saving.value=false}}
async function reconcileOriginal(){
  if(!pending.value||saving.value)return;saving.value=true;error.value='';try{const id=pending.value.requestId,r=(await Request.post<any>(`${endpoint()}/history/${id}/reconcile`)).data;prepared.value=r;if(!await settled(r))await readPinned(id,true)}catch(e:any){if(e.response?.status===404&&!context().submitted)prepared.value={state:'MISSING_PREVIEW'};error.value=message(e)}finally{saving.value=false}
}
function editMissingPreview(){if(context().submitted||prepared.value?.state!=='MISSING_PREVIEW')return;const original=edit.value||pending.value;clearOperation();if(original?.values)edit.value={...original,requestId:uuid()};else load()}
async function cancellation(confirm=false){
  if(!pending.value||saving.value)return;saving.value=true;error.value='';try{const c=context(),id=pending.value.requestId;
    if(!confirm&&!c.cancelPending){if(!c.cancelReason.trim())throw new Error('请填写取消未提交预览的原因');const original=(await Request.get<any>(`${endpoint()}/history/${id}`)).data;if(original.state!=='PREVIEWED')throw new Error('原操作已提交或待核对，不能取消预览');c.cancelPending={requestId:uuid(),revision:original.receiptRevision,reason:c.cancelReason};remember()}
    c.cancelReceipt={state:confirm?'UNKNOWN':'PREVIEW_UNKNOWN'};const result=(await Request.post<any>(`${endpoint()}/history/${id}/cancellations${confirm?'':'/preview'}`,c.cancelPending)).data;c.cancelReceipt=result;
    if(result.state==='PERSISTED'){await readPinned(id,false);Message.info('原预览已取消；迟到的原编号提交也不会写入配置')}
  }catch(e){error.value=message(e)}finally{saving.value=false}
}
async function reconcileCancellation(){
  if(!pending.value||saving.value)return;const c=context(),id=c.cancelPending?.requestId||saved[active.value]?.cancelRequestId;if(!id)return;saving.value=true;error.value='';try{c.cancelReceipt=(await Request.post<any>(`${endpoint()}/history/${pending.value.requestId}/cancellations/${id}/reconcile`)).data;if(c.cancelReceipt.state==='PERSISTED')await readPinned(pending.value.requestId,false);else if(c.cancelReceipt.state==='NOT_APPLIED'){c.cancelPending=null;delete saved[active.value]!.cancelRequestId;remember()}}catch(e:any){if(e.response?.status===404&&c.cancelReceipt?.state==='PREVIEW_UNKNOWN'){c.cancelPending=null;delete saved[active.value]!.cancelRequestId;remember()}error.value=message(e)}finally{saving.value=false}
}
async function reconcileCancelRecord(row:any){if(saving.value)return;saving.value=true;error.value='';try{const c=context(),result=(await Request.post<any>(`${endpoint()}/history/${c.controlSource}/cancellations/${row.requestId}/reconcile`)).data;c.controls=c.controls.map(r=>r.requestId===row.requestId?result:r)}catch(e){error.value=message(e)}finally{saving.value=false}}
async function historyAction(row: any, operation: 'restore' | 'accept-current') {
  if(saving.value||row.legacy)return;
  if(pending.value&&!(operation==='accept-current'&&prepared.value?.state==='CONFLICT'&&row.requestId===pending.value.requestId)){error.value='请先核对当前原编号';return}
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
    if(formFrozen.value||edit.value?.operation!=='upsert'||!edit.value.values)return;
    for (const f of section.value!.fields) if (!f.readonly && f.name !== section.value!.primaryKey) edit.value.values[f.name] = data[f.name];
    Message.info('已填入表单，请预览核对后保存');
  } catch (e) { Message.error(message(e)); }
}
async function openHistory(reset = true) {
  if (reset) legacyPage.value = 1;
  try { history.value = (await Request.get<any[]>(endpoint() + '/history?legacyPage=' + legacyPage.value)).data;showHistory.value = true; }
  catch (e) { error.value = message(e); }
}
onMounted(async () => {
  loading.value = true;
  try { sections.value=(await Request.get<Section[]>('/api/v1/admin/activities')).data;const pinned=Object.keys(saved).find(k=>sections.value.some(s=>s.key===k));if(pinned)active.value=pinned;await load(); }
  catch (e) { error.value = message(e); }
  finally { loading.value = false; }
});
</script>

<template>
  <a-card title="活动与装备规则管理" style="margin: 24px">
    <a-alert style="margin-bottom: 16px">签到、战令与收集箱沿用配套游戏插件。此处修改活动规则，保留玩家领取和收集进度。活动到期不自动重置玩家记录。</a-alert>
    <a-select v-model="active" @change="load" :disabled="saving || loading" style="width: 360px"><a-option v-for="s in sections" :key="s.key" :value="s.key">{{ s.title }}{{ s.writable === false ? "（只读）" : "" }}</a-option></a-select>
    <a-alert v-if="error" type="error">{{ error }}</a-alert>
    <a-space style="margin: 16px 0" wrap>
      <a-button @click="load" :loading="loading" :disabled="saving">刷新</a-button>
      <a-button type="primary" v-if="section && section.writable !== false && !error && (section.primaryKey || !rows.length)" :disabled="loading || saving || hasPending" @click="begin()">新增配置</a-button>
      <a-button :disabled="loading || !!error" @click="download(`${active}-config.json`, { section: active, rows })">导出配置</a-button>
      <a-button :disabled="loading || !!error" @click="openHistory()">修改记录与快照</a-button>
    </a-space>
    <a-space wrap><a-input v-model="lookup" placeholder="按原活动操作编号读取" /><a-button :disabled="saving||loading||!lookup" @click="readOriginal">读取原活动操作</a-button><a-button v-if="hasPending" :disabled="saving||loading" @click="lookup=pending!.requestId;readOriginal()">恢复原操作表单</a-button></a-space>
    <a-alert v-if="hasPending">当前活动保留操作 {{pending!.requestId}}：{{labels[prepared?.state]||prepared?.state}}；请按原编号核对，其他活动可独立管理。</a-alert>
    <a-button v-if="prepared?.state==='MISSING_PREVIEW'&&!context().submitted" :disabled="saving||loading" @click="editMissingPreview">清除未提交的无回执编号</a-button>
    <a-alert v-if="context().last">最近核对 {{context().last.requestId}}：{{labels[context().last.state]||context().last.state}}</a-alert>
    <a-space v-if="hasPending" direction="vertical" fill><a-input v-model="cancelReason" placeholder="填写取消未提交预览的原因" :max-length="128" :disabled="saving||!!cancelPending" /><a-space><a-button :disabled="saving||loading||prepared?.state!=='PREVIEWED'" @click="cancellation(false)">预览取消未提交操作</a-button><a-button :disabled="saving||cancelReceipt?.state!=='PREVIEWED'" @click="cancellation(true)">确认取消原预览</a-button><a-button v-if="cancelPending||saved[active]?.cancelRequestId" :disabled="saving" @click="reconcileCancellation">核对取消编号</a-button></a-space><div v-if="cancelPending||cancelReceipt">取消编号 {{cancelPending?.requestId||cancelReceipt?.requestId}}：{{labels[cancelReceipt?.state]||cancelReceipt?.state}}</div></a-space>
    <a-table v-if="context().controls.length" :data="context().controls" :pagination="false" row-key="requestId"><template #columns><a-table-column title="取消操作编号" data-index="requestId" /><a-table-column title="原操作" data-index="targetRequestId" /><a-table-column title="状态" data-index="state" /><a-table-column title="原因" data-index="reason" /><a-table-column title="核对"><template #cell="{record}"><a-button v-if="record.state==='PREPARED'" :disabled="saving" @click="reconcileCancelRecord(record)">核对取消回执</a-button></template></a-table-column></template></a-table>
    <a-table :data="rows" :loading="loading" :pagination="{ pageSize: 10 }" :scroll="{ x: 1000 }" row-key="revision">
      <template #columns>
        <a-table-column v-for="f in summaries" :key="f.name" :title="f.label">
          <template #cell="{ record }">{{ record.values[f.name] ?? '未设置' }}</template>
        </a-table-column>
        <a-table-column title="操作" :width="180" fixed="right">
          <template #cell="{ record }">
            <a-space><a-button size="small" :disabled="saving || hasPending" @click="begin(record)">{{ section?.writable === false ? "查看" : "编辑" }}</a-button>
              <a-button v-if="section?.primaryKey && section.writable !== false && section.deleteAllowed !== false" size="small" status="danger" :disabled="saving || hasPending" @click="begin(record, 'delete')">删除</a-button>
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
      <a-input v-if="edit.operation === 'accept-current'" v-model="edit.reason" :disabled="saving || formFrozen" :max-length="128" placeholder="填写保留当前配置并关闭冲突的原因" />
      <a-form v-if="edit.values" :model="edit.values" layout="vertical">
        <a-collapse :default-active-key="groups.slice(0, 1)">
          <a-collapse-item v-for="group in groups" :key="group" :header="group">
            <a-row :gutter="16">
              <a-col v-for="f in fields(group)" :key="f.name" :span="8">
                <a-form-item :label="f.label" :extra="f.hint">
                  <a-input-number v-if="f.type === 'int'" v-model="edit.values[f.name]" :min="f.min" :max="f.max" :precision="0" :disabled="saving || formFrozen || edit.operation !== 'upsert' || section.writable === false || f.readonly || (f.name === section.primaryKey && (section.autoId || edit.revision !== 'new'))" />
                  <a-input v-else v-model="edit.values[f.name]" :max-length="255" :disabled="saving || formFrozen || edit.operation !== 'upsert' || section.writable === false || f.readonly" :placeholder="f.type === 'date' ? 'YYYY-MM-DD' : f.type === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : ''" />
                </a-form-item>
              </a-col>
            </a-row>
          </a-collapse-item>
          <a-collapse-item v-if="section.writable !== false && edit.operation === 'upsert'" key="restore" header="从单条配置快照恢复到表单">
            <a-textarea v-model="imported" placeholder="粘贴修改记录中的单条配置 JSON；保留当前编号，核对表单后再保存。" :auto-size="{ minRows: 3, maxRows: 8 }" :disabled="saving || formFrozen" />
            <a-button @click="previewImport" :disabled="saving || formFrozen">填入表单</a-button>
          </a-collapse-item>
        </a-collapse>
      </a-form>
      <a-table v-if="prepared" :data="prepared.changes" :pagination="false" :columns="[{title:'字段',dataIndex:'label'},{title:'原值',dataIndex:'before'},{title:'新值',dataIndex:'after'}]" />
      <a-alert v-if="prepared && prepared.state !== 'PREVIEWED'" type="warning">{{labels[prepared.state] || prepared.state}}，请先查看操作历史。</a-alert>
      <a-alert v-if="pending">原编号 {{pending.requestId}}：{{labels[prepared?.state]||prepared?.state||'尚未核对'}}。表单与提交内容已冻结；关闭弹窗或切换活动会保留编号。{{prepared?.requestReplayUnavailableReason}}</a-alert>
      <a-space style="margin-top: 20px">
        <a-button v-if="section.writable !== false" :disabled="saving || !!cancelPending || (hasPending && !exact)" @click="preview">{{hasPending&&formFrozen?'重读原预览':'预览操作'}}</a-button>
        <a-button v-if="section.writable !== false" type="primary" :disabled="saving || !!cancelPending || !exact || prepared?.state !== 'PREVIEWED'" :loading="saving" @click="save">确认本次预览</a-button>
        <a-button v-if="hasPending" :disabled="saving" @click="reconcileOriginal">核对原操作编号</a-button>
        <a-button v-if="prepared?.state==='MISSING_PREVIEW'&&!context().submitted" :disabled="saving" @click="editMissingPreview">重新编辑未提交表单</a-button>
        <a-button :disabled="saving" @click="edit = null">关闭并保留编号</a-button>
      </a-space>
    </template>
  </a-modal>
  <a-modal v-model:visible="showHistory" title="操作历史与旧版快照" :width="900" :footer="false">
    <a-alert>预览不写原生数据。结果待核对时不要更换编号重试；恢复只处理原操作字段。旧版 pending 保留原日志。核对只记录当前值及原因，不重试旧操作；无法判定的原结果保持未知。</a-alert>
    <a-table :data="history" :pagination="{ pageSize: 10 }" row-key="id">
      <template #columns>
        <a-table-column title="时间" data-index="created_at" />
        <a-table-column title="管理员" data-index="actor" />
        <a-table-column title="操作" data-index="operation_name" />
        <a-table-column title="状态"><template #cell="{record}">{{labels[record.state] || record.state}}{{record.legacy ? (record.resolution?.state === 'NO_CHANGE' ? '（旧版已核对，原记录保留）' : '（旧版）') : ''}}</template></a-table-column>
        <a-table-column title="操作编号"><template #cell="{record}">{{record.legacy ? record.resolution?.requestId : record.requestId}}</template></a-table-column>
        <a-table-column title="原因" data-index="reason" />
        <a-table-column title="核对与恢复"><template #cell="{record}">
          <a-button v-if="record.legacy && record.state === 'pending' && record.resolution?.state !== 'NO_CHANGE' && record.resolution?.state !== 'PREPARED'" :disabled="saving" @click="openLegacy(record)">核对旧日志</a-button>
          <a-button v-if="record.legacy && record.resolution?.state === 'PREPARED'" :disabled="saving" @click="reconcileLegacy(record)">核对确认记录</a-button>
          <a-button v-if="!record.legacy" :disabled="saving" @click="showHistory=false;lookup=record.requestId;readOriginal()">读取原编号</a-button>
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
    <a-space><a-button :disabled="saving || legacyPage === 1" @click="legacyPage--;openHistory(false)">上一页旧日志</a-button><span>旧日志第 {{legacyPage}} 页（每页100条）</span><a-button :disabled="saving || history.filter(r => r.legacy).length < 100" @click="legacyPage++;openHistory(false)">下一页旧日志</a-button></a-space>
  </a-modal>
  <a-modal :visible="!!legacyEdit" title="核对旧版未决日志" :width="1000" :footer="false" :mask-closable="false" :closable="!saving" @cancel="legacyEdit = null">
    <template v-if="legacyEdit">
      <a-alert v-if="error" type="error">{{error}}</a-alert>
      <a-alert type="warning">{{assessments[legacyEdit.assessment]}}。确认将解除这份旧日志对后续配置管理的阻塞；不会改写原日志或原生配置。</a-alert>
      <p>旧日志：{{legacyEdit.legacyId}}；目标：{{legacyEdit.targetKey}}；核对编号：{{legacyRequestId}}</p>
      <a-table :data="legacyEdit.currentRows" :pagination="{pageSize:5}" :scroll="{x:1600}"><template #columns><a-table-column v-for="field in section?.fields || []" :key="field.name" :title="field.label" :data-index="field.name" /></template></a-table>
      <a-textarea v-model="legacyReason" placeholder="填写核对依据及保留当前配置的原因" :max-length="128" :disabled="saving" />
      <a-space style="margin-top:16px"><a-button :disabled="saving" @click="reviewLegacy(false)">预览旧日志核对</a-button><a-button type="primary" :disabled="saving || legacyPrepared?.state !== 'PREVIEWED'" @click="reviewLegacy(true)">确认保留当前配置</a-button></a-space>
    </template>
  </a-modal>
</template>
