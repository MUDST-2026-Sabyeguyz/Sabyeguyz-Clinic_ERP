<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  loading.value = true; error.value = ''
  try { await auth.login(username.value, password.value); await router.push('/') }
  catch (cause) { error.value = cause instanceof Error ? cause.message : 'เข้าสู่ระบบไม่สำเร็จ' }
  finally { loading.value = false }
}
</script>

<template>
  <main class="form-card" style="max-width: 420px; margin: 12vh auto">
    <h1>เข้าสู่ระบบ Clinic ERP</h1>
    <form @submit.prevent="submit">
      <label>ชื่อผู้ใช้<input v-model="username" required autocomplete="username" /></label>
      <label>รหัสผ่าน<input v-model="password" required type="password" minlength="8" autocomplete="current-password" /></label>
      <button class="primary" :disabled="loading">{{ loading ? 'กำลังเข้าสู่ระบบ…' : 'เข้าสู่ระบบ' }}</button>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </form>
  </main>
</template>
