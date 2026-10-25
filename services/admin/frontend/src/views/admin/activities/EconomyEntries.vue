<script setup lang="ts">
import {ref,computed,onMounted} from 'vue';
import Request from '../../../api/Request';
const busy=ref(false),loaded=ref(false),error=ref(''),page=ref(1),more=ref(false),tasks=ref<any[]>([]),potRows=ref<any[]>([]),shopRows=ref<any[]>([]),refs=ref<any[]>([]);
const pools=computed(()=>[1,2,3].map(n=>({pool:n,table:n===1?'earthenware_pot':`earthenware_pot_${n}`,field:n===1?'item_id':`item_id_${n}`,item:potRows.value.length===1?potRows.value[0][n===1?'item_id':`item_id_${n}`]:null,flag:potRows.value.length===1?potRows.value[0][`item_off_${n}`]:null})));
async function load(){busy.value=true;loaded.value=false;error.value='';refs.value=[];try{
 const [schedule,pot,shop]=await Promise.all([Request.get<any>(`/api/v1/admin/extension/records/shop-schedules?page=${page.value}&size=20`),Request.get<any>('/api/v1/admin/activities/pot-switch'),Request.get<any>('/api/v1/admin/activities/shop-switch')]);
 tasks.value=schedule.data.rows;more.value=schedule.data.hasMore;potRows.value=pot.data.rows.map((x:any)=>x.values);shopRows.value=shop.data.rows.map((x:any)=>x.values);loaded.value=true;
}catch(e:any){error.value=e?.response?.data?.message||e?.message||'读取失败';}finally{busy.value=false;}}
async function validate(){busy.value=true;refs.value=[];error.value='';try{
 for(const [kind,raw] of [['npc',tasks.value.map(x=>x.NPC)],['item',pools.value.map(x=>x.item)]] as const){
 const ids=[...new Set(raw.map(Number).filter(x=>Number.isInteger(x)&&x>0&&x<=2147483647))];
 if(ids.length)refs.value.push(...(await Request.post<any[]>('/api/v1/admin/extension/resources/validate',{kind,ids})).data.map(x=>({...x,kind})));
 }
}catch(e:any){error.value=e?.response?.data?.message||e?.message||'校验失败';}finally{busy.value=false;}}
onMounted(load);
</script>
<template>
<a-alert type="warning">此页只读。原生商店任务结束会按 NPC 删除购买计数，累计限购也可能被清除；未确认换期规则前不开放任务编辑或启用。</a-alert>
<a-space style="margin:16px 0"><a-button :loading="busy" @click="load">刷新当前配置</a-button><a-button :disabled="busy||!loaded" @click="validate">校验当前页 NPC 与配置道具</a-button></a-space>
<a-alert v-if="error" type="error">{{error}}</a-alert>
<template v-if="loaded">
<p>商店开关（原始值，仅展示）：{{JSON.stringify(shopRows)}}</p>
<p>调度类型：1 每日指定时分；2 指定日期，当天到点后；3 间隔分钟。插件使用进程当地时间及数据库 NOW/CURDATE，必须核对两边时区。状态 0 待运行、1 运行中、2 完成。</p>
<p>商品 shop_id 是商品分组，timed_task.NPC 是调度 NPC；二者不是同一个编号，不能把配套 shop_id=2 当作 NPC=2。</p>
<a-alert v-if="page===1&&!tasks.length&&!more" type="warning">当前没有商店调度记录，NPC 入口与刷新时间未配置；不会自动创建。</a-alert>
<a-table :data="tasks" :pagination="false" :scroll="{x:1300}" :columns="[{title:'任务',dataIndex:'id'},{title:'名称',dataIndex:'cname'},{title:'NPC',dataIndex:'NPC'},{title:'类型',dataIndex:'time_type'},{title:'设定时间',dataIndex:'task_time'},{title:'间隔分钟',dataIndex:'minute_interval'},{title:'持续分钟',dataIndex:'end_after_minutes'},{title:'状态',dataIndex:'task_state'},{title:'最近执行',dataIndex:'last_executed'},{title:'开始时间',dataIndex:'start_time'},{title:'结束执行',dataIndex:'end_executed'}]"/>
<a-space><a-button :disabled="page===1||busy" @click="page--;load()">上一页</a-button><span>第 {{page}} 页</span><a-button :disabled="!more||busy" @click="page++;load()">下一页</a-button></a-space>
<p>土罐原生配置行数：{{potRows.length}}。以下对应关系来自插件读取分支，不代表两份配套奖池文件已经确定装载位置。</p>
<a-alert v-if="potRows.length!==1" type="warning">{{potRows.length===0?'缺少消耗道具配置':'存在多行配置，归属不明确'}}，不推测池编号或触发道具。</a-alert>
<a-table :data="pools" :pagination="false" :columns="[{title:'请求池编号',dataIndex:'pool'},{title:'原生奖池表',dataIndex:'table'},{title:'消耗道具字段',dataIndex:'field'},{title:'当前道具编号',dataIndex:'item'},{title:'当前开关原始值',dataIndex:'flag'}]"/>
<p>道具校验 found 仅表示 PVF 列表中存在；不能证明它触发该池。开关和字段保持原值，不提供自动修复。</p>
<a-table :data="refs" :pagination="false" :columns="[{title:'类型',dataIndex:'kind'},{title:'编号',dataIndex:'id'},{title:'列表校验',dataIndex:'status'}]"/>
</template>
</template>
