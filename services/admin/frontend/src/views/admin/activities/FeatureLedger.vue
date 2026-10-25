<script setup lang="ts">
import {computed,ref} from 'vue';
type Subfeature={id:string;name:string;disposition:string;delivery:string;source:string;consumer:string;configuration:string;evidence:string[];missing:string;required:string;adminEntry:string;currentSwitch:string;owner:string};
type Feature={id:string;feature:string;subfeatures:Subfeature[];reviewedAt:string};
const props=defineProps<{features:Feature[]}>();
const query=ref(''),status=ref(''),selected=ref<(Subfeature&{parent:string;reviewedAt:string})>();
const all=computed(()=>props.features.flatMap(f=>(f.subfeatures||[]).map(s=>({...s,parent:f.feature,reviewedAt:f.reviewedAt}))));
const statuses=computed(()=>[...new Set(all.value.map(x=>x.disposition))]);
const rows=computed(()=>all.value.filter(x=>(!status.value||x.disposition===status.value)&&(!query.value||`${x.id} ${x.parent} ${x.name} ${x.missing}`.includes(query.value.trim()))));
</script>
<template>
<a-alert>原始 {{features.length}} 项已拆为 {{all.length}} 个独立子项。状态为核对快照；管理完成、只读查询和实际游戏可用分别记录，缺资料不提供开关。</a-alert>
<a-space wrap style="margin:16px 0"><a-input v-model="query" placeholder="搜索编号、功能或缺口" allow-clear style="width:300px"/><a-select v-model="status" placeholder="全部处理结果" allow-clear style="width:200px"><a-option v-for="s in statuses" :key="s" :value="s">{{s}}</a-option></a-select></a-space>
<a-table :data="rows" row-key="id" :pagination="{pageSize:15}" :scroll="{x:1100}"><template #columns>
<a-table-column title="原始功能" data-index="parent"/><a-table-column title="子项" data-index="name"/><a-table-column title="处理结果" data-index="disposition"/><a-table-column title="交付" data-index="delivery"/><a-table-column title="编号" data-index="id"/>
<a-table-column title="依据"><template #cell="{record}"><a-button size="small" @click="selected=record">查看证据与缺口</a-button></template></a-table-column>
</template></a-table>
<a-modal :visible="!!selected" :footer="false" title="子项证据与验收边界" width="800px" @cancel="selected=undefined">
<template v-if="selected"><h3>{{selected.id}} · {{selected.name}}</h3>
<p>核对日期：{{selected.reviewedAt}}；处理结果：{{selected.disposition}}</p>
<dl><dt>配套来源</dt><dd>{{selected.source}}</dd><dt>实际消费组件</dt><dd>{{selected.consumer}}</dd><dt>配置位置</dt><dd>{{selected.configuration}}</dd><dt>处理插件</dt><dd>{{selected.owner}}</dd><dt>后台入口</dt><dd>{{selected.adminEntry}}</dd><dt>开关快照</dt><dd>{{selected.currentSwitch}}</dd><dt>证据定位</dt><dd>{{selected.evidence.join('；')}}</dd><dt>剩余边界 / 关闭原因</dt><dd>{{selected.missing}}</dd><dt>后续启用或验收所需证据</dt><dd>{{selected.required}}</dd></dl>
<a-alert type="warning">此页不会修改开关、发放奖励或重置玩家数据。没有执行客户端游戏验收。</a-alert></template>
</a-modal>
</template>
<style scoped>dt{font-weight:600;margin-top:12px}dd{margin:4px 0;overflow-wrap:anywhere}</style>
