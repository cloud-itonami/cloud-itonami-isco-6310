(ns subsistencefarm.advisor
  "Subsistence Farm Advisor — proposing a household record-keeping/
  logistics coordination operation (log a work record, schedule a
  household farm operation, flag a livelihood concern, coordinate a
  seeds/tools supply order) from a household roster, plot registration
  and livelihood-reporting policy. Swappable mock/llm; the advisor
  ONLY proposes — `subsistencefarm.governor` independently gates every
  proposal and always escalates livelihood concerns and
  above-threshold supply orders. The advisor never proposes to
  directly finalize a planting/harvest-timing agronomic decision or to
  override the farmer's own judgment about their household's crops —
  those stay permanently out of this actor's scope. Modeled closely on
  cloud-itonami-isco-7111's housebuilder.advisor.

  A proposal: {:op :log-work-record|:schedule-farm-operation|
               :flag-livelihood-concern|:coordinate-supply-order
               :effect :propose :farmer-id str :plot-id str
               :cost number :concern-type kw :task str :stake kw
               :confidence n :rationale str}"
  (:require #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as edn])))

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- rationale-for [op farmer-id plot-id concern-type]
  (case op
    :log-work-record
    (str "logged work record for farmer " farmer-id " at plot " plot-id)

    :schedule-farm-operation
    (str "scheduled farm operation for planting task at plot " plot-id)

    :flag-livelihood-concern
    (str "flagged " (name (or concern-type :concern)) " concern for farmer "
         farmer-id " at plot " plot-id " — routed for human review")

    :coordinate-supply-order
    (str "coordinated supply order for farmer " farmer-id " at plot " plot-id)

    (str "proposed " (name op) " for farmer " farmer-id " at plot " plot-id)))

(defn- infer [_store {:keys [op stake farmer-id plot-id cost concern-type task]
                       :as request}]
  {:op op
   :effect :propose
   :farmer-id farmer-id
   :plot-id plot-id
   :cost cost
   :concern-type concern-type
   :task task
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (rationale-for op farmer-id plot-id concern-type)})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a subsistence-farming household record-keeping/logistics
   coordination advisor. Given a request, propose an :op (one of
   :log-work-record, :schedule-farm-operation, :flag-livelihood-concern,
   :coordinate-supply-order), the :farmer-id, :plot-id, and any
   :cost/:concern-type/:task fields, an honest :confidence and a
   :stake. Never propose an op outside this closed list, and never
   propose to directly finalize a planting/harvest-timing agronomic
   decision, or to override the farmer's own judgment about their
   household's crops — those are always out of this actor's scope; it
   coordinates household record-keeping/logistics only and never makes
   agronomic decisions itself. Livelihood concerns (crop-failure risk,
   food-security risk) always require human sign-off regardless of
   confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (edn/read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
