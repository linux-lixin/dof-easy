<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import Request from '../api/Request'
const props=defineProps<{visible:boolean;source:any;operation:string}>()
const emit=defineEmits<{(e:'update:visible',value:boolean):void;(e:'changed'):void}>()
const busy=ref(false),error=ref(''),reason=ref(''),frozen=ref<any>(null),receipt=ref<any>(null),history=ref<any[]>([])
const base=computed(()=>`api/v1/keys/claims/${props.source?.requestId}/controls`)
function uuid(){const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]!&15)|64;b[8]=(b[8]!&63)|128;const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`}
const failure=(e:any)=>e.response?.data?.message||e.message||'控制结果未知，请保留操作编号并核对历史'
async function reload(){history.value=(await Request.get(base.value)).data;if(frozen.value){const r=history.value.find((r:any)=>r.requestId===frozen.value.requestId);if(r)receipt.value=r}}
async function run(action:()=>Promise<void>){if(busy.value)return;busy.value=true;error.value='';try{await action()}catch(e){error.value=failure(e)}finally{busy.value=false}}
watch(()=>[props.visible,props.source?.requestId,props.operation],()=>{if(props.visible){reason.value='';frozen.value=null;receipt.value=null;history.value=[];run(reload)}},{immediate:true})
function preview(){return run(async()=>{if(!frozen.value){if(!reason.value.trim())throw new Error('请填写CDK控制原因');if(!props.source?.receiptRevision)throw new Error('缺少原回执版本，请刷新历史');frozen.value={requestId:uuid(),revision:props.source.receiptRevision,reason:reason.value,operation:props.operation}}
  receipt.value=(await Request.post(base.value+'/preview',frozen.value)).data;await reload()})}
function submit(){return run(async()=>{if(!frozen.value||receipt.value?.state!=='PREVIEWED')return;receipt.value={...receipt.value,state:'UNKNOWN'};receipt.value=(await Request.post(base.value,frozen.value)).data;await reload();emit('changed')})}
function reconcile(row:any){return run(async()=>{const r=(await Request.post(`${base.value}/${row.requestId}/reconcile`)).data;if(frozen.value?.requestId===row.requestId)receipt.value=r;await reload();emit('changed')})}
function load(row:any){frozen.value={requestId:row.requestId,revision:row.revision,reason:row.reason,operation:row.operation};receipt.value=row;reason.value=row.reason}
</script>
<template>
  <a-modal :visible="visible" :title="operation==='cancel-preview'?'取消未提交兑换预览':'保留未知并冻结卡密'" :width="880" :footer="false" :mask-closable="false" @cancel="emit('update:visible',false)">
    <a-space direction="vertical" fill>
      <a-alert type="warning">兑换编号 {{source?.requestId}}，卡密记录 {{source?.keyId}}。取消只针对尚未提交的预览；原编号不能再发奖。确认未知只保留当前数据并冻结此码，原发放结果仍未知，不证明到账、不回收、不重发。每次控制另记当前管理员与原因，不改原受益账号。</a-alert>
      <a-alert v-if="error" type="error">{{error}}</a-alert>
      <a-input v-model="reason" placeholder="填写CDK控制原因" :max-length="100" :disabled="busy||!!frozen" />
      <a-space wrap>
        <a-button :disabled="busy||!!receipt&&receipt.state!=='PREVIEWED'" @click="preview">{{frozen?'重读控制预览':'预览CDK控制'}}</a-button>
        <a-button type="primary" :disabled="busy||receipt?.state!=='PREVIEWED'" @click="submit">提交CDK控制</a-button>
        <a-button :disabled="busy" @click="run(reload)">读取控制历史</a-button>
        <a-button v-if="frozen" :disabled="busy" @click="reconcile(frozen)">核对控制编号</a-button>
      </a-space>
      <a-alert v-if="frozen">控制编号 {{frozen.requestId}} · {{receipt?.state||'预览响应未知'}} · 不修改原生奖励数据，不代表到账或领取成功</a-alert>
      <pre v-if="receipt?.outcome" style="white-space:pre-wrap;overflow-wrap:anywhere">{{JSON.stringify(receipt.outcome,null,2)}}</pre>
      <a-table :data="history" :pagination="false" row-key="requestId">
        <template #columns>
          <a-table-column title="控制编号" data-index="requestId" />
          <a-table-column title="管理员" data-index="actor" />
          <a-table-column title="状态" data-index="state" />
          <a-table-column title="原因" data-index="reason" />
          <a-table-column title="操作"><template #cell="{record}">
            <a-button v-if="record.state==='PREVIEWED'" :disabled="busy||!!frozen" size="mini" @click="load(record)">加载控制预览</a-button>
            <a-button v-if="record.state==='PREPARED'" :disabled="busy" size="mini" @click="reconcile(record)">核对控制结果</a-button>
          </template></a-table-column>
        </template>
      </a-table>
    </a-space>
  </a-modal>
</template>
