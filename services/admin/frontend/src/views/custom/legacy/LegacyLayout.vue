<script setup lang="ts">
import LegacyHeader from '../../../components/custom/LegacyHeader.vue';
import LegacyFooter from '../../../components/custom/LegacyFooter.vue';
import LegacyPreloader from '../../../components/custom/LegacyPreloader.vue';
</script>

<template>
  <div class="legacy-site">
    <div class="page-wrapper">
      <LegacyPreloader />
      <LegacyHeader />
      <slot />
      <LegacyFooter />
    </div>
  </div>
</template>
