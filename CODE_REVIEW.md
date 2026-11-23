# Code Review, 2026-11-22

A full review of the repository on 2026-11-22. Every finding was checked against
the code and then moved to a GitHub issue. The issues are the record from here
on; this file is only an index of what the review produced and what it got wrong.

## Findings and where they are tracked

| Finding | Issue | Severity |
|---|---|---|
| Broadcast waits a full send timeout per queued client when the pool is stuck | #64 | medium |
| `trigger-event` double-encodes JSON string bodies; no blank or length check | #65 | medium |
| `deploy/app.yaml` has no security context or resource limits | #63 | medium |
| CI does not validate `deploy/` | #62 | medium |
| `SseProperties` accepts zero or negative timeouts | #66 | low |
| `createEventStream`: `connected` ordering, unreachable catch, plain-string payload | #67 | low |
| `SseIntegrationTest`: premature connect signal, leaked connections, weak assertion | #68 | low |
| Unit tests: dead test, unused Mockito scaffolding, missing shutdown | #69 | low |
| CI: coverage threshold not enforced, `verify-references.sh` not run, Jib runs twice | #70 | low |
| Test client status and error handling; README actuator port; `/api` mapping | #71 | low |
| Collapse `deploy/` and `k8s/` into one deployment configuration tree | #61 | n/a |

## Corrections to the original review

- **Broadcast stall (#64)** was rated high with a worst case of 83 minutes. That
  assumes the pool threads stay blocked forever. Events are about 100 bytes and
  emitters time out after 5 minutes, so it takes non-reading clients plus an
  oversized custom event to get there. Rated medium.
- **"Incomplete failure cleanup" in `createEventStream`** described a path that
  cannot run: before the controller returns, `SseEmitter.send()` only buffers.
  Kept in #67 as dead code to remove, not as a bug.
- **A `503` leaving the test client showing "Connected"** is wrong. `EventSource`
  does not retry on a `503`; it closes, and the page already handles that. Only
  the reconnect after the 5-minute timeout shows a stale status.
- **`k8s/` as "legacy Minikube scaffolding to delete"** was the reviewer's
  assumption, not a decision. The Minikube overlay is already gone; what remains
  are the EKS overlays. They stay as they are. See below.

## Deployment configuration

- `deploy/` is the only configuration that is deployed (Argo CD, local lab).
- `k8s/` holds EKS overlays that have never been deployed. They, and the open
  issues about them (#14, #19, #23, #24), are educated guesses until an AWS
  target exists and the manifests are built, deployed, tested and iterated on.
- The intended end state is one tree for all deployment configuration (#61).
- Leftovers of the removed Minikube setup are tracked in #72.
