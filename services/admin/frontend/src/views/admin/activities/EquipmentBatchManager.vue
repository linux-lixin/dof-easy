<script setup lang="ts">
import {computed,onMounted,ref} from 'vue';
import Request from '../../../api/Request';
const props=defineProps<{section:string;file:File|null}>();const emit=defineEmits<{(e:'busy-change',value:boolean):void;(e:'changed'):void}>();
const busy=ref(false),error=ref(''),reason=ref(''),taskId=ref(''),task=ref<any>(null),history=ref<any[]>([]),control=ref<any>(null),controlId=ref(''),page=ref(1),sourceId=ref('');
const key='equipment.batch.identifiers.v1';const endpoint=()=>`/api/v1/admin/activities/${props.section}/batches`;
const controlRows=ref<any[]>([]),controlPage=ref(1),controlTotal=ref(0);
const labels:Record<string,string>={PREVIEWED:'已冻结预览，未执行',PREPARED:'执行中或中断待核对',PERSISTED:'已记录终态，请检查逐项结果',NOT_APPLIED:'未执行项已取消',CONFLICT:'原结果待人工核对',NO_CHANGE:'无需修改',ACKNOWLEDGED:'已保留当前值，原结果仍未知'};
const active=computed(()=>task.value&&['PREVIEWED','PREPARED'].includes(task.value.state));
function remember(){let x:any={};try{x=JSON.parse(sessionStorage.getItem(key)||'{}')}catch{}x[props.section]={taskId:taskId.value,controlId:controlId.value};sessionStorage.setItem(key,JSON.stringify(x));sessionStorage.setItem('equipment.batch.lastSection.v1',props.section)}
function message(e:any){return e.response?.data?.message||e.message||'批量请求失败，请读取原任务编号'}
async function perform(action:()=>Promise<void>){if(busy.value)return;busy.value=true;emit('busy-change',true);error.value='';try{await action()}catch(e){error.value=message(e)}finally{busy.value=false;emit('busy-change',false)}}
async function readTask(){if(!taskId.value)return;task.value=(await Request.get<any>(`${endpoint()}/${encodeURIComponent(taskId.value)}?page=${page.value}&pageSize=20`)).data;remember();await readControlHistory()}
async function readControlHistory(){const result=(await Request.get<any>(`${endpoint()}/${taskId.value}/controls?page=${controlPage.value}&pageSize=20`)).data;if(!Array.isArray(result.rows)||!Number.isFinite(Number(result.total)))throw new Error('任务控制记录响应格式不兼容');controlRows.value=result.rows;controlTotal.value=Number(result.total)}
async function controlPageChanged(value:number){controlPage.value=value;await perform(readControlHistory)}
async function selectControl(id:string){controlId.value=id;remember();await perform(readControl)}
async function readHistory(){history.value=(await Request.get<any[]>(endpoint())).data}
async function load(){await perform(async()=>{await readTask();await readHistory()})}
async function preview(mode:'import'|'clear'|'restore'){
 await perform(async()=>{
  if(active.value)throw new Error('请先完成或取消当前任务');if(!reason.value.trim())throw new Error('请填写批量操作原因');if(mode==='import'&&!props.file)throw new Error('请先选择并检查网页规则文件');if(mode==='restore'&&!sourceId.value)throw new Error('请填写原批量任务编号');
  const state=(await Request.get<any>(endpoint()+'/state')).data;taskId.value=crypto.randomUUID();control.value=null;controlId.value='';task.value=null;page.value=1;controlPage.value=1;controlRows.value=[];controlTotal.value=0;remember();
  const edit={requestId:taskId.value,revision:state.revision,reason:reason.value};
  if(mode==='import'){const query=new URLSearchParams(edit);task.value=(await Request.post<any>(endpoint()+'/import/preview?'+query,props.file,{timeout:1800000,headers:{'Content-Type':'application/json'}})).data}
  else task.value=(await Request.post<any>(endpoint()+`/${mode}/preview`,mode==='restore'?{...edit,sourceTaskId:sourceId.value}:edit,{timeout:1800000})).data;
  await readHistory();
 });
}
async function step(){await perform(async()=>{if(!task.value)return;task.value=(await Request.post<any>(`${endpoint()}/${taskId.value}/step`,{revision:task.value.revision},{timeout:1800000})).data;await readHistory();emit('changed')})}
async function open(id:string){await perform(async()=>{taskId.value=id;page.value=1;controlPage.value=1;controlRows.value=[];controlTotal.value=0;task.value=null;control.value=null;controlId.value='';remember();await readTask()})}
async function next(value:number){page.value=value;await perform(readTask)}
async function reconcile(child:string){await perform(async()=>{task.value=(await Request.post<any>(`${endpoint()}/${taskId.value}/items/${child}/reconcile`,{revision:task.value.revision})).data;page.value=1;emit('changed')})}
async function readControl(){if(!controlId.value)return;control.value=(await Request.get<any>(`${endpoint()}/${taskId.value}/controls/${controlId.value}`)).data}
async function previewControl(operation:'cancel'|'accept-current',child?:string){await perform(async()=>{
 if(!reason.value.trim())throw new Error('请填写控制原因');const edit:any={requestId:crypto.randomUUID(),revision:task.value.revision,operation,reason:reason.value};
 if(child){const row=(await Request.get<any>(`${endpoint()}/${taskId.value}/items/${child}`)).data;edit.childRequestId=child;edit.currentRevision=row.currentRevision}
 controlId.value=edit.requestId;remember();await Request.post(`${endpoint()}/${taskId.value}/controls/preview`,edit);await readControl();
})}
async function reconcileControl(){await perform(async()=>{await readControl();await readTask();await readHistory()})}
async function commitControl(){await perform(async()=>{if(!control.value?.request)throw new Error('请先读取控制预览');await Request.post(`${endpoint()}/${taskId.value}/controls`,control.value.request);await readControl();await readTask();await readHistory();emit('changed')})}
onMounted(async()=>{try{const saved=JSON.parse(sessionStorage.getItem(key)||'{}')[props.section];if(saved){taskId.value=saved.taskId||'';controlId.value=saved.controlId||''}}catch{}await perform(async()=>{await readHistory();await readTask();await readControl()})});
</script>
<template>
 <a-card title="批量规则任务" style="margin-bottom:16px">
  <a-alert>导入按代码新增或更新，保留文件未包含的规则；清除只针对冻结预览中的规则。每次执行最多20项，页面关闭或服务重启后读取原任务继续。配置写入与组件、游戏效果分别验收。</a-alert>
  <a-alert v-if="error" type="error">{{error}}</a-alert>
  <a-input v-model="reason" :input-attrs="{'aria-label':'批量操作原因'}" placeholder="批量操作或控制原因" :max-length="128" :disabled="busy" />
  <a-space wrap style="margin:12px 0"><a-button :disabled="busy||active||!file" @click="preview('import')">预览全表文件导入</a-button><a-button status="danger" :disabled="busy||active" @click="preview('clear')">预览清除全部规则</a-button><a-input v-model="sourceId" :input-attrs="{'aria-label':'原批量任务编号'}" placeholder="恢复来源任务编号" :disabled="busy" /><a-button :disabled="busy||active||!sourceId" @click="preview('restore')">预览关联恢复</a-button></a-space>
  <a-space wrap><a-input v-model="taskId" :input-attrs="{'aria-label':'批量任务编号'}" placeholder="原任务编号" :disabled="busy" /><a-button :disabled="busy" @click="load">读取原批量任务</a-button></a-space>
  <a-space v-if="task" direction="vertical" fill>
   <div>任务 {{task.requestId}}：{{labels[task.state]||task.state}}；操作 {{task.operation}}；总计 {{task.summary.total}} 项</div><div>逐项统计 {{JSON.stringify(task.summary.counts)}}；终态说明 {{task.outcome||'尚未结束'}}</div>
   <a-alert v-if="task.operation==='clear' && Number(task.moduleSnapshotAtFinish?.rowCount)>0" type="warning">执行结束时仍有 {{task.moduleSnapshotAtFinish.rowCount}} 条规则；外部新增或变动的规则未被额外删除，请核对。</a-alert>
   <a-space><a-button :disabled="busy||!active" type="primary" @click="step">执行下一段（最多20项）</a-button><a-button :disabled="busy||!active" @click="previewControl('cancel')">预览取消未提交项</a-button></a-space>
   <a-table :data="task.items" row-key="requestId" :pagination="{current:page,pageSize:20,total:Number(task.summary.total)}" @page-change="next" :scroll="{x:900}"><template #columns><a-table-column title="规则代码" data-index="targetKey" /><a-table-column title="子操作编号" data-index="requestId" /><a-table-column title="状态"><template #cell="{record}">{{labels[record.state]||record.state}}</template></a-table-column><a-table-column title="原预览"><template #cell="{record}"><pre>{{JSON.stringify({before:record.before,after:record.after})}}</pre></template></a-table-column><a-table-column title="核对"><template #cell="{record}"><a-button v-if="record.state==='PREPARED'" :disabled="busy" @click="reconcile(record.requestId)">核对原子项</a-button><a-button v-if="record.state==='CONFLICT'" :disabled="busy" @click="previewControl('accept-current',record.requestId)">预览保留当前值</a-button></template></a-table-column></template></a-table>
  </a-space>
  <a-table v-if="task" :data="controlRows" row-key="requestId" :pagination="{current:controlPage,pageSize:20,total:controlTotal}" @page-change="controlPageChanged"><template #columns><a-table-column title="任务控制编号" data-index="requestId" /><a-table-column title="控制状态"><template #cell="{record}">{{labels[record.state]||record.state}}</template></a-table-column><a-table-column title="原因" data-index="reason" /><a-table-column title="读取"><template #cell="{record}"><a-button :disabled="busy" @click="selectControl(record.requestId)">读取此控制</a-button></template></a-table-column></template></a-table>
  <a-space v-if="controlId" wrap><span>控制编号 {{controlId}}：{{labels[control?.state]||control?.state||'待核对'}}</span><a-button :disabled="busy" @click="reconcileControl">读取控制回执</a-button><a-button :disabled="busy||!control?.request||control?.state!=='PREVIEWED'" @click="commitControl">确认本次任务控制</a-button></a-space>
  <a-space wrap><a-button v-for="row in history" :key="row.requestId" :disabled="busy" @click="open(row.requestId)">{{row.operation}} {{row.requestId}} {{labels[row.state]||row.state}}</a-button></a-space>
 </a-card>
</template>
