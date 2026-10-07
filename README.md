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


