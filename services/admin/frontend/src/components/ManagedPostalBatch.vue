<script setup lang="ts">
import {computed,reactive,ref,watch} from 'vue'
import Request from '../api/Request'
import ItemPicker from './ItemPicker.vue'
import ManagedPostal from './ManagedPostal.vue'
import PostalBatchControl from './PostalBatchControl.vue'
const props=defineProps<{visible:boolean;recipients:number[]}>()
const emit=defineEmits<{(e:'update:visible',value:boolean):void}>()
const busy=ref(false),running=ref(false),unknown=ref(false),error=ref(''),ids=ref<number[]>([]),history=ref<any[]>([]),historyLoaded=ref(false)
const frozen=ref<any>(null),task=ref<any>(null),item=ref<any>(null),reason=ref(''),retryOf=ref<string>(),inspect=ref<number>()
const mail=reactive({quantity:1,gold:0,message:'',upgrade:0,seperateUpgrade:0,amplifyOption:0,amplifyValue:0,sealFlag:false})
const controlVisible=ref(false),controlOperation=ref('cancel-pending'),retryTargets=ref<number[]>([])
const root='api/v1/postal/batch'
const editable=computed(()=>!busy.value&&!frozen.value&&historyLoaded.value)
const failure=(e:any)=>e.response?.data?.message||e.message||'任务结果无法确认，请读取原编号，不要新建任务重发'
function uuid(){const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]!&15)|64;b[8]=(b[8]!&63)|128;const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`}
function reset(){frozen.value=null;task.value=null;unknown.value=false;item.value=null;reason.value='';retryOf.value=undefined;ids.value=[...props.recipients];Object.assign(mail,{quantity:1,gold:0,message:'',upgrade:0,seperateUpgrade:0,amplifyOption:0,amplifyValue:0,sealFlag:false})}
function restore(data:any){retryTargets.value=data.targets.filter((r:any)=>r.state==='NOT_APPLIED').map((r:any)=>Number(r.recipient));task.value=data;frozen.value={...data.draft,requestId:data.requestId,revision:data.revision};ids.value=[...data.draft.recipients];reason.value=data.draft.reason;retryOf.value=data.draft.retryOf||undefined;Object.assign(mail,data.draft.mail);mail.quantity=data.draft.mail.itemId?data.draft.mail.quantity:1;item.value=data.draft.mail.itemId?{id:data.draft.mail.itemId,name:data.mailPlan.attachment?.name||String(data.draft.mail.itemId)}:null;unknown.value=false}
async function reloadHistory(){try{history.value=(await Request.get(root+'/history')).data;historyLoaded.value=true}catch(e){history.value=[];historyLoaded.value=false;throw e}}
async function refresh(){if(busy.value)return;busy.value=true;error.value='';try{if(frozen.value)restore((await Request.get(`${root}/${frozen.value.requestId}`)).data);await reloadHistory()}catch(e:any){error.value=failure(e)}finally{busy.value=false}}
watch(()=>props.visible,async value=>{if(value){reset();history.value=[];historyLoaded.value=false;await refresh()}else running.value=false},{immediate:true})
function draft(){
  if(!historyLoaded.value)throw new Error('请先读取任务历史')
  if(!ids.value.length||ids.value.length>100)throw new Error('请选择1～100个固定角色')
  if(!reason.value.trim())throw new Error('请填写批量操作原因')
  return {recipients:ids.value,mail:{...mail,itemId:item.value?.id||0,quantity:item.value?mail.quantity:0},reason:reason.value,retryOf:retryOf.value}
}
async function preview(){
  if(busy.value||unknown.value&&task.value?.state!=='PREVIEWED')return;busy.value=true;error.value=''
  try{
    if(!frozen.value){const d=draft();const checked=(await Request.post(root+'/validate',d)).data;frozen.value={...d,requestId:uuid(),revision:checked.revision}}
    restore((await Request.post(root+'/preview',frozen.value)).data);await reloadHistory()
  }catch(e:any){error.value=failure(e)}finally{busy.value=false}
}
function link(step=false){return {requestId:frozen.value.requestId,revision:frozen.value.revision,...(step&&task.value?.targets?.some((r:any)=>r.state==='PREVIEWED')?{childRequestId:task.value.targets.find((r:any)=>r.state==='PREVIEWED').requestId}:{})}}
async function run(start=false){
  if(busy.value||!frozen.value||unknown.value)return;busy.value=true;running.value=true;error.value=''
  try{
    if(start)restore((await Request.post(root,frozen.value)).data)
    while(running.value&&['READY_FOR_NEXT','ALL_RESULTS_RECORDED'].includes(task.value?.phase))restore((await Request.post(`${root}/${frozen.value.requestId}/step`,link(true))).data)
    await reloadHistory()
  }catch(e:any){unknown.value=true;error.value=failure(e)}finally{running.value=false;busy.value=false}
}
async function reconcile(){
  if(busy.value||!frozen.value)return;busy.value=true;error.value=''
  try{restore((await Request.post(`${root}/${frozen.value.requestId}/reconcile`,link())).data);await reloadHistory()}catch(e:any){error.value=failure(e)}finally{busy.value=false}
}
async function load(row:any){if(busy.value)return;busy.value=true;error.value='';try{restore((await Request.get(`${root}/${row.requestId}`)).data)}catch(e:any){error.value=failure(e)}finally{busy.value=false}}
function selectRetry(recipient:number,checked:boolean|(string|number|boolean)[]){retryTargets.value=retryTargets.value.filter(id=>id!==recipient);if(checked===true)retryTargets.value.push(recipient)}
function beginRetry(){
  if(busy.value||unknown.value||!task.value)return
  const failed=task.value.targets.filter((r:any)=>r.state==='NOT_APPLIED'&&retryTargets.value.includes(Number(r.recipient))).map((r:any)=>Number(r.recipient));if(!failed.length)return
  const source=task.value,body=source.draft.mail,attachment=source.mailPlan.attachment;reset();ids.value=failed;retryOf.value=source.requestId;Object.assign(mail,body);item.value=body.itemId?{id:body.itemId,name:attachment?.name||String(body.itemId)}:null
}
function newDraft(){if(!busy.value&&!unknown.value)reset()}
function close(){running.value=false;controlVisible.value=false;emit('update:visible',false)}
function label(state:string){return ({PREVIEWED:'仅预览',PREPARED:'写入待核对',PERSISTED:'已写入，领取未验证',NOT_APPLIED:'确认未提交',CONFLICT:'结果未知，需核对',ACKNOWLEDGED:'已人工保留，原结果未知'} as Record<string,string>)[state]||state}
function phase(state:string){return ({PREVIEWED:'仅预览，尚未开始',READY_FOR_NEXT:'可继续剩余目标',NEEDS_RECONCILIATION:'子结果待核对，已停止后续发放',ALL_RESULTS_RECORDED:'目标回执齐全，待保存汇总',COMPLETED:'全部目标已写入，领取仍待验证',COMPLETED_WITH_FAILURES:'任务结束，存在确认未提交目标',COMPLETED_WITH_UNCERTAINTY:'任务结束，存在人工保留的未知结果'} as Record<string,string>)[state]||state}
</script>
<template>
  <a-modal :visible="visible" title="批量邮件与任务历史" :width="1100" :footer="false" :mask-closable="false" @cancel="close">
    <a-space direction="vertical" fill>
      <a-alert>名单和内容随预览固定。开始后逐目标执行；关闭页面或停止后续目标后，可从历史继续。结果不明时先核对，成功和未知目标不会被自动重发。</a-alert>
      <a-alert v-if="error" type="error">{{error}}</a-alert>
      <a-space><a-button :disabled="busy" @click="refresh">读取原任务与历史</a-button><a-button :disabled="busy||unknown" @click="newDraft">新建批量预览</a-button><a-button v-if="running" @click="running=false">停止后续目标</a-button></a-space>
      <template v-if="!task||task.state==='PREVIEWED'">
        <div>固定角色编号：{{frozen?.recipients?.join('、')||ids.join('、')||'尚未选择角色，可先读取历史'}}；最多100个。</div>
        <a-alert v-if="retryOf" type="warning">关联原任务 {{retryOf}}，仅包含确认未提交目标；服务器会重新核对原身份、内容和PVF批次。</a-alert>
        <a-form :model="{...mail,reason}" layout="vertical">
          <a-form-item label="附件（金币和正文可单独发送）"><ItemPicker v-model="item" :disabled="!editable" /></a-form-item>
          <a-grid :cols="3" :col-gap="16">
            <a-form-item label="数量"><a-input-number v-model="mail.quantity" :disabled="!editable||!item" :min="1" :max="2147483647" /></a-form-item>
            <a-form-item label="金币" field="gold"><a-input-number v-model="mail.gold" placeholder="批量邮件金币" :disabled="!editable" :min="0" :max="2147483647" /></a-form-item>
            <a-form-item label="强化"><a-input-number v-model="mail.upgrade" :disabled="!editable||!item" :min="0" :max="31" /></a-form-item>
            <a-form-item label="锻造"><a-input-number v-model="mail.seperateUpgrade" :disabled="!editable||!item" :min="0" :max="31" /></a-form-item>
            <a-form-item label="增幅属性"><a-select v-model="mail.amplifyOption" :disabled="!editable||!item"><a-option :value="0">无</a-option><a-option :value="1">体力</a-option><a-option :value="2">精神</a-option><a-option :value="3">力量</a-option><a-option :value="4">智力</a-option></a-select></a-form-item>
            <a-form-item label="增幅值"><a-input-number v-model="mail.amplifyValue" :disabled="!editable||!item" :min="0" :max="65535" /></a-form-item>
          </a-grid>
          <a-form-item label="封装"><a-switch v-model="mail.sealFlag" :disabled="!editable||!item" /></a-form-item>
          <a-form-item label="正文"><a-textarea v-model="mail.message" placeholder="批量邮件正文" :disabled="!editable" :max-length="255" /></a-form-item>
          <a-form-item label="批量操作原因" field="reason"><a-input v-model="reason" placeholder="填写本次批量操作原因" :disabled="!editable" :max-length="100" /></a-form-item>
        </a-form>
      </template>
      <a-space wrap>
        <a-button :disabled="busy||unknown||!!task&&task.state!=='PREVIEWED'||!historyLoaded" @click="preview">{{frozen?'重读原批量预览':'生成批量预览'}}</a-button>
        <a-button type="primary" :disabled="busy||unknown||task?.state!=='PREVIEWED'" @click="run(true)">开始批量任务</a-button>
        <a-button :disabled="busy||unknown||!['READY_FOR_NEXT','ALL_RESULTS_RECORDED'].includes(task?.phase)" @click="run()">继续已确认的剩余目标</a-button>
        <a-button :disabled="busy||!frozen" @click="reconcile">核对批量结果</a-button>
        <a-button :disabled="busy||unknown||!retryTargets.length" @click="beginRetry">仅重试确认未提交目标</a-button>
      </a-space>
      <a-space v-if="task?.controlRevision" wrap>
        <a-button :disabled="busy||!task.targets.some((r:any)=>r.state==='PREVIEWED')" @click="controlOperation='cancel-pending';controlVisible=true">永久终止未提交目标</a-button>
        <a-button :disabled="busy||task.phase!=='ALL_RESULTS_RECORDED'" @click="controlOperation='finalize';controlVisible=true">由当前管理员保存汇总</a-button>
        <a-button :disabled="busy" @click="controlVisible=true">任务控制历史</a-button>
        <span>跨管理员接管：终止未提交部分，再勾选目标创建新关联预览；未决项可逐条核对，最后由当前管理员保存汇总。</span>
      </a-space>
      <a-alert v-if="frozen" :type="unknown?'warning':'info'">任务编号 {{frozen.requestId}} · {{unknown?'响应不明，请读取原任务或核对':phase(task?.phase)}}<template v-if="task"><br />回执进度 {{task.progress.terminal}} / {{task.progress.total}}；已写入 {{task.progress.sent}}；未提交 {{task.progress.notApplied}}；未知 {{task.progress.unknown}}；待核对 {{task.progress.pending}}</template></a-alert>
      <pre v-if="task?.state==='PREVIEWED'" style="white-space:pre-wrap;overflow-wrap:anywhere">{{JSON.stringify(task.mailPlan,null,2)}}</pre>
      <a-table v-if="task" :data="task.targets" :pagination="false" row-key="requestId" :scroll="{x:1100}">
        <template #columns>
          <a-table-column title="关联重试"><template #cell="{record}"><a-checkbox v-if="record.state==='NOT_APPLIED'" :model-value="retryTargets.includes(Number(record.recipient))" :disabled="busy" @change="checked=>selectRetry(Number(record.recipient),checked)">选择</a-checkbox></template></a-table-column>
          <a-table-column title="收件角色" data-index="recipient" />
          <a-table-column title="子操作编号" data-index="requestId" :width="310" />
          <a-table-column title="状态" :width="220"><template #cell="{record}">{{label(record.state)}}</template></a-table-column>
          <a-table-column title="邮件编号" data-index="postalId" />
          <a-table-column title="错误"><template #cell="{record}">{{record.error?.message}}</template></a-table-column>
          <a-table-column title="人工核对"><template #cell="{record}"><a-button v-if="['PREPARED','CONFLICT'].includes(record.state)" :disabled="busy" size="mini" @click="inspect=Number(record.recipient)">打开角色核对</a-button></template></a-table-column>
        </template>
      </a-table>
      <a-divider>持久化批量历史</a-divider>
      <a-table :data="history" :pagination="false" row-key="requestId" :scroll="{x:1000}"><template #columns>
        <a-table-column title="任务编号" data-index="requestId" :width="310" />
        <a-table-column title="人数" data-index="total" />
        <a-table-column title="任务记录"><template #cell="{record}">{{record.state==='PERSISTED'?'任务回执已汇总，请读取逐项结果':record.state==='PREVIEWED'?'仅预览':'未结束，请读取详情'}}</template></a-table-column>
        <a-table-column title="原因" data-index="reason" />
        <a-table-column title="来源任务" data-index="retryOf" />
        <a-table-column title="查看"><template #cell="{record}"><a-button :disabled="busy" size="mini" @click="load(record)">读取原任务</a-button></template></a-table-column>
      </template></a-table>
    </a-space>
    <PostalBatchControl v-model:visible="controlVisible" :source="task" :operation="controlOperation" @changed="refresh" />
    <ManagedPostal v-if="inspect" :visible="true" :charac-no="inspect" :name="`角色 ${inspect}`" @update:visible="inspect=undefined;refresh()" />
  </a-modal>
</template>
