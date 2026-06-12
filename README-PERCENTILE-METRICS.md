# Percentile metrics extension

This overlay leaves the existing JSON log-ingestion path intact and adds an independent, opt-in Dynatrace Metrics API exporter.

## New Backend Listener arguments

| Argument | Default | Purpose |
| --- | --- | --- |
| `dt.metrics.enabled` | `false` | Enables percentile aggregation and Metrics API emission. When disabled, the plugin does not compute or send percentile metrics. |
| `dt.metrics.url` | `https://<env-id>.live.dynatrace.com/api/v2/metrics/ingest` | Dynatrace Metrics API endpoint. |
| `dt.metrics.api.token` | empty | Token used for Metrics API calls. When empty, the plugin falls back to `dt.api.token`. The selected token needs the `metrics.ingest` scope. |
| `dt.metrics.flush.interval.ms` | `10000` | Interval for sending aggregated percentile gauges. |
| `dt.metrics.percentiles` | `50;90;95;99` | Semicolon-separated percentiles. |
| `dt.metrics.dimensions` | empty | Optional semicolon-separated static dimensions, such as `environment=staging;team=checkout`. |

## Emitted metric example

```text
jmeter.mint.p95,transaction="HTTP Request - Submit order",injector_hostname="loadgen-01",environment="staging" gauge,218.5 1710000000000
```

The metric keys are fixed as `jmeter.mint.p<percentile>`, such as `jmeter.mint.p90` and `jmeter.mint.p95`. The exporter groups samples by JMeter sample label. This includes request samplers and Transaction Controller parent samples when JMeter passes them to the Backend Listener. It uses JMeter's `SamplerMetric#getAllPercentile` aggregation and resets interval counters with `resetForTimeInterval`, matching JMeter Backend Listener behavior.

## Token scopes

The current log path still uses `logs.ingest`. The new Metrics API path needs `metrics.ingest`. A single token may be used for both paths when both scopes are granted, or `dt.metrics.api.token` may be set separately.

## Quiet-mode documentation correction

The existing implementation still emits JSON logs in `quiet` mode; it only omits request and response details. The main README description that says quiet mode sends metrics only should be corrected separately if this overlay is merged.
