import request from './index'

export const authApi = {
  login(data) {
    return request.post('/api/auth/login', data)
  },
  
  logout() {
    return request.post('/api/auth/logout')
  },
  
  info() {
    return request.get('/api/auth/info')
  }
}
