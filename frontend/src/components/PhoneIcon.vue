<script setup lang="ts">
import telephoneIcon from '@/assets/telephone.png'
</script>

<template>
  <span
    class="phone-icon"
    :style="{ '--phone-icon-image': `url(${telephoneIcon})` }"
    aria-hidden="true"
  ></span>
</template>

<style scoped>
.phone-icon {
  display: inline-block;
  width: 16px;
  height: 16px;
  flex: 0 0 16px;
  background: currentcolor;
  vertical-align: -0.1em;
  mask: var(--phone-icon-image) center / contain no-repeat;
  -webkit-mask: var(--phone-icon-image) center / contain no-repeat;
}
</style>
