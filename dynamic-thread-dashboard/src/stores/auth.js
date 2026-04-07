import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const isLogin = computed(() => !!token.value)

  async function login(credentials) {
    try {
      const response = await authApi.login(credentials)
      if (response.success) {
        token.value = response.token
        username.value = response.username
        localStorage.setItem('token', response.token)
        localStorage.setItem('username', response.username)
        return { success: true }
      } else {
        return { success: false, message: response.message }
      }
    } catch (error) {
      return { success: false, message: error.message || 'Login failed' }
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch (error) {
      console.error('Logout error:', error)
    } finally {
      token.value = ''
      username.value = ''
      localStorage.removeItem('token')
      localStorage.removeItem('username')
    }
  }

  async function checkAuth() {
    if (!token.value) {
      return false
    }
    try {
      const response = await authApi.info()
      return response.isLogin
    } catch (error) {
      token.value = ''
      username.value = ''
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      return false
    }
  }

  return {
    token,
    username,
    isLogin,
    login,
    logout,
    checkAuth
  }
})
