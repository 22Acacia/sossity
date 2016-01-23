(ns sossity.simulator
  (:require
   [clojure.core.async :as a
    :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! timeout pub sub unsub unsub-all go-loop put!]]

   [loom.graph :refer :all]
   [loom.alg :refer :all]
   [loom.io :refer :all]
   [loom.attr :refer :all]))

;use doseq or doall to realize all the pubs?

(defn take-and-print [channel prefix]
  (go-loop []
    (println prefix " sink: " (<! channel))
    (recur)))

(defn pass-on [in-channel node out-channels]
  "eventually this will be used to apply a fn (or java jar or py) and fanout the results to chans"
  (go-loop []
    (let [v (<! in-channel)]
      (println node ": " v)
      (doseq  [c out-channels]
        #_(println "channel" c)
        (>! c (assoc v :chans (conj (:chans v) node)))))
    (recur)))

(def g1 (digraph {:a [:b :c]
                  :b [:d]
                  :d [:g :h :i]
                  :g nil
                  :h nil
                  :i nil
                  :c [:e :j]
                  :e [:f]
                  :f nil
                  :j nil}))

(def g2 (digraph {:a [:b]
                  :b [:c]
                  :c nil}))

(defn get-node-or-edge-attr [g k]
  (reduce #(let [a (attr g %2 k)]
             (if (some? a)
               (assoc %1 %2 a))) {} (nodes g)))

(defn build-node [input-pub input-topic  output-topics]
  "returns a seq of output publications for a node to talk to"
  (let [input (chan)
        out-chans (repeatedly (count output-topics) chan)
        out-pubs  (doall (map #(pub % (fn [x] :topic  #_(not= x nil))) out-chans))]
    (sub input-pub :topic #_input-topic input)
    (pass-on input input-topic out-chans)
    out-pubs))

(defn build-end-node [input-pub node]
  (let [input (chan)]
    (sub input-pub :topic input)
    (take-and-print input node)))

(defn build-head-node []
  "returns the channel and pub for a head node"

  (let [input (chan)]
    [input (pub input :topic)]))

;;do a reduce to collect the pubs?

(defn create-outputs [g node source-pub]
  "returns graph after outputs created"
  (let [outward-edges (out-edges g node)
        out-pubs (build-node source-pub
                             node
                             outward-edges)]
    (println node)
    (reduce #(add-attr %1 (key %2) :pub (val %2)) g (zipmap outward-edges out-pubs))))

(defn get-pub [g node]
  "get the pub off the node itself (if it's a source node) or its input edge (if it's a pipeline node)"
  (or
   (attr g node :pub)
   (attr g (first (in-edges g node)) :pub)))

(defn handle-graph-node [g node]
  "annotates a node and edges in the graph with channels, returns the graph"
  ;there has got to be a better way to do this than all the nested nightmare

  (let [gr  (if (= 0 (in-degree g node))
              (let [[ch p] (build-head-node)]
                (-> g
                    (add-attr node :in-chan ch)
                    (add-attr node :pub p)))
              g)]

    (println (out-degree gr node))
    (if (> (out-degree gr node) 0)

      (create-outputs gr node (get-pub gr node))

      (if (= 0 (out-degree gr node))
        (let [out-sink (build-end-node (get-pub gr node) node)]
          (add-attr gr node :sink out-sink))
        gr                                                  ;;failure, should never be reached
))))

(defn compose-cluster [g]
  "creates all the pubsubs for a graph, then returns [{:nodename, chan}] for all the head nodes (ultimately to be attached to REST endpoints)"
  "reduces over a reversed depth-first traversal so a parent is building the channels for its children"
  (let [a (reduce #(handle-graph-node %1 %2) g (reverse (post-traverse g)))
        pipes (get-node-or-edge-attr a :in-chan)]

    (println a)
    (put! (:a pipes) {:topic :topic :dest "/#home"})))

(defn test-build-node []
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

(defn pubsubtest [derp]
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
