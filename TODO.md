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
- [ ] Create the separate platform project (working name `k8s-lab-infra`,
      from `~/projects/k8s-lab`). Scope:
      - build a cluster: first the OrbStack lab, so it can be torn down and
        rebuilt at will; AWS or another target later
      - bootstrap the default services on it (Argo CD, Prometheus, Grafana)
      - do both repeatably across environments

      What `~/projects/k8s-lab` holds today (inventory taken 2026-11-22):
      - a walkthrough document, written as a generic template around a
        placeholder app, not a record of what was run
      - `monitoring-values.yaml`: Helm values for `kube-prometheus-stack`
      - `myapp-application.yaml`: the Argo CD `Application` for this
        repository, despite the filename

      Its sample Deployment, Service and ServiceMonitor are superseded by
      `deploy/` here. Everything else belongs in the platform project.

      Gaps to close there before a rebuild is repeatable:
      - it is not a git repository
      - the Grafana admin password is in plain text in the values file; move
        it out before the folder goes into git
      - nothing is pinned. Running versions to pin: `kube-prometheus-stack`
        93.7.0, Argo CD v3.6.2, Kubernetes v1.36.5 on OrbStack 2.2.5
      - no script; the rebuild is copy and paste from the document
      - set the Grafana sidecar label and namespace search explicitly in the
        values file, since this repository's dashboard depends on them
      - Prometheus and Grafana use ephemeral storage, so metrics history and
        UI-made dashboards are lost on teardown
      - teardown leaves the Prometheus Operator CRDs behind unless the whole
        cluster is deleted
      - rename `myapp-application.yaml` and retire the placeholder app steps
- [ ] Decide where the Argo CD `Application` for this repository should live.
      Today it is a loose file in `~/projects/k8s-lab` (a copy is shown in
      `BUILD_AND_DEPLOY.md`). The usual home is the platform project, as one
      entry in an app-of-apps or ApplicationSet.
- [ ] Rebuild dependency on this repository: the lab pod fails to pull unless
      `./gradlew jibDockerBuild` has produced the tag in `deploy/app.yaml`
      before Argo CD syncs. Either document that as a bootstrap step in the
      platform project or move the lab to a registry.
- [ ] Revisit the parked AWS-gated issues: #13 (ingress half), #14, #23, #24
