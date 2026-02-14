import axios from 'axios'

// 注意：baseURL 是 /ai，不是完整 URL！
const api = axios.create({
  baseURL: '/ai', // ← 关键：使用相对路径
  timeout: 100000,
});

export const search = (query: string) => {
  return api.get<string[]>(`/weather?city=${query}`)
}

export const getWeather = (city: string) => {
  return api.get<string>(`/weather?city=${encodeURIComponent(city)}`);
};
