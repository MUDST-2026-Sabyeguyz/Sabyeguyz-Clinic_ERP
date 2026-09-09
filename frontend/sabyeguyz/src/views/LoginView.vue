<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import logoUrl from '@/assets/sabyeguyz-logo.png'

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
  <main class="login-page">
    <section class="login-brand"><img :src="logoUrl" alt="Sabyeguyz" /><p>ฟื้นฟูการเคลื่อนไหว เพื่อคุณภาพชีวิตที่ดีขึ้น</p></section>
    <section class="login-card"><p class="welcome">ยินดีต้อนรับกลับมา!</p><h1>เข้าสู่ระบบ</h1>
    <form @submit.prevent="submit">
      <label>อีเมล / ชื่อผู้ใช้<input v-model="username" required autocomplete="username" /></label>
      <label>รหัสผ่าน<input v-model="password" required type="password" minlength="8" autocomplete="current-password" /></label>
      <button class="primary" :disabled="loading">{{ loading ? 'กำลังเข้าสู่ระบบ…' : 'เข้าสู่ระบบ' }}</button>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </form></section>
  </main>
</template>

<style scoped>
.login-page { display: grid; grid-template-columns: 1.05fr .95fr; min-height: 100vh; background: #eaf6f1; }.login-brand { display: grid; place-content: center; gap: 20px; padding: 48px; color: #d8f0eb; text-align: center; background: #176f6c; }.login-brand img { width: min(440px, 80%); margin: 0 auto; }.login-brand p { margin: 0; font-size: clamp(20px, 2.4vw, 32px); font-weight: 700; }.login-card { align-self: center; width: min(560px, calc(100% - 64px)); margin: auto; padding: 48px 34px; border-radius: 18px; background: white; box-shadow: 0 3px 3px rgb(0 0 0 / 20%); }.login-card h1 { margin: 0 0 26px; color: #111; font-size: 36px; }.welcome { margin: 0 0 8px; color: #176f6c; font-size: 26px; font-weight: 700; }.login-card form { width: 100%; }.login-card label { display: block; width: 100%; margin-top: 16px; font-weight: 700; }.login-card input { display: block; width: 100%; min-height: 54px; margin-top: 8px; background: #f8f8f8; }.login-card .primary { width: 100%; min-height: 54px; margin-top: 24px; border-radius: 8px; background: #176f6c; font-size: 20px; font-weight: 700; }.error { color: #b42318; }
@media (max-width: 720px) { .login-page { grid-template-columns: 1fr; }.login-brand { min-height: 260px; padding: 30px; }.login-brand img { width: 260px; }.login-card { width: min(560px, calc(100% - 32px)); margin: 28px auto; padding: 34px 24px; } }
</style>
