# Vue 管理后台和门户

`src/views` 保存页面，`src/api` 保存 API 调用，`public/assets/legacy` 保存页面仍引用的静态资源，不能仅因名称包含 legacy 就删除。本轮整理不更改路由、接口地址、资源 URL 或依赖版本。

在本目录执行：

```sh
npm ci
npm run dev
npm run build
```

`npm run build` 包含类型检查；Docker 构建延续原有直接运行 Vite 的流程，二者的检查范围不同。生成的 `dist` 不提交。

完整后台 Docker 构建上下文是 `services/admin`，不是本目录。环境与部署说明见[后台文档](../../../docs/admin/README.md)。
