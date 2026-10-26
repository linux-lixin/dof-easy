<script setup lang="ts">
import { ref, onMounted } from 'vue';
import Request from '../../../api/Request';
import PasswordManager from './PasswordManager.vue';
import BanManager from './BanManager.vue';
import RegistrationCodes from './RegistrationCodes.vue';
import CompanionPreview from './CompanionPreview.vue';
import FeatureLedger from './FeatureLedger.vue';
import EconomyEntries from './EconomyEntries.vue';
type View = {key:string;title:string;columns:string[];filter:string|null};
const tab=ref('features'),features=ref<any[]>([]),views=ref<View[]>([]),key=ref(''),rows=ref<any[]>([]),owner=ref<number>(),page=ref(1),more=ref(false),error=ref(''),loading=ref(false),clock=ref<any>({});
const kind=ref('item'),ids=ref(''),checks=ref<any[]>([]);
const failure=(e:any)=> e?.response?.data?.message || e?.message || '请求失败';
async function load(){loading.value=true;error.value='';try{const r=await Request.get<any>(`/api/v1/admin/extension/records/${key.value}`,{params:{owner:owner.value,page:page.value,size:50}});rows.value=r.data.rows;more.value=r.data.hasMore;}catch(e){rows.value=[];error.value=failure(e);}finally{loading.value=false;}}
async function validate(){error.value='';loading.value=true;try{const list=ids.value.split(/[\s,，]+/).filter(Boolean).map(Number);checks.value=(await Request.post<any[]>('/api/v1/admin/extension/resources/validate',{kind:kind.value,ids:list})).data;}catch(e){checks.value=[];error.value=failure(e);}finally{loading.value=false;}}
onMounted(async()=>{try{features.value=(await Request.get<any[]>('/api/v1/admin/extension/features')).data;views.value=(await Request.get<View[]>('/api/v1/admin/extension/records')).data;clock.value=(await Request.get('/api/v1/admin/extension/clock')).data;key.value=views.value[0]?.key||'';}catch(e){error.value=failure(e);}});
</script>
<template>
<a-card title="扩展运营与验收" style="margin:24px">
<a-alert>玩家记录只读，不提供清空、补领或批量属性重算。缺少规则依据的功能保持关闭；客户端验收另行进行。</a-alert>
<a-alert v-if="error" type="error">{{error}}</a-alert>
<a-tabs v-model:active-key="tab">
<a-tab-pane key="features" title="功能台账">
<FeatureLedger :features="features" /></a-tab-pane>
<a-tab-pane key="records" title="玩家与运营记录">
<a-space wrap style="margin:16px 0"><a-select v-model="key" style="width:260px" @change="page=1;load()"><a-option v-for="v in views" :key="v.key" :value="v.key">{{v.title}}</a-option></a-select><a-input-number v-model="owner" :min="1" :precision="0" placeholder="账号/角色编号（按当前表）"/><a-button @click="page=1;load()" :loading="loading">查询</a-button></a-space>
<p>过滤字段：{{views.find(v=>v.key===key)?.filter||'不支持'}}；记录不包含密码、兑换码、IP/MAC 或玩家姓名。举报聊天视图只返回类型与时间等元数据，不含正文，不代表全部聊天。</p>
<a-table :data="rows" :loading="loading" :pagination="false" :scroll="{x:2000}"><template #columns><a-table-column v-for="f in views.find(v=>v.key===key)?.columns||[]" :key="f" :title="f" :data-index="f"/></template></a-table>
<a-space><a-button :disabled="page===1||loading" @click="page--;load()">上一页</a-button><span>第 {{page}} 页</span><a-button :disabled="!more||loading" @click="page++;load()">下一页</a-button></a-space>
</a-tab-pane>
<a-tab-pane key="references" title="配套资源校验">
<a-space><a-select v-model="kind" style="width:120px"><a-option value="item">道具</a-option><a-option value="dungeon">副本</a-option><a-option value="npc">NPC</a-option></a-select><a-input v-model="ids" placeholder="编号用逗号分隔，最多200项" style="width:400px"/><a-button @click="validate" :loading="loading">校验</a-button></a-space>
<p>found：列表存在；missing：可读取列表中不存在；unverified：资源未挂载或未能解析。存在不代表游戏使用效果已验证。</p>
<a-table :data="checks" :columns="[{title:'编号',dataIndex:'id'},{title:'状态',dataIndex:'status'}]"/>
</a-tab-pane>
<a-tab-pane key="accounts" title="封禁管理"><BanManager /></a-tab-pane>
<a-tab-pane key="passwords" title="管理员改密"><PasswordManager /></a-tab-pane>
<a-tab-pane key="companion" title="CDK、商店与奖池配套预览"><CompanionPreview /></a-tab-pane>
<a-tab-pane key="economy-entries" title="商店调度与土罐入口"><EconomyEntries /></a-tab-pane>
<a-tab-pane key="registration" title="注册激活码"><RegistrationCodes /></a-tab-pane>
<a-tab-pane key="clock" title="活动时间与调度"><pre>{{JSON.stringify(clock,null,2)}}</pre><p>战令每级固定1000经验；后台6000字段为每周任务经验上限。签到结束日期与战令结束时间采用的时区规则不同。</p></a-tab-pane>
</a-tabs></a-card>
</template>
