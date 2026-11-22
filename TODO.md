# TODO

Working list for this project. Longer-range ideas and open questions live in
`FUTURE_IMPROVEMENTS.md`.

## Direction

This project is the test bed for a production-like pipeline that eventually
deploys to AWS, on EKS or ECS. The platform itself (Terraform and related
infrastructure code) will probably live in a separate project. The
infrastructure architecture is not decided yet.

## Next: decide what happens to `k8s/`

Two manifest sets now exist side by side:

- `deploy/`: plain manifests, what Argo CD actually deploys
- `k8s/base` plus the `dev` and `prod` overlays: Kustomize, with the ConfigMap,
  management port 8081, security context, resource limits and Ingress

With the Minikube overlay removed, nothing local exercises the `k8s/` stack. It is covered
only by the CI render checks, and the EKS overlays have never been deployed.

- [ ] Choose one: point Argo CD at a Kustomize overlay, fold the useful parts
      of `k8s/` into `deploy/`, or drop `k8s/` until the AWS target is chosen
- [ ] Carry over whatever hardening is worth keeping (non-root user, read-only
      filesystem, resource limits, separate management port)
- [ ] Reconcile `FUTURE_IMPROVEMENTS.md` with this file. Its first item, Argo CD
      integration, is now partly done.

## Argo CD lab follow-ups

- [x] Confirm Prometheus is scraping: the `spring-sse-example` target shows as
      up under Status > Targets (checked 2026-11-22)
- [ ] The image is built locally and referenced by a fixed tag with
      `imagePullPolicy: IfNotPresent`. Rebuilding under the same tag changes
      nothing in git, so Argo CD does not redeploy; it needs a manual
      `kubectl rollout restart`. Work out a better image flow (registry, unique
      tags, image updater).
- [ ] Remove stale local images: `com.example/spring-sse-example:1.0.1` and
      `spring-sse-example:1.0.1`
- [ ] Delete the hand-built Grafana dashboard once the provisioned
      "Spring SSE Example" one shows up, or export it over
      `deploy/dashboards/spring-sse-example.json` if its layout is better
      (see `OBSERVABILITY.md`)
- [x] Add SSE-specific metrics in `EventService` (active connections gauge,
      events-sent counter) and panels for them (2026-11-22)
- [ ] Add a CI step that renders `deploy/` with `kubectl kustomize` and checks
      the dashboard JSON parses. Nothing validates `deploy/` in CI today.

## Later: AWS pipeline

- [ ] Dig into common patterns before designing anything: how production
      pipelines are usually laid out for this kind of service. Areas to cover:
      - app repo versus platform repo versus GitOps config repo
      - image promotion across environments (tags versus digests)
      - EKS versus ECS for a small, long-lived-connection service like SSE
      - GitOps on EKS (Argo CD) versus the ECS deployment model
      - Terraform layout, state and environment separation
      - secrets and CI-to-AWS authentication (OIDC rather than stored keys)
      - load balancer behaviour with SSE (idle timeouts, connection draining)
- [ ] Decide EKS or ECS
- [ ] Sketch the infrastructure architecture
- [ ] Create the separate platform project for Terraform
- [ ] Revisit the parked AWS-gated issues: #13 (ingress half), #14, #23, #24
