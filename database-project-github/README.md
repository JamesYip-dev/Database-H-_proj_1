# 数据库课程项目

比较 Java CSV 文件操作、PostgreSQL 与 openGauss 的关键词查询、持久化更新、索引查找、关联统计及并发读写。

- 报告：`docs/report_draft.docx`
- 主要结果：`WSL-results.md`
- 环境、依赖及运行步骤：`README-WSL.md`
- 源码与启动脚本：`03_dbms`、`04_index`、`05_join`、`06_concurrency` 和 `shared`

## 数据库连接

本仓库不包含密码，也不包含数据库容器或现成数据库。运行前需要准备数据库、课程数据与自己的连接配置。`03_dbms/*.properties` 和 `shared/database.properties` 中的地址及用户名是本次实验配置，可按实际环境修改。通过 `PG_PASSWORD`、`OG_PASSWORD` 环境变量或终端隐藏输入提供密码；切勿提交本机凭据。

## 原始结果

仅保留报告采用的正式结果。为减少上传文件数量，关联实验的 12 份主计时文件合并为对应结果目录下的 `timings.csv`；并发实验的 144 份逐轮记录合并为 `workers.csv` 与 `increments.csv.gz`。`increments.csv.gz` 为无损 gzip 压缩，解压后是 CSV。新增 `source_file` 列记录原路径，其余列和所有记录均保留。报告中的原始路径可通过该列对应查找；运行程序仍按原格式生成新结果。

启动脚本固定使用 OpenJDK 25，JAR 依赖在 `lib`。性能结论仅适用于报告所述数据、环境和任务。
