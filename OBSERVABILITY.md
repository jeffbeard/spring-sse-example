# Observability

How metrics get from the application into Prometheus and Grafana in the Argo CD
lab, and how the Grafana dashboard is kept in git.

## How it fits together

| Piece | Where | What it does |
|---|---|---|
| Metrics endpoint | `micrometer-registry-prometheus` in `build.gradle` | Serves `/actuator/prometheus` on port 8080 |
| Exposure | `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` in `deploy/app.yaml` | Exposes `health` and `prometheus` in the lab only |
| Scrape config | `deploy/servicemonitor.yaml` | Tells Prometheus to scrape the Service's `http` port every 30s |
| Dashboard | `deploy/dashboards/spring-sse-example.json` | The Grafana dashboard, as exported JSON |
| Packaging | `deploy/kustomization.yaml` | Wraps the JSON in a ConfigMap labelled `grafana_dashboard: "1"` |

Argo CD syncs `deploy/` from `main`. Grafana's dashboard sidecar watches every
namespace for ConfigMaps with the `grafana_dashboard` label and loads them, so a
merged change to the JSON reaches Grafana with no manual step.

The `k8s/` overlays expose `health` only (issue #13) and are not scraped.

Requirements on the cluster: the Prometheus Operator CRDs and a Grafana with the
dashboard sidecar enabled. The `kube-prometheus-stack` Helm chart provides both.

## Looking at it

The lab cluster runs on OrbStack, which makes Kubernetes Service DNS names
resolvable from the Mac itself. Open these straight in a browser, with no
port-forward:

| What | URL |
|---|---|
| Application | http://spring-sse-example.spring-sse-example.svc.cluster.local/ |
| Grafana | http://monitoring-grafana.monitoring.svc.cluster.local/ |
| Prometheus | http://monitoring-kube-prometheus-prometheus.monitoring.svc.cluster.local:9090/ |
| Argo CD | https://argocd-server.argocd.svc.cluster.local/applications |

The pattern is `<service>.<namespace>.svc.cluster.local`, plus the Service port
when it is not 80 or 443. Argo CD serves a self-signed certificate, so the
browser warns once.

Where to look:

- Prometheus: Status > Targets should list
  `serviceMonitor/spring-sse-example/...` as UP.
- Grafana: Dashboards > "Spring SSE Example".
- Argo CD: the `spring-sse-example` application should be Synced and Healthy.

On a cluster without that host-level DNS (kind, EKS), port-forward
instead:

```bash
kubectl port-forward -n spring-sse-example svc/spring-sse-example 8080:80
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
kubectl port-forward -n argocd svc/argocd-server 8443:443
```

## Dashboard panels

Every query filters on `job="spring-sse-example"`, the job label the
ServiceMonitor produces. In the JSON this is the hidden `$job` variable.

| Panel | Query |
|---|---|
| Active SSE connections | `sum(sse_connections_active{job="spring-sse-example"})` and `sum(sse_connections_max{job="spring-sse-example"})` |
| SSE events sent by type | `sum by (event) (rate(sse_events_sent_total{job="spring-sse-example"}[5m]))` |
| SSE connection churn | `rate(...[5m])` of `sse_connections_opened_total`, `sse_connections_rejected_total` and `sse_connections_dropped_total` |
| Up | `up{job="spring-sse-example"}` |
| CPU usage | `process_cpu_usage{job="spring-sse-example"}` |
| Heap used | `sum by (pod) (jvm_memory_used_bytes{job="spring-sse-example",area="heap"})` |
| Live threads | `jvm_threads_live_threads{job="spring-sse-example"}` |
| Request rate by URI | `sum by (uri) (rate(http_server_requests_seconds_count{job="spring-sse-example"}[5m]))` |
| Average request latency | `sum(rate(http_server_requests_seconds_sum{job="spring-sse-example"}[5m])) / sum(rate(http_server_requests_seconds_count{job="spring-sse-example"}[5m]))` |
| 5xx rate | `sum(rate(http_server_requests_seconds_count{job="spring-sse-example",status=~"5.."}[5m]))` |
| GC pause time | `sum by (action) (rate(jvm_gc_pause_seconds_sum{job="spring-sse-example"}[5m]))` |

Things to know when reading them:

- An SSE stream is recorded in `http_server_requests_*` only when it closes, and
  its duration is the whole life of the connection. Open streams are invisible
  in the request rate, and long ones pull the average latency up.
- The 5xx panel is empty until the app has returned at least one 5xx.
- The SSE panels are the ones that describe open streams; see the next section.

## SSE metrics

`EventService` registers these with Micrometer. Prometheus names replace dots
with underscores and add `_total` to counters.

| Metric | Type | Meaning |
|---|---|---|
| `sse_connections_active` | gauge | Streams open right now |
| `sse_connections_max` | gauge | Configured cap (`app.sse.max-connections`) |
| `sse_connections_opened_total` | counter | Streams admitted |
| `sse_connections_rejected_total` | counter | Streams refused because the cap was reached |
| `sse_connections_dropped_total` | counter | Streams removed after a failed or timed-out send |
| `sse_events_sent_total{event=...}` | counter | Deliveries, one per connection that received the event |

The `event` label takes the event name: `connected`, `heartbeat`, `notification`
or `custom`. Each distinct value is a separate time series, so it must stay a
small fixed set and never carry client-supplied text.

A client that disconnects is noticed on the next send to it, so
`sse_connections_active` can lag a disconnect by up to one notification
interval (15 seconds).

## Changing the dashboard

Git is the source of truth. A dashboard loaded by the sidecar is provisioned, so
edits made in the Grafana UI are not kept: Grafana either refuses to save them
or the next sync overwrites them.

1. Edit the dashboard in the Grafana UI to try the change out.
2. Export it: Share > Export > Export as JSON. Leave "Export for sharing
   externally" off, so the `datasource` variable is kept as it is.
3. Save the result over `deploy/dashboards/spring-sse-example.json`.
4. Check that `uid` is still `spring-sse-example`. The uid is what makes Grafana
   update the existing dashboard instead of creating a second one.
5. Render it locally, then open a PR:
   ```bash
   kubectl kustomize deploy | kubectl apply --dry-run=server -n spring-sse-example -f -
   ```

To add another dashboard, put its JSON in `deploy/dashboards/` and add it to the
`files` list in `deploy/kustomization.yaml`. Give it its own `uid`.

## Other environments

The dashboard JSON has no cluster-specific values. The data source is a
variable that resolves to whichever Prometheus data source Grafana has, and the
ConfigMap carries no namespace. Any environment whose Grafana runs the dashboard
sidecar can load the same file by including it in that environment's
Kustomize build, so one file in git keeps every environment's dashboard the
same.
