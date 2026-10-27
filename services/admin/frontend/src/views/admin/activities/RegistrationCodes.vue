<script setup lang="ts">
import {ref,onMounted} from 'vue';
import Request from '../../../api/Request';
const base='/api/v1/admin/registration';
const policy=ref<any>({enabled:false}),rows=ref<any[]>([]),history=ref<any[]>([]),operations=ref<any[]>([]),receipts=ref<any[]>([]);
const submitted=ref(false);
const page=ref(1),historyPage=ref(1),receiptPage=ref(1),days=ref(28),busy=ref(false),error=ref(''),reason=ref(''),created=ref<any>(),pending=ref<any>(),preview=ref<any>();
const failure=(e:any)=>e?.response?.data?.message||e?.message||'请求失败';
const uuid=()=>{const b=crypto.getRandomValues(new Uint8Array(16));b[6]=(b[6]!&15)|64;b[8]=(b[8]!&63)|128;const s=Array.from(b,x=>x.toString(16).padStart(2,'0')).join('');return `${s.slice(0,8)}-${s.slice(8,12)}-${s.slice(12,16)}-${s.slice(16,20)}-${s.slice(20)}`;};
async function load(){
 const values=await Promise.all([Request.get<any>(base+'/policy'),Request.get<any[]>(`${base}/codes?page=${page.value}`),Request.get<any[]>(`${base}/history?page=${historyPage.value}`),Request.get<any[]>(base+'/operations'),Request.get<any[]>(`${base}/receipts?page=${receiptPage.value}`)]);
 [policy.value,rows.value,history.value,operations.value,receipts.value]=values.map(x=>x.data);
}
async function run(task:()=>Promise<void>){busy.value=true;error.value='';try{await task();}catch(e){error.value=failure(e);}finally{busy.value=false;}}
async function prepare(operation:string,target?:any){await run(async()=>{
 if(pending.value)throw new Error('请先处理当前预览或使用原操作编号核对。');
 if(!reason.value.trim())throw new Error('请填写操作原因。');
 const edit:any={requestId:uuid(),operation,reason:reason.value};
 if(operation==='policy'){edit.enabled=!policy.value.enabled;edit.revision=policy.value.revision;}
 if(operation==='create'){edit.days=days.value;edit.revision=policy.value.codeRevision;}
 if(operation==='revoke'){edit.codeId=target.id;edit.revision=(await Request.get<any>(`${base}/codes/${target.id}`)).data.revision;}
 if(operation==='accept-current'){edit.targetRequestId=target.requestId;edit.revision=(await Request.get<any>(`${base}/operations/${target.requestId}/current`)).data.revision;}
 if(operation==='check-receipt'){edit.targetRequestId=target.request_id;edit.revision=(await Request.get<any>(`${base}/receipts/${target.request_id}`)).data.revision;}
 pending.value=Object.freeze(edit);preview.value=undefined;submitted.value=false;
 preview.value=(await Request.post<any>(base+'/preview',edit)).data;await load();
 });}
async function retryPreview(){await run(async()=>{preview.value=(await Request.post<any>(base+'/preview',pending.value)).data;await load();});}
async function confirm(){await run(async()=>{
 const e=pending.value;submitted.value=true;let result:any;
 if(e.operation==='policy')result=await Request.put(base+'/policy',e);
 else if(e.operation==='create')result=await Request.post(base+'/codes',e);
 else if(e.operation==='revoke')result=await Request.delete(`${base}/codes/${e.codeId}`,{data:e});
 else if(e.operation==='accept-current')result=await Request.post(`${base}/operations/${e.targetRequestId}/accept-current`,e);
 else result=await Request.post(`${base}/receipts/${e.targetRequestId}/reconcile`,e);
 preview.value=result.data;if(result.data.code)created.value=result.data;
 if(['PERSISTED','NO_CHANGE','NOT_APPLIED'].includes(result.data.state)){pending.value=undefined;preview.value=undefined;}
 await load();
 });}
async function reconcile(id:string){await run(async()=>{
 let r:any;try{r=(await Request.post<any>(`${base}/operations/${id}/reconcile`)).data;}catch(e:any){if(e?.response?.status===404&&pending.value?.requestId===id){submitted.value=false;throw new Error('未找到操作记录，请使用原编号重新读取预览。');}throw e;}
 if(r.code)created.value=r;
 if(pending.value?.requestId===id){preview.value=r;if(r.state==='PREVIEWED')submitted.value=false;if(['PERSISTED','NO_CHANGE','NOT_APPLIED'].includes(r.state)){pending.value=undefined;preview.value=undefined;}}
 await load();
 });}
