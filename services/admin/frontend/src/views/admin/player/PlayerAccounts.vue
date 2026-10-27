<!-- 文件: `webui2/src/views/admin/player/PlayerAccounts.vue` -->
<script setup lang="ts">
import {onMounted, ref} from "vue";
import type {AccountSearchForm} from "../../../api/entitys/AccountSearchForm";
import type {PageResult} from "../../../api/entitys/PageResult";
import ManagedRecharge from "../../../components/ManagedRecharge.vue";
import Request from "../../../api/Request";
import {Message} from "@arco-design/web-vue";

const loading = ref(false);

const searchForm = ref<AccountSearchForm>({
  pageNum: 1,
  pageSize: 10
})
const pageResult = ref<PageResult>({
  page: 1,
  totalPageSize: 10,
  totalSize: 0,
  list: []
});
const rechargeVisible = ref(false);
const rechargeCurrency=ref<'cera'|'cera_point'>('cera');
const rechargeTarget = ref<{uid:number;accountname:string}>();
const windowHeight = ref(window.innerHeight - 250)


const search = (resetPage = false) => {
  if (resetPage) {
    searchForm.value.pageNum = 1;
  }

  const query = new URLSearchParams({page: String(searchForm.value.pageNum), pageSize: String(searchForm.value.pageSize)});
  if (searchForm.value.account) query.set('account', searchForm.value.account);
  if (searchForm.value.loginStatus !== undefined && searchForm.value.loginStatus !== '') query.set('loginStatus', String(searchForm.value.loginStatus));
  if (searchForm.value.lastLoginTime && searchForm.value.lastLoginTime.length === 2) {
    query.set('lastLoginDate', String(searchForm.value.lastLoginTime[0]));
    query.set('lastLoginDateEnd', String(searchForm.value.lastLoginTime[1]));
  }
  const url = `api/v1/account?${query.toString()}`;

  loading.value = true;
  Request.get(url).then((response) => {
    pageResult.value = response.data;

    // 兼容后端返回当前页字段
    if (pageResult.value && typeof (pageResult.value as any).page === "number") {
      searchForm.value.pageNum = (pageResult.value as any).page;
    }
  }).catch((e: any) => {
    Message.error(e?.message || "查询失败");
  }).finally(() => {
    loading.value = false;
  });
}

const onPageChange = (page: number) => {
  searchForm.value.pageNum = page;
  search(false);
};

const onPageSizeChange = (pageSize: number) => {
  searchForm.value.pageSize = pageSize;
  search(true);
};

const openRecharge = (uid: number | string, accountname: string, currency:'cera'|'cera_point'='cera') => {
  const value = Number(uid);
  if (!Number.isSafeInteger(value) || value < 1) { Message.error('账号编号无效'); return; }
  rechargeCurrency.value=currency;
  rechargeTarget.value = {uid:value,accountname};
  rechargeVisible.value = true;
};


onMounted(() => {
  window.addEventListener('resize', () => {
    windowHeight.value = window.innerHeight - 250;
  });
  search();
});
</script>

<template>
  <div class="account-manager">
    <a-card>
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="账号">
          <a-input placeholder="搜索玩家账号" allow-clear v-model="searchForm.account"/>
        </a-form-item>
        <a-form-item label="状态">
          <a-select placeholder="全部状态" style="width: 150px;" allow-clear v-model="searchForm.loginStatus">
            <a-option label="全部状态" value="" />
            <a-option label="在线" value="true" />
            <a-option label="离线" value="false" />
          </a-select>
        </a-form-item>
        <a-form-item label="最后登录时间" >
          <a-range-picker v-model="searchForm.lastLoginTime" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="search(true)">搜索</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 账号列表 -->
    <a-table
        scrollbar
        :scroll="{ y: windowHeight }"
        style="margin-top: 10px;"
        :data="pageResult.list"
        :loading="loading"
        :pagination="false"
    >
      <template #empty>
        <div style="text-align: center; padding: 20px;">
          暂无数据
        </div>
      </template>
      <template #columns>
        <a-table-column title="账号" data-index="accountname" />
        <a-table-column title="在线状态" data-index="loginStatus">
          <template #cell="{ record }">
            <a-tag :color="record.loginStatus ? 'green' : 'gray'">
              {{ record.loginStatus ? '在线' : '离线' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="用户身份" data-index="parentUid">
          <template #cell="{ record }">
            <a-tag :color="record.parentUid === 0 ? 'green' : 'blue'">
              {{ record.parentUid === 0 ? 'GM管理员' : '玩家' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="频道号" data-index="channelNo">
          <template #cell="{ record }">
          <span>
            {{ record.loginStatus ? record.channelNo : '--' }}
          </span>
          </template>
        </a-table-column>
        <a-table-column title="最后登录时间" data-index="lastLoginDate">
          <template #cell="{ record }">
            {{ record.lastLoginDate ? record.lastLoginDate: '从未登录' }}
          </template>
        </a-table-column>
        <a-table-column title="操作">
          <template #cell="{ record }">
            <a-space>
              <a-button size="small" type="primary" @click="openRecharge(record.uid, record.accountname)">充值</a-button>
              <a-button size="small" @click="openRecharge(record.uid,record.accountname,'cera_point')">代币券管理</a-button>
              <!--            <a-button size="small" status="warning" @click="{}"-->
              <!--            >封号/解封</a-button-->
              <!--            >-->
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 12px;">
      <a-pagination
          :current="searchForm.pageNum"
          :page-size="searchForm.pageSize"
          :total="pageResult.totalSize"
          show-total
          show-jumper
          show-page-size
          :page-size-options="[10, 20, 50, 100]"
          @change="onPageChange"
          @page-size-change="onPageSizeChange"
      />
    </div>

    <ManagedRecharge v-if="rechargeTarget" :key="rechargeTarget.uid" v-model:visible="rechargeVisible" :uid="rechargeTarget.uid" :account-name="rechargeTarget.accountname" :currency="rechargeCurrency" @changed="search(false)" />
  </div>
</template>

<style scoped lang="less">
.account-manager{
  padding: 10px;
}
</style>
