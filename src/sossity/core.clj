(ns sossity.core
  (:require
   [loom.graph :refer :all]
   [loom.alg :refer :all]
   [loom.io :refer :all]
   [loom.attr :refer :all]
   [cheshire.core :refer :all]
   [flatland.useful.experimental :refer :all]
   [clojure.tools.cli :refer [parse-opts]]
   [traversy.lens :as t :refer :all :exclude [view update combine]])
  (:gen-class))

(def crc-prefix "googlecli_container_replica_controller")
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


(defn topic-name [topic] (str topic "_in"))
(defn source-topic-name [topic] (str topic "_out"))
(defn error-topic-name [topic] (str topic "_err"))

(defn new-topic-name [in out] (str in "-to-" out))
(defn new-error-topic-name [in out] (str in "to" out "_error"))

(defn item-metadata [node a-graph]
  (cond-let
   [item (get (:pipelines a-graph) node)] (assoc item :exec :pipeline)
   [item (get (:sources a-graph) node)] (assoc item :exec :source)
   [item (get (:sinks a-graph) node)] (if (= (:type item) "bq")
                                        (assoc item :exec :pipeline)
                                        (assoc item :exec :sink))))

(defn config [a-graph]
  {:config-file a-graph
   :project (get-in a-graph [:provider :project])
   :region (get-in a-graph [:provider :opts :zone])})


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

(defn filter-node-attrs
  ([g keyword value]
     (filter (fn [x] (= value (attr g x keyword))) (nodes g)))
  ([g keyword value nodes]
   (filter (fn [x] (= value (attr g x keyword))) nodes))
  )

(defn filter-not-node-attrs
  ([g keyword value]
   (filter (fn [x] (= value (attr g x keyword))) (nodes g)))
  ([g keyword value nodes]
   (filter (fn [x] (= value (attr g x keyword))) nodes))
  )


(defn filter-not-edge-attrs
  ([g keyword value]
     (filter (fn [x] (not= value (attr g x keyword))) (edges g)))
  ([g keyword value edges]
   (filter (fn [x] (not= value (attr g x keyword))) edges)))


(defn filter-edge-attrs
  ([g keyword value]
   (filter (fn [x] (= value (attr g x keyword))) (edges g)))
  ([g keyword value edges]
   (filter (fn [x] (= value (attr g x keyword))) edges)))




;NEED ERROR CHECKING - make sure all sources and sinks are in execution graph, etc
;CASE-SENSITIVE


(defn is-dataflow-job?
  "Determines if item is dataflow job. eventually should be done by checking in-degree and out-degree = at least 1, but for now just check metadata"
  [g node a-graph]
  (or
   (not (= nil (get-in a-graph [:pipelines node])))
   (= :pipeline (attr g node :exec))
   (= "bq" (attr g node :type))))

(defn is-bigquery?
  [g node]
  (= "bq" (attr g node :type)))

(defn is-pipeline?
  [g node]
  (= :pipeline (attr g node :exec)))

(defn is-cloud-storage?
  [g node]
  (= "gcs" (attr g node :type)))

