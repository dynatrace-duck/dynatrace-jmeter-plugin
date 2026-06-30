# Percentile metrics extension

This overlay leaves the existing JSON log-ingestion path intact and adds an independent Dynatrace Metrics API exporter that reuses the main Dynatrace connection settings.

## New Backend Listener arguments

| Argument | Default | Purpose |
| --- | --- | --- |
| `dt.metrics.flush.interval.ms` | `10000` | Interval for sending aggregated percentile gauges. |
| `dt.metrics.percentiles` | `50;90;95;99` | Semicolon-separated percentiles. |
| `dt.metrics.dimensions` | empty | Optional semicolon-separated static dimensions, such as `environment=staging;team=checkout`. |

The exporter uses `dt.url` as the shared base Dynatrace environment URL and `dt.api.token` as the shared token. The token must include both `logs.ingest` and `metrics.ingest` scopes.

## Emitted metric example

```text
jmeter.mint.p95,transaction="HTTP Request - Submit order",injector_hostname="loadgen-01",environment="staging" gauge,218.5 1710000000000
```

The metric keys are fixed as `jmeter.mint.p<percentile>`, such as `jmeter.mint.p90` and `jmeter.mint.p95`. The exporter groups samples by JMeter sample label. This includes request samplers and Transaction Controller parent samples when JMeter passes them to the Backend Listener. It uses JMeter's `SamplerMetric#getAllPercentile` aggregation and resets interval counters with `resetForTimeInterval`, matching JMeter Backend Listener behavior.

## Quiet-mode documentation correction

The existing implementation still emits JSON logs in `quiet` mode; it only omits request and response details. The main README description that says quiet mode sends metrics only should be corrected separately if this overlay is merged.
