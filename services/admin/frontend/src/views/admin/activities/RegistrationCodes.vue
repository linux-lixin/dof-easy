<script setup lang="ts">
import {ref,onMounted} from 'vue';
import Request from '../../../api/Request';
const policy=ref({enabled:false,revision:0}),rows=ref<any[]>([]),history=ref<any[]>([]),page=ref(1),historyPage=ref(1),days=ref(28),busy=ref(false),error=ref(''),created=ref<any>();
const failure=(e:any)=>e?.response?.data?.message||e?.message||'请求失败';
async function load(){policy.value=(await Request.get<any>('/api/v1/admin/registration/policy')).data;rows.value=(await Request.get<any[]>(`/api/v1/admin/registration/codes?page=${page.value}`)).data;history.value=(await Request.get<any[]>(`/api/v1/admin/registration/history?page=${historyPage.value}`)).data;}
async function act(action:string,id?:number){busy.value=true;error.value='';try{
 if(action==='toggle')await Request.put('/api/v1/admin/registration/policy',{enabled:!policy.value.enabled,revision:policy.value.revision});
 if(action==='create'){const random=crypto.getRandomValues(new Uint8Array(24));const requestId=Array.from(random,b=>b.toString(16).padStart(2,'0')).join('');created.value=(await Request.post('/api/v1/admin/registration/codes',{days:days.value,requestId})).data;}
 if(action==='revoke')await Request.delete(`/api/v1/admin/registration/codes/${id}`);
 await load();
}catch(e){error.value=failure(e);}finally{busy.value=false;}}
const time=(value:any)=>value?new Date(Number(value)).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false}):'—';
onMounted(()=>act('load'));
</script>
<template>
<a-alert>激活码仅用于自有登录器注册，与充值卡密、游戏 CDK、GM 授权码独立。默认关闭；启用前须分发支持 activationCode 的自有登录器。现有账号登录不受影响。</a-alert>
<a-alert v-if="error" type="error">{{error}}</a-alert>
<a-space wrap style="margin:16px 0"><span>注册激活码：{{policy.enabled?'已启用':'已关闭'}}</span><a-popconfirm :content="policy.enabled?'关闭后无需激活码即可注册。确认关闭？':'启用后旧版本登录器无法注册新账号。确认已分发兼容版本？'" @ok="act('toggle')"><a-button :disabled="busy">{{policy.enabled?'关闭要求':'启用要求'}}</a-button></a-popconfirm><a-input-number v-model="days" :min="1" :max="365" :precision="0"/><span>天有效</span><a-button :disabled="busy||!!created" @click="act('create')">生成一个激活码</a-button><a-button :disabled="busy" @click="act('load')">刷新</a-button></a-space>
<a-alert v-if="created" type="warning">新激活码 #{{created.id}}：<code style="user-select:all">{{created.code}}</code>，截止 {{time(created.expiresAt)}}（北京时间）。明文只显示一次，关闭后无法找回。<a-button @click="created=undefined">已保存，隐藏明文</a-button></a-alert>
<a-table :data="rows" :pagination="false" row-key="id"><template #columns><a-table-column title="编号" data-index="id"/><a-table-column title="状态" data-index="state"/><a-table-column title="到期（北京时间）"><template #cell="{record}">{{time(record.expires_at)}}</template></a-table-column><a-table-column title="使用账号" data-index="used_uid"/><a-table-column title="操作"><template #cell="{record}"><a-popconfirm content="撤销后不可恢复，确认？" @ok="act('revoke',record.id)"><a-button :disabled="busy||!['ready','expired'].includes(record.state)">撤销</a-button></a-popconfirm></template></a-table-column></template></a-table>
<a-space><a-button :disabled="page===1||busy" @click="page--;act('load')">上一页</a-button><span>{{page}}</span><a-button :disabled="rows.length<50||busy" @click="page++;act('load')">下一页</a-button></a-space>
<p>操作审计（不记录激活码明文或摘要）</p><a-table :data="history" :pagination="{pageSize:10}" :columns="[{title:'编号',dataIndex:'id'},{title:'操作人',dataIndex:'actor'},{title:'激活码编号',dataIndex:'code_id'},{title:'操作',dataIndex:'action'}]"/>
<a-space><a-button :disabled="historyPage===1||busy" @click="historyPage--;act('load')">上一页审计</a-button><span>{{historyPage}}</span><a-button :disabled="history.length<50||busy" @click="historyPage++;act('load')">下一页审计</a-button></a-space>
</template>
