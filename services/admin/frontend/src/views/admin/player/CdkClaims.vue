<script setup lang="ts">
import {onMounted,ref} from 'vue'
import Request from '../../../api/Request'
import CdkControl from '../../../components/CdkControl.vue'
import ManagedRecharge from '../../../components/ManagedRecharge.vue'
const rows=ref<any[]>([]),busy=ref(false),error=ref(''),lookup=ref(''),selected=ref<any>(null),controlVisible=ref(false),operation=ref('ack-unknown')
const compensation=ref<any>(null),compensationVisible=ref(false)
const labels:Record<string,string>={PREVIEWED:'预览保留，未提交',PREPARED:'结果待核对',PERSISTED:'已写入，到账待验',NOT_APPLIED:'确认未提交',CONFLICT:'未知，禁止再次兑换',ACKNOWLEDGED:'未知已保留，此码冻结'}
const failure=(e:any)=>e.response?.data?.message||e.message||'CDK历史读取失败'
async function refresh(){if(busy.value)return;busy.value=true;error.value='';try{rows.value=(await Request.get('api/v1/keys/claims')).data;if(selected.value)selected.value=(await Request.get(`api/v1/keys/claims/${selected.value.requestId}`)).data}catch(e){error.value=failure(e)}finally{busy.value=false}}
async function read(){if(busy.value)return;busy.value=true;error.value='';try{selected.value=(await Request.get(`api/v1/keys/claims/${lookup.value}`)).data}catch(e){error.value=failure(e)}finally{busy.value=false}}
async function reconcile(row:any){if(busy.value)return;busy.value=true;error.value='';try{selected.value=(await Request.post(`api/v1/keys/claims/${row.requestId}/reconcile`)).data;rows.value=(await Request.get('api/v1/keys/claims')).data}catch(e){error.value=failure(e)}finally{busy.value=false}}
function control(row:any,action:string){selected.value=row;operation.value=action;controlVisible.value=true}
onMounted(refresh)
</script>
<template>
  <a-space direction="vertical" fill>
    <a-alert>这里管理现有网站生成的 CDK；晴空配套文件的 CDK 规则仍需单独对照。发奖复用邮件或点券写入器；持码兑换不代表已验证玩家账号登录。未知结果禁止重发，确认保留后冻结该码。此页不显示卡密原文。</a-alert>
    <a-alert v-if="error" type="error">{{error}}</a-alert>
    <a-space><a-button :disabled="busy" @click="refresh">读取兑换历史</a-button><a-input v-model="lookup" placeholder="按原兑换编号核对" /><a-button :disabled="busy||!lookup" @click="read">读取指定回执</a-button><a-link href="/cdk" target="_blank">打开玩家兑换页面</a-link></a-space>
    <div>最近50条，待核对优先；更早记录可按原编号读取。</div>
    <a-table :data="rows" :pagination="false" row-key="requestId" :scroll="{x:1300}"><template #columns>
      <a-table-column title="兑换编号" data-index="requestId" :width="310" />
      <a-table-column title="卡密记录" data-index="keyId" />
      <a-table-column title="受益账号UID" data-index="uid" />
      <a-table-column title="角色编号" data-index="recipient" />
      <a-table-column title="奖励"><template #cell="{record}">{{record.type===0?'点券 '+record.face:'物品 '+record.face}}</template></a-table-column>
      <a-table-column title="状态" :width="220"><template #cell="{record}">{{labels[record.state]||record.state}}</template></a-table-column>
      <a-table-column title="操作" :width="260"><template #cell="{record}"><a-space wrap>
        <a-button :disabled="busy" size="mini" @click="selected=record">查看回执</a-button>
        <a-button v-if="record.state==='PREPARED'" :disabled="busy" size="mini" @click="reconcile(record)">核对兑换</a-button>
        <a-button v-if="record.state==='PERSISTED'&&record.type===0" :disabled="busy" size="mini" @click="compensation=record;compensationVisible=true">预览点券补偿扣回</a-button>
        <a-button v-if="record.state==='PREVIEWED'" :disabled="busy" size="mini" @click="control(record,'cancel-preview')">取消未提交预览</a-button>
        <a-button v-if="record.state==='CONFLICT'" :disabled="busy" size="mini" @click="control(record,'ack-unknown')">保留未知并冻结此码</a-button>
      </a-space></template></a-table-column>
    </template></a-table>
    <template v-if="selected"><a-divider>原编号 {{selected.requestId}}</a-divider><div>{{labels[selected.state]||selected.state}}</div><a-button :disabled="busy" @click="operation=selected.state==='PREVIEWED'?'cancel-preview':'ack-unknown';controlVisible=true">读取控制历史</a-button><pre style="white-space:pre-wrap;overflow-wrap:anywhere">{{JSON.stringify(selected,null,2)}}</pre></template>
    <ManagedRecharge v-if="compensation" v-model:visible="compensationVisible" :uid="Number(compensation.uid)" :account-name="compensation.before?.account||String(compensation.uid)" :source-credit="{requestId:compensation.requestId,delta:Number(compensation.face)}" @changed="refresh" />
    <CdkControl v-model:visible="controlVisible" :source="selected" :operation="operation" @changed="refresh" />
  </a-space>
</template>
