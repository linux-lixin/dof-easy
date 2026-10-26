<script setup lang="ts">
import { ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';
const uid = ref<number>(), state = ref<any>(null), history = ref<any[]>([]), legacy = ref<any[]>([]), legacyPage = ref(1);
const days = ref(1), reason = ref(''), operation = ref('ban'), target = ref(''), busy = ref(false), error = ref('');
const previewed = ref<any>(null), pending = ref<any>(null), requestId = ref('');
const labels: Record<string,string> = { PREVIEWED:'仅预览',PREPARED:'结果待核对',PERSISTED:'处罚已保存',NO_CHANGE:'无需写入',NOT_APPLIED:'未写入',CONFLICT:'当前值存在冲突',ACKNOWLEDGED:'已保留当前值，原结果未知' };
function uuid(){const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]!&15)|64;b[8]=(b[8]!&63)|128;const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`;}
const message=(e:any)=>e?.response?.data?.message||e?.message||'操作失败，请保留编号并核对历史';
const endpoint=()=>`/api/v1/admin/accounts/${uid.value}/ban`;
watch(()=>JSON.stringify([days.value,reason.value,operation.value,target.value,state.value?.revision]),()=>{requestId.value=uuid();previewed.value=null;pending.value=null;});
watch(uid,()=>{operation.value='ban';target.value='';reason.value='';state.value=null;history.value=[];legacy.value=[];previewed.value=null;pending.value=null;});
async function oldHistory(){legacy.value=(await Request.get<any[]>(`/api/v1/admin/accounts/${uid.value}/history?page=${legacyPage.value}`)).data;}
async function load(){if(busy.value)return;busy.value=true;error.value='';state.value=null;previewed.value=null;pending.value=null;
  try{state.value=(await Request.get<any>(endpoint())).data;history.value=(await Request.get<any[]>(endpoint()+'/history')).data;legacyPage.value=1;await oldHistory();}
  catch(e){error.value=message(e);}finally{busy.value=false;}}
async function preview(){if(busy.value||!state.value)return;busy.value=true;error.value='';
  try{pending.value={requestId:requestId.value,revision:state.value.revision,operation:operation.value,reason:reason.value,days:operation.value==='ban'?days.value:0,...(target.value?{targetRequestId:target.value}:{})};previewed.value=(await Request.post<any>(endpoint()+'/preview',pending.value)).data;}
  catch(e){previewed.value=null;error.value=message(e);}finally{busy.value=false;}}
async function save(){if(busy.value||previewed.value?.state!=='PREVIEWED'||!pending.value)return;busy.value=true;error.value='';
  try{const body=pending.value;const r=body.operation==='ban'?await Request.put<any>(endpoint(),body):body.operation==='unban'?await Request.delete<any>(endpoint(),{data:body}):await Request.post<any>(endpoint()+'/'+body.operation,body);
    if(r.data.persisted){Message.success('处罚持久化结果已核对；实际登录与游戏效果仍需验证');state.value=(await Request.get<any>(endpoint())).data;previewed.value=null;pending.value=null;}
    else error.value=labels[r.data.state]||r.data.state;
    history.value=(await Request.get<any[]>(endpoint()+'/history')).data;
  }catch(e){error.value=message(e);}finally{busy.value=false;}}
async function historical(row:any,action:string){await load();if(!state.value)return;operation.value=action;target.value=row.requestId;reason.value='';}
async function reconcile(row:any){if(busy.value)return;busy.value=true;error.value='';try{const r=(await Request.post<any>(endpoint()+`/history/${row.requestId}/reconcile`)).data;Message.info(labels[r.state]||r.state);history.value=(await Request.get<any[]>(endpoint()+'/history')).data;state.value=(await Request.get<any>(endpoint())).data;}catch(e){error.value=message(e);}finally{busy.value=false;}}
async function legacyMove(delta:number){if(busy.value)return;busy.value=true;legacyPage.value+=delta;try{await oldHistory();}catch(e){error.value=message(e);}finally{busy.value=false;}}
</script>
<template>
  <a-alert>只处理本后台创建的账号登录处罚。其他来源或处罚类型占用同一个原生主键时拒绝覆盖；封禁限制后续登录，不保证踢出在线角色。</a-alert>
  <a-alert v-if="error" type="error">{{error}}</a-alert>
  <a-space style="margin:16px 0"><a-input-number v-model="uid" :min="1" :max="2147483647" :precision="0" :disabled="busy" placeholder="账号编号"/><a-button :loading="busy" @click="load">读取处罚</a-button></a-space>
  <template v-if="state">
    <p>版本：{{state.revision}}；{{state.penalty ? (state.managedByThisBackend ? '本后台创建的处罚' : '其他来源或类型，禁止覆盖') : '没有登录处罚记录'}}。持久化和实际效果分别验证。</p>
    <pre>{{JSON.stringify(state.penalty,null,2)}}</pre>
    <a-space wrap><a-select v-model="operation" :disabled="busy" style="width:210px" @change="target=''">
      <a-option value="ban">封禁后续登录</a-option><a-option value="unban">解除本后台处罚</a-option>
      <a-option v-if="target && operation==='restore'" value="restore">恢复历史处罚操作</a-option><a-option v-if="target && operation==='accept-current'" value="accept-current">保留当前冲突值</a-option>
    </a-select><a-input-number v-if="operation==='ban'" v-model="days" :min="1" :max="3650" :precision="0" :disabled="busy" placeholder="天数"/><a-input v-model="reason" :max-length="100" :disabled="busy" placeholder="操作原因" style="width:360px"/></a-space>
    <p v-if="target">关联历史：{{target}}</p><p>操作编号：{{requestId}}</p>
    <a-alert v-if="operation==='ban'">开始及结束时间由服务器在预览时固定；确认和重复提交不会顺延期限。</a-alert>
    <a-space style="margin:16px 0"><a-button :disabled="busy" @click="preview">预览处罚操作</a-button><a-button type="primary" :disabled="busy||previewed?.state!=='PREVIEWED'" @click="save">确认处罚预览</a-button></a-space>
    <a-table v-if="previewed" :data="previewed.changes||[]" :pagination="false" :columns="[{title:'字段',dataIndex:'field'},{title:'当前值',dataIndex:'before'},{title:'写入值',dataIndex:'after'}]"/>
  </template>
  <a-table :data="history" :pagination="{pageSize:10}" row-key="requestId"><template #columns>
    <a-table-column title="操作编号" data-index="requestId"/><a-table-column title="操作" data-index="operation"/><a-table-column title="状态"><template #cell="{record}">{{labels[record.state]||record.state}}</template></a-table-column><a-table-column title="原因" data-index="reason"/>
    <a-table-column title="核对与恢复"><template #cell="{record}"><a-space wrap>
      <a-button v-if="record.state==='PREPARED'" :disabled="busy" @click="reconcile(record)">核对处罚结果</a-button><a-button v-if="record.state==='PERSISTED'" :disabled="busy" @click="historical(record,'restore')">预览处罚恢复</a-button><a-button v-if="record.state==='CONFLICT'" :disabled="busy" @click="historical(record,'accept-current')">保留当前处罚</a-button>
    </a-space></template></a-table-column>
  </template></a-table>
  <p>旧版账号审计（保留原记录）</p><a-table :data="legacy" :pagination="false" :columns="[{title:'操作人',dataIndex:'actor'},{title:'操作',dataIndex:'operation'},{title:'原因',dataIndex:'detail'},{title:'时间',dataIndex:'created_at'}]"/>
  <a-space><a-button :disabled="busy||legacyPage===1" @click="legacyMove(-1)">上一页旧审计</a-button><span>第 {{legacyPage}} 页</span><a-button :disabled="busy||legacy.length<50" @click="legacyMove(1)">下一页旧审计</a-button><a-link href="/account-security" target="_blank">玩家自助改密</a-link></a-space>
</template>
