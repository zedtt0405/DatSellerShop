import axios from 'axios';

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080',
  headers: { 'Content-Type': 'application/json' },
});
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('dat_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use((r) => r, (error) => {
  if (error.response?.status === 401) {
    localStorage.removeItem('dat_token');
    localStorage.removeItem('dat_user');
  }
  return Promise.reject(error);
});
export const money = (v) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(Number(v || 0));
export const imageOf = (p) => p?.primaryImageUrl || p?.images?.[0]?.imageUrl || 'https://images.unsplash.com/photo-1529139574466-a303027c1d8b?w=900&q=85';
export const errorMessage = (e) => e?.response?.data?.message || e?.response?.data?.detail || 'Có lỗi xảy ra. Vui lòng thử lại.';
