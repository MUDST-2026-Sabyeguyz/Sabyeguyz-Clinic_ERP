<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, ApiError, clearSession } from '@/api/client'
import logo from '@/assets/sabyeguyz-logo.png'
const router = useRouter()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', password: '' })
async function signIn() {
  if (!form.username || !form.password) return
  submitting.value = true
  errorMessage.value = ''
  try {
    // A new login must never reuse credentials from an earlier local backend.
    clearSession()
    const result = await authApi.login(form.username, form.password)
    const { token, ...user } = result
    localStorage.setItem('authToken', token)
    localStorage.setItem('authUser', JSON.stringify(user))
    await router.push(result.role === 'MANAGER' ? '/staff/dashboard' : '/staff/patients')
  } catch (error) {
    errorMessage.value = error instanceof ApiError ? error.message : 'เข้าสู่ระบบไม่สำเร็จ'
  } finally {
    submitting.value = false
  }
}
</script>
<template>
  <main class="login-page">
    <section class="login-brand">
      <img :src="logo" alt="SaByeGuyz" />
      <h2>ฟื้นฟูการเคลื่อนไหว เพื่อคุณภาพชีวิตที่ดีขึ้น</h2>
    </section>
    <section class="login-content">
      <form class="login-shell" @submit.prevent="signIn">
        <div class="intro">
          <small>ยินดีต้อนรับกลับมา!</small>
          <h1>เข้าสู่ระบบ</h1>
        </div>
        <label
          >Username หรือ Email<input
            v-model.trim="form.username"
            type="text"
            autocomplete="username"
            required /></label
        ><label
          >รหัสผ่าน<input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            required
          /></label
        >
        <p class="role-note">ระบบจะใช้บทบาทจริงจากบัญชีผู้ใช้ที่ backend ส่งกลับ</p>
        <button type="submit" :disabled="submitting || !form.username || !form.password">
          {{ submitting ? 'กำลังเข้าสู่ระบบ...' : 'เข้าสู่ระบบ' }}
        </button>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
      </form>
    </section>
  </main>
</template>
<style scoped src="../styles/staff-login.css"></style>
