(ns sossity.simulator
  (:require
   [clojure.core.async :as a
    :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! timeout pub sub unsub unsub-all go-loop put!]]
   [sossity.core :as s]
   [clojure.pprint :as pp]
   [cemerick.pomegranate :as pom]
   [loom.graph :refer :all]
   [cheshire.core :refer :all]
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

(defn construct [klass & args]
  (clojure.lang.Reflector/invokeConstructor (. Class forName klass) (to-array args)))

(def this-conf (atom {}))

(defn dummy-topic
  ([x]
   :topic)
  ([] :topic))

(defn setup-jars [g sossity-config]
  "load angled-dream and all relevant jars. sossity-config is a global ref, but fuckit"
  (do (doall (map #(pom/add-classpath (val %)) (s/get-all-node-or-edge-attr g :local-jar-path)))
      (pom/add-classpath (:local-angleddream-path sossity-config))))

(defn apply-test-transform [node input]
  (assoc input :chans (conj (:chans input) node)))

(defn apply-sossity-transform [composer-class input g]
  (do (setup-jars g @this-conf)
      (let [clazz (construct "com.acacia.sdk.AbstractTransformTester" (construct composer-class))] ;loaded at run-time
        (-> (.apply clazz (generate-string input)) (decode true)))))

(defn transform-fn [g node input]
  (if-let [t (attr g node :transform-class)]
    (apply-sossity-transform t input g)
    (apply-test-transform node input)))

(defn pass-on [in-channel g node out-channels]
  "eventually this will be used to apply a fn (or java jar or py) and fanout the results to chans"
  (go-loop []
    (let [v (<! in-channel)]
      (println node ": " v)
      (doseq  [c out-channels]
        (>! c (transform-fn g node v))))
    (recur)))

(defn build-node [input-pub g node output-topics]
  "returns a seq of output publications for a node to talk to"
  (let [input (chan)
        out-chans (repeatedly (count output-topics) chan)
        out-pubs  (doall (map #(pub % (fn [x] (dummy-topic x))) out-chans))]
    (sub input-pub (dummy-topic)  input)
    (pass-on input g node out-chans)
    out-pubs))

(defn build-end-node [input-pub node]
  "simulates a sink by attaching a fn to the channel that just prints"
  (let [input (chan)]
    (sub input-pub (dummy-topic) input)
    (take-and-print input node)))

(defn build-head-node []
  "returns the channel and pub for a head node"
  (let [input (chan)]
    [input (pub input dummy-topic)]))

(defn create-outputs [g node source-pub]
  "returns graph after outputs created"
  (let [outward-edges (out-edges g node)
        out-pubs (build-node source-pub
                             g
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
        pipes (s/get-all-node-or-edge-attr a :in-chan)]
    (pp/pprint a)
    (pp/pprint pipes)
    (map #(put! (val %) {:chans []}) pipes)))

(defn test-cluster [a-graph]
  "given a config map, test a cluster"
  (do
    (swap! this-conf (fn [x] (s/config-md a-graph)))
    (let [dag (s/create-dag a-graph @this-conf)]
      (setup-jars dag @this-conf)
      (compose-cluster dag))))
