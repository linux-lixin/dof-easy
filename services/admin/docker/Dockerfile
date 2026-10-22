# Frontend Build Stage
FROM node:22-alpine AS frontend
WORKDIR /app/webui
COPY frontend/package*.json ./
RUN npm install --registry=https://registry.npmmirror.com
COPY frontend ./
# Skip TypeScript check (vue-tsc), just build with vite
RUN npx vite build

# Backend Build Stage
FROM maven:3.9-eclipse-temurin-24 AS backend
WORKDIR /app
COPY backend/pom.xml .
COPY backend/src ./src
# Dependencies are resolved from pom.xml; no local libs directory is required.
# Package the application (skip tests for speed)
RUN mvn clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:21-jre

# Install Nginx & gettext & openssl & tzdata & mysql-client
# use eclipse-temurin (Ubuntu/Debian based) which supports apt-get
RUN apt-get update && \
    apt-get install -y nginx gettext-base openssl tzdata mariadb-client && \
    ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Set Timezone ENV
ENV TZ=Asia/Shanghai

WORKDIR /app

# Copy Frontend Artifacts to Nginx specific dir
COPY --from=frontend /app/webui/dist /app/static

# Copy Backend Artifact
COPY --from=backend /app/target/*.jar app.jar

# Copy Nginx Config & Entrypoint & Init SQL
COPY docker/nginx.conf.template /etc/nginx/nginx.conf.template
COPY docker/entrypoint.sh /app/entrypoint.sh
COPY database/init.sql /app/init.sql.template
RUN chmod +x /app/entrypoint.sh

# Environment Variables
ENV NGINX_PORT=80
ENV SERVER_PORT=9001
# Database Config
ENV SPRING_DATASOURCE_URL=""
ENV SPRING_DATASOURCE_USERNAME=""
# Supply SPRING_DATASOURCE_PASSWORD at runtime through Compose or docker run.
ENV SPRING_DATASOURCE_DRIVER_CLASS_NAME="com.mysql.jdbc.Driver"

# Volume for data with keys
VOLUME /app/data

# Expose Nginx Port
EXPOSE 80

# Start
ENTRYPOINT ["/app/entrypoint.sh"]
