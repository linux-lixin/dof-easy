<template>
  <!-- 1. 如果没有子节点，或者子节点为空，渲染为普通菜单项 -->
  <template v-if="!item.children || item.children.length === 0">
    <a-menu-item :key="parseItemFullPath()">
      <template #icon v-if="item.meta?.icon">
        <ruoyi-icon :icon-name="item.meta.icon" />
      </template>
      {{ item.meta?.title }}
    </a-menu-item>
  </template>

  <!-- 2. 如果有子节点，渲染为子菜单 (SubMenu) -->
  <template v-else>
    <a-sub-menu :key="item.path">
      <template #title>
        <span>
          <ruoyi-icon v-if="item.meta?.icon" :icon-name="item.meta.icon" />
          {{ item.meta?.title }}
        </span>
      </template>

      <!-- 核心：递归调用自己 -->
      <!-- 遍历当前 item 的 children，再次传入本组件 -->
      <recursive-menu-item
          v-for="child in item.children"
          :key="item.path + (child.path.startsWith('/') ? child.path : `/${child.path}`)"
          :parentPath="item.path"
          :item="child"
      />
    </a-sub-menu>
  </template>
</template>

<script setup lang="ts">
// 接收父组件传来的菜单项数据
import RuoyiIcon from "./RuoyiIcon.vue";
const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  parentPath: {
    type: String,
    required: false,
    default: ''
  }
})

const parseItemFullPath = () => {
  if (props.item.fullPath) {
    return props.item.fullPath;
  }
  props.item.fullPath = props.parentPath + (props.item.path.startsWith('/') ? props.item.path : `/${props.item.path}`);
  return props.item.fullPath;
}
</script>

<script lang="ts">
export default {
  name: 'RecursiveMenuItem',
}
</script>
