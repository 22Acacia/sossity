(ns sossity.simulator
  (:require
    [clojure.core.async :as a
     :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! timeout pub sub unsub unsub-all go-loop put! ]
     ]

    [loom.graph :refer :all]
    [loom.alg :refer :all]
    [loom.io :refer :all]
    [loom.attr :refer :all]
    )
  )


#_(defn hot-dog-machine-v2
  [hot-dog-count]
  (let [in (chan)
        out (chan)]
    (go (loop [hc hot-dog-count]
          (if (> hc 0)
            (let [input (<! in)]
              (if (= 3 input)
                 (do (>! out "hot dog")
                     (recur (dec hc)))
                 (do (>! out "wilted lettuce")
                     (recur hc))))
            (do (close! in)
                 (close! out)))))
    [in out]))


#_(def dothing (let [[in out] (hot-dog-machine-v2 2)]
               (>!! in "pocket lint")
               (println (<!! out))

               (>!! in 3)
               (println (<!! out))

               (>!! in 3)
               (println (<!! out))

               (>!! in 3)
               (<!! out)))

;use doseq or doall to realize all the pubs?

(defn take-and-print [channel prefix]
  (go-loop []
    (println prefix "sink: " (<! channel))
    (recur)))


(defn pass-on [in-channel node out-channels]
  (go-loop []
    (let [v (<! in-channel)]
      (println node ": " v)
      (doseq  [c out-channels]
              (println "channel" c)
               (>! c (assoc v :chans (conj (:chans v) node)))))
    (recur)))

(def top (digraph { "a" ["b"]}))



(defn build-node [input-pub input-topic  output-topics]
  (let [input (chan)
        out-chans (repeatedly (count output-topics) chan)
        out-pubs (doall (map #(pub % (fn [x] (:topic x))) out-chans))
        ]
    (sub input-pub input-topic input)
    (pass-on input input-topic out-chans)
    out-pubs
    ))


(defn test-build-node []
  (let
    [publisher (chan)
     publication (pub publisher #(:topic %))
     out-topics ["unitA" "unitB"]]

    (let [out-pubs (build-node publication :pipeA out-topics)]
      (doall (map #(let [sc (chan)]
                    (sub %1 :pipeA sc)
                    (take-and-print sc %2)) out-pubs out-topics)))
    (put! publisher {:topic :pipeA :dest "zzz/#home"}))
  )


(defn wat []
  (let [a (range 2)
        b (range 2 4)]
    (doseq [x a
            y b]
      (prn (str x " " y)))))


(defn doit [derp]
  (let [publisher (chan)
        publication (pub publisher #(:topic %))

        out-one (chan)
        out-pub (pub out-one #(:topic %))

        out-two (chan)
        out-two-pub (pub out-two #(:topic %))

        subscriber-one (chan)

        subscriber-two-a (chan)

        subscriber-two-b (chan)

        ]

    (sub publication :account-created subscriber-one)

    (pass-on subscriber-one "subscriber-one" [out-one out-two])

    (sub out-pub :account-created     subscriber-two-a)
    (sub out-two-pub :account-created     subscriber-two-b)


    (take-and-print subscriber-two-a "subscriber-two-a")
    (take-and-print subscriber-two-b "subscriber-two-b")

    (put! publisher {:topic :account-created :dest "/#home"})


    )
  )





;(def publisher (chan))
#_(def publication (pub publisher #(:topic %)))

;(def subscriber-one (chan))
;(def subscriber-two (chan))
;(def subscriber-three (chan))

;(sub publication :account-created subscriber-one)
;(sub publication :account-created subscriber-one)
;(sub publication :user-logged-in  subscriber-two)
;(sub publication :change-page     subscriber-three)


#_(take-and-print subscriber-one "subscriber-one")
#_(take-and-print subscriber-two "subscriber-two")
#_(take-and-print subscriber-three "subscriber-three")

