(ns sossity.core
  (:require
   [loom.graph :refer :all]
   [loom.alg :refer :all]
   [loom.io :refer :all]
   [loom.attr :refer :all]
   [cheshire.core :refer :all]
   [sossity.simulator :as sim]
   [sossity.util :as u]
   [flatland.useful.experimental :refer :all]
   [clojure.tools.cli :refer [parse-opts]]
   [traversy.lens :as t :refer :all :exclude [view update combine]]
   [clj-time.core :as ti]
   [clojure.core.async :as a
    :refer [>! <! >!! <!! go chan buffer close! thread alts! alts!! timeout pub sub unsub unsub-all go-loop put!]])
  (:gen-class))

(def crc-prefix "googlecli_container_replica_controller")
(def app-prefix "googleappengine_app")
(def df-prefix "googlecli_dataflow")
(def pt-prefix "google_pubsub_topic")
(def replication-controller-name-regex #"([a-z0-9]([-a-z0-9]*[a-z0-9])?(\\\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*)")
(def angledream-class "com.acacia.angleddream.Main")
(def sink-docker-img "gcr.io/hx-test/store-sink")
(def sink-retries 3)
(def sink-buffer-size 1000)
(def sink-container "${google_container_cluster.hx_fstack_cluster.name}")
(def source-image "gcr.io/hx-test/source-master")
(def source-port "8080")
(def default-bucket-location "EU")
(def default-force-bucket-destroy true)
(def sub-suffix "_sub")
(def gstoragebucket "build-artifacts-public-eu")
(def gstoragekey "hxtest-1.0-SNAPSHOT")
(def default-min-idle 1)
(def default-max-idle 1)
(def default-min-pending-latency "3s")
(def default-max-pending-latency "6s")

(defn source-topic-name [topic] (str topic "_out"))

(defn new-topic-name [in out] (str in "-to-" out))

(defn item-metadata [node a-graph]
  (cond-let
   [item (get (:pipelines a-graph) node)] (assoc item :exec :pipeline)
   [item (get (:sources a-graph) node)] (assoc item :exec :source)
   [item (get (:sinks a-graph) node)] (if (= (:type item) "bq")
                                        (assoc item :exec :pipeline)
                                        (assoc item :exec :sink))))

(defn config-md [a-graph]
  {:config-file a-graph
   :project (get-in a-graph [:provider :project])
   :region (get-in a-graph [:opts :zone])
   :remote-composer-classpath     (get-in a-graph [:config :remote-composer-classpath])
   :local-angleddream-path (get-in a-graph [:config :local-angleddream-path])
   :remote-libs-path (get-in a-graph [:config :remote-libs-path])})

(defn build-items [g node md]
  "Add each metadata per node"                              ;may need to add some kinda filter on this
  (reduce #(add-attr %1 node (key %2) (val %2)) g md))

(defn build-annot [g nodes a-graph]
  "Annotate nodes w/ metadata"
  (reduce #(build-items %1 %2 (item-metadata %2 a-graph)) g nodes))

(defn anns [g a-graph]
  "Traverse graph and annotate nodes w/ metadata"
  (let [t (bf-traverse g)] ;traverse graph to get list of nodes
    (build-annot g t a-graph)))

;NEED ERROR CHECKING - make sure all sources and sinks are in execution graph, etc
;CASE-SENSITIVE


(defn is-bigquery?
  [g node]
  (= "bq" (attr g node :type)))

(defn topic
  [project node]
  (str "projects/" project "/topics/" node))

(defn name-edge
  "name the edge"
  [_ [in out]]
  (new-topic-name in out))

(defn topic-edge
  "combine edge name and graph project with 2 strings to demonstrate varaible args"
  [g edge conf]
  (topic (:project conf) (attr g edge :name)))

(defn apply-edge-metadata [the-graph attr-name metadata-fn & fn-args]
  "Apply attr-name to all edges for the function metadata-fn"
  (reduce #(add-attr-to-edges %1 attr-name (apply metadata-fn %1 %2 fn-args) [%2]) the-graph (edges the-graph)))

(defn name-edges [g conf]
  "Give a str to the :name attribute"
  (->
   g
   (apply-edge-metadata :name name-edge)
   (apply-edge-metadata :topic topic-edge conf)))

(defn predecessor-depends
  "Determine which resources a node depends on"
  [g node]
  (let [preds (predecessors g node)
        sources (u/filter-node-attrs g :exec :source preds)
        jobs (u/filter-node-attrs g :exec :pipeline preds)
        source-names (mapv #(str app-prefix "." %) sources)
        job-names (mapv #(str df-prefix "." %)  jobs)]
    (conj source-names job-names)))

(defn create-container-cluster
  "Create a Kubernetes cluster"
  [a-graph]
  (let [zone (get-in a-graph [:opts :zone])
        output (assoc (:cluster a-graph) :zone zone)]
    output))

(defn create-sink-container [g node conf]
  (let [item_name (clojure.string/lower-case (str node "-sink"))
        proj_name (:project conf)
        sub_name (str (attr g (first (in-edges g node)) :name) "_sub")
        bucket_name (attr g node :bucket)
        zone (:region conf)
        output {item_name {:name item_name :docker_image sink-docker-img :container_name sink-container :zone zone :env_args {:num_retries sink-retries :batch_size sink-buffer-size :proj_name proj_name :sub_name       sub_name :bucket_name bucket_name}}}]
    output))

(defn create-sub [g edge]
  "make a subscription for every node of type gcs based on the inbound edge [for now should only be 1 inbound edge]"
  (let [name (str (attr g edge :name) sub-suffix)
        topic (attr g edge :name)]
    {name {:name name :topic topic :depends_on [(str pt-prefix "." (attr g edge :name))]}}))

(defn create-subs [g node]
  (apply merge (map #(create-sub g %) (in-edges g node))))

(defn create-bucket [g node]
  (if (and (= "gcs" (attr g node :type)) (attr g node :bucket))
    {(attr g node :bucket) {:name (attr g node :bucket) :force_destroy default-force-bucket-destroy :location default-bucket-location}}))

;NOTE: need to create some kind of multiplexer job to make it so multiple jobs can read from multiple sources? ugh


;;add depeendencies
(defn create-appengine-module
  "Creates a rest endpont and a single pubsub -- the only time we restrict to a single output"
  [node g]
  {node {:moduleName node :version "init" :gstorageKey gstoragekey :gstorageBucket gstoragebucket :scaling
         {:minIdleInstances default-min-idle :maxIdleInstances default-max-idle :minPendingLatency default-min-pending-latency :maxPendingLatency default-max-pending-latency}
         :topicName  (attr g (first (out-edges g node)) :topic)}})

(defn create-dataflow-job                                ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-jar?
  [g node conf]
  (let [output-edges (u/filter-not-edge-attrs g :type :error (out-edges g node))
        input-edge (first (u/filter-not-edge-attrs g :type :error (in-edges g node)))
        error-edge (first (u/filter-edge-attrs g :type :error (out-edges g node)))
        predecessor-depends (predecessor-depends g node)
        output-topics (map #(attr g % :topic) output-edges)
        error-topic (attr g error-edge :topic)
        input-topic (attr g input-edge :topic)
        class angledream-class
        output-depends (map #(str pt-prefix "." %) (map #(attr g % :name) output-edges))
        input-depends (str pt-prefix "." (attr g input-edge :name))
        error-depends (if error-edge (str pt-prefix "." (attr g error-edge :name)))
        depends-on (flatten [(flatten [output-depends predecessor-depends error-depends])  input-depends])
        classpath (clojure.string/join (interpose ":" [(get-in conf [:config-file :config :remote-composer-classpath])
                                                       (str (:remote-libs-path conf) "/" (attr g node :transform-jar))])) ;classpath has only one dash!
        opt-map {:pubsubTopic  input-topic
                 :pipelineName node
                 :errorPipelineName error-topic}
        opt-mapb (if-not (= (attr g node :type) "bq")
                   (assoc opt-map :outputTopics (clojure.string/join (interpose "," output-topics)))
                   opt-map)
        bucket-opt-map {:bucket (attr g node :bucket)}
        bq-opts (if (is-bigquery? g node) (dissoc (attrs g node) :type))
        optional-args (apply merge opt-mapb (get-in conf [:config-file :opts]) (if (:bucket bucket-opt-map) bucket-opt-map) bq-opts)]
    {node {:name          node
           :classpath     classpath
           :class         class
           :depends_on    depends-on
           :optional_args optional-args}}));NOTE: name needs to only have [- a-z 0-9] and must start with letter


(defn create-dataflow-jobs [g conf]
  (apply merge (map #(create-dataflow-job g % conf) (u/filter-node-attrs g :exec :pipeline))))

(defn output-provider
  [provider-map]
  (assoc (:provider provider-map) :region (get-in provider-map [:opts :zone])))

(defn output-pubsub [g]
  (apply merge (map #(assoc-in {} [(attr g % :name) :name] (attr g % :name)) (edges g))))

(defn create-sources [g]
  (apply merge (map #(create-appengine-module % g)
                    (u/filter-node-attrs g :exec :source))))

(defn output-sinks [g conf]
  (let [sinks (u/filter-node-attrs g :exec :sink)
        out-sinks (if-not (get-in conf [:config-file :config :error-buckets])
                    (u/filter-not-node-attrs g :error true sinks)
                    sinks)]
    (apply merge (map #(create-sink-container g % conf) out-sinks))))

(defn output-subs [g]
  (apply merge (map #(create-subs g %) (u/filter-node-attrs g :type "gcs"))))

(defn output-buckets [g]
  (apply merge (map #(create-bucket g %) (nodes g))))

(defn output-container-cluster
  [a-graph]
  {:hx_fstack_cluster (create-container-cluster a-graph)})

(defn add-error-sink-node [g parent]
  (let [name (str parent "-error")]
    (-> g
        (add-nodes name)
        (add-attr name :type "gcs")
        (add-attr name :exec :sink)
        (add-attr name :bucket name)
        (add-attr name :error true)
        (add-edges [parent name])
        (add-attr-to-edges :type :error [[parent name]]))))

(defn add-error-sinks
  "Add error sinks to every non-source item on the graph. Sources don't have errors because they should return a 4xx or 5xx when something gone wrong"
  [g conf]
  (let [connected (filter #(> (in-degree g %1) 0) (nodes g))]
    (reduce #(add-error-sink-node %1 %2) g connected)))

(defn create-dag
  [a-graph conf]
  (let [g (digraph (into {} (map (juxt :origin :targets) (:edges a-graph))))]
    ;decorate nodes
    (-> g
        (anns a-graph)
        (add-error-sinks conf)
        (name-edges conf)))                                    ;return the graph?
)

(defn create-terraform-json
  [a-graph]
  (let [conf (config-md a-graph)
        g (create-dag a-graph conf)
        goo-provider {:google (output-provider a-graph)}
        cli-provider {:googlecli (output-provider a-graph)}
        app-provider {:googleappengine (output-provider a-graph)}
        pubsubs {:google_pubsub_topic (output-pubsub g)}
        subscriptions {:google_pubsub_subscription (output-subs g)}
        buckets {:google_storage_bucket (output-buckets g)}
        dataflows {:googlecli_dataflow (create-dataflow-jobs g conf)}
        container-cluster {:google_container_cluster (output-container-cluster a-graph)}
        sources {:googleappengine_app (create-sources g)}
        sinks  (output-sinks g conf)
        controllers {:googlecli_container_replica_controller sinks}
        combined {:provider (merge goo-provider cli-provider app-provider)
                  :resource (merge pubsubs subscriptions container-cluster controllers sources buckets dataflows)}
        out (clojure.string/trim (generate-string combined {:pretty true}))]
    (str "{" (subs out 1 (- (count out) 2)) "}")))                        ;trim first [ and last ] from json


(defn output-terraform-file
  [a-graph file]
  (spit file (create-terraform-json a-graph) :create true :append false :truncate true))                          ;NOTE -- need to remove first [ and last ]


(defn read-and-create
  [input output]
  (output-terraform-file (read-string (slurp input)) output))

(defn view-graph
  [input]
  (loom.io/view (create-dag (read-string (slurp input)) (config-md (read-string (slurp input))))))

(defn test-cluster [a-graph]
  "given a config map, test a cluster. returns [graph input-pipes]"
  (do
    (swap! sim/this-conf (fn [x] (config-md a-graph)))
    (swap! sim/execute-timestamp (fn [x] (.toString (ti/now))))
    (let [dag (create-dag a-graph @sim/this-conf)]
      (sim/setup-jars dag @sim/this-conf)
      (sim/compose-cluster dag))))

(defn file-tester [a-graph]
  "tests against test files in config.clj"
  (let [g (test-cluster a-graph)
        pipes (doall (u/get-all-node-or-edge-attr g :in-chan))
        in-files (doall (u/get-all-node-or-edge-attr g :test-input))]
    (doseq [pipe (keys pipes)]
      (doseq [data (doall (parse-stream (clojure.java.io/reader (get in-files pipe)) true))]
        (put! (get pipes pipe) (sim/handle-message data))))))

(def cli-options
  [["-c" "--config CONFIG" "path to .clj config file for pipeline"]
   ["-o" "--output OUTPUT" "path to output terraform file"]
   ["-v" "--view" "view visualization, requires graphviz installed"]
   ["-s" "--sim" "simulate cluster, running input scripts and producing file output"]])
(defn -main
  "Entry Point"
  [& args]
  (let [opts (:options (clojure.tools.cli/parse-opts args cli-options))]
    (do
      (if (:view opts) (view-graph (:config opts)))
      (if (:sim opts)
        (do (file-tester (read-string (slurp (:config opts))))
            (Thread/sleep 10000)
            )
        (read-and-create (:config opts) (:output opts))))))

