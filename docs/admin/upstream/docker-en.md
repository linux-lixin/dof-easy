> 上游说明存档：界面与历史用法供参考，原路径和默认值不作为当前部署步骤。当前结构与构建请从 [后台文档](../README.md) 开始。

# DNF Server Docker Guide

This directory contains the necessary files to build and run the DNF Server in a Docker container.

## Features

- **All-in-One**: Provides unified GM management services, including GM basic functions (game accounts, roles, mail management, etc.) and game gateways (login, registration), etc.
- **Default Platform Build**: Uses the current Docker builder's default architecture without an explicit platform override or multi-platform build.
- **Auto-Initialization**:
    - **Database**: Automatically initializes the database schema on the first run using the provided connection settings.
    - **Keys**: Automatically generates RSA keys (`private.key`, `publickey.pem`) if they are missing.
- **Data Persistence**: Keys and other data are persisted via Docker volumes.

## Prerequisities

- Docker installed on your system.
- Docker can access the game database (MySQL 5).

## Quick Start

### 1. Run the Container

Replace the environment variables with your actual database configuration.

```bash
docker run -d \
  --name dnf-server \
  -p 80:80 \
  -v $(pwd)/data:/app/data \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://YOUR_DB_IP:3306/dnf_service?useUnicode=true&characterEncoding=utf8&autoReconnect=true" \
  -e SPRING_DATASOURCE_USERNAME="root" \
  -e SPRING_DATASOURCE_PASSWORD="your_password" \
  guoshengkai/dnf-server:latest
```

> **Note**: The container listens on port **80** by default.

### 2. Verify

- **Web Interface**: Open `http://localhost` in your browser.
- **Logs**: Check logs with `docker logs -f dnf-server`.

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_DATASOURCE_URL` | JDBC Connection URL. The image will parse this to determine the DB host, port, and database name for initialization. | Required |
| `SPRING_DATASOURCE_USERNAME` | Database username. | Required |
| `SPRING_DATASOURCE_PASSWORD` | Database password. | Required |
| `NGINX_PORT` | The port Nginx listens on inside the container. | `80` |
| `SERVER_PORT` | The port the Java backend listens on (internal). | `8080` |
| `TZ` | Timezone setting. | `Asia/Shanghai` |

### Volumes

| path | Description |
|------|-------------|
| `/app/data` | Stores generated RSA keys (`private.key`, `publickey.pem`) and the database initialization marker. Map this to a host directory to persist keys across restarts. |

> **Warning**: Ensure the mapped `data` directory has appropriate permissions for the container to read/write.
> - The public and private keys in the `data` directory must match.
> - The public key in the game's `game` directory must match the public key in the `data` directory.
> - If you change the keys, you need to restart the game service.

## Self-build Image

### 1. Local Build (Single Architecture)

If you only need to run the image on your current machine:

```bash
docker build -t dnf-server .
```

### 2. Build and Push (Default Platform)

The provided scripts use the current Docker builder's default architecture. They retain the historical `build-multi-arch.sh` / `.ps1` names but no longer build multiple platforms or switch builders.
**Note**: You must be logged in to a Docker Registry (e.g., Docker Hub) because these scripts push the resulting image.

1.  Login to Docker Hub:
    ```bash
    docker login
    ```

2.  Run the build script:
    ```bash
    ./build-multi-arch.sh
    ```
    The script will prompt you for your Docker Hub repository name (e.g., `yourusername/dnf-server`) and then build & push the images automatically.

## Database Initialization

The container includes a mechanism to automatically initialize the database:

1.  On startup, it checks for a marker file in `/app/data`.
2.  If the marker is missing (first run), it parses the `SPRING_DATASOURCE_URL`.
3.  It extracts the database name (e.g., `dnf_service`) and creates it if it doesn't exist.
4.  It executes the included SQL schema to set up tables.
5.  **Success**: It creates the marker file so subsequent restarts skip this step.

**Warning**: Ensure your database user has permissions to `CREATE DATABASE` and `CREATE TABLE`.

## Auto-Generated Keys

The application requires RSA keys for operation.
- If `private.key` and `publickey.pem` are **not found** in `/app/data/`, the container automatically generates a **2048-bit PKCS#8** key pair.
- If you have existing keys, simply place them in your mapped `data` volume directory before starting the container.
