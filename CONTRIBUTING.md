# Contributing

`cloud-itonami-isco-6310` accepts contributions to the OSS actor, policy tests,
documentation, examples and open occupation blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for policy, audit, store or disclosure
behavior.

## Rules

- Do not commit real farmer, plot or operator data, credentials or operating
  documents.
- Keep production writes and disclosures behind Subsistence Farm Governor.
- Treat this occupation's workflows as high-risk: add tests for permission,
  scope-exclusion, livelihood-escalation and audit logging.
- Never widen the closed op-allowlist to include a planting/harvest-timing
  agronomic-decision op, or a farmer-crop-judgment-override op, without a
  dedicated ADR and explicit human review.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which policy invariant is affected
- how it was tested
- whether operator or certification docs need updates
