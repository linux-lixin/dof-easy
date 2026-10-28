<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Request from '../../../api/Request'
import type { PageResult } from '../../../api/entitys/PageResult'

const account = ref(''), period = ref<string[]>([]), page = ref(1), size = ref(20)
const busy = ref(false), error = ref(''), data = ref<PageResult | null>(null)
async function load(reset = false) {
  if (busy.value) return
  if (reset) page.value = 1
  busy.value = true; error.value = ''; data.value = null
  const params = new URLSearchParams({ page: String(page.value), pageSize: String(size.value) })
  if (account.value) params.set('account', account.value)
  const [start, end] = period.value || []
  if (start && end) {
    params.set('start', start); params.set('end', end)
  }
  try {
    const result = (await Request.get(`api/v1/postal?${params.toString()}`)).data
    if (!result || !Array.isArray(result.list) || !Number.isFinite(Number(result.totalSize)))
      throw new Error('邮件查询返回格式不正确，请核对服务版本')
    data.value = result
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || '无法读取邮件记录'
  } finally { busy.value = false }
}
function changePage(value: number) { page.value = value; load() }
function changeSize(value: number) { size.value = value; load(true) }
onMounted(() => load())
</script>

<template>
  <a-space direction="vertical" fill class="postal-records">
    <a-alert>按收件人账号精确查询其全部角色邮件；工会账号仅能查看自己及直属成员。日期按服务器时区计算，结束日期包含当天。记录中的删除标记不作为奖励到账或领取成功的证明。</a-alert>
    <a-space wrap>
      <a-input v-model="account" :disabled="busy" :max-length="128" placeholder="收件人账号（精确匹配，可选）" style="width:280px" @press-enter="load(true)" />
      <a-range-picker v-model="period" :disabled="busy" />
      <a-button type="primary" :loading="busy" @click="load(true)">查询邮件</a-button>
    </a-space>
    <a-alert v-if="error" type="error">{{ error }}</a-alert>
    <template v-if="data">
      <a-table :data="data.list" :pagination="false" :loading="busy" row-key="postalId" :scroll="{x:1800}">
        <template #columns>
          <a-table-column title="邮件编号" data-index="postalId" />
          <a-table-column title="发送时间" data-index="occTime" :width="210" />
          <a-table-column title="发件人" data-index="sendCharacName" />
          <a-table-column title="收件角色编号" data-index="receiveCharacNo" />
          <a-table-column title="物品代码" data-index="itemId" />
          <a-table-column title="数量／附加参数" data-index="addInfo" />
          <a-table-column title="金币" data-index="gold" />
          <a-table-column title="强化" data-index="upgrade" />
          <a-table-column title="锻造" data-index="seperateUpgrade" />
          <a-table-column title="增幅属性" data-index="amplifyOption" />
          <a-table-column title="增幅值" data-index="amplifyValue" />
          <a-table-column title="封装"><template #cell="{record}">{{record.sealFlag ? '是' : '否'}}</template></a-table-column>
          <a-table-column title="删除标记"><template #cell="{record}">{{record.deleteFlag ? '是' : '否'}}</template></a-table-column>
          <a-table-column title="正文引用编号" data-index="letterId" />
        </template>
      </a-table>
      <a-pagination :current="page" :page-size="size" :total="Number(data.totalSize)" :disabled="busy" show-total show-page-size :page-size-options="[10,20,50,100]" @change="changePage" @page-size-change="changeSize" />
    </template>
  </a-space>
</template>

<style scoped>
.postal-records :deep(.arco-table-cell) { white-space: nowrap; }
</style>
