# 性能实验结果目录

本目录存放每次性能实验的原始数据与汇总报告，随仓库入库以便追溯和对比。

## 目录命名规范

每次实验建一个子目录，命名为 `YYYYMMDD-HHMM-场景/`，例如：

```
results/
└── 20260828-1430-shop-api-load/
    ├── summary.csv        # JMeter 聚合报告
    ├── report/            # HTML 报告（jmeter -e -o 生成）
    └── notes.md           # 实验环境、参数与结论说明
```

## 约定

- JMeter 原始采样文件 `*.jtl` 体积较大，由 `tests/performance/.gitignore` 忽略，不入库；需要复现时重新运行 `../run-jmeter.ps1` / `../run-catalog-jmeter.ps1` 生成。
- 每次实验的 `notes.md` 至少记录：压测场景（对应 `.jmx` 文件）、并发数与持续时间、目标环境（本地 / Docker / K8s）、关键指标（吞吐量、P95、错误率）和结论。
