<script setup lang="ts">
const props = defineProps({
  iconName: {
    type: String,
    required: true
  }
});
</script>

<template>
  <icon-home v-if="props.iconName === 'home'"/>
  <icon-common v-else-if="props.iconName === 'component'" />
  <icon-align-left v-else-if="props.iconName === 'row'"/>
  <icon-computer v-else-if="props.iconName === 'system'"/>
  <icon-user v-else-if="props.iconName === 'user'"/>
  <icon-user-group  v-else-if="props.iconName === 'role'"/>
  <icon-mind-mapping v-else-if="props.iconName === 'tree-table'"/>
  <icon-relation v-else-if="props.iconName === 'tree'"/>
  <icon-bookmark v-else-if="props.iconName === 'post'"/>
  <icon-book v-else-if="props.iconName === 'dist'"/>
  <icon-align-left v-else-if="props.iconName.includes('log')"/>
  <icon-dashboard v-else-if="props.iconName === 'dashboard'"/>
  <span v-else-if="props.iconName === '#' || !props.iconName"></span>
  <icon-command v-else/>
</template>

<style scoped>

</style>
