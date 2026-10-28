# 文档维护规范

用途与读者：文档与代码维护者。适用范围：所有当前文档；历史证据另列范围。前置条件与操作见正文，执行后核对成功信号；历史证据不代表本次重新执行。

事实来源：[catalog.json](../catalog.json)；[scope.json](../../services/admin/backend/src/main/resources/dof/scope.json)；[execution-plan.json](../../services/admin/backend/src/main/resources/dof/execution-plan.json)。限制和当前验收边界见[当前状态](../status/README.md)。


每份当前指南必须说明用途、读者、适用模式、前置条件、操作或定义、预期结果、限制和事实来源。模块 README 可保留源码结构和最短示例，完整过程只在一个权威页维护。API 字段、默认值与状态应链接源码；操作指南不能只写“见历史记录”。

## 目录与证据

按[文档首页](../README.md)的八类职责放置；每目录 README 给阅读顺序。新页面登记 `docs/catalog.json.pages` 并链接父目录首页。旧路径用一跳迁移提示，禁止链和循环；更新当前导航直达目标。新增证据用新日期文件，不能修改冻结的 88 份 JSON 或图片；修正结论写当前状态并保留历史原文。历史截图保留来源和日期，不标为当前页面。

当前部署只更新 `docs/status/current.json` 与其证据指针，事实由校验器逐字段对照。功能完成仍以原 `scope.json` 和 `execution-plan.json` 为准，禁止新建第二套完成台账。源码合并、编译、打包、镜像、部署、接口和真实消费分别陈述。不同批次数不相加，条件跳过和未执行不算通过，默认 `releaseReady=false`。

## 提交流程

1. 从受影响源码与锁核对事实，更新权威正文，给出适用版本和来源。
2. 修改目录时同步 catalog、父索引及直接迁移入口。保持原 JSON、许可证、占位说明和来源图片哈希。
3. 新文件显式 `git add` 后运行 `python scripts/diagnostics/check_docs.py`；也可用 `--include 相对路径` 明确纳入尚未 staged 的文档。检查器不扫描无关未提交文件与私有目录。
4. 运行 checker 回归测试及 `git diff --check`，审查 staged 清单中的凭据与运行材料；经 PR 检查后合并。

检查成功只代表文档一致性，不提升功能门禁。发现历史证据冲突，保留原记录、注明先后与对象，不能改原 JSON 迎合当前结论。任何新增事实校验规则必须有能失败的样例证明，不用仅匹配自身输出的测试充数。