const time=(v:any)=>v?new Date(typeof v==='number'||/^\d+$/.test(String(v))?Number(v):v).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false}):'—';
onMounted(()=>run(load));
</script>
<template>
<a-alert>这里的激活码用于支持 activationCode 的自有登录器注册，与晴空原生注册卡、充值卡、游戏 CDK 分开管理。启用前须分发兼容登录器。现有账号登录不受影响。注册赠送读取当前实例原生配置；原生网关行为仍需单独验收。</a-alert>
<a-alert v-if="error" type="error">{{error}}</a-alert>
<a-alert v-if="policy.registrationReadiness" :type="policy.registrationReadiness.ready?'info':'warning'">网页注册：{{policy.registrationReadiness.ready?'配置预检通过，实际效果待验':policy.registrationReadiness.reason}}；IP 配额 {{policy.registrationReadiness.ipLimit}}，赠送 {{policy.registrationReadiness.giftAmount}}（类型 {{policy.registrationReadiness.giftCurrency}}：1 点券，0 代币）。</a-alert>
<a-alert v-if="policy.generationAvailable===false" type="warning">{{policy.generationReason}}</a-alert>
<a-space wrap style="margin:16px 0"><span>注册激活码：{{policy.enabled?'已启用':'已关闭'}}</span><a-input v-model="reason" :max-length="128" placeholder="操作原因（必填）"/><a-button :disabled="busy||!!pending" @click="prepare('policy')">预览{{policy.enabled?'关闭要求':'启用要求'}}</a-button><a-input-number v-model="days" :min="1" :max="365" :precision="0"/><span>天有效</span><a-button :disabled="busy||!!pending||policy.generationAvailable===false" @click="prepare('create')">预览生成激活码</a-button><a-button :disabled="busy" @click="run(load)">刷新</a-button></a-space>
<a-alert v-if="pending" type="warning">操作编号：{{pending.requestId}}。{{submitted?'已经尝试提交，请先核对结果；不能当作未提交预览关闭。':'尚未尝试提交。'}}网络错误后保留此编号核对，不要换编号提交。
 <pre style="white-space:pre-wrap">{{preview}}</pre>
 <a-space><a-button :disabled="busy||submitted" @click="retryPreview">用原编号读取预览</a-button><a-popconfirm content="确认执行当前预览？启用策略会影响旧登录器的新账号注册；撤销激活码不可恢复。" @ok="confirm"><a-button :disabled="busy||submitted||preview?.state!=='PREVIEWED'">确认提交</a-button></a-popconfirm><a-button :disabled="busy" @click="reconcile(pending.requestId)">核对操作结果</a-button><a-button v-if="!submitted&&preview?.state==='PREVIEWED'" :disabled="busy" @click="pending=undefined;preview=undefined">关闭未提交预览</a-button></a-space>
</a-alert>
<a-alert v-if="created" type="warning">激活码 #{{created.id}}：<code style="user-select:all">{{created.code}}</code>，截止 {{time(created.expiresAt)}}。请妥善保存；有权限的管理员可通过原生成操作编号取回同一码。<a-button @click="created=undefined">隐藏明文</a-button></a-alert>
<a-table :data="rows" :pagination="false" row-key="id"><template #columns><a-table-column title="编号" data-index="id"/><a-table-column title="状态" data-index="state"/><a-table-column title="到期（北京时间）"><template #cell="{record}">{{time(record.expires_at)}}</template></a-table-column><a-table-column title="使用账号" data-index="used_uid"/><a-table-column title="操作"><template #cell="{record}"><a-button :disabled="busy||!!pending||!['ready','expired'].includes(record.state)" @click="prepare('revoke',record)">预览撤销</a-button></template></a-table-column></template></a-table>
<a-space><a-button :disabled="page===1||busy" @click="page--;run(load)">上一页</a-button><span>{{page}}</span><a-button :disabled="rows.length<50||busy" @click="page++;run(load)">下一页</a-button></a-space>
<p>管理操作（优先显示待核对与冲突，最近 50 条；保存不等于消费端效果已验证）</p>
<a-table :data="operations" :pagination="false" row-key="requestId"><template #columns><a-table-column title="操作编号" data-index="requestId"/><a-table-column title="动作" data-index="operation"/><a-table-column title="状态" data-index="state"/><a-table-column title="原因" data-index="reason"/><a-table-column title="操作"><template #cell="{record}"><a-button :disabled="busy||record.state==='PREVIEWED'" @click="reconcile(record.requestId)">{{record.operation==='create'&&record.state==='PERSISTED'?'取回同一码':'核对结果'}}</a-button><a-button v-if="record.state==='CONFLICT'" :disabled="busy||!!pending" @click="prepare('accept-current',record)">预览保留当前值</a-button></template></a-table-column></template></a-table>
<p>注册回执（不显示密码、激活码或注册 IP）。核对未决回执只确认本次网页事务未提交；其他注册链的账号须另行查询。不补发或重复执行注册。</p>
<a-table :data="receipts" :pagination="false" row-key="request_id"><template #columns><a-table-column title="注册编号" data-index="request_id"/><a-table-column title="状态" data-index="state"/><a-table-column title="账号 UID" data-index="uid"/><a-table-column title="赠送数量" data-index="gift_amount"/><a-table-column title="赠送类型" data-index="gift_currency"/><a-table-column title="操作"><template #cell="{record}"><a-button :disabled="busy||!!pending||record.state!=='PREPARED'" @click="prepare('check-receipt',record)">预览确认本次未提交</a-button></template></a-table-column></template></a-table>
<a-space><a-button :disabled="receiptPage===1||busy" @click="receiptPage--;run(load)">上一页回执</a-button><span>{{receiptPage}}</span><a-button :disabled="receipts.length<50||busy" @click="receiptPage++;run(load)">下一页回执</a-button></a-space>
<p>旧操作审计（不记录激活码明文或摘要）</p><a-table :data="history" :pagination="{pageSize:10}" :columns="[{title:'编号',dataIndex:'id'},{title:'操作人',dataIndex:'actor'},{title:'激活码编号',dataIndex:'code_id'},{title:'操作',dataIndex:'action'}]"/>
<a-space><a-button :disabled="historyPage===1||busy" @click="historyPage--;run(load)">上一页审计</a-button><span>{{historyPage}}</span><a-button :disabled="history.length<50||busy" @click="historyPage++;run(load)">下一页审计</a-button></a-space>
</template>
