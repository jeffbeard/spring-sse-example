# TODO

Working list for this project. Longer-range ideas and open questions live in
`FUTURE_IMPROVEMENTS.md`.

## Direction

This project is the test bed for a production-like pipeline that eventually
deploys to AWS, on EKS or ECS. The platform itself (Terraform and related
infrastructure code) will probably live in a separate project. The
infrastructure architecture is not decided yet.

## Now: remove the Minikube setup

The Argo CD lab (`deploy/`, synced from `main` to the local OrbStack cluster)
replaces it. Do this as one `chore:` PR.

Delete:

- [ ] `k8s/overlays/minikube/` (5 files)
- [ ] `scripts/deploy-minikube-kustomize.sh`

Edit:

- [ ] `.github/workflows/ci.yml`: drop `feature/minikube*` from the image-push
      branch condition on the `docker` job
- [ ] `scripts/verify-manifests.sh`: remove the two `minikube` exemptions in
      the actuator exposure checks
- [ ] `k8s/base/configmap.yaml` and `k8s/overlays/dev/configmap-patch.yaml`:
      comments that mention minikube

Docs:

- [ ] `CLAUDE.md`
- [ ] `BUILD_AND_DEPLOY.md`
- [ ] `KUBERNETES_DEPLOYMENT.md`
- [ ] `FUTURE_IMPROVEMENTS.md`
- [ ] This file: remove this section. `scripts/verify-references.sh` scans the
      root `*.md` files and fails on a path to a deleted script.

Check afterwards:

- [ ] `scripts/verify-manifests.sh` and `scripts/verify-references.sh` pass
- [ ] `grep -ri minikube .` returns nothing unexpected

## Next: decide what happens to `k8s/`

Two manifest sets now exist side by side:

- `deploy/`: plain manifests, what Argo CD actually deploys
- `k8s/base` plus the `dev` and `prod` overlays: Kustomize, with the ConfigMap,
  management port 8081, security context, resource limits and Ingress

Once Minikube is gone, nothing local exercises the `k8s/` stack. It is covered
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
