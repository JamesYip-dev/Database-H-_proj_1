# WSL 数据库课程实验

实验主题与代码、报告要求以根目录 `soul.md` 为准。比较合理 Java CSV 实现与 DBMS，并按具体任务和指标比较 PostgreSQL、openGauss。

## 环境

- WSL Ubuntu 22.04，OpenJDK 25；启动脚本固定使用 `/usr/lib/jvm/java-25-openjdk-amd64`。
- PostgreSQL 18.6：`database-project-postgres`，127.0.0.1:5433，数据库 film，独立 Docker 数据卷。
- openGauss 6.0.0：`opengauss`，127.0.0.1:5432，数据库 film，沿用容器内部数据。
- 两库 shared_buffers=1GB、max_connections=200，fsync、synchronous_commit、full_page_writes 均开启。
- Java、CSV、数据库数据在 WSL 内部；计时不跨 /mnt/c。JDBC 驱动与容器存储仍不同，未消除所有外部差异。
- 连接配置在 03_dbms；本机密码文件由启动脚本读取，不要打印、分享或提交。

## 运行

```bash
cd ~/database-project
sudo /snap/bin/docker start opengauss database-project-postgres
# 等两个数据库就绪后：
./03_dbms/run-keywords.sh
# ./03_dbms/run-comparison.sh  # 关键词三组，然后姓名更新三组
./04_index/run-index.sh postgres
./04_index/run-index.sh opengauss
./05_join/run-join.sh
./06_concurrency/run-concurrency.sh
```

五项实验使用 03_dbms 起的目录，查询与姓名更新工具类集中在 03_dbms/src。各项代码、脚本、数据和 results 位于对应实验目录；01_search、02_update 仅保留早期结果。

05_join/prepare-data.py 用 Python 标准库从 filmdb.sql 提取关联所需字段，并检查编号引用；不向原始数据库导入新业务表。关联与并发实验使用独立副本，结束后清理。

## 关联与并发测量

关联查询：四任务各三组，预热 10 次、正式 30 次；主比较使用各库原配置。另在实验副本上进行计划、统计信息、统一规划参数及外键利用的诊断，详细记录与主结果分开保存。

并发读写：分散 / 64 条热点，1 / 4 / 8 / 16 客户端，80% 读、20% 写，每轮预热 3 秒、恢复后测量 30 秒，共三组。完整运行约 40 分钟。固定种子的每客户端请求流，完成次数随速度不同；按实际完成写入核对数据。正式结果使用默认参数，短时校验可运行 `./06_concurrency/run-concurrency.sh 0.4 1`，不用于性能结论。

不要同时运行其他实验或文档渲染来干扰计时。

## 报告与结果

只保留可编辑的 `docs/report_draft.docx`；提交前按课程要求导出 report.pdf。`WSL-results.md` 给出主要中位数、吞吐量与精确结果目录；详细计时、核对记录和执行计划留在各实验 results。

## Java 源码对应关系

- 关键词：KeywordSearch.java、SearchData.java；DbmsComparison.java 为查询与更新联合入口。
- 姓名更新：DbmsComparison.java、PeopleUpdate.java、PeopleDatabase.java。
- 索引：IndexExperiment.java。
- 关联：JoinExperiment.java、JoinData.java、JoinDatabase.java；计划诊断为 JoinDiagnostics.java。
- 并发：ConcurrencyExperiment.java、ConcurrentWorkload.java、CsvCounterStore.java、DbCounterStore.java、CounterStore.java。
- 共用工具位于 shared；EnvironmentEvidence.java 用于记录环境，可通过 `./shared/run-java.sh EnvironmentEvidence` 运行。

所有类均无 package 声明且文件名唯一，可将保留的 23 个 Java 源文件平铺提交。运行实验仍需本说明中的数据、依赖和连接配置。
