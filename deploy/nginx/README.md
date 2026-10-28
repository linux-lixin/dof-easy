# 可选宿主机反向代理

用途与读者：配置自己域名的运维者。只有需要宿主机 Nginx/宝塔代理时使用；后台容器自带 Nginx，无需为直接访问安装另一个代理。

[nginx-location.conf.example](nginx-location.conf.example)是 location 片段，不是独立站点。输入为你管理的域名、HTTPS 证书与后台本机监听端口；将片段纳入自己的站点配置前核对 upstream 和请求体限制。模板不会自动修改系统 Nginx。先执行宿主机 `nginx -t`，成功才用该主机已有服务方式 reload；失败保留原配置，不 reload。

后台建议仅本机绑定，通过自己的 HTTPS 入口访问。成功信号为代理网页、登录和受保护 API 访问正常；匿名管理仍应拒绝。前提、监听差异与大文件上传见[安装](../../docs/operations/install.md)和[外置/宝塔指南](../../docs/operations/external-mysql.md)。
