<template>
  <main class="page-shell login-page"><section class="panel login-panel">
    <h1>เข้าสู่ระบบ</h1><p class="muted">Clinic ERP</p>
    <form @submit.prevent="submit"><label>ชื่อผู้ใช้<input v-model="username" required autocomplete="username"></label><label>รหัสผ่าน<input v-model="password" required type="password" autocomplete="current-password"></label><p v-if="error" class="error">{{ error }}</p><button type="submit" :disabled="loading">{{ loading ? 'กำลังตรวจสอบ…' : 'เข้าสู่ระบบ' }}</button></form>
  </section></main>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const router = useRouter(); const auth = useAuthStore(); const username = ref(''); const password = ref(''); const error = ref(''); const loading = ref(false)
async function submit() { loading.value = true; error.value = ''; try { await auth.login(username.value, password.value); await router.push('/') } catch (e) { error.value = e instanceof Error ? e.message : 'เข้าสู่ระบบไม่สำเร็จ' } finally { loading.value = false } }
</script>
