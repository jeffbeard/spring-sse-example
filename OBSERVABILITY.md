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

```bash
# Prometheus: Status > Targets should list serviceMonitor/spring-sse-example/... as UP
kubectl port-forward -n monitoring svc/monitoring-kube-prometheus-prometheus 9090:9090

# Grafana: Dashboards > "Spring SSE Example"
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
```

## Dashboard panels

Every query filters on `job="spring-sse-example"`, the job label the
ServiceMonitor produces. In the JSON this is the hidden `$job` variable.

| Panel | Query |
|---|---|
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
- There are no SSE-specific metrics yet (active connections, events sent). See
  `TODO.md`.

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
