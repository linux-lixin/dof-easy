<script setup lang="ts">
import {ref,computed,onMounted} from 'vue';
import Request from '../../../api/Request';
const modules=ref<any[]>([]),key=ref('shop'),checks=ref<any[]>([]),busy=ref(false),error=ref('');
const selected=computed(()=>modules.value.find(x=>x.module===key.value));
const rows=computed(()=>selected.value?.rows.map((row:any,index:number)=>({index:index+1,details:JSON.stringify(row)}))||[]);
const failure=(e:any)=>e?.response?.data?.message||e?.message||'请求失败';
async function validate(){busy.value=true;error.value='';checks.value=[];const module=selected.value;try{const ids=module?.referenceIds||[];for(let i=0;i<ids.length;i+=200){const result=(await Request.post<any[]>('/api/v1/admin/extension/resources/validate',{kind:'item',ids:ids.slice(i,i+200)})).data;checks.value.push(...result);}}catch(e){error.value=failure(e);}finally{busy.value=false;}}
onMounted(async()=>{try{modules.value=(await Request.get<any[]>('/api/v1/admin/extension/companion-preview')).data;}catch(e){error.value=failure(e);}});
</script>
<template>
<a-alert>这里只展示配套文件解析结果，不写入游戏表、不生成可兑换码、不启用发奖。CDK 与盲盒文件格式相同，不能凭文件名推测限次或权重语义。第一列已隐藏，不显示兑换码或原始分组文本。</a-alert>
<a-alert v-if="error" type="error">{{error}}</a-alert>
<a-alert v-if="!modules.length && !error">尚未部署本机配套资源预览，当前没有导入条目。配置方法见后台部署说明。</a-alert>
<a-space style="margin:16px 0"><a-select v-model="key" :disabled="busy" style="width:300px" @change="checks=[]"><a-option v-for="m in modules" :key="m.module" :value="m.module">{{m.file}}（{{m.rowCount}} 条）</a-option></a-select><a-button :loading="busy" @click="validate">校验全部引用道具</a-button></a-space>
<template v-if="selected"><p>来源 SHA-256：<code>{{selected.sha256}}</code></p><a-alert type="warning">{{selected.blockReason}}</a-alert><p v-if="selected.rawWeightTotal!==undefined">原始数值合计 {{selected.rawWeightTotal}}；零值行 {{selected.zeroWeightRows}}。{{selected.weightNotice}}</p><p v-if="selected.unverifiedRestrictionTypes?.length">未确认的限购类型：{{selected.unverifiedRestrictionTypes.join('、')}}</p></template>
<template v-if="selected?.nativeEvidence">
<p>原生规则核对：静态分析通过，游戏业务待验证。插件 SHA-256：<code>{{selected.nativeEvidence.pluginSha256}}</code></p>
<a-table v-if="selected.nativeEvidence.restrictionTypes" :data="selected.nativeEvidence.restrictionTypes" :pagination="false" :columns="[{title:'类型',dataIndex:'value'},{title:'限购归属',dataIndex:'scope'},{title:'重置周期',dataIndex:'period'}]"/>
<p v-if="selected.nativeEvidence.periodBoundary">{{selected.nativeEvidence.periodBoundary}}</p>
<a-alert v-if="selected.nativeEvidence.weightRule" type="warning">{{selected.nativeEvidence.weightRule}} {{selected.nativeEvidence.drawRule}}</a-alert>
<p v-if="selected.nativeWeightCheck">按当前文件计算的缩放权重总数：{{selected.nativeWeightCheck.scaledTotal}}；32 位整数安全：{{selected.nativeWeightCheck.signedIntSafe ? '是' : '否，禁止导入'}}</p>
<p v-for="channel in selected.nativeEvidence.nativeChannels" :key="channel">{{channel}}</p>
<p>{{selected.nativeEvidence.scheduleRule}}</p><a-alert v-if="selected.nativeEvidence.resetWarning" type="warning">{{selected.nativeEvidence.resetWarning}}</a-alert><p>{{selected.nativeEvidence.entryRule}}</p><p>{{selected.nativeEvidence.drawRequest}}</p><p>{{selected.nativeEvidence.remaining}}</p>
</template>
<a-table :data="checks" :pagination="{pageSize:10}" :columns="[{title:'道具编号',dataIndex:'id'},{title:'PVF 校验',dataIndex:'status'}]"/>
<p>found 仅代表配套 PVF 列表中存在；missing/unverified 保留原值，不自动替换。下列条目只用于核对来源；未映射字段保留原始数值，不发布为可执行配置。</p>
<a-table :data="rows" :pagination="{pageSize:15}" :columns="[{title:'序号',dataIndex:'index',width:70},{title:'配套条目',dataIndex:'details'}]"/>
<a-link href="/admin/activities">查看当前原生商店和奖池配置</a-link>
</template>
