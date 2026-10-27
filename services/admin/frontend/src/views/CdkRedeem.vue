<script setup lang="ts">
import {computed,ref} from 'vue'
import Request from '../api/Request'
function uuid(){const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]!&15)|64;b[8]=(b[8]!&63)|128;const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`}
const busy=ref(false),error=ref(''),code=ref(''),account=ref(''),itemCode=ref(false),recipient=ref(0),requestId=ref(sessionStorage.getItem('cdk-request-id')||uuid()),captchaId=ref(uuid()),captcha=ref(''),frozen=ref<any>(null),receipt=ref<any>(null)
const uncertain=computed(()=>['UNKNOWN','PREPARED','CONFLICT'].includes(receipt.value?.state))
const stateLabels:Record<string,string>={PREVIEWED:'兑换预览已保留，未发奖；继续需刷新验证码',PREPARED:'兑换意图已保存，结果待核对',PERSISTED:'兑换已写入，游戏到账或领取仍待核实',NOT_APPLIED:'确认未提交；新兑换需使用新编号',CONFLICT:'发放结果未知，请联系管理员核对，勿重复兑换',ACKNOWLEDGED:'管理员已保留未知结果并冻结此码，不能再次兑换',UNKNOWN:'响应未知，先按原编号核对',REJECTED:'请求被拒绝，请保留编号核对或刷新验证码',NO_RECORD:'未找到原编号回执；可核对原输入后提交同一编号'}
function refreshCaptcha(){captchaId.value=uuid();captcha.value=''}
function body(){
  if(!frozen.value){if(!account.value||!code.value)throw new Error('请填写收取账号和卡密');if(itemCode.value&&(!Number.isInteger(recipient.value)||recipient.value<1))throw new Error('物品码需要收取账号所属的角色编号');frozen.value={requestId:requestId.value,content:code.value,useAccount:account.value,charach:itemCode.value?recipient.value:0};sessionStorage.setItem('cdk-request-id',requestId.value)}
  return frozen.value
}
const failure=(e:any)=>e.response?.data?.message||e.message||'结果未知，请保留原编号核对'
async function submit(){
  if(busy.value||uncertain.value||['PERSISTED','NOT_APPLIED','ACKNOWLEDGED'].includes(receipt.value?.state))return
  busy.value=true;error.value=''
  try{const b=body();receipt.value={state:'UNKNOWN'};receipt.value=(await Request.post('api/v1/keys/use',{...b,valiId:captchaId.value,valicode:captcha.value})).data}
  catch(e:any){receipt.value={state:e.response&&[400,404].includes(e.response.status)?'REJECTED':'UNKNOWN'};error.value=failure(e)}finally{busy.value=false}
}
async function reconcile(){
  if(busy.value)return;busy.value=true;error.value=''
  try{receipt.value=(await Request.post('api/v1/keys/use/reconcile',body())).data}
  catch(e:any){if(e.response?.status===404)receipt.value={state:'NO_RECORD'};error.value=failure(e)}finally{busy.value=false}
}
function fresh(){if(busy.value||uncertain.value)return;frozen.value=null;receipt.value=null;error.value='';requestId.value=uuid();sessionStorage.setItem('cdk-request-id',requestId.value);refreshCaptcha()}
</script>
<template>
  <a-card title="CDK 兑换与结果核对" style="max-width:760px;margin:40px auto">
    <a-space direction="vertical" fill>
      <a-alert>奖励以服务器保存的卡密定义为准。点券码填写收取账号；物品码还须提供该账号所属角色的编号。请保存操作编号，响应不明时先核对。网页不保存卡密到浏览器存储。</a-alert>
      <a-alert v-if="error" type="error">{{error}}</a-alert>
      <a-form :model="{code,account,itemCode,recipient,requestId,captcha}" layout="vertical">
        <a-form-item label="操作编号（恢复时填写原编号）"><a-input v-model="requestId" placeholder="CDK操作编号" :disabled="busy||!!frozen" /></a-form-item>
        <a-form-item label="卡密"><a-input-password v-model="code" placeholder="输入卡密" :max-length="255" :disabled="busy||!!frozen" /></a-form-item>
        <a-form-item label="收取账号"><a-input v-model="account" placeholder="输入收取账号" :max-length="64" :disabled="busy||!!frozen" /></a-form-item>
        <a-form-item label="这是物品码"><a-switch v-model="itemCode" :disabled="busy||!!frozen" /></a-form-item>
        <a-form-item v-if="itemCode" label="该账号所属角色编号"><a-input-number v-model="recipient" :min="1" :max="2147483647" :disabled="busy||!!frozen" /></a-form-item>
        <a-form-item label="本页专用验证码"><a-space><a-input v-model="captcha" placeholder="输入CDK验证码" :max-length="16" :disabled="busy" /><img :src="`/api/v1/keys/captcha/${captchaId}`" alt="CDK验证码，点击刷新" @click="refreshCaptcha" /><a-button :disabled="busy" @click="refreshCaptcha">刷新验证码</a-button></a-space></a-form-item>
      </a-form>
      <a-space wrap>
        <a-button type="primary" :disabled="busy||uncertain||['PERSISTED','NOT_APPLIED','ACKNOWLEDGED'].includes(receipt?.state)" @click="submit">{{frozen?'继续同编号兑换':'提交兑换'}}</a-button>
        <a-button :disabled="busy" @click="reconcile">核对原编号</a-button>
        <a-button :disabled="busy||uncertain" @click="fresh">新建兑换编号</a-button>
      </a-space>
      <a-alert v-if="frozen" :type="uncertain?'warning':'info'">操作编号 {{frozen.requestId}}<template v-if="receipt"><br />{{stateLabels[receipt.state]||receipt.state}}<br v-if="receipt.postalId" /><span v-if="receipt.postalId">邮件编号 {{receipt.postalId}}</span></template></a-alert>
      <a-link href="/login">管理员登录</a-link>
    </a-space>
  </a-card>
</template>
