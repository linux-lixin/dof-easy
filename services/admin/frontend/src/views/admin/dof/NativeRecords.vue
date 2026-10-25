<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Request from '../../../api/Request'
const root='/api/v1/admin/dof/records'
const views=ref<any[]>([]), key=ref(''), owner=ref<number>(), page=ref(1), data=ref<any>(null), error=ref(''), busy=ref(false)
const period=ref('all'), searchField=ref(''), searchText=ref('')
const selected=computed(()=>views.value.find(v=>v.key===key.value))
async function load(reset=false) {
  if(busy.value)return
  busy.value=true; error.value=''; if(reset)page.value=1
  try { data.value=(await Request.get(`${root}/${key.value}`,{params:{owner:owner.value,page:page.value,size:50,period:period.value,
    searchField:searchText.value?searchField.value:undefined,searchText:searchText.value||undefined}})).data }
  catch(e:any) { data.value=null; error.value=e.response?.data?.message||'查询失败，请重新连接' }
  finally { busy.value=false }
}
function changeView() { owner.value=undefined;period.value='all';searchField.value=selected.value?.searchFields?.[0]?.key||'';searchText.value='';load(true) }
onMounted(async()=>{
  try { views.value=(await Request.get(root)).data.views; key.value=views.value[0]?.key||''; if(key.value)await load(true) }
  catch(e:any) { error.value=e.response?.data?.message||'无法读取记录分类' }
})
</script>
<template>
  <a-alert>查询当前隔离实例的原生运营记录。与原工具的逐项等价检查仍在进行。</a-alert>
  <a-alert v-if="error" type="error">{{ error }}</a-alert>
  <a-space class="controls" wrap>
    <a-select v-model="key" :disabled="busy" style="width:230px" @change="changeView">
      <a-option v-for="v in views" :key="v.key" :value="v.key">{{v.title}}</a-option>
    </a-select>
    <a-input-number v-if="selected?.ownerAvailable" v-model="owner" :min="1" :precision="0" :disabled="busy" :placeholder="selected?.ownerLabel+'（可选）'" />
    <a-select v-if="selected?.periodAvailable" v-model="period" :disabled="busy" style="width:150px">
      <a-option value="all">全部时间</a-option><a-option value="today">今天</a-option>
      <a-option value="yesterday">昨天</a-option><a-option value="last7days">最近七天</a-option>
    </a-select>
    <template v-if="selected?.searchFields?.length">
      <a-select v-model="searchField" :disabled="busy" style="width:170px"><a-option v-for="f in selected.searchFields" :key="f.key" :value="f.key">{{f.label}}</a-option></a-select>
      <a-input v-model="searchText" :disabled="busy" :max-length="128" style="width:240px" placeholder="包含文字（可选）" @press-enter="load(true)" />
    </template>
    <a-button :loading="busy" @click="load(true)">查询</a-button>
  </a-space>
  <template v-if="data">
    <p>{{data.notice}} 最近读取：{{new Date(data.observedAt).toLocaleString()}}</p>
    <a-table :data="data.rows" :loading="busy" :pagination="false" :scroll="{x:1000}">
      <template #columns><a-table-column v-for="f in data.view.columns" :key="f.key" :title="f.label" :data-index="f.key" /></template>
    </a-table>
    <a-space class="controls">
      <a-button :disabled="busy||page===1" @click="page--;load()">上一页</a-button><span>第 {{page}} 页</span>
      <a-button :disabled="busy||!data.hasMore" @click="page++;load()">下一页</a-button>
    </a-space>
  </template>
</template>
<style scoped>.controls { margin:16px 0 }</style>
