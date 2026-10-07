# EVOPS 基础工作区

这是 springboot的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

根目录的 `..\..\docs\schema\evops.sql` 是交付副本，应与工作区 `src/main/resources/schema.sql` 保持一致。

## 植物工厂种植批次台账

在骨架上新增植物工厂生产台账（包 `com.evops.plant`），先跑通：建档 → 播种 → 养护 → 采收 → 质检；销售结算后续再接，本阶段不提供结算接口。

业务表（见 `schema.sql`，版本 `pf-ledger-1`）：

- `t_pf_greenhouse` 温室：光源/光周期/PPFD、营养液循环模式/EC/pH/循环间隔
- `t_pf_zone` 设备分区（隶属温室）
- `t_pf_variety` 品种、`t_pf_tray` 播种盘
- `t_pf_batch` 种植批次：关联品种、播种盘、计划采收日；批次号唯一（序列 `seq_pf_batch_no` + `uk_pf_batch_no`）
- `t_pf_batch_relocation` 转棚留痕（只追加，含起止棚位/原因/操作者）
- `t_pf_care_log` 养护记录（LIGHT 光照 / NUTRIENT 营养液循环 / EQUIPMENT 设备）
- `t_pf_harvest_order` 采收单：等级（A/B/C）、重量、去向；采收单号唯一（`seq_pf_harvest_no` + `uk_pf_harvest_no`）
- `t_pf_quality_check` 质检：一张采收单至多一条（`uk_pf_qc_harvest`），结论回写采收单
- `t_pf_operator` 操作者：0=系统（定时任务兜底），保证任何记录都不丢操作者

接口（HTTP Basic，账号 bootstrap/bootstrap；健康检查除外）：

- 建档：`POST/GET /api/pf/greenhouses`、`POST/GET /api/pf/greenhouses/{id}/zones`、`/api/pf/varieties`、`/api/pf/trays`
- 播种：`POST /api/pf/batches`；养护：`POST /api/pf/batches/{id}/care-logs`；转棚：`POST /api/pf/batches/{id}/relocations`
- 采收：`POST /api/pf/harvests/batches/{batchId}`；质检：`POST /api/pf/harvests/{id}/quality-check`
- 起步核对：`POST /api/pf/startup/run`（手动触发定时任务）、`GET /api/pf/startup/batch`

定时任务 `StartupBatchJob`：启动即执行、之后每 6 小时巡检，幂等确保"植物工厂种植批次台账起步"批次（固定编号 `PFB-STARTUP-0001`，`startup_flag=Y`）存在且仅一条。重启/多次触发只回查跳过，不多写记录；任务线程无登录主体时操作者兜底为系统(0)。

## 植物光配方（版本 `pf-light-1`）

按"品种 + 生长阶段"配置光谱比例、亮度与时段，定时任务按棚区（温室+分区）整点下发；内置奶油生菜(V001)起步配方。

**统一口径（临界时间归属无歧义，测试见 `src/test`）：**

- 阶段：`GrowthStage` SEEDLING/VEGETATIVE/FLOWERING/FRUITING，按批次生长进度（已生长天/计划周期天×100）归属，区间 `[from, to)` **左闭右开**——进度 30 归营养生长期，90 归结果期，100 钳制为最后阶段；阶段必须首尾相接覆盖 0~100。
- 时段：分钟数 `[start, end)` 左闭右开，06:00 整点属白天、22:00 整点属夜间关灯；`end <= start` 为跨午夜（22:00→06:00）；每个阶段时段必须无缝覆盖全天。
- 整点：所有下发 `planned_at` 截断到整点，幂等键为"温室+分区+整点"，重复触发/重启不多发。

**版本规则：** 配方 DRAFT→PUBLISHED 只追加，发布后不可改；`effective_at` 取发布时刻的**下一整点**，整点切换。批次"进入"阶段时在 `t_pf_batch_stage_binding` 冻结当时生效版本——**新版本只影响未开始阶段**，已开始阶段沿用冻结版本到阶段结束。下发流水 `t_pf_light_dispatch.payload_json` 为**快照**，历史执行不随后续改配方变化（重试也按快照重发）。

**失败重试：** 设备网关（`LightDeviceGateway`，本地实现 `LoggingLightDeviceGateway` 可模拟设备故障）抛错即置 FAILED 并入 `t_pf_light_dispatch_retry`，退避 1/2/4/8/16 分钟（封顶 60）由重试任务扫描，最多 5 次，超限 GIVEN_UP 待人工介入。

**人工临时调整：** `POST /api/pf/light/overrides` 强制 `endAt`（结束时间）与 `approverId`（审批人）非空且结束晚于开始；有效期内整点下发覆盖配方（批次级 > 分区级 > 温室级），到期整点自动恢复配方（来源 RESTORE），可提前撤销，全过程留痕。

接口（HTTP Basic）：

- 配方：`POST/GET /api/pf/light/recipes`、`POST /api/pf/light/recipes/{id}/publish`、`GET /api/pf/light/recipes/{id}`
- 人工调整：`POST/GET /api/pf/light/overrides`、`POST /api/pf/light/overrides/{id}/revoke`
- 执行记录：`GET /api/pf/light/dispatches?result=FAILED`、`GET /api/pf/light/retries?status=PENDING`
- 手动触发：`POST /api/pf/light/dispatch/run`（Quartz）、`POST /api/pf/light/dispatch/tick`（同步立即执行）、`POST /api/pf/light/retries/run`

定时任务：`LightDispatchJob`（cron 每整点 + 启动补跑一次）、`LightRetryJob`（每 5 分钟）。

