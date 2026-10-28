# Vue 管理后台和门户

用途与读者：本模块使用者与维护者。适用范围：以正文所列实例和模式为准。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[README.md](https://github.com/linux-lixin/dof-easy/blob/ecd4505d1ebdd28486b13a718cd991874efbe96f/services/admin/frontend/README.md)。限制和当前验收边界见[当前状态](../../../docs/status/README.md)。


`src/views` 保存页面，`src/api` 保存 API 调用，`public/assets/legacy` 保存页面仍引用的静态资源，不能仅因名称包含 legacy 就删除。原生管理页面位于 `src/views/admin/dof/`，路由 `/admin/dof`；默认登录器管理与原生配套任务按模式区分，见 [当前状态](../../../docs/status/README.md)。

在本目录执行：

```sh
npm ci
npm run dev
npm run build
```

`npm run build` 包含类型检查；Docker 构建延续原有直接运行 Vite 的流程，二者的检查范围不同。生成的 `dist` 不提交。

完整后台 Docker 构建上下文是 `services/admin`，不是本目录。环境与部署说明见[后台文档](../../../docs/development/admin.md)。
