<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import { LauncherApi } from '../../../api/launcher';
import type { LauncherConfig } from '../../../api/entitys/LauncherConfig';
import type { LauncherBanner } from '../../../api/entitys/LauncherBanner';

const loading = ref(false);
const saving = ref(false);

const config = ref<LauncherConfig>({
  id: 1,
  version: '',
  downloadUrl: '',
  md5: '',
  remark: '',
  title: '',
});

const banners = ref<LauncherBanner[]>([]);
const bannerSaving = ref(false);


const loadAll = async () => {
  loading.value = true;
  try {
    const cfgRes = await LauncherApi.getConfig();
    config.value = cfgRes.data;
    const bRes = await LauncherApi.listBanners();
    banners.value = bRes.data || [];
    console.log('banners loaded', banners.value);
  } finally {
    loading.value = false;
  }
};

const saveConfig = async () => {
  saving.value = true;
  try {
    const res = await LauncherApi.saveConfig(config.value);
    config.value = res.data;
    Message.success('保存成功');
  } finally {
    saving.value = false;
  }
};

const addBannerRow = () => {
  const row: any = {
    id: 0,
    title: '',
    imageUrl: '',
    sortNo: 0,
    enabled: 1,
  };
  banners.value.push(row);
};

const saveBanner = async (row: LauncherBanner) => {
  bannerSaving.value = true;
  try {
    if (!row.title || !row.imageUrl) {
      Message.warning('请填写标题和图片地址');
      return;
    }
    if (!row.sortNo && row.sortNo !== 0) row.sortNo = 0;

    if (!row.id) {
      const res = await LauncherApi.addBanner(row);
      Object.assign(row as any, res.data);
      // 保存后有了 id，把 key 固定为 id-key，避免重渲染异常
      (row as any)._key = `id-${(row as any).id}`;
    } else {
      const res = await LauncherApi.updateBanner(row);
      Object.assign(row as any, res.data);
      (row as any)._key = `id-${(row as any).id}`;
    }
    Message.success('保存成功');
  } finally {
    bannerSaving.value = false;
  }
};

const deleteBanner = async (row: LauncherBanner) => {
  if (!row.id) {
    banners.value = banners.value.filter((b) => b !== row);
    return;
  }
  await LauncherApi.deleteBanner(row.id);
  banners.value = banners.value.filter((b) => b.id !== row.id);
  Message.success('已删除');
};

onMounted(loadAll);
</script>

<template>
  <a-space direction="vertical" fill size="large">
    <a-spin :loading="loading" style="width: 100%">
      <a-card title="核心配置" style="margin: 24px;">
        <template #extra>
          <a-space>
            <a-button :loading="loading" @click="loadAll">刷新</a-button>
            <a-button type="primary" :loading="saving" @click="saveConfig">保存配置</a-button>
          </a-space>
        </template>
        <!-- Arco Form 需要 model 为对象而不是 Ref -->
        <a-form layout="vertical" :model="config">
          <a-grid :cols="24" :col-gap="12" :row-gap="12">
            <a-grid-item :span="24">
              <a-form-item label="窗口标题">
                <a-input v-model="config.title" placeholder="例如 地下城与勇士V1.0" />
              </a-form-item>
            </a-grid-item>
            <a-grid-item :span="8">
              <a-form-item label="整包版本">
                <a-input v-model="config.version" placeholder="例如 1.0.0" />
              </a-form-item>
            </a-grid-item>

            <a-grid-item :span="8">
              <a-form-item label="整包下载地址">
                <a-input v-model="config.downloadUrl" placeholder="https://.../install.zip" />
              </a-form-item>
            </a-grid-item>

            <a-grid-item :span="8">
              <a-form-item label="MD5（可选）">
                <a-input v-model="config.md5" placeholder="32位MD5" />
              </a-form-item>
            </a-grid-item>

            <a-grid-item :span="24">
              <a-form-item label="备注（可选）">
                <a-textarea v-model="config.remark" :auto-size="{ minRows: 2, maxRows: 6 }" />
              </a-form-item>
            </a-grid-item>
          </a-grid>
        </a-form>
      </a-card>

      <a-card title="登录器背景" style="margin: 24px;">
        <template #extra>
          <a-button type="primary" @click="addBannerRow">新增大图</a-button>
        </template>

        <a-table :data="banners" :pagination="false">
          <template #empty>
            <div style="text-align: center; padding: 20px;">
              暂无数据
            </div>
          </template>
          <template #columns>
            <a-table-column title="标题" :width="220">
              <template #cell="{ record }">
                <a-input v-model="record.title" placeholder="标题" />
              </template>
            </a-table-column>

            <a-table-column title="图片地址" :ellipsis="true">
              <template #cell="{ record }">
                <a-input v-model="record.imageUrl" placeholder="图片地址" />
              </template>
            </a-table-column>

            <a-table-column title="排序" :width="110" align="center">
              <template #cell="{ record }">
                <a-input-number v-model="record.sortNo" :min="0" :step="1" hide-button />
              </template>
            </a-table-column>

            <a-table-column title="启用" :width="90" align="center">
              <template #cell="{ record }">
                <a-switch v-model="record.enabled" :checked-value="1" :unchecked-value="0" />
              </template>
            </a-table-column>

            <a-table-column title="操作" :width="180" align="right">
              <template #cell="{ record }">
                <a-space>
                  <a-button size="mini" :loading="bannerSaving" @click="saveBanner(record)">保存</a-button>
                  <a-button size="mini" status="danger" :loading="bannerSaving" @click="deleteBanner(record)">删除</a-button>
                </a-space>
              </template>
            </a-table-column>
          </template>
        </a-table>
      </a-card>
    </a-spin>
  </a-space>
</template>

<style scoped>
/* 使用框架默认样式 */
</style>
