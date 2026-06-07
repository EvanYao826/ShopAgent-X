import axios from 'axios';

const api = axios.create({
  baseURL: '/api/admin',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
});

api.interceptors.request.use(config => {
  const token = localStorage.getItem('adminToken');
  if (token) config.headers['Authorization'] = `Bearer ${token}`;
  return config;
}, error => Promise.reject(error));

api.interceptors.response.use(
  response => response.data.code === 200 ? response.data : Promise.reject(new Error(response.data.message || 'Request failed')),
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('adminToken');
      localStorage.removeItem('adminInfo');
      window.location.href = '/admin/login';
    }
    return Promise.reject(error);
  }
);

export const adminAuthAPI = {
  login: (username, password) => api.post('/login', { username, password })
};

export const userManagementAPI = {
  listUsers: (page = 1, size = 10) => api.get(`/users?page=${page}&size=${size}`),
  updateUserStatus: (userId, status) => api.post(`/users/${userId}/status`, null, { params: { status } })
};

export const knowledgeManagementAPI = {
  upload: (file, categoryId) => {
    const formData = new FormData();
    formData.append('file', file);
    if (categoryId) formData.append('categoryId', categoryId);
    return api.post('/knowledge/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } });
  },
  list: (categoryId) => api.get(`/knowledge/list${categoryId ? `?categoryId=${categoryId}` : ''}`),
  delete: (id) => api.delete(`/knowledge/${id}`),
  retryParse: (id, filePath) => api.post('/knowledge/retry-parse', { id, filePath })
};

export const qaLogAPI = {
  list: (page = 1, size = 10) => api.get(`/logs?page=${page}&size=${size}`)
};

// 商品管理 API
export const productManagementAPI = {
  list: (params) => api.get('/product/list', { params }),
  updateStatus: (id, status) => api.put(`/product/${id}/status`, null, { params: { status } }),
  update: (id, data) => api.put(`/product/${id}`, data),
  stats: () => api.get('/product/stats'),
  brands: () => api.get('/product/brands')
};

// 对话管理 API
export const conversationManagementAPI = {
  list: (params) => api.get('/conversation/list', { params }),
  messages: (id) => api.get(`/conversation/${id}/messages`),
  stats: () => api.get('/conversation/stats'),
  delete: (id) => api.delete(`/conversation/${id}`)
};

// 订单管理 API
export const orderManagementAPI = {
  list: (params) => api.get('/order/list', { params }),
  items: (id) => api.get(`/order/${id}/items`),
  stats: () => api.get('/order/stats')
};

export default api;
