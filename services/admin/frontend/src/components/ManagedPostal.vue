<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import Request from '../api/Request'
import ItemPicker from './ItemPicker.vue'
const props=defineProps<{visible:boolean;characNo:number;name:string}>()
const emit=defineEmits<{(e:'update:visible',value:boolean):void}>()
const busy=ref(false),error=ref(''),current=ref<any>(null),history=ref<any[]>([]),historyLoaded=ref(false)
const frozen=ref<any>(null),receipt=ref<any>(null),item=ref<any>(null)
const quantity=ref(1),gold=ref(0),upgrade=ref(0),forge=ref(0),amplifyOption=ref(0),amplifyValue=ref(0),seal=ref(false),message=ref(''),reason=ref('')
const operation=ref('send'),target=ref<string>()
const root=computed(()=>`api/v1/postal/${props.characNo}`)
const pending=computed(()=>history.value.filter(r=>['PREPARED','CONFLICT'].includes(r.state)))
const unknown=computed(()=>['UNKNOWN','SUBMITTING','PREPARED','CONFLICT'].includes(receipt.value?.state))
const editable=computed(()=>!busy.value&&!frozen.value&&!!current.value&&historyLoaded.value&&(!pending.value.length||operation.value==='accept-current'))
const failure=(e:any)=>e.response?.data?.message||e.message||'邮件结果无法确认，请保留原操作编号'
function uuid(){const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]!&15)|64;b[8]=(b[8]!&63)|128;const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`}
function reset(){frozen.value=null;receipt.value=null;item.value=null;quantity.value=1;gold.value=0;upgrade.value=0;forge.value=0;amplifyOption.value=0;amplifyValue.value=0;seal.value=false;message.value='';reason.value='';operation.value='send';target.value=undefined}
async function reload(){
  const results=await Promise.allSettled([Request.get(root.value+'/send-status'),Request.get(root.value+'/history')])
  if(results[0].status==='fulfilled')current.value=results[0].value.data
  else{current.value=null;error.value=failure(results[0].reason)}
  historyLoaded.value=results[1].status==='fulfilled'
  if(results[1].status==='fulfilled'){
    history.value=results[1].value.data
    if(frozen.value){const row=history.value.find(r=>r.requestId===frozen.value.requestId);if(row)receipt.value=row}
  }else{history.value=[];error.value=failure(results[1].reason)}
}
async function refresh(){if(busy.value)return;busy.value=true;error.value='';try{await reload()}finally{busy.value=false}}
watch(()=>[props.visible,props.characNo],async()=>{if(props.visible){reset();current.value=null;history.value=[];historyLoaded.value=false;await refresh()}},{immediate:true})
function request(){
  if(!current.value||!historyLoaded.value)throw new Error('请先读取收件身份和操作历史')
  if(!reason.value.trim())throw new Error('请填写操作原因')
  if(operation.value==='send'&&!item.value&&gold.value===0&&!message.value)throw new Error('请选择物品、填写金币或正文')
  return {receiveCharacNo:props.characNo,requestId:uuid(),revision:current.value.revision,operation:operation.value,reason:reason.value,targetRequestId:target.value,
    mail:operation.value==='accept-current'?undefined:{itemId:item.value?.id||0,quantity:item.value?quantity.value:0,gold:gold.value,message:message.value,upgrade:upgrade.value,seperateUpgrade:forge.value,amplifyOption:amplifyOption.value,amplifyValue:amplifyValue.value,sealFlag:seal.value}}
}
async function preview(){
  if(busy.value||!frozen.value&&!editable.value)return;busy.value=true;error.value=''
  try{if(!frozen.value)frozen.value=request();receipt.value=(await Request.post(root.value+'/preview',frozen.value)).data;await reload()}
  catch(e:any){error.value=failure(e)}finally{busy.value=false}
}
async function submit(){
  if(busy.value||!frozen.value||receipt.value?.state!=='PREVIEWED')return
  busy.value=true;error.value='';receipt.value={...receipt.value,state:'SUBMITTING'}
  try{receipt.value=(await Request.post('api/v1/postal',frozen.value)).data;await reload()}
  catch(e:any){receipt.value={...receipt.value,state:'UNKNOWN'};error.value=failure(e)}finally{busy.value=false}
}
async function reconcile(row:any){
  if(busy.value)return;busy.value=true;error.value=''
  try{const result=(await Request.post(`${root.value}/history/${row.requestId}/reconcile`)).data;if(frozen.value?.requestId===row.requestId)receipt.value=result;await reload()}
  catch(e:any){error.value=failure(e)}finally{busy.value=false}
}
function load(row:any){
  if(busy.value||unknown.value||row.batchRequestId||row.state!=='PREVIEWED'||row.actor!==current.value?.actor)return
  frozen.value={receiveCharacNo:props.characNo,requestId:row.requestId,revision:row.revision,operation:row.operation,reason:row.reason,targetRequestId:row.targetRequestId||undefined,mail:row.operation==='send'?row.outcome.mail:undefined};receipt.value=row
}
async function begin(row?:any){
  if(busy.value||unknown.value&&!row)return;busy.value=true;error.value=''
  try{await reload();if(!current.value||!historyLoaded.value)return;if(row){const actual=history.value.find(r=>r.requestId===row.requestId);if(actual?.state!=='CONFLICT')throw new Error('历史状态已改变，请重新核对')}
    if(!row&&pending.value.length)throw new Error('仍有未决邮件，请先核对历史');reset();if(row){operation.value='accept-current';target.value=row.requestId}
  }catch(e:any){error.value=failure(e)}finally{busy.value=false}
}
function label(state:string){return ({PREVIEWED:'仅预览，未发送',PREPARED:'写前记录已保存，待核对',SUBMITTING:'正在提交',UNKNOWN:'响应不明，保留原编号',PERSISTED:'邮件已写入，领取未验证',NOT_APPLIED:'确认未提交',CONFLICT:'结果不明，需人工核对',ACKNOWLEDGED:'已人工确认保留，原结果未知',NO_CHANGE:'未发送新邮件'} as Record<string,string>)[state]||state}
</script>
<template>
  <a-modal :visible="visible" :title="`邮件管理：${name}（角色 ${characNo}）`" :width="1040" :mask-closable="false" :footer="false" @cancel="emit('update:visible',false)">
    <a-space direction="vertical" fill>
      <a-alert>发送者固定为 DNF Manager。附件依据绑定 PVF 校验；正文按数据库 UTF-8 保存，客户端显示和实际领取仍需验证。结果不明时保留原编号核对。</a-alert>
      <a-alert v-if="error" type="error">{{error}}</a-alert>
      <a-space><a-button :disabled="busy" @click="refresh">读取身份与历史</a-button><span v-if="current">账号 UID {{current.uid}} · {{current.atomicReceipt?'InnoDB 原子回执':'含 MyISAM，中断须人工核对'}}</span></a-space>
      <a-alert v-if="pending.length" type="warning">有 {{pending.length}} 项未决邮件，不能新建发送；请核对下方原操作。</a-alert>
      <a-alert v-if="operation==='accept-current'" type="warning">确认 {{target}} 的当前结果并保留原生数据；不会重发或撤销邮件，也不证明原邮件已送达。请先人工核对并填写原因。</a-alert>
      <a-form :model="{quantity,gold,upgrade,forge,amplifyOption,amplifyValue,seal,message,reason}" layout="vertical">
        <template v-if="operation==='send'">
          <a-form-item label="附件物品（可不选，金币或正文可以单独发送）"><ItemPicker v-model="item" :disabled="!editable" /></a-form-item>
          <a-grid :cols="3" :col-gap="16">
            <a-form-item label="数量"><a-input-number v-model="quantity" :disabled="!editable||!item" :min="1" :max="2147483647" /></a-form-item>
            <a-form-item label="金币" field="gold"><a-input-number v-model="gold" placeholder="邮件金币" :disabled="!editable" :min="0" :max="2147483647" /></a-form-item>
            <a-form-item label="强化"><a-input-number v-model="upgrade" :disabled="!editable||!item" :min="0" :max="31" /></a-form-item>
            <a-form-item label="锻造"><a-input-number v-model="forge" :disabled="!editable||!item" :min="0" :max="31" /></a-form-item>
            <a-form-item label="增幅属性"><a-select v-model="amplifyOption" :disabled="!editable||!item"><a-option :value="0">无</a-option><a-option :value="1">体力</a-option><a-option :value="2">精神</a-option><a-option :value="3">力量</a-option><a-option :value="4">智力</a-option></a-select></a-form-item>
            <a-form-item label="增幅值"><a-input-number v-model="amplifyValue" :disabled="!editable||!item" :min="0" :max="65535" /></a-form-item>
          </a-grid>
          <a-form-item label="封装（由服务器核对 PVF，史诗及不兼容物品拒绝）"><a-switch v-model="seal" :disabled="!editable||!item" /></a-form-item>
          <a-form-item label="正文"><a-textarea v-model="message" :disabled="!editable" :max-length="255" show-word-limit /></a-form-item>
        </template>
        <a-form-item label="操作原因" field="reason"><a-input v-model="reason" placeholder="填写本次邮件操作原因" :disabled="!editable" :max-length="100" /></a-form-item>
      </a-form>
      <a-space wrap>
        <a-button :disabled="busy||!editable&&!frozen||unknown" @click="preview">{{frozen?'重读原预览':'生成预览'}}</a-button>
        <a-button type="primary" :disabled="busy||receipt?.state!=='PREVIEWED'" @click="submit">提交已预览内容</a-button>
        <a-button :disabled="busy||unknown||pending.length>0" @click="begin()">新建发送预览</a-button>
        <a-button v-if="frozen" :disabled="busy" @click="reconcile(frozen)">核对原操作</a-button>
      </a-space>
      <a-alert v-if="frozen" :type="unknown?'warning':'info'">操作编号 {{frozen.requestId}} · {{receipt?label(receipt.state):'预览响应未确认'}}<pre v-if="receipt" style="white-space:pre-wrap;overflow-wrap:anywhere">{{JSON.stringify(receipt.outcome,null,2)}}</pre></a-alert>
      <a-table :data="history" :pagination="false" row-key="requestId" :scroll="{x:1100}">
        <template #columns>
          <a-table-column title="操作编号" data-index="requestId" :width="310" />
          <a-table-column title="状态" :width="230"><template #cell="{record}">{{label(record.state)}}</template></a-table-column>
          <a-table-column title="原因" data-index="reason" />
          <a-table-column title="邮件编号"><template #cell="{record}">{{record.outcome?.postalId||'—'}}</template></a-table-column>
          <a-table-column title="核对" :width="240"><template #cell="{record}"><a-space wrap>
            <a-button v-if="record.state==='PREVIEWED'&&!record.batchRequestId&&record.actor===current?.actor" :disabled="busy||unknown" size="mini" @click="load(record)">加载原预览</a-button>
            <span v-if="record.batchRequestId">所属批量任务：{{record.batchRequestId}}</span>
            <a-button v-if="record.state==='PREPARED'" :disabled="busy" size="mini" @click="reconcile(record)">核对结果</a-button>
            <a-button v-if="record.state==='CONFLICT'" :disabled="busy" size="mini" @click="begin(record)">人工确认保留</a-button>
          </a-space></template></a-table-column>
        </template>
      </a-table>
    </a-space>
  </a-modal>
</template>
