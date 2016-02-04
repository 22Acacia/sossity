(ns sossity.simulator
  (:require
   [clojure.core.async :as a
    :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! timeout pub sub unsub unsub-all go-loop put!]]

   [clojure.pprint :as pp]
   [cemerick.pomegranate :as pom]
   [loom.graph :refer :all]
   [cheshire.core :refer :all]
   [loom.alg :refer :all]
   [loom.io :refer :all]
   [clj-time.core :as t]
   [digest :as d]
   [clj-uuid :as uuid]
   [loom.attr :refer :all]
   [sossity.util :as u]))

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

(def this-conf (atom {}))

(def execute-timestamp (atom ""))

(defn take-and-print [channel node]
  "just prints whatever is on channel"
  (do
    #_(clojure.java.io/make-parents (str (get-in @this-conf [:config-file :config :test-output]) "test-output/" @execute-timestamp))
    (go-loop []
      (let [out (generate-string (<! channel))
            out-file (str (get-in @this-conf [:config-file :config :test-output]) "test-output/" @execute-timestamp "/" node ".txt")]
        (if (:debug @this-conf) (println node " sink: " out))
        (clojure.java.io/make-parents out-file)
        (spit out-file (str out "\n") :append true :create true))
      (recur))))

(defn construct [klass & args]
  (clojure.lang.Reflector/invokeConstructor (. Class forName klass) (to-array args)))

(defn dummy-topic
  ([x]
   :topic)
  ([] :topic))

(defn setup-jars [g sossity-config]
  "load angled-dream and all relevant jars. sossity-config is a global ref, but fuckit"
  (doall (map #(pom/add-classpath (u/path (attr g % :local-jar-path) (attr g % :transform-jar))) (u/filter-node-attrs g :local-jar-path some?))
         #_(pom/add-classpath (:local-angleddream-path sossity-config))
         ))

(defn apply-test-transform [node input]
  (assoc input :chans (conj (:chans input) node)))

(defn apply-sossity-transform [composer-class input g]
  (do (setup-jars g @this-conf)
      (let [clazz (construct "com.acacia.sdk.AbstractTransformTester" (construct composer-class))] ;loaded at run-time
        (-> (.apply clazz (generate-string input)) (decode true)))))

(defn transform-fn [g node input]
  (if-let [t (attr g node :composer-class)]
    (apply-sossity-transform t input g)
    input
    ))

(defn pass-on [in-channel g node out-channels]
  "eventually this will be used to apply a fn (or java jar or py) and fanout the results to chans"
  (go-loop []
    (let [v (<! in-channel)]
      (if (:debug @this-conf) (println node ": " v))
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
    (if (:debug @this-conf) (println "creating outputs: " node))
    (reduce #(add-attr %1 (key %2) :pub (val %2)) g (zipmap outward-edges out-pubs))))

(defn get-pub [g node]
  "get the pub off the node itself (if it's a source node) or its input edge (if it's a pipeline node)"
  (or
   (attr g node :pub)
   (attr g (first (in-edges g node)) :pub)))

(defn handle-graph-node [g node]
  "annotates a node and edges in the graph with channels, returns the graph"
  ;there has got to be a better way to do this than all the nested nightmare

  (let [gr  (if (= :source (attr g node :exec))                    ;if a node has nothing coming in, it's a head node
              (let [[ch p] (build-head-node)]
                (-> g
                    (add-attr node :in-chan ch)
                    (add-attr node :pub p)))
              g)]

    (if (or (= :pipeline (attr g node :exec)) (= :source (attr g node :exec)))
      (create-outputs gr node (get-pub gr node))

      (if (and (= :sink (attr g node :exec)) (not= true (attr g node :error)))
        (let [out-sink (build-end-node (get-pub gr node) node)]
          (add-attr gr node :sink out-sink))
        gr                                                  ;;failure, should never be reached
))))

(defn compose-cluster [g]
  "creates all the pubsubs for a graph, then returns graph"
  "reduces over a reversed depth-first traversal so a parent is building the channels for its children"
  (let [a (reduce #(handle-graph-node %1 %2) g (reverse (post-traverse g)))]
    #_(pp/pprint a)
    a))

;    (map #(put! (val %) {:chans []}) pipes)

(defn handle-message [message]
  {:timestamp     (.toString (t/now))
   :resource_hash (d/digest "md5" (generate-string message))
   :id            (uuid/v1)
   :resource      message})

(defn rest-tester [a-graph]
  "creates a 'dev server' with endpoints at [sourcename]/endpoint, not sure what to do with results ")

