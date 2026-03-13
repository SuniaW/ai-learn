# --- 阶段一：编译阶段 ---
FROM node:18-alpine as build-stage

# 设置工作目录
WORKDIR /app

# 复制依赖文件并安装
COPY package*.json ./
RUN npm install

# 复制所有源码并构建
COPY . .
RUN npm run build

# --- 阶段二：运行阶段 ---
FROM nginx:stable-alpine as production-stage

# 从第一阶段的成果物中复制 dist 文件夹到 Nginx 目录
COPY --from=build-stage /app/dist /usr/share/nginx/html

# 复制自定义的 Nginx 配置覆盖默认配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露 80 端口
EXPOSE 80

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]