<script setup lang="ts">
import { ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';
const uid=ref<number>(),state=ref<any>(null),history=ref<any[]>([]),password=ref(''),reason=ref(''),operation=ref('reset'),target=ref('');
const busy=ref(false),error=ref(''),requestId=ref(''),prepared=ref<any>(null),pending=ref<any>(null);
const labels:Record<string,string>={PREVIEWED:'仅预览',PREPARED:'结果待核对',PERSISTED:'密码已重置',NOT_APPLIED:'未写入',CONFLICT:'当前密码状态与前后记录均不同',NO_CHANGE:'已保留当前状态',ACKNOWLEDGED:'原结果未知，已确认保留当前状态'};
const endpoint=()=>`/api/v1/admin/accounts/${uid.value}/password`;
const message=(e:any)=>e?.response?.data?.message||e?.message||'操作失败，请保留编号并核对历史';
function uuid(){const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]!&15)|64;b[8]=(b[8]!&63)|128;const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`;}
watch(()=>JSON.stringify([password.value,reason.value,operation.value,target.value,state.value?.revision]),()=>{requestId.value=uuid();prepared.value=null;pending.value=null;});
watch(uid,()=>{state.value=null;history.value=[];password.value='';reason.value='';operation.value='reset';target.value='';pending.value=null;prepared.value=null;});
async function load(){if(busy.value)return;busy.value=true;error.value='';state.value=null;prepared.value=null;pending.value=null;try{state.value=(await Request.get<any>(endpoint()+'-state')).data;history.value=(await Request.get<any[]>(endpoint()+'/history')).data;}catch(e){error.value=message(e);}finally{busy.value=false;}}
async function preview(){if(busy.value||!state.value)return;busy.value=true;error.value='';try{
  pending.value={requestId:requestId.value,revision:state.value.revision,operation:operation.value,reason:reason.value,...(operation.value==='reset'?{newPassword:password.value}:{targetRequestId:target.value})};prepared.value=(await Request.post<any>(endpoint()+'/preview',pending.value)).data;
}catch(e){prepared.value=null;error.value=message(e);}finally{busy.value=false;}}
async function save(){if(busy.value||!pending.value||prepared.value?.state!=='PREVIEWED')return;busy.value=true;error.value='';try{
  const r=(await Request.put<any>(endpoint(),pending.value)).data;
  if(r.persisted){Message.success(operation.value==='reset'?'密码写入已核对，请使用新密码验证登录':'已确认保留当前密码状态，未重置密码');password.value='';pending.value=null;prepared.value=null;operation.value='reset';target.value='';state.value=(await Request.get<any>(endpoint()+'-state')).data;}
  else error.value=labels[r.state]||r.state;
  history.value=(await Request.get<any[]>(endpoint()+'/history')).data;
}catch(e){error.value=message(e);}finally{busy.value=false;}}
async function reconcile(row:any){if(busy.value)return;busy.value=true;error.value='';try{const r=(await Request.post<any>(endpoint()+`/history/${row.requestId}/reconcile`)).data;Message.info(labels[r.state]||r.state);state.value=(await Request.get<any>(endpoint()+'-state')).data;history.value=(await Request.get<any[]>(endpoint()+'/history')).data;}catch(e){error.value=message(e);}finally{busy.value=false;}}
async function keep(row:any){await load();if(!state.value)return;operation.value='accept-current';target.value=row.requestId;password.value='';reason.value='';}
</script>
<template>
  <a-alert>仅重置普通玩家密码，不能修改管理员或自己的密码。旧密码不可查看或恢复。写入结果不明确时先核对历史，不更换编号重复操作。</a-alert>
  <a-alert v-if="error" type="error">{{error}}</a-alert>
  <a-space style="margin:16px 0"><a-input-number v-model="uid" :min="1" :max="2147483647" :precision="0" :disabled="busy" placeholder="改密账号编号"/><a-button :loading="busy" @click="load">读取改密状态</a-button></a-space>
  <template v-if="state">
    <p>账号：{{uid}}；状态版本：{{state.revision}}</p>
    <a-alert v-if="!state.resetAllowed" type="warning">此账号受到保护，不能通过玩家密码重置修改。</a-alert>
    <a-alert v-if="operation==='accept-current'" type="warning">保留当前密码状态并关闭冲突记录；原操作结果仍未知。关联编号：{{target}}</a-alert>
    <a-form :model="{}" layout="vertical" style="margin-top:16px">
      <a-form-item v-if="operation==='reset'" label="玩家新密码"><a-input-password v-model="password" :max-length="64" :disabled="busy||!state.resetAllowed" autocomplete="new-password" placeholder="填写8至64位玩家新密码"/></a-form-item>
      <a-form-item label="操作原因"><a-input v-model="reason" :max-length="100" :disabled="busy" placeholder="填写管理员改密或核对原因"/></a-form-item>
    </a-form>
    <p>操作编号：{{requestId}}</p><a-alert v-if="prepared?.state==='PREVIEWED'">目标账号 {{uid}}，{{operation==='reset'?'将写入当前填写的新密码，旧密码不保留':'保留当前密码状态，不写入新密码'}}。请核对账号和原因后确认。</a-alert>
    <a-space style="margin:16px 0"><a-button :disabled="busy||operation==='reset'&&!state.resetAllowed" @click="preview">预览改密操作</a-button><a-button type="primary" :disabled="busy||prepared?.state!=='PREVIEWED'" @click="save">确认改密预览</a-button></a-space>
  </template>
  <a-table :data="history" :pagination="{pageSize:10}" row-key="requestId"><template #columns><a-table-column title="操作编号" data-index="requestId"/><a-table-column title="状态"><template #cell="{record}">{{labels[record.state]||record.state}}</template></a-table-column><a-table-column title="原因" data-index="reason"/><a-table-column title="核对"><template #cell="{record}"><a-button v-if="record.state==='PREPARED'" :disabled="busy" @click="reconcile(record)">核对改密结果</a-button><a-button v-if="record.state==='CONFLICT'" :disabled="busy" @click="keep(record)">保留当前密码状态</a-button></template></a-table-column></template></a-table>
</template>