(defn topic
  [node project]
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


#_(defn name-edges [g conf]
    (-> (reduce #(add-attr-to-edges %1 :name (name-edge %1 %2) [%2]) g (edges g))
        (reduce #(add-attr-to-edges %1 :topic (topic (name-edge %1 %2 conf)) [%2]) g (edges g))))                                                         ;pubsubs are edges


(defn non-sink-successors
  [g node]
  (filter #(> (out-degree g %1) 0) (successors g node)))

#_(defn predecessor-sources
  "Determine which nodes are at the 'head' of a data flow, usually REST endpoints"
  [g node a-graph]
  (let [preds (set (predecessors g node))
        sources (filter preds (set (-> (:sources a-graph) (t/view all-keys))))
        source-names (mapv #(str crc-prefix "." % "-source") sources)]
    source-names))

#_(defn predecessor-depends
  "Determine which predecessors of a node are sources vs. normal jobs"
  [g node a-graph]
  (let [preds (set (predecessors g node))
        jobs (filter preds (set (-> (:pipelines a-graph) (t/view all-keys))))
        source-names (predecessor-sources g node a-graph)
        job-names (mapv #(str df-prefix "." %)  jobs)]
    (conj source-names job-names)))


(defn predecessor-depends
  "Determine which resources a node depends on"
  [g node]
  (let [preds (predecessors g node)
        sources (filter-node-attrs g :exec :source preds)
        jobs (filter-node-attrs g :exec :source preds)
        source-names (mapv #(str crc-prefix "." %) sources)
        job-names (mapv #(str df-prefix "." %)  jobs)]
    (conj source-names job-names)))



(defn create-container-cluster
  "Create a Kubernetes cluster"
  [a-graph]
  (let [zone (get-in a-graph [:opts :zone])
        output (assoc (:cluster a-graph) :zone zone)]
    output))

;;add depeendencies
(defn create-sink-container [g [node v :as item]  a-graph]
  "Create a sink Kubernetes container"
  (if-not (is-dataflow-job? g node a-graph)
    (let [item_name (clojure.string/lower-case (str node "-sink"))
          proj_name (get-in a-graph [:provider :project])
          sub_name (str node "_sub")
          bucket_name (:bucket v)
          zone (get-in a-graph [:opts :zone])
          output {item_name {:name item_name :docker_image sink-docker-img :container_name sink-container :zone zone :env_args {:num_retries sink-retries :batch_size sink-buffer-size :proj_name proj_name :sub_name       sub_name :bucket_name bucket_name}}}]
      output)))

#_(defn create-sub [g item a-graph]
  "Create a pubsub subscription"
  (if-not (is-dataflow-job? g (key item) a-graph)
    (let [node (key item)
          name (str node "_sub")
          topic (topic-name node)
          output {name {:name name :topic topic :depends_on [(str pt-prefix "." topic)]}}]
      output)))


(defn create-sub [g edge]
  "make a subscription for every node of type gcs based on the inbound edge [for now should only be 1 inbound edge]"
  (let [name (str (attr g edge :name) "_sub")
        topic (attr g edge :topic)]
    {name {:name name :topic topic :depends_on [(str pt-prefix "." name)]}}
    )
  )


(defn create-subs [g node]
  (apply merge (map #(create-sub g %) (in-edges g node))))

(defn create-bucket [g node]
  (if (and (= "gcs" (attr g node :type)) (attr g node :bucket))
    {(attr g node :bucket) {:name (attr g node :bucket) :force_destroy default-force-bucket-destroy :location default-bucket-location}}
    ))

;NOTE: need to create some kind of multiplexer job to make it so multiple jobs can read from multiple sources? ugh


;;add depeendencies
(defn create-source-container
  "Creates a rest endpont and a single pubsub -- the only time we restrict to a single output"
  [item a-graph]
  (let [node (key item)
        post_route (str "/" node "/post")
        health_route (str "/" node "/health")
        item_name (str node "-source")
        stream_name (topic (source-topic-name node) (get-in a-graph [:provider :project]))
        container_name "${google_container_cluster.hx_fstack_cluster.name}"
        zone (get-in a-graph [:opts :zone])
        output {item_name {:name item_name :docker_image source-image :external_port source-port :container_name container_name :zone zone :env_args {:post_route post_route :health_route health_route :stream_name stream_name}}}]
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

#_(defn create-dataflow-item                                  ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-graph?
  [g node a-graph]
  (if (is-dataflow-job? g node a-graph)                     ;always nil for now
    (let [project (get-in a-graph [:provider :project])
          output-topics (map #(topic-name %) (successors g node))
          input-topic (determine-input-topic g node a-graph)
          error-topic (error-topic-name node)
          predecessor-depends (predecessor-depends g node a-graph)
          class angledream-class
          output-depends (map #(str pt-prefix "." %) output-topics)
          input-depends (str pt-prefix "." input-topic)
          depends-on (flatten [(flatten [output-depends predecessor-depends]) (str pt-prefix "." error-topic) input-depends])
          cli-map (dissoc (:opts a-graph) :composer-classpath)
          classpath (clojure.string/join (interpose ":" (concat (get-in a-graph [:opts :composer-classpath]) (get-in a-graph [:pipelines node :transform-graph])))) ;classpath has only one dash!
          opt-map {:pubsubTopic (topic input-topic project)
                   :pipelineName node
                   :errorPipelineName (topic error-topic project)}
          opt-mapb (if-not (is-bigquery? g node)
                     (assoc opt-map :outputTopics (clojure.string/join (interpose "," (map #(topic % project) output-topics))))
                     opt-map)

          endpoint-opt-map (endpoint-opts :sinks node a-graph)
          bq-opts (if (is-bigquery? g node) (dissoc (attrs g node) :type))
          optional-args (apply merge cli-map opt-mapb endpoint-opt-map bq-opts)]
      {name {:name      name
             :classpath classpath
             :class class
             :depends_on depends-on
             :optional_args optional-args}})))


(defn create-dataflow-job                                ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-graph?
  [g node conf]
  (let [
        output-topics (map #(attr g % :topic) (filter-not-edge-attrs g :type :error (out-edges g node)))
        input-topic (attr g (first (filter-not-edge-attrs g :type :error (in-edges g node)))
                          :topic)
        error-topic (attr g (first (filter-edge-attrs g :type :error (out-edges g node)))
                          :topic)
        predecessor-depends (predecessor-depends g node)
        class angledream-class
        output-depends (map #(str pt-prefix "." %) output-topics)
        input-depends (str pt-prefix "." input-topic)
        error-depends (str pt-prefix "." error-topic)
        depends-on (flatten [(flatten [output-depends predecessor-depends error-depends])  input-depends])
        cli-map (:config (:config-file conf))
        classpath (clojure.string/join (interpose ":" (concat (get-in config [:config-file :composer-classpath]) (attr g node :transform-graph)))) ;classpath has only one dash!
        opt-map {:pubsubTopic  input-topic
                 :pipelineName node
                 :errorPipelineName error-topic}
        opt-mapb (if-not (= (attr g node :type) "bq")
                   (assoc opt-map :outputTopics (clojure.string/join (interpose "," output-topics)))
                   opt-map)

        bucket-opt-map {:bucket (attr g node :bucket)}
        bq-opts (if (is-bigquery? g node) (dissoc (attrs g node) :type))
        optional-args (apply merge cli-map opt-mapb bucket-opt-map bq-opts)]
    {node {:name          node
           :classpath     classpath
           :class         class
           :depends_on    depends-on
           :optional_args optional-args}}))





  ;NOTE: name needs to only have [- a-z 0-9] and must start with letter

#_(defn create-dataflow-jobs [g a-graph]
  (let [t (bf-traverse g)                                 ;filter out anything in soruces or sinks without type cdf or bgq
        jobs (filter (comp not nil?) (map #(create-dataflow-item g % a-graph) t))]
    jobs))

(defn create-dataflow-jobs [g conf]
  (apply merge (map #(create-dataflow-job g % conf) (filter-node-attrs g :exec :pipeline)))
  )


(defn output-provider
  [provider-map]
  (assoc (:provider provider-map) :region (get-in provider-map [:opts :zone])))

(defn output-pubsub [g]
  (apply merge (map #(assoc-in {} [(attr g % :name) :name] (attr g % :name)) (edges g))))

(defn create-sources [a-graph]
  (map #(create-source-container % a-graph)
       (:sources a-graph)))

(defn create-sinks [g a-graph]
  (map #(create-sink-container g % a-graph) (:sinks a-graph)))

(defn output-subs [g]
  (apply merge (map #(create-subs g %) (filter-node-attrs g :type "gcs"))))

(defn output-buckets [g ]
  (apply merge (map #(create-bucket g %) (nodes g))))

(defn output-container-cluster
  [a-graph]
  {:hx_fstack_cluster (create-container-cluster a-graph)})

(defn add-error-sink-node [g parent]
  (let [name (str parent "-error")]
    (-> g
        (add-nodes name)
        (add-attr name :type "gcs")
        (add-attr name :bucket name)
        (add-edges [parent name])
        (add-attr-to-edges :type :error [[parent name]]))))

(defn add-error-sinks
  "Add error sinks to every non-source item on the graph. Sources don't have errors because they should return a 4xx or 5xx when something gone wrong"
  [g]
  (let [connected (filter #(> (in-degree g %1) 0) (nodes g))]
    (reduce #(add-error-sink-node %1 %2) g connected)))

(defn create-dag
  [a-graph conf]
  (let [g (digraph (into {} (map (juxt :origin :targets) (:edges a-graph))))]
    ;decorate nodes
    (-> g
        (anns a-graph)
        add-error-sinks
        (name-edges conf)))                                    ;return the graph?
)

;FIXME: need to have path_to_angleddream_bundled_jar? Or maybe just merge this. replica controller names have to match DNS entries, so no underscores or capital letters



(defn create-terraform-json                                 ;gotta be a better way to clean up these 'apply merges'. NEED TO CHANGE TO USE GRAPH, NOT CONFIG, FOR BUILDING
  [a-graph]
  (let [conf (config a-graph)
        g (create-dag a-graph conf)
        goo-provider {:google (output-provider a-graph)}
        cli-provider {:googlecli (output-provider a-graph)}
        pubsubs {:google_pubsub_topic (output-pubsub g)}
        subscriptions {:google_pubsub_subscription (output-subs g )}
        buckets {:google_storage_bucket (output-buckets g)}
        dataflows {:googlecli_dataflow (create-dataflow-jobs g conf)}
        container-cluster {:google_container_cluster (output-container-cluster a-graph)}
        sources (apply merge (create-sources a-graph))
        sinks (apply merge (create-sinks g a-graph))
        controllers {:googlecli_container_replica_controller (apply merge sources sinks)}
        combined {:provider (merge goo-provider cli-provider)
                  :resource (merge pubsubs subscriptions container-cluster controllers buckets dataflows)}
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
  (loom.io/view (create-dag (read-string (slurp input)) (config (read-string (slurp input))))))

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