(ns sossity.simulator
  (:require
   [clojure.core.async :as a
    :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! timeout pub sub unsub unsub-all go-loop put!]]

   [loom.graph :refer :all]
   [loom.alg :refer :all]
   [loom.io :refer :all]
   [loom.attr :refer :all]))

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

(defn take-and-print [channel prefix]
  "just prints whatever is on channel"
  (go-loop []
    (println prefix " sink: " (<! channel))
    (recur)))

(defn pass-on [in-channel node out-channels]
  "eventually this will be used to apply a fn (or java jar or py) and fanout the results to chans"
  (go-loop []
    (let [v (<! in-channel)]
      (println node ": " v)
      (doseq  [c out-channels]
        (>! c (assoc v :chans (conj (:chans v) node)))))
    (recur)))

(defn get-node-or-edge-attr [g k]
  "return an attribute from all nodes"
  (reduce #(let [a (attr g %2 k)]
             (if (some? a)
               (assoc %1 %2 a))) {} (nodes g)))

(defn build-node [input-pub node output-topics]
  "returns a seq of output publications for a node to talk to"
  (let [input (chan)
        out-chans (repeatedly (count output-topics) chan)
        out-pubs  (doall (map #(pub % (fn [x] :topic  #_(not= x nil))) out-chans))]
    (sub input-pub :topic  input)
    (pass-on input node out-chans)
    out-pubs))

(defn build-end-node [input-pub node]
  "simulates a sink by attaching a fn to the channel that just prints"
  (let [input (chan)]
    (sub input-pub :topic input)
    (take-and-print input node)))

(defn build-head-node []
  "returns the channel and pub for a head node"
  (let [input (chan)]
    [input (pub input :topic)]))

(defn create-outputs [g node source-pub]
  "returns graph after outputs created"
  (let [outward-edges (out-edges g node)
        out-pubs (build-node source-pub
                             node
                             outward-edges)]
    (println "creating outputs: " node)
    (reduce #(add-attr %1 (key %2) :pub (val %2)) g (zipmap outward-edges out-pubs))))

(defn get-pub [g node]
  "get the pub off the node itself (if it's a source node) or its input edge (if it's a pipeline node)"
  (or
   (attr g node :pub)
   (attr g (first (in-edges g node)) :pub)))

(defn handle-graph-node [g node]
  "annotates a node and edges in the graph with channels, returns the graph"
  ;there has got to be a better way to do this than all the nested nightmare

  (let [gr  (if (= 0 (in-degree g node))                    ;if a node has nothing coming in, it's a head node
              (let [[ch p] (build-head-node)]
                (-> g
                    (add-attr node :in-chan ch)
                    (add-attr node :pub p)))
              g)]

    (println (str "out-deg: " node (out-degree gr node)))
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

