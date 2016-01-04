(ns sossity.core
  (:require
   [loom.graph :refer :all]
   [loom.alg :refer :all]
   [loom.io :refer :all]
   [loom.attr :refer :all]
   [cheshire.core :refer :all]
   [clojure.tools.cli :refer [parse-opts]]
   [traversy.lens :as t :refer :all :exclude [view update combine]])
  (:gen-class))

(def sr-prefix "googlecli_container_replica_controller")
(def df-prefix "googlecli_dataflow")
(def pl-prefix "google_pubsub_topic")
(def replication-controller-name-regex #"([a-z0-9]([-a-z0-9]*[a-z0-9])?(\\\\.[a-z0-9]([-a-z0-9]*[a-z0-9])?)*)")
(def container-oauth-scopes {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                            "https://www.googleapis.com/auth/devstorage.read_only"
                                            "https://www.googleapis.com/auth/logging.write"
                                            "https://www.googleapis.com/auth/monitoring"
                                            "https://www.googleapis.com/auth/cloud-platform"]})

(defn topic-name [topic] (str topic "_in"))
(defn source-topic-name [topic] (str topic "_out"))
(defn error-topic-name [topic] (str topic "_err"))

(defn item-metadata [node a-graph]
  (or (get (:pipelines a-graph) node) (get (:sources a-graph) node) (get (:sinks a-graph) node)))

