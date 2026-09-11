# cloud-itonami-isco-6310

Open Occupation Blueprint for **ISCO-08 6310**: Subsistence Crop Farmers.

This repository designs a forkable OSS business for a subsistence-farming household record-keeping and logistics coordination practice: a record-keeping and supply-coordination robot manages household labor/plot records under a governor-gated actor, so a subsistence-farming household keeps its own operating records instead of renting a closed farm-management SaaS.

**Maturity: `:implemented`.** `src/subsistencefarm/` implements the
`SubsistenceFarmActor` as a `langgraph.graph/state-graph`
(`subsistencefarm.actor`) wired to a `Subsistence Farm Advisor`
(`subsistencefarm.advisor`) and an independent `SubsistenceFarmGovernor`
(`subsistencefarm.governor`), following the itonami actor pattern
(ADR-2607121000): `:intake -> :advise -> :govern -> :decide -+-> :commit
(:ok?) +-> :request-approval (:escalate?, human-in-the-loop interrupt)
+-> :hold (:hard?)`. 23 tests / 50 assertions green (`kbb -M:test`).
HARD invariants (always hold, never overridable): farmer provenance,
plot provenance, no-actuation (`:effect` must be `:propose`), a closed
op-allowlist (`:log-work-record`, `:schedule-farm-operation`,
`:flag-livelihood-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any
proposal that would directly finalize a planting/harvest-timing
agronomic decision or override the farmer's own judgment about their
household's crops. Always-escalate paths (human sign-off regardless of
confidence, mapping this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-livelihood-concern` (always) and `:coordinate-supply-order` above
the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical/administrative domain work**. Here a record-keeping/logistics coordination robot performs household labor scheduling, planting/harvest/yield-record logging and seeds/tools supply-order coordination for a subsistence-farming household, under an actor that proposes actions and an independent **Subsistence Farm Governor** that gates them. The governor never
dispatches hardware itself, never performs farm labor itself, and never finalizes a planting/harvest-timing agronomic decision or overrides the farmer's own judgment about their household's crops; `:high`/`:livelihood-critical` actions (such as a flagged crop-failure-risk/food-security concern, or an above-threshold supply order) require human sign-off. **This actor coordinates farm record-keeping/logistics only — it never makes agronomic decisions itself.**

## Livelihood-vulnerability dimension

Subsistence crop farmers grow crops primarily for household consumption, not commercial sale. A crop-failure risk here threatens the household's own food security directly, alongside the standard agricultural physical-safety hazards other cloud-itonami agriculture verticals carry. `:flag-livelihood-concern` therefore always escalates to human sign-off, exactly like a safety concern in the physical-hazard domains — it can never be auto-commit-eligible, regardless of confidence. No op in the closed allowlist may ever finalize a planting or harvest-timing decision, or override the farmer's own judgment about their household's crops.

## Core Contract

```text
household roster + plot registration + livelihood-reporting policy
        |
        v
Subsistence Farm Advisor -> Subsistence Farm Governor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, finalize
a planting/harvest-timing agronomic decision, override the farmer's own
judgment about their household's crops, suppress an operating record, or
disclose sensitive data without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `6310`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
