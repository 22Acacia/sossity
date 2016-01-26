(ns sossity.simulator-test
  (:require
   [clojure.core.async :as a
    :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! timeout pub sub unsub unsub-all go-loop put!]]

   [loom.graph :refer :all]
   [loom.alg :refer :all]
   [loom.io :refer :all]
   [loom.attr :refer :all]))

#_(defn test-build-node []
    (let
     [publisher (chan)
      publication (pub publisher #(:topic))
      out-topics ["unitA" "unitB"]]

      (let [out-pubs (build-node publication :pipeA out-topics)]
        (doall (map #(let [sc (chan)]
                       (sub %1 :pipeA sc)
                       (take-and-print sc %2)) out-pubs out-topics)))
      (put! publisher {:topic :pipeA :dest "zzz/#home"})))

#_(defn test-build-node-b []
    (let
     [publisher (chan)
      publication (pub publisher #(:topic %))
      out-nodes ["unitA" "unitB"]]
      (let [out-pubs (build-node publication :pipeA out-nodes)]
        (doseq [p out-pubs]
          (build-end-node p :pipeA)))
      (put! publisher {:topic :pipeA :dest "zzz/#home"})))

#_(defn pubsubtest [derp]
    (let [publisher (chan)
          publication (pub publisher #(:topic %))

          out-one (chan)
          out-pub (pub out-one #(:topic %))

          out-two (chan)
          out-two-pub (pub out-two #(:topic %))

          subscriber-one (chan)

          subscriber-two-a (chan)

          subscriber-two-b (chan)] (sub out-pub :account-created     subscriber-two-a)
         (sub out-two-pub :account-created     subscriber-two-b)

         (take-and-print subscriber-two-b "subscriber-two-b")
         (take-and-print subscriber-two-a "subscriber-two-a")

         (sub publication :account-created subscriber-one)

         (pass-on subscriber-one "subscriber-one" [out-one out-two]) (put! publisher {:topic :account-created :dest "/#home"})))
