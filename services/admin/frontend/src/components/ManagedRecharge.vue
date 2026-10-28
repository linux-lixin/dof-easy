<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Request from '../api/Request'
const props = defineProps<{visible:boolean;uid:number;accountName:string;sourceCredit?:{requestId:string;delta:number};currency?:'cera'|'cera_point'}>()
const emit = defineEmits<{(e:'update:visible',value:boolean):void;(e:'changed'):void}>()
const busy=ref(false), error=ref(''), current=ref<any>(null), history=ref<any[]>([])
const operation=ref('credit'), amount=ref<number>(), reason=ref(''), target=ref<string>()
const frozen=ref<any>(null), receipt=ref<any>(null)
const points=computed(()=>props.currency==='cera_point')
const unit=computed(()=>points.value?'代币券':'点券')
const endpoint=computed(()=>points.value?'api/v1/recharge/points':'api/v1/recharge')
const root=computed(()=>`${endpoint.value}/${props.uid}`)
const uncertain=computed(()=>['UNKNOWN','SUBMITTING','PREPARED','CONFLICT'].includes(receipt.value?.state))
const failure=(e:any)=>e.response?.data?.message||e.message||'无法核对货币操作，请保留原编号'
function uuid(){const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]!&15)|64;b[8]=(b[8]!&63)|128;const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`}
async function reload(){
  const results=await Promise.allSettled([Request.get(root.value),Request.get(root.value+'/history')])
  if(results[0].status==='fulfilled')current.value=results[0].value.data
  else {current.value=null;error.value=failure(results[0].reason)}
  if(results[1].status==='fulfilled')history.value=results[1].value.data
  else {history.value=[];error.value=failure(results[1].reason)}
}
async function refresh(){if(busy.value)return;busy.value=true;error.value='';try{await reload()}finally{busy.value=false}}
function reset(){frozen.value=null;receipt.value=null;operation.value='credit';amount.value=undefined;reason.value='';target.value=undefined}
watch(()=>[props.visible,props.uid,props.sourceCredit?.requestId,props.currency],async()=>{if(props.visible){reset();current.value=null;history.value=[];await refresh();if(current.value&&props.sourceCredit){operation.value='compensate';target.value=props.sourceCredit.requestId;amount.value=-props.sourceCredit.delta}}},{immediate:true})
function request(){
  if(!current.value)throw new Error('请先读取当前余额')
  if(!reason.value.trim())throw new Error('请填写操作原因')
  if(operation.value!=='accept-current'&&(!Number.isSafeInteger(amount.value)||!amount.value||Math.abs(amount.value)>9999999||
    (operation.value==='credit'?amount.value<0:amount.value>0)))throw new Error('请输入正确的整数数额')
  return {uid:props.uid,requestId:uuid(),revision:current.value.revision,operation:operation.value,
    [points.value?'amount':'cera']:operation.value==='accept-current'?undefined:amount.value,reason:reason.value,targetRequestId:target.value}
}
async function preview(){
  if(busy.value)return;busy.value=true;error.value=''
  try{
    if(!frozen.value)frozen.value=request()
    receipt.value=(await Request.post(root.value+'/preview',frozen.value)).data
    await reload()
  }catch(e:any){error.value=failure(e)}finally{busy.value=false}
}
async function submit(){
  if(busy.value||!frozen.value||receipt.value?.state!=='PREVIEWED')return
  busy.value=true;error.value='';receipt.value={...receipt.value,state:'SUBMITTING'}
  try{
    receipt.value=(await Request.post(endpoint.value,frozen.value)).data
    await reload();if(receipt.value.persisted)emit('changed')
  }catch(e:any){receipt.value={...receipt.value,state:'UNKNOWN'};error.value=failure(e)}finally{busy.value=false}
}
async function reconcile(row:any){
  if(busy.value)return;busy.value=true;error.value=''
  try{
    const result=(await Request.post(`${root.value}/history/${row.requestId}/reconcile`)).data
    if(frozen.value?.requestId===row.requestId)receipt.value=result
    await reload();if(result.persisted)emit('changed')
  }catch(e:any){error.value=failure(e)}finally{busy.value=false}
}
function loadPreview(row:any){
  if(busy.value||uncertain.value||row.actor!=current.value?.actor)return
  frozen.value={uid:props.uid,requestId:row.requestId,revision:row.revision,operation:row.operation,
    [points.value?'amount':'cera']:row.operation==='accept-current'?undefined:Number(row.delta),reason:row.reason,targetRequestId:row.targetRequestId||undefined}
  receipt.value=row;operation.value=row.operation;amount.value=row.operation==='accept-current'?undefined:Number(row.delta)
  reason.value=row.reason;target.value=row.targetRequestId||undefined
}
async function begin(row?:any,kind='credit'){
  if(busy.value||uncertain.value&&kind==='credit')return
  busy.value=true;error.value=''
  try{
    await reload();if(!current.value)return
    reset();operation.value=kind
    if(row){target.value=row.requestId;if(kind==='compensate')amount.value=-Number(row.delta)}
  }finally{busy.value=false}
}
function label(state:string){return ({PREVIEWED:'仅预览，未提交',PREPARED:'写前记录已保存，待核对',SUBMITTING:'提交中',UNKNOWN:'响应不明，保留原编号',PERSISTED:'已持久化',NOT_APPLIED:'确认未提交',CONFLICT:'结果不明，需人工核对',ACKNOWLEDGED:'已保留当前值，原结果仍未知',NO_CHANGE:'未更改余额'} as Record<string,string>)[state]||state}
</script>
<template>
  <a-modal :visible="visible" :title="`${unit}管理：${accountName}（UID ${uid}）`" :width="1000" :mask-closable="false" :closable="!busy" :esc-to-close="!busy" :footer="false" @cancel="!busy&&emit('update:visible',false)">
    <a-space direction="vertical" fill>
      <a-alert v-if="points">代币券使用原生 cash_cera_point 钱包，与晴空积分商城分开管理；实际客户端读取和消费仍须验证。</a-alert>
      <a-alert>仅管理员可调整{{unit}}。先预览，再提交同一操作编号；超时先核对历史。扣回采用关联补偿，不恢复旧余额；数据库持久化与玩家实际到账分别验证。</a-alert>
      <a-alert v-if="error" type="error">{{error}}</a-alert>
      <a-alert v-if="current && !current.atomicReceipt" type="warning">MyISAM 无法将余额和回执一起提交。中断后不能凭余额相等判断是否执行；人工核对并明确保留当前值后才能继续。</a-alert>
      <a-space><a-button :loading="busy" @click="refresh">读取余额与历史</a-button><span v-if="current">当前{{unit}}：{{current.amount??current.cera}} · {{current.engine}} · {{current.instanceId}}</span></a-space>
      <a-form layout="vertical" :model="{operation,amount,reason}">
        <a-form-item label="操作"><a-input :model-value="operation==='credit'?'充值':operation==='compensate'?'关联原充值补偿扣回':'明确保留当前余额（不判断原发放结果）'" disabled /></a-form-item>
        <a-form-item v-if="target" label="关联原操作编号"><a-input :model-value="target" disabled /></a-form-item>
        <a-form-item v-if="operation!=='accept-current'" :label="`${unit}数额（充值为正，补偿扣回为负）`">
          <a-input-number v-model="amount" :disabled="busy||!!frozen||!current" :precision="0" :min="operation==='credit'?1:-9999999" :max="operation==='credit'?9999999:-1" />
        </a-form-item>
        <a-form-item label="操作原因"><a-input v-model="reason" :disabled="busy||!!frozen||!current" :max-length="100" placeholder="填写本次调整的原因" /></a-form-item>
      </a-form>
      <a-space wrap>
        <a-button :disabled="busy||!current||!!frozen&&receipt?.state!=='PREVIEWED'&&receipt!==null" @click="preview">{{frozen?'重读原预览':`预览${unit}变更`}}</a-button>
        <a-button type="primary" :disabled="busy||receipt?.state!=='PREVIEWED'" @click="submit">提交本次预览</a-button>
        <a-button v-if="frozen" :disabled="busy" @click="reconcile(frozen)">核对本次操作</a-button>
        <a-button :disabled="busy||uncertain" @click="begin()">新建充值预览</a-button>
      </a-space>
      <a-alert v-if="frozen" :type="uncertain?'warning':'info'">
        操作编号：{{frozen.requestId}} · {{receipt?label(receipt.state):'预览响应未确认，请重读原预览'}}
        <template v-if="receipt"><br />本次记录：{{receipt.beforeAmount??receipt.beforeCera}} → {{receipt.afterAmount??receipt.afterCera}}，变动 {{receipt.delta}}；{{receipt.note}}</template>
      </a-alert>
      <a-table :data="history" :pagination="false" row-key="requestId" :scroll="{x:1200}">
        <template #columns>
          <a-table-column title="操作编号" data-index="requestId" :width="310" />
          <a-table-column title="状态" :width="180"><template #cell="{record}">{{label(record.state)}}</template></a-table-column>
          <a-table-column title="变动" data-index="delta" />
          <a-table-column title="原因" data-index="reason" />
          <a-table-column title="关联原操作" data-index="targetRequestId" />
          <a-table-column title="核对与补偿" :width="280"><template #cell="{record}">
            <a-space wrap>
              <a-button v-if="record.state==='PREVIEWED'&&record.actor==current?.actor" :disabled="busy||uncertain" size="mini" @click="loadPreview(record)">加载原预览</a-button>
              <a-button v-if="record.state==='PREPARED'" :disabled="busy" size="mini" @click="reconcile(record)">核对结果</a-button>
              <a-button v-if="record.state==='PERSISTED'&&record.operation==='credit'" :disabled="busy||uncertain" size="mini" @click="begin(record,'compensate')">预览补偿扣回</a-button>
              <a-button v-if="record.state==='CONFLICT'" :disabled="busy" size="mini" @click="begin(record,'accept-current')">明确保留当前余额</a-button>
            </a-space>
          </template></a-table-column>
        </template>
      </a-table>
    </a-space>
  </a-modal>
</template>