(defn build-items [g node md]
  "Add each metadata per node"
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


(defn is-dataflow-job?
  "Determines if item is dataflow job. eventually should be done by checking in-degree and out-degree = at least 1, but for now just check metadata"
  [g node a-graph]
  (or
   (not (= nil (get-in a-graph [:pipelines node])))
   (= "cdf" (attr g node :type))
   (= "bq" (attr g node :type))))

(defn is-bigquery?
  [g node]
  (= "bq" (attr g node :type)))

(defn is-pipeline?
  [g node]
  (= "cdf" (attr g node :type)))

(defn is-cloud-storage?
  [g node]
  (= "gcs" (attr g node :type)))

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

(defn predecessor-sources
  [g node a-graph]
  (let [preds (set (predecessors g node))
        sources (filter preds (set (-> (:sources a-graph) (t/view all-keys))))
        source-names (mapv #(str sr-prefix "." % "-source") sources)]
    source-names))

(defn predecessor-depends
  "Determine which predecessors of a node are sources vs. normal jobs"
  [g node a-graph]
  (let [preds (set (predecessors g node))
        jobs (filter preds (set (-> (:pipelines a-graph) (t/view all-keys))))
        source-names (predecessor-sources g node a-graph)
        job-names (mapv #(str df-prefix "." %)  jobs)]
    (conj source-names job-names)))

(defn create-container-cluster
  [a-graph]
  (let [zone (get-in a-graph [:opts :zone])
        output (assoc (assoc (:cluster a-graph) :zone zone) :node_config container-oauth-scopes)]
    output))

;;add depeendencies
(defn create-sink-container [g item a-graph]
  (if-not (is-pipeline? g (key item))
    (let [node (key item)
          item_name (clojure.string/lower-case (str node "-sink"))
          docker_image "gcr.io/hx-test/store-sink"
          num_retries 3
          batch_size 10000
          proj_name (get-in a-graph [:provider :project])
          sub_name (str node "_sub")
          container_name "${google_container_cluster.hx_fstack_cluster.name}"
          bucket_name (:bucket (val item))
          zone (get-in a-graph [:opts :zone])
          output {item_name {:name item_name :docker_image docker_image :container_name container_name :zone zone :env_args {:num_retries num_retries :batch_size batch_size :proj_name proj_name
                                                                                                                             :sub_name    sub_name :bucket_name bucket_name}}}]

      output)))

(defn create-sub [g item]
  (if-not (is-pipeline? g (key item))
    (let [node (key item)
          name (str node "_sub")
          topic (topic-name node)
          output {name {:name name :topic topic :depends_on [(str pl-prefix "." topic)]}}]
      output)))

(defn create-bucket [g item]
  (if (is-cloud-storage? g (key item))
    (let [name (:bucket (val item))
          force_destroy true
          location "EU"
          output {name {:name name :force_destroy force_destroy :location location}}]
      output)))

;NOTE: need to create some kind of multiplexer job to make it so multiple jobs can read from multiple sources? ugh


;;add depeendencies
(defn create-source-container
  "Creates a rest endpont and a single pubsub -- the only time we restrict to a single output"
  [item a-graph]
  (let [node (key item)
        post_route (str "/" node "/post")
        health_route (str "/" node "/health")
        item_name (str node "-source")
        docker_image "gcr.io/hx-test/source-master"
        external_port "8080"
        stream_name (topic (source-topic-name node) (get-in a-graph [:provider :project]))
        container_name "${google_container_cluster.hx_fstack_cluster.name}"
        zone (get-in a-graph [:opts :zone])
        output {item_name {:name item_name :docker_image docker_image :external_port external_port :container_name container_name :zone zone :env_args {:post_route post_route :health_route health_route :stream_name stream_name}}}]
    output))

(defn determine-input-topic
  [g node a-graph]
  (if (empty? (predecessor-sources g node a-graph))
    (topic-name node)
    (source-topic-name (first (predecessors g node)))))

(defn endpoint-opts
  "parent is :sources or :sinks"
  [parent node a-graph]
  (dissoc (get-in a-graph [parent node]) :type))

(defn create-dataflow-item                                  ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-graph?
  [g node a-graph]
  (if (is-dataflow-job? g node a-graph) ;always nil for now
    (let [project (get-in a-graph [:provider :project])
          output-topics (map #(topic-name %) (successors g node))
          input-topic (determine-input-topic g node a-graph)
          error-topic (error-topic-name node)
          predecessor-depends (predecessor-depends g node a-graph)
          name node
          class "com.acacia.angleddream.Main"      ;need to make tshis a smart default
          output-depends (map #(str pl-prefix "." %) output-topics)
          input-depends (str pl-prefix "." input-topic)
          depends-on (flatten [(flatten [output-depends predecessor-depends]) (str pl-prefix "." error-topic) input-depends])
          cli-map (dissoc (:opts a-graph) :composer-classpath)
          classpath (clojure.string/join (interpose ":" (concat (get-in a-graph [:opts :composer-classpath]) (get-in a-graph [:pipelines node :transform-graph])))) ;classpath has only one dash!
          opt-map {:pubsubTopic (topic input-topic project) :pipelineName name :errorPipelineName error-topic}
          opt-mapb (assoc opt-map :outputTopics (clojure.string/join (interpose "," (map #(topic % project) output-topics))))
          endpoint-opt-map (endpoint-opts :sinks node a-graph)
          bq-opts (if (is-bigquery? g node) (dissoc (attrs g node) :type))
          optional-args (apply merge cli-map opt-mapb endpoint-opt-map bq-opts)]
      {name {:name name :classpath classpath :class class :depends_on depends-on :optional_args optional-args}})))

;NOTE: name needs to only have [- a-z 0-9] and must start with letter

#_(defn create-dataflow-jobs [g a-graph]
    (let [t (bf-traverse g)                                   ;filter out anything in soruces or sinks without type cdf or bgq
          jobs (filter (comp not nil?) (map #(create-dataflow-item g % a-graph) t))]
      jobs))

(defn create-dataflow-jobs [g a-graph]
  (let [t (bf-traverse g)                                   ;filter out anything in soruces or sinks without type cdf or bgq
        jobs (filter (comp not nil?) (map #(create-dataflow-item g % a-graph) t))]
    jobs))

(defn output-provider
  [provider-map]
  (assoc (:provider provider-map) :region (get-in provider-map [:opts :zone])))

(defn output-pubsub [pubsub-coll]
  (map #(assoc-in {} [% :name] %) pubsub-coll))

(defn create-sources [a-graph]
  (map #(create-source-container % a-graph) (:sources a-graph)))

(defn create-sinks [g a-graph]
  (map #(create-sink-container g %  a-graph) (:sinks a-graph)))

(defn create-subs [g a-graph]
  (map #(create-sub g %) (:sinks a-graph)))

(defn create-buckets [g a-graph]
  (map #(create-bucket g %) (:sinks a-graph)))

(defn output-container-cluster
  [a-graph]
  {:hx_fstack_cluster (create-container-cluster a-graph)})

(defn create-dag
  [a-graph]
  (let [g (digraph (into {} (map (juxt :origin :targets) (:edges a-graph))))]

    ;decorate nodes
    (-> (anns g a-graph)
        #_(build-annot  (:sources a-graph))
        #_(build-annot  (:sinks a-graph))
        #_(add-attr-to-nodes :type :source (get-submembers-keys a-graph :sources))
        #_(add-attr-to-nodes :type :sink (get-submembers-keys a-graph :sinks))))                                    ;return the graph?
)

;FIXME: need to have path_to_angleddream_bundled_jar? Or maybe just merge this. replica controller names have to match DNS entries, so no underscores or capital letters
;FIXME: need to figure out when to have multiple subs to one pubsub, seems useful?

(defn create-terraform-json                                 ;gotta be a better way to clean up these 'apply merges'
  [a-graph]
  (let [g (create-dag a-graph)
        goo-provider {:google (output-provider a-graph)}
        cli-provider {:googlecli (output-provider a-graph)}
        pubsubs {:google_pubsub_topic (apply merge (output-pubsub (create-pubsubs g)))}
        subscriptions {:google_pubsub_subscription (apply merge (create-subs g a-graph))}
        buckets {:google_storage_bucket (apply merge (create-buckets g a-graph))}
        dataflows {:googlecli_dataflow (apply merge (create-dataflow-jobs g a-graph))}
        container-cluster {:google_container_cluster (output-container-cluster a-graph)}
        sources (apply merge (create-sources a-graph))
        sinks (apply merge (create-sinks g a-graph))
        controllers {:googlecli_container_replica_controller (apply merge sources sinks)}
        combined {:provider (merge goo-provider cli-provider) :resource (merge pubsubs subscriptions container-cluster controllers buckets dataflows)}
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
  (loom.io/view (create-dag (read-string (slurp input)))))

(def cli-options
  [["-c" "--config CONFIG" "path to .clj config file for pipeline"]
   ["-o" "--output OUTPUT" "path to output terraform file"]
   ["-v" "--view" "view visualization, requires graphviz installed"]
   #_["-cr" "--credentials" "location to credentials file for terraform to use"]])

(defn -main
  "Entry Point"
  [& args]
  (let [opts (:options (clojure.tools.cli/parse-opts args cli-options))]
    (do
      (if (:view opts) (view-graph (:config opts)))
      (read-and-create (:config opts) (:output opts)))))