<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { LauncherApi } from '../../../api/launcher';

const mode = ref<'dof' | 'native' | 'unknown'>('unknown');
const failed = ref(false);
onMounted(async () => {
  try {
    mode.value = (await LauncherApi.getRuntime()).data.mode;
  } catch {
    failed.value = true;
  }
});
</script>

<template>
  <a-alert :type="failed ? 'warning' : 'info'" style="margin: 24px 24px 0">
    <template v-if="mode === 'native'">
      当前玩家登录方式：原生登录器。请在网页的 Dof 管理页面保存配置、处理 PVF 并生成配套包；本页设置用于另一种 dof 登录模式。
    </template>
    <template v-else-if="mode === 'dof'">
      当前玩家登录方式：自有登录器。本页配置对自有登录器生效；切换登录方式请使用实例部署命令。
    </template>
    <template v-else>{{ failed ? '无法读取当前登录方式，请检查后台连接。' : '正在读取当前登录方式…' }}</template>
  </a-alert>
</template>
