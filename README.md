# JMeter Dynatrace Backend Listener

## Overview
### Description
JMeter Dynatrace Backend Listener is a JMeter plugin that sends test results to Dynatrace in two complementary ways:

1. **Log Ingest** – every JMeter sample result is forwarded as a structured JSON log event via the [Log Ingest v2 API](https://www.dynatrace.com/support/help/dynatrace-api/environment-api/log-monitoring/ingest-logs). All standard SampleResult fields are automatically parsed by Dynatrace on ingest, making them immediately available for dashboarding, alerting, and Davis AI analysis.
2. **Metrics Ingest** – response-time percentile gauges (e.g. P50, P90, P95, P99) are periodically pushed to the [Metrics API v2](https://www.dynatrace.com/support/help/dynatrace-api/environment-api/metric-v2/post-ingest-metrics) as `jmeter.mint.p<N>` gauge metrics, one per sampler per configured percentile. This path runs concurrently with log ingest and will never interrupt or block log delivery.

Both paths are **always active** — metric collection is not user-configurable and cannot be disabled.

### Features

* Sends JMeter sample results as structured JSON log events to Dynatrace
  * All standard SampleResult fields are automatically parsed by Dynatrace on ingest
* Periodic response-time percentile metric export (always-on)
  * Configurable percentiles, flush interval, and custom static dimensions
* Single API Token authentication — one token covers both Log Ingest and Metrics Ingest
* Batched requests with automatic payload splitting
  * Log requests are capped at 4 MB / 9,000 records to stay within Dynatrace Log Ingest v2 limits
  * Metrics requests are capped at ~900 KB to stay within Dynatrace Metrics API limits
* Sample filters
  * Only send the samples you want using `dt.sample.filter`: `filter1;filter2;filter3`
  * Exclude specific samplers: `!!exclude_this;filter1;filter2`
* Field filters
  * Restrict the fields sent to Dynatrace using `dt.fields`: `field1;field2;field3`
  * Available fields:
    * AllThreads, BodySize, Bytes, SentBytes, ConnectTime, ContentType
    * DataType, ErrorCount, GrpThreads, IdleTime, Latency, ResponseTime
    * SampleCount, SampleLabel, ThreadName, URL, ResponseCode
    * TestStartTime, SampleStartTime, SampleEndTime, Timestamp
    * InjectorHostname, ElapsedTime, ElapsedDuration
    * AssertionResults, FailureMessage, Success *(when assertions are present)*
    * RequestHeaders, RequestBody, ResponseHeaders, ResponseBody, ResponseMessage *(mode-dependent)*
* Four logging modes
  * __debug__ – sends request/response details (headers, body) for every sample
  * __info__ – sends all samples, but includes request/response details only for failed samples *(recommended for most environments)*
  * __error__ – only forwards failed samples (along with their request/response details)
  * __quiet__ – sends metrics only; never includes request/response details

### API Token

A **single Dynatrace API token** is required. The token must have the following scopes:

| Scope | Purpose |
|---|---|
| `logs.ingest` | Send JMeter sample results as log events |
| `metrics.ingest` | Send response-time percentile gauges |

Both scopes must be granted on the same token. Separate tokens are not supported.

### Configuration Parameters

#### Log Ingest

| Parameter | Default | Description |
|---|---|---|
| `dt.url` | `https://<env-id>.live.dynatrace.com` | Base Dynatrace environment URL; the `/api/v2/logs/ingest` path is appended automatically |
| `dt.api.token` | *(empty)* | Dynatrace API token with `logs.ingest` **and** `metrics.ingest` scopes |
| `dt.timestamp` | `yyyy-MM-dd'T'HH:mm:ss.SSSZZ` | Format for `SampleStartTime` / `SampleEndTime` attributes |
| `dt.batch.size` | `100` | Number of samples to accumulate before sending a batch |
| `dt.timeout.ms` | `10000` | Socket timeout in milliseconds |
| `dt.sample.filter` | *(empty)* | Semicolon-separated list of sample label filters |
| `dt.fields` | *(empty)* | Semicolon-separated list of fields to include (empty = all) |
| `dt.test.mode` | `info` | Logging mode: `debug`, `info`, `error`, or `quiet` |
| `dt.parse.all.req.headers` | `false` | Expand all request headers as individual log attributes |
| `dt.parse.all.res.headers` | `false` | Expand all response headers as individual log attributes |

#### Metrics Ingest (always-on)

| Parameter | Default | Description |
|---|---|---|
| `dt.metrics.url` | `https://<env-id>.live.dynatrace.com` | Base Dynatrace environment URL; the `/api/v2/metrics/ingest` path is appended automatically |
| `dt.metrics.flush.interval.ms` | `10000` | How often (ms) to push accumulated percentile gauges |
| `dt.metrics.percentiles` | `50;90;95;99` | Semicolon-separated list of percentiles to compute and export |
| `dt.metrics.dimensions` | *(empty)* | Semicolon-separated `key=value` pairs added as static dimensions to every metric line |

The same `dt.api.token` value is used for both the Log Ingest and Metrics Ingest paths.

Every log event sent to Dynatrace always contains a `timestamp` (ISO 8601 sample time) and a `content` (the sample label) field in addition to the fields listed above.

### Listener class name
```
io.github.delirius325.jmeter.backendlistener.dynatrace.DynatraceBackendClient
```

### Maven
```xml
<dependency>
  <groupId>io.github.delirius325</groupId>
  <artifactId>jmeter.backendlistener.dynatrace</artifactId>
  <version>3.0.0</version>
</dependency>
```

## Contributing
Feel free to contribute by branching and making pull requests, or simply by suggesting ideas through the "Issues" tab.

### Packaging and testing your newly added code
Execute the command below. Make sure `JAVA_HOME` is set properly.
```
mvn package
```
Move the resulting JAR to your `JMETER_HOME/lib/ext`.

