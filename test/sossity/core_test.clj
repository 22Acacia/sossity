(ns sossity.core-test
  (:require [clojure.test :refer :all]
            [sossity.core :refer :all]
            [loom.graph :refer :all]
            [loom.alg :refer :all]
            [loom.io :refer :all]
            [loom.attr :refer :all]

            ))

(deftest parse-graph
  (testing "Parse the graph into a nice thing"
    (is (= 0 1))))

(def test-graph  ;each pipeline can only be defined in an origin once?
  {
   :provider   {:account-file "blah.json" :project "hx-test" :region "us-central1"}
   :pipelines  [{:name            "Pipeline1"
                 :transform-graph ["AbstractTransform/AbstractTransform.jar" "PythonScripts/AppendTest1.py"]}
                {:name            "Pipeline2"
                 :transform-graph ["AbstractTransform/AbstractTransform.jar" "PythonScripts/AppendTest2.py"]}
                {:name "RepairJob"
                 :transform-graph ["PythonScripts/RepairClickstream.py"]}]
   :sources      [{:package "ClickStream"} {:package "ShoppingCartClickStream"} {:package "CartTransaction"} {:package "EmailClickStream"}]
   :sinks        [{:package "S3Sink"} {:package "EnhancedAdwordsSink"} {:package "EnhancedAdwordsSink"} {:package "CartTrans-Redshift"} {:package "EmailClicks-RedShift"} {:package "RawEmailS3"}]
   :edges        [{:origin "Pipeline1" :targets ["Pipeline2"]}
                  {:origin "ClickStream" :targets ["Pipeline1"]}
                  {:origin "Pipeline2"
                   :error-sink :google-storage ;implied sink of gcs directory "Pipeline2/errors/blah"
                   :error-handler "RepairJob" ;implied source of gcs directory "Pipeline2/errors/blah"
                   :targets ["S3Sink" "EnhancedAdwordsSink"]}
                  {:origin "ShoppingCartClickStream" :targets ["Pipeline3"]}
                  {:origin "Pipeline3" :targets ["Pipeline2"]}
                  {:origin "CartTransaction" :targets ["Pipeline4"]}
                  {:origin "Pipeline4" :targets ["CartTrans-Redshift"  {:name "Pipeline2" :type "post"}]} ;implied {:name "CartTrans-Redshift"}, and write to a RedShift table and write to the post-processing queue of Pipeline2?
                  {:origin "EmailClickstream" :targets ["Pipeline5" "RawEmailS3"]}
                  {:origin "Pipeline5" :targets ["Emailclicks-RedShift"]}]
   })

(defn create-dag
  [a-graph]
  (let [g (digraph (into {} (map (juxt :origin :targets) (:edges a-graph))))
        pipeline-nodes (mapv :name (:pipelines test-graph))
        ]
        ;decorate nodes
       (doseq [kv (:pipelines test-graph)]                  ;; do with some kinda reduce? add-attr-to-nodes returns an fn
         (doseq [metadata (dissoc kv :name)]
              (add-attr-to-nodes g (key metadata) (val metadata) (filter #(= (:name kv) %1) (nodes g))) ;find nodes with pipeline data and decorate them
           )

         )

        g)                                                  ;return the graph?

  )


(defn output-topic
  [node]
  (str node "-out")
  )

(defn error-topic
  [node]
  (str node "-err")
  )

(defn non-sink-pipes
  [node]
  ((juxt (fn [x] (output-topic x)) (fn [x] (error-topic x))) node)
  )

(defn sink-pipes
  [node]
  (str node "-error")
  )


(defn create-pubsubs
  [g]                                                 ;out and error for all sources and pipelines, just error for sinks. nodes with cardinality? of 1 have out/error, 0 have error
  (let [t (bf-traverse g)
        connected (filter #(> (out-degree g %1) 0) t)
        ends (filter #(= (out-degree g %1) 0)  t)
        ]
    (flatten [
              (map non-sink-pipes connected)
              (map sink-pipes ends)
              ])
    ))



(defn create-sources-with-dependencies
  [g]
  )

(defn create-sinks-with-dependencies
  [g]
  )

(defn create-dataflow-job                                  ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-graph?
  [node]
  ;remember to generate dependency on edges like in example ... depth-first?
  (let [output-topics (map output-topic (successors g node))
        input-topic (map output-topic (predecessors g node))
        ancestor-jobs (predecessors g node)
        name node
        class "Main.class"
        ;error topic implied in name and handled in orchestrator
        depends-on (flatten [output-topics input-topic ancestor-jobs])]
    ;pipelines and jobs, TYPE.NAME like aws_instance.web
                    )

  )


(defn output-terraform-file
  [g file]
  )

; SCREW THIS,  OUTPUT JSON
(defn stringify-items
  [item]
  (map #(str (clojure.string/replace (name (key %)) #"-" "_"  ) " = " (val %) "\n") item) )

(defn output-provider
  [provider-map]
  (str "provider \"google\" {" (stringify-items provider-map) "}")
  )

(defn output-pubsub
  [pubsub-map]
  (map  #(str "resource \"google_pubsub\" \"" %1 "\" { name = \"" %1 "\"} \n")
        pubsub-map )
  )

(defn output-dataflow
  [dataflow-map]

  )


;parse graph
