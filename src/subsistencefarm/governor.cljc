(ns subsistencefarm.governor
  "SubsistenceFarmGovernor — the independent safety/scope layer gating
  every household record-keeping/logistics proposal an advisor may
  make for a subsistence-farming household. The governor never
  dispatches hardware itself, never performs farm labor itself, and
  never finalizes a planting/harvest-timing agronomic decision or
  overrides the farmer's own judgment about their household's crops —
  those are permanently out of this actor's scope and remain the
  farmer's exclusive judgment (README's 'Robotics premise': this actor
  coordinates FARM RECORD-KEEPING/LOGISTICS ONLY — it never makes
  agronomic decisions itself). Modeled closely on
  cloud-itonami-isco-7111's housebuilder.governor, adapted for
  subsistence farming's distinct livelihood-vulnerability dimension:
  crops here are grown primarily for household consumption, so a
  crop-failure risk threatens food security directly, not just a
  commercial outcome — `:flag-livelihood-concern` always escalates to
  a human, exactly like a safety concern in the physical-hazard
  domains, and no op in the closed allowlist may ever finalize a
  planting/harvest-timing decision or override the farmer's own
  judgment about their household's crops.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. farmer provenance     — the household member must be
                                independently verified/registered
                                before any action.
    2. plot provenance       — the farm plot must be independently
                                verified/registered before any action.
    3. no-actuation           — proposal :effect must be :propose (the
                                governor never dispatches hardware and
                                never performs farm labor itself; it
                                only gates what the advisor may
                                coordinate).
    4. closed op-allowlist    — only :log-work-record,
                                :schedule-farm-operation,
                                :flag-livelihood-concern and
                                :coordinate-supply-order may ever be
                                proposed; anything else is refused.
    5. scope-excluded action  — any proposal to directly finalize a
                                planting/harvest-timing agronomic
                                decision, or to override the farmer's
                                own judgment about their household's
                                crops, is a hard, permanent block
                                (checked both against the proposed :op
                                and, defense-in-depth, against the
                                proposal's :rationale text — matched as
                                full finalization/execution ACTION
                                phrases such as \"proceed with the
                                planting decision\" / \"finalize the
                                harvest timing decision\" / \"override
                                the farmer's crop judgment\", never as
                                bare nouns like \"planting\",
                                \"harvest\" or \"crop\", so the check
                                can never self-trip on the advisor's
                                own routine rationale text, e.g.
                                \"logged work record for farmer …\" or
                                \"scheduled farm operation for planting
                                task …\" or \"…routed for human
                                review\" — all three legitimately
                                contain bare agronomic nouns but none
                                is a finalization action, and all are
                                exercised by
                                `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off
  regardless of confidence):
    6. :op :flag-livelihood-concern (a crop-failure-risk / food-security
                                risk concern always escalates to a
                                human, never auto-commits — this is the
                                subsistence-farming livelihood
                                dimension: a crop failure here
                                threatens the household's own food
                                supply, so this op can NEVER be
                                auto-commit-eligible).
    7. :op :coordinate-supply-order above `supply-cost-threshold`.
    8. low confidence (< `confidence-floor`)."
  (:require [kotoba.lang.text :as str]
            [subsistencefarm.store :as store]))

(def confidence-floor 0.6)
(def supply-cost-threshold 300)

(def allowed-ops
  #{:log-work-record :schedule-farm-operation
    :flag-livelihood-concern :coordinate-supply-order})

;; Defense-in-depth: none of these ops are ever in `allowed-ops`
;; above, so they are already refused by the closed-allowlist check
;; below; they are named again here — as explicit finalization/
;; execution ACTIONS, never bare nouns — so a future allowlist edit
;; cannot silently re-open this specific out-of-scope path without
;; also touching this list.
(def ^:private scope-excluded-ops
  #{:finalize-planting-decision :finalize-harvest-timing-decision
    :proceed-with-planting-decision :proceed-with-harvest-decision
    :override-farmer-crop-judgment})

;; Full finalization/execution ACTION phrases only — never bare nouns
;; ("planting", "harvest", "crop", "farmer") — so this can never match
;; inside the mock advisor's own default rationale text (which
;; legitimately contains those bare nouns, e.g. "planting task" /
;; "routed for human review"). See
;; `governor-test/default-mock-advisor-proposals-never-self-trip-on-scope-exclusion`.
(def ^:private scope-excluded-phrases
  ["proceed with the planting decision" "proceed with the harvest decision"
   "proceed with the harvest timing decision"
   "finalize the planting decision" "finalize the harvest timing decision"
   "override the farmer's crop judgment" "override the farmer's judgment"
   "override farmer crop judgment" "override farmer judgment"])

(defn- contains-excluded-phrase? [s]
  (let [s (str/lower (or s ""))]
    (boolean (some #(str/includes? s %) scope-excluded-phrases))))

(defn- hard-violations [proposal farmer-record plot-record]
  (let [{:keys [op rationale]} proposal]
    (cond-> []
      (nil? farmer-record)
      (conj {:rule :no-farmer
             :detail "未登録 farmer への提案は不可（farmer record は独立して検証・登録済みでなければならない）"})

      (nil? plot-record)
      (conj {:rule :no-plot
             :detail "未登録 plot への提案は不可（plot record は独立して検証・登録済みでなければならない）"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation
             :detail "effect は :propose のみ許可（governor は農作業を直接実行しない）"})

      (not (contains? allowed-ops op))
      (conj {:rule :unknown-op
             :detail (str op " は closed op-allowlist に無い — 提案不可")})

      (or (contains? scope-excluded-ops op) (contains-excluded-phrase? rationale))
      (conj {:rule :scope-excluded-action
             :detail "作付け/収穫時期の農事判断の確定・farmer 自身の作物判断の上書きは、この actor の権限外 — 常に永続ブロック"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `subsistencefarm.store/Store`. Pure — never
  mutates the store, never dispatches a farm operation."
  [request _context proposal store]
  (let [farmer-record (store/farmer store (:farmer-id request))
        plot-record (some->> (:plot-id proposal) (store/plot store))
        hard (hard-violations proposal farmer-record plot-record)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        supply-order-over-threshold?
        (and (= :coordinate-supply-order (:op proposal))
             (number? (:cost proposal))
             (> (:cost proposal) supply-cost-threshold))
        always-risky? (or (= :flag-livelihood-concern (:op proposal))
                           supply-order-over-threshold?)]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
