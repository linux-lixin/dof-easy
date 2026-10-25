<script setup lang="ts">
import {ref} from 'vue';
import Request from '../api/Request';
const form=ref({account:'',currentPassword:'',newPassword:'',validationIndex:crypto.randomUUID(),valicode:''});
const busy=ref(false),message=ref('');
function refresh(){form.value.validationIndex=crypto.randomUUID();form.value.valicode='';}
async function save(){busy.value=true;message.value='';try{await Request.post('/api/v1/client/password',form.value);message.value='密码已修改，请使用自己的登录器重新登录';form.value.currentPassword='';form.value.newPassword='';}catch(e:any){message.value=e?.response?.data?.message||e?.message||'改密失败';}finally{busy.value=false;refresh();}}
</script>
<template><a-card title="玩家修改密码" style="max-width:480px;margin:60px auto"><a-alert>验证原密码后修改，不更换登录器。管理员密码请通过实例管理流程修改。</a-alert><a-form :model="form" layout="vertical" @submit-success="save"><a-form-item label="账号"><a-input v-model="form.account" :max-length="64"/></a-form-item><a-form-item label="原密码"><a-input-password v-model="form.currentPassword"/></a-form-item><a-form-item label="新密码（8–64位）"><a-input-password v-model="form.newPassword" :max-length="64"/></a-form-item><a-form-item label="验证码"><a-input v-model="form.valicode"/><img :src="`/api/v1/client/password-captcha/${form.validationIndex}`" @click="refresh" alt="点击更换验证码"/></a-form-item><a-button html-type="submit" type="primary" :loading="busy">修改密码</a-button></a-form><p>{{message}}</p></a-card></template>
