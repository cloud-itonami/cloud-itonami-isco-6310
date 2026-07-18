(ns subsistencefarm.store
  "SSoT for the ISCO-08 6310 subsistence-crop-farming household
  record-keeping/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a record-keeping/logistics coordination robot performs
  household labor scheduling, planting/harvest/yield-record logging
  and seeds/tools supply-order coordination for a subsistence-farming
  household under this advisor/governor pair, which never dispatches
  hardware itself, never performs farm labor itself, and never
  finalizes a planting/harvest-timing agronomic decision or overrides
  the farmer's own judgment about their household's crops — those
  remain the farmer's exclusive judgment). Modeled closely on
  cloud-itonami-isco-7111's housebuilder.store, adapted for subsistence
  farming's distinct livelihood-vulnerability dimension: unlike a
  commercial job site, a crop-failure here threatens the household's
  own food security directly, so a dedicated `:flag-livelihood-concern`
  op (always escalated) sits alongside the routine record/schedule/
  supply ops.

  Domain:

    farmer  — a registered subsistence-farming household member
              (:farmer-id, :name)
    plot    — a registered household farm plot {:plot-id :name
              :max-supply-cost number}. `:max-supply-cost` is an
              informational registered ceiling used only to decide
              whether a `:coordinate-supply-order` proposal escalates
              to human sign-off (the governor never blocks a
              within-threshold order outright; it only decides
              commit vs. escalate).
    record  — a committed operating record (a logged
              planting/harvest/yield entry, a scheduled household farm
              operation, a flagged livelihood concern, or a coordinated
              supply order) — written ONLY via commit-record!.
    ledger  — append-only audit trail, commit or hold.")

(defprotocol Store
  (farmer [s farmer-id])
  (plot [s plot-id])
  (records-of [s farmer-id])
  (ledger [s])
  (register-farmer! [s farmer])
  (register-plot! [s plot])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (farmer [_ farmer-id] (get-in @a [:farmers farmer-id]))
  (plot [_ plot-id] (get-in @a [:plots plot-id]))
  (records-of [_ farmer-id] (filter #(= farmer-id (:farmer-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-farmer! [s f]
    (swap! a assoc-in [:farmers (:farmer-id f)] f) s)
  (register-plot! [s p]
    (swap! a assoc-in [:plots (:plot-id p)] p) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:farmers {} :plots {} :records [] :ledger []}
                                    seed)))))
