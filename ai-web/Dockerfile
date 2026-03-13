# --- 第一阶段：构建阶段 ---
FROM node:20-alpine AS build-stage

# 设置工作目录
WORKDIR /app

# 利用 Docker 缓存层：先拷贝 package.json
COPY package*.json ./

# 安装依赖（如果是国内服务器，可以设置镜像源）
RUN npm config set registry https://registry.npmmirror.com && \
    npm install

# 拷贝所有源码并执行打包
COPY . .
RUN npm run build

# --- 第二阶段：运行阶段 ---
FROM nginx:stable-alpine AS production-stage

# 从构建阶段拷贝打包后的文件到 Nginx 目录
COPY --from=build-stage /app/dist /usr/share/nginx/html

# 拷贝自定义的 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 暴露 80 端口
EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]