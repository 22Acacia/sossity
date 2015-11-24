(ns sossity.core
  (:require [clojure.test :refer :all]
            [sossity.core :refer :all]
            [loom.graph :refer :all]
            [loom.alg :refer :all]
            [loom.io :refer :all]
            [loom.attr :refer :all]
            [cheshire.core :refer :all]
            [clojure.tools.cli :refer [parse-opts]]
            [traversy.lens :as t :refer :all :exclude [view update combine]])
  (:gen-class))

(def sr-prefix "google_container_replica_controller")
(def df-prefix "google_dataflow")
(def pl-prefix "google_pubsub")
(def container-node-config {"node_config" {"oauth_scopes" ["https://www.googleapis.com/auth/compute"
                                                           "https://www.googleapis.com/auth/devstorage.read_only"
                                                           "https://www.googleapis.com/auth/logging.write"
                                                           "https://www.googleapis.com/auth/monitoring"
                                                           "https://www.googleapis.com/auth/cloud-platform"]}})

(defn build-items [g items]
  (reduce #(add-attr %1 (key items) (key %2) (val %2)) g (val items)))

(defn build-annot [g item-set]
  (reduce #(build-items %1 %2) g item-set))

;tag sources and sinks

;NEED ERROR CHECKING - make sure all sources and sinks are in execution graph, etc
;CASE-SENSITIVE

(defn topic-name [topic] (str topic "_in"))
(defn source-topic-name [topic] (str topic "_out"))
(defn error-topic-name [topic] (str topic "_err"))

(defn topic
  [node project]
  (str "projects/" project "/topics/" node))

(defn non-source-pipes
  [node]
  ((juxt #(topic-name %) #(error-topic-name %)) node))

(defn source-pipes
  [node]
  ((juxt #(source-topic-name %) #(error-topic-name %)) node))

(defn create-pubsubs
  [g]                                                 ;out and error for all sources and pipelines, just error for sinks. nodes with cardinality? of 1 have out/error, 0 have error
  (let [t (bf-traverse g)
        connected (filter #(> (in-degree g %1) 0) t)
        ends (filter #(= (in-degree g %1) 0)  t)]
    (into [] (flatten [(map #(non-source-pipes %) connected)
                       (map #(source-pipes %) ends)]))))

(defn non-sink-successors
  [g node]
  (filter #(> (out-degree g %1) 0) (successors g node)))

(defn create-sources-with-dependencies
  [g])

(defn create-sinks-with-dependencies
  [g])

#_(defn dataflow-only-node?
    [t a-graph]
    (let [df-source-sinks (set (-> a-graph (t/view (*>
                                                    (+> (in [:sinks]) (in [:sources]))
                                                    each
                                                    (conditionally #(= (:type %) "cdf"))
                                                    (in [:package])))))]
      (some? (some (set [t]) df-source-sinks))))

#_(defn get-submembers-keys
    "good to know if something is a source or sink"
    [a-graph k]
    (let [df-source-sinks (set (-> a-graph (t/view (*>
                                                    (+> (in [k]))
                                                    each
                                                    all-keys))))]
      df-source-sinks))

(defn predecessor-depends
  "Determine which predecessors of a node are sources vs. normal jobs"
  [g node a-graph]
  (let [preds (set (predecessors g node))
        sources (filter preds (set (-> (:sources a-graph) (t/view all-keys))))
        jobs (filter preds (set (-> (:pipelines a-graph) (t/view all-keys))))
        source-names (mapv #(str sr-prefix "." %) sources)
        job-names (mapv #(str df-prefix "." %)  jobs)]
    (conj source-names job-names)))

(defn create-source-container
  "Creates a rest endpont and a single pubsub -- the only time we restrict to a single output"
  [item a-graph]
  (let [node (key item)
        post_route (str "/" node "/post")
        health_route (str "/" node "/health")
        item_name (str node "-producer")
        docker_image "gcr.io/hx-test/prod-test"
        external_port "8080"
        stream_name (topic (source-topic-name node) (get-in a-graph [:provider :project]))
        container_name "${google_container_cluster.hx_fstack_cluster.name}"
        zone (get-in a-graph [:opts :zone])
        output {:name item_name :docker_image docker_image :external_port external_port :container_name container_name :zone zone :optional_args {:post_route post_route :health_route health_route :stream_name stream_name}}]
    output))

(defn create-dataflow-item                                  ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-graph?
  [g node a-graph]
  ;remember to generate dependency on edges like in example ... depth-first?
  (if (or (= nil (attr g node :type)) (= "cdf" (attr g node :type)))
    (let [project (get-in a-graph [:provider :project])
          output-topics (map #(topic-name %) (successors g node))
          input-topic (topic-name node)
          error-topic (error-topic-name node)
          ancestor-depends (predecessor-depends g node a-graph)
          name node
          class "com.acacia.dataflow.Main"      ;need to make tshis a smart default
          output-depends (map #(str pl-prefix "." %) output-topics)
          input-depends (str pl-prefix "." input-topic)
          depends-on (flatten [(flatten [output-depends ancestor-depends]) error-topic input-depends])
          cli-map (dissoc (:opts a-graph) :classpaths)
          classpath (clojure.string/join (interpose ":" (get-in a-graph [:opts :classpaths]))) ;classpath has only one dash!
          opt-map {:project project :pubsubTopic (topic input-topic project) :pipelineName name :outputTopics
                   (clojure.string/join (interpose "," (map #(topic % project) output-topics)))}
          optional-args (merge cli-map opt-map)]
      (-> (assoc-in {} ["resource" "google_dataflow" name "name"] name)
          (update-in ["resource" "google_dataflow" name] merge {"name" name "classpath" classpath "class" class "depends_on"
                                                                depends-on "project" project "optional_args" optional-args})))))

(defn create-dataflow-jobs [g a-graph]
  (let [t (bf-traverse g)                                   ;filter out anything in soruces or sinks without type cdf
        jobs (filter (comp not nil?) (map #(create-dataflow-item g % a-graph) t))]
    jobs))

(defn output-provider
  [provider-map]
  {:provider {:google (assoc (:provider provider-map) :region (get-in provider-map [:opts :zone]))}})

(defn output-pubsub
  [pubsub-map]
  (map
   #(assoc-in {} ["resource" "google_pubsub_topic" % "name"] %)
   pubsub-map))

(defn output-producer [producer-map]
  {:resource {:google_container_replica_controller {(clojure.string/replace (:name producer-map) "-" "_") producer-map}}})

(defn create-producers [a-graph]
  (map #(output-producer (create-source-container % a-graph)) (:sources a-graph)))

(defn create-dag
  [a-graph]
  (let [g (digraph (into {} (map (juxt :origin :targets) (:edges a-graph))))]
    ;decorate nodes
    (-> (build-annot g (:pipelines a-graph))
        (build-annot  (:sources a-graph))
        (build-annot  (:sinks a-graph))
        #_(add-attr-to-nodes :type :source (get-submembers-keys a-graph :sources))
        #_(add-attr-to-nodes :type :sink (get-submembers-keys a-graph :sinks))))                                    ;return the graph?
)

(defn create-terraform-json
  [a-graph]
  (let [g (create-dag a-graph)
        provider (output-provider a-graph)
        pubsubs (output-pubsub (create-pubsubs g))
        dataflows (create-dataflow-jobs g a-graph)
        producers (create-producers a-graph)
        combined (concat (flatten [provider producers pubsubs dataflows]))
        out (clojure.string/trim (generate-string [combined] {:pretty true}))]
    (subs out 1 (- (count out) 2))))                        ;trim first [ and last ] from json


(defn output-terraform-file
  [a-graph file]
  (spit file (create-terraform-json a-graph) :create true :append false :truncate true))                          ;NOTE -- need to remove first [ and last ]


(defn read-and-create
  [input output]
  (output-terraform-file (read (slurp input)) output))

(def cli-options
  [["-c" "--config CONFIG" "path to .clj config file for pipeline"]
   ["-o" "--output OUTPUT" "path to output terraform file"]])

(defn -main
  "Entry Point"
  [& args]
  (let [opts (clojure.tools.cli/parse-opts args cli-options)]
    (read-and-create (:config opts) (:output opts))))