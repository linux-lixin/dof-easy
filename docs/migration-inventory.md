# 文档迁移清单

基线 `ecd4505d1ebdd28486b13a718cd991874efbe96f`。86 份说明逐项登记；88 份既有 JSON、22 张图片以及资源占位说明、许可证与第三方声明通过 SHA-256 冻结。

原工作区未提交审计文件不在本次目录清单或提交范围内。目录重组不变更业务、资源锁、功能台账和测试服务器。

完整机器清单：[catalog.json](catalog.json)。

| 原路径 | 权威归属 | 处理 |
|---|---|---|
| `CONTRIBUTING.md` | [CONTRIBUTING.md](../CONTRIBUTING.md) | 原位保留 |
| `README.md` | [README.md](../README.md) | 原位保留 |
| `clients/launcher/README.md` | [clients/launcher/README.md](../clients/launcher/README.md) | 原位保留 |
| `deploy/README.md` | [deploy/README.md](../deploy/README.md) | 原位保留 |
| `deploy/manual/README.md` | [deploy/manual/README.md](../deploy/manual/README.md) | 原位保留 |
| `deploy/manual/all/README.md` | [deploy/manual/all/README.md](../deploy/manual/all/README.md) | 原位保留 |
| `deploy/manual/build/README.md` | [deploy/manual/build/README.md](../deploy/manual/build/README.md) | 原位保留 |
| `deploy/manual/split/README.md` | [deploy/manual/split/README.md](../deploy/manual/split/README.md) | 原位保留 |
| `deploy/manual/split/admin/README.md` | [deploy/manual/split/admin/README.md](../deploy/manual/split/admin/README.md) | 原位保留 |
| `deploy/manual/split/core/README.md` | [deploy/manual/split/core/README.md](../deploy/manual/split/core/README.md) | 原位保留 |
| `deploy/manual/split/game/README.md` | [deploy/manual/split/game/README.md](../deploy/manual/split/game/README.md) | 原位保留 |
| `deploy/manual/split/p2p/README.md` | [deploy/manual/split/p2p/README.md](../deploy/manual/split/p2p/README.md) | 原位保留 |
| `docs/README.md` | [docs/README.md](README.md) | 原位保留 |
| `docs/admin/README.md` | [docs/development/admin.md](development/admin.md) | 迁移，旧路径直接提示 |
| `docs/admin/activities.md` | [docs/operations/admin/activities.md](operations/admin/activities.md) | 迁移，旧路径直接提示 |
| `docs/admin/extension-management.md` | [docs/operations/admin/records.md](operations/admin/records.md) | 迁移，旧路径直接提示 |
| `docs/admin/launcher-integration.md` | [docs/reference/clients/compatibility.md](reference/clients/compatibility.md) | 迁移，旧路径直接提示 |
| `docs/admin/plugin-ownership.md` | [docs/architecture/execution-ownership.md](architecture/execution-ownership.md) | 迁移，旧路径直接提示 |
| `docs/admin/replacement-implementation.md` | [docs/history/admin/2026-10-28-implementation.md](history/admin/2026-10-28-implementation.md) | 迁移，旧路径直接提示 |
| `docs/admin/script-compression-validation.md` | [docs/development/native/compressed-script-audit.md](development/native/compressed-script-audit.md) | 迁移，旧路径直接提示 |
| `docs/admin/upstream/README.md` | [docs/history/upstream/admin/README.md](history/upstream/admin/README.md) | 迁移，旧路径直接提示 |
| `docs/admin/upstream/docker-en.md` | [docs/history/upstream/admin/docker-en.md](history/upstream/admin/docker-en.md) | 迁移，旧路径直接提示 |
| `docs/admin/upstream/docker-zh.md` | [docs/history/upstream/admin/docker-zh.md](history/upstream/admin/docker-zh.md) | 迁移，旧路径直接提示 |
| `docs/architecture.md` | [docs/architecture/overview.md](architecture/overview.md) | 迁移，旧路径直接提示 |
| `docs/baota-deployment.md` | [docs/operations/external-mysql.md](operations/external-mysql.md) | 迁移，旧路径直接提示 |
| `docs/container-installer.md` | [docs/operations/install.md](operations/install.md) | 迁移，旧路径直接提示 |
| `docs/container-verification.md` | [docs/validation/container-verification.md](validation/container-verification.md) | 迁移，旧路径直接提示 |
| `docs/dof/README.md` | [docs/getting-started/login-modes.md](getting-started/login-modes.md) | 迁移，旧路径直接提示 |
| `docs/dof/client-format-v2-update.md` | [docs/operations/native-client-publication.md](operations/native-client-publication.md) | 迁移，旧路径直接提示 |
| `docs/dof/client-reader-validation.md` | [docs/status/native-client.md](status/native-client.md) | 迁移，旧路径直接提示 |
| `docs/dof/client-readiness-update.md` | [docs/history/clients/readiness-pr26.md](history/clients/readiness-pr26.md) | 迁移，旧路径直接提示 |
| `docs/dof/deployment.txt` | [docs/operations/isolated-admin.md](operations/isolated-admin.md) | 迁移，旧路径直接提示 |
| `docs/dof/equipment-broadcast.md` | [docs/reference/native/equipment-broadcast.md](reference/native/equipment-broadcast.md) | 迁移，旧路径直接提示 |
| `docs/dof/game-test-deployment.md` | [docs/operations/isolated-game.md](operations/isolated-game.md) | 迁移，旧路径直接提示 |
| `docs/dof/launcher.md` | [docs/development/native-resource-derivation.md](development/native-resource-derivation.md) | 迁移，旧路径直接提示 |
| `docs/dof/protocol.txt` | [docs/reference/native/client-protocol.md](reference/native/client-protocol.md) | 迁移，旧路径直接提示 |
| `docs/dof/publication.txt` | [docs/development/admin-packaging.md](development/admin-packaging.md) | 迁移，旧路径直接提示 |
| `docs/dof/server-protocol-contract.md` | [docs/reference/native/server-protocol.md](reference/native/server-protocol.md) | 迁移，旧路径直接提示 |
| `docs/dof/server-runtime-validation.md` | [docs/status/native-runtime.md](status/native-runtime.md) | 迁移，旧路径直接提示 |
| `docs/dof/status.md` | [docs/status/README.md](status/README.md) | 迁移，旧路径直接提示 |
| `docs/history/compatibility-audit.md` | [docs/history/compatibility-audit.md](history/compatibility-audit.md) | 原位保留 |
| `docs/history/deployment-fixes-2026-10-15.md` | [docs/history/deployment-fixes-2026-10-15.md](history/deployment-fixes-2026-10-15.md) | 原位保留 |
| `docs/history/game/DeploymentFixes-2026-10-15.md` | [docs/history/game/DeploymentFixes-2026-10-15.md](history/game/DeploymentFixes-2026-10-15.md) | 原位保留 |
| `docs/history/network-helpers/README.md` | [docs/history/network-helpers/README.md](history/network-helpers/README.md) | 原位保留 |
| `docs/history/offline86/README.md` | [docs/history/offline86/README.md](history/offline86/README.md) | 原位保留 |
| `docs/history/ubuntu-abi/README.md` | [docs/history/ubuntu-abi/README.md](history/ubuntu-abi/README.md) | 原位保留 |
| `docs/history/ubuntu-abi/image-geoip-start-proof.txt` | [docs/history/ubuntu-abi/image-geoip-start-proof.txt](history/ubuntu-abi/image-geoip-start-proof.txt) | 原位保留 |
| `docs/history/ubuntu-abi/image-shm-start-proof.txt` | [docs/history/ubuntu-abi/image-shm-start-proof.txt](history/ubuntu-abi/image-shm-start-proof.txt) | 原位保留 |
| `docs/image-registry.md` | [docs/operations/images.md](operations/images.md) | 迁移，旧路径直接提示 |
| `docs/launcher/README.md` | [docs/getting-started/login-modes.md](getting-started/login-modes.md) | 迁移，旧路径直接提示 |
| `docs/launcher/deployment.md` | [docs/operations/wpf-client.md](operations/wpf-client.md) | 迁移，旧路径直接提示 |
| `docs/launcher/usage.md` | [docs/history/upstream/launcher/usage.md](history/upstream/launcher/usage.md) | 迁移，旧路径直接提示 |
| `docs/project-layout-validation.md` | [docs/validation/project-layout.md](validation/project-layout.md) | 迁移，旧路径直接提示 |
| `docs/resource-layout.md` | [docs/reference/resources.md](reference/resources.md) | 迁移，旧路径直接提示 |
| `docs/stage-2026-10-28.md` | [docs/history/stages/2026-10-28.md](history/stages/2026-10-28.md) | 迁移，旧路径直接提示 |
| `docs/ubuntu/README.md` | [docs/operations/manual-deployment.md](operations/manual-deployment.md) | 迁移，旧路径直接提示 |
| `docs/ubuntu/cleanup-validation.md` | [docs/validation/ubuntu-cleanup.md](validation/ubuntu-cleanup.md) | 迁移，旧路径直接提示 |
| `docs/ubuntu/compatibility.md` | [docs/reference/platform-compatibility.md](reference/platform-compatibility.md) | 迁移，旧路径直接提示 |
| `docs/ubuntu/ports.md` | [docs/reference/ports.md](reference/ports.md) | 迁移，旧路径直接提示 |
| `docs/ubuntu/validation.md` | [docs/validation/ubuntu.md](validation/ubuntu.md) | 迁移，旧路径直接提示 |
| `docs/validation/bundled-native-assets.md` | [docs/validation/bundled-native-assets.md](validation/bundled-native-assets.md) | 原位保留 |
| `docs/validation/container-installer.md` | [docs/validation/container-installer.md](validation/container-installer.md) | 原位保留 |
| `docs/validation/deploy-layout.md` | [docs/validation/deploy-layout.md](validation/deploy-layout.md) | 原位保留 |
| `docs/validation/deployable-resources-only.md` | [docs/validation/deployable-resources-only.md](validation/deployable-resources-only.md) | 原位保留 |
| `docs/validation/direct-topology.md` | [docs/validation/direct-topology.md](validation/direct-topology.md) | 原位保留 |
| `docs/validation/mysql-deployment.md` | [docs/validation/mysql-deployment.md](validation/mysql-deployment.md) | 原位保留 |
| `docs/validation/raid-plugin-layout.md` | [docs/validation/raid-plugin-layout.md](validation/raid-plugin-layout.md) | 原位保留 |
| `docs/validation/unified-server-resources.md` | [docs/validation/unified-server-resources.md](validation/unified-server-resources.md) | 原位保留 |
| `metadata/README.md` | [metadata/README.md](../metadata/README.md) | 原位保留 |
| `metadata/dof/derivation/README.md` | [metadata/dof/derivation/README.md](../metadata/dof/derivation/README.md) | 原位保留 |
| `scripts/README.md` | [scripts/README.md](../scripts/README.md) | 原位保留 |
| `scripts/imports/README.md` | [scripts/imports/README.md](../scripts/imports/README.md) | 原位保留 |
| `services/admin/README.md` | [services/admin/README.md](../services/admin/README.md) | 原位保留 |
| `services/admin/backend/README.md` | [services/admin/backend/README.md](../services/admin/backend/README.md) | 原位保留 |
| `services/admin/frontend/README.md` | [services/admin/frontend/README.md](../services/admin/frontend/README.md) | 原位保留 |
| `services/game/README.md` | [services/game/README.md](../services/game/README.md) | 原位保留 |
| `services/game/resources/README.md` | [services/game/resources/README.md](../services/game/resources/README.md) | 原位保留 |
| `services/game/resources/native/README.md` | [services/game/resources/native/README.md](../services/game/resources/native/README.md) | 原位保留 |
| `tests/README.md` | [tests/README.md](../tests/README.md) | 原位保留 |
| `tests/integration/dof/digest_helper/README.md` | [tests/integration/dof/digest_helper/README.md](../tests/integration/dof/digest_helper/README.md) | 原位保留 |
| `tests/integration/dof/frida_database/README.md` | [tests/integration/dof/frida_database/README.md](../tests/integration/dof/frida_database/README.md) | 原位保留 |
| `tests/integration/dof/postal_read/README.md` | [tests/integration/dof/postal_read/README.md](../tests/integration/dof/postal_read/README.md) | 原位保留 |
| `tests/integration/dof/recharge/README.md` | [tests/integration/dof/recharge/README.md](../tests/integration/dof/recharge/README.md) | 原位保留 |
| `tests/integration/dof/server_stack/README.md` | [tests/integration/dof/server_stack/README.md](../tests/integration/dof/server_stack/README.md) | 原位保留 |
| `tests/integration/installer/README.md` | [tests/integration/installer/README.md](../tests/integration/installer/README.md) | 原位保留 |
| `tests/integration/mysql/README.md` | [tests/integration/mysql/README.md](../tests/integration/mysql/README.md) | 原位保留 |
