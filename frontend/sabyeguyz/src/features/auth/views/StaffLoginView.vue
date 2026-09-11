<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import logo from '@/assets/sabyeguyz-logo.png'
const router = useRouter()
const submitting = ref(false)
const form = reactive({ username: '', password: '', role: 'manager' })
async function signIn() {
  if (!form.username || !form.password) return
  submitting.value = true
  localStorage.setItem('mockStaffRole', form.role)
  await router.push(form.role === 'manager' ? '/staff/dashboard' : '/staff/appointments')
  submitting.value = false
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
      <form class="login-shell" @submit.prevent="signIn">
        <div class="intro">
          <small>ยินดีต้อนรับกลับมา!</small>
          <h1>เข้าสู่ระบบ</h1>
        </div>
        <label
          >อีเมล<input
            v-model.trim="form.username"
            type="email"
            autocomplete="username"
            required /></label
        ><label
          >รหัสผ่าน<input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            required
          /><a href="#">ลืมรหัสผ่าน</a></label
        ><label
          >เลือกบทบาทสำหรับทดลองระบบ<select v-model="form.role">
            <option value="manager">Manager</option>
            <option value="therapist">นักกายภาพบำบัด</option>
          </select></label
        >
        <p class="role-note">
          {{
            form.role === 'manager'
              ? 'เข้าถึง Dashboard, Reports, Commission และ Back Office ได้'
              : 'เข้าถึงผู้ป่วย นัดหมาย การรักษา คอร์ส ธุรกรรม และค่าตอบแทนของตนเอง'
          }}
        </p>
        <button type="submit" :disabled="submitting || !form.username || !form.password">
          {{ submitting ? 'กำลังเข้าสู่ระบบ...' : 'เข้าสู่ระบบ' }}
        </button>
      </form>
    </section>
  </main>
</template>
<style scoped src="../styles/staff-login.css"></style>
