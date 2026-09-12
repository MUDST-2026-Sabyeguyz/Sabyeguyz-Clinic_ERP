<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import logo from '@/assets/sabyeguyz-logo.png'
import { ApiError, authApi, clearSession } from '@/api/client'
const router = useRouter()
const submitting = ref(false)
const emailTouched = ref(false)
const loginError = ref('')
const form = reactive({ username: '', password: '' })
const usernameIsValid = computed(() => form.username.trim().length > 0)
const usernameError = computed(() =>
  emailTouched.value && !usernameIsValid.value ? 'กรุณากรอกชื่อผู้ใช้หรืออีเมล' : '',
)
async function signIn() {
  emailTouched.value = true
  loginError.value = ''
  if (!usernameIsValid.value || !form.password) return
  submitting.value = true
  try {
    const user = await authApi.login(form.username.trim(), form.password)
    const role = user.role === 'MANAGER' ? 'manager' : 'therapist'
    localStorage.setItem('authToken', user.token)
    localStorage.setItem('authUser', JSON.stringify(user))
    localStorage.setItem('staffRole', role)
    localStorage.setItem('staffUsername', user.username)
    // Existing screens read this key for role-specific presentation; its value comes from the API.
    localStorage.setItem('mockStaffRole', role)
    localStorage.setItem('mockStaffEmail', user.email ?? user.username)
    await router.push('/staff/patients')
  } catch (error) {
    clearSession()
    localStorage.removeItem('mockStaffEmail')
    loginError.value =
      error instanceof ApiError ? error.message : 'ไม่สามารถเชื่อมต่อระบบได้ กรุณาลองใหม่'
  } finally {
    submitting.value = false
  }
}
</script>
<template>
  <main class="login-page">
    <section class="login-brand">
      <span class="plus one">+</span><span class="plus two">+</span
      ><img :src="logo" alt="SaByeGuyz" />
      <h2>ฟื้นฟูการเคลื่อนไหว เพื่อคุณภาพชีวิตที่ดีขึ้น</h2>
    </section>
    <section class="login-content">
      <form class="login-shell" novalidate @submit.prevent="signIn">
        <div class="intro">
          <small>ยินดีต้อนรับกลับมา!</small>
          <h1>เข้าสู่ระบบ</h1>
        </div>
        <label class="email-field"
          >ชื่อผู้ใช้หรืออีเมล<input
            v-model.trim="form.username"
            type="text"
            placeholder="กรอกชื่อผู้ใช้หรืออีเมล"
            autocomplete="username"
            required
            :aria-invalid="Boolean(usernameError)"
            aria-describedby="email-message"
            @input="emailTouched = true"
            @blur="emailTouched = true"
          /><span v-if="usernameError" id="email-message" class="field-error" role="alert">{{
            usernameError
          }}</span
          ><span v-else id="email-message" class="field-hint"
            >ใช้ชื่อผู้ใช้หรืออีเมลที่ได้รับจากผู้ดูแลระบบ</span
          ></label
        ><label
          >รหัสผ่าน<input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            required
          /><a href="#">ลืมรหัสผ่าน</a></label
        >
        <p v-if="loginError" class="field-error" role="alert">{{ loginError }}</p>
        <button type="submit" :disabled="submitting || !usernameIsValid || !form.password">
          {{ submitting ? 'กำลังเข้าสู่ระบบ...' : 'เข้าสู่ระบบ' }}
        </button>
      </form>
    </section>
  </main>
</template>
<style scoped src="../styles/staff-login.css"></style>
