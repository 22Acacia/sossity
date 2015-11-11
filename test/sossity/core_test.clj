(ns sossity.core-test
  (:require [clojure.test :refer :all]
            [sossity.core :refer :all]
            [loom.graph :refer :all]
            [loom.alg :refer :all]
            [loom.io :refer :all]
            [loom.attr :refer :all]
            [cheshire.core :refer :all]
            [traversy.lens :as t :refer :all :exclude [view update combine]]
            ))

(deftest parse-graph
  (testing "Parse the graph into a nice thing"
    (is (= 0 1))))

(def test-graph  ;each pipeline can only be defined in an origin once?
  {:provider  {:account-file "blah.json" :project "hx-test" :region "us-central1"}
   :pipelines {"Pipeline1"
               {:transform-graph ["AbstractTransform/AbstractTransform.jar"]}
               "Pipeline2"
               {:transform-graph ["AbstractTransform/AbstractTransform2.jar"]}
               "Pipeline3"
               {:transform-graph ["PythonScripts/PipelinePy3.py"]}
               "Pipeline4"
               {:transform-graph ["AbstractTransform/Pipeline4.jar"]}
               "Pipeline5"
               {:transform-graph ["PythonScripts/Pipeline5.py"]}
               #_"RepairJob"
               #_{:transform-graph ["PythonScripts/RepairClickstream.py"]}
               }
   :sources   [{:package "ClickStream"} {:package "ShoppingCartClickStream"} {:package "CartTransaction"} {:package "EmailClickStream"}]
   :sinks     [{:package "S3Sink"} {:package "EnhancedAdwordsSink"} {:package "EnhancedAdwordsSink"} {:package "CartTrans-Redshift"} {:package "EmailClicks-RedShift"} {:package "RawEmailS3"}]
   :edges     [{:origin "Pipeline1" :targets ["Pipeline2"]}
               {:origin "ClickStream" :targets ["Pipeline1"]}
               {:origin        "Pipeline2"
                :error-sink    :google-storage ;implied sink of gcs directory "Pipeline2/errors/blah"
                :error-handler "RepairJob" ;implied source of gcs directory "Pipeline2/errors/blah"
                :targets       ["S3Sink" "EnhancedAdwordsSink"]}
               {:origin "ShoppingCartClickStream" :targets ["Pipeline3"]}
               {:origin "Pipeline3" :targets ["Pipeline2"]}
               {:origin "CartTransaction" :targets ["Pipeline4"]}
               {:origin "Pipeline4" :targets ["CartTrans-Redshift"  ]} ;implied {:name "CartTrans-Redshift"}, and write to a RedShift table and write to the post-processing queue of Pipeline2? {"Pipeline2" :type "post"}
               {:origin "EmailClickstream" :targets ["Pipeline5" "RawEmailS3"]}
               {:origin "Pipeline5" :targets ["Emailclicks-RedShift"]}]})


(def mini-graph
  {"A" ["B" "C"] "B" ["D"] "C" [] "D" ["E" "F"]}
  )

(def mini-attrs
  {"A" {:k 1 :v "a1" :q 9}                                  ; -->reduce on "A" {:k 1} "A" {:v 2}
   "C" {:k 1 :v "c1" :q 11}
   }
  )

(defn build-items [g items]
  (reduce #(add-attr %1 (key items) (key %2) (val %2) ) g (val items))
  )


(defn build-annot [g item-set]
  (reduce #(build-items %1 %2) g item-set)
  )

(defn create-dag
  [a-graph]
  (let [g (digraph (into {} (map (juxt :origin :targets) (:edges a-graph))))]
        ;decorate nodes
        (build-annot g (:pipelines a-graph))

    )                                                  ;return the graph?
)

(defn output-topic
  [node]
  (str node "-out"))

(defn error-topic
  [node]
  (str node "-err"))

(defn non-sink-pipes
  [node]
  ((juxt #(output-topic %) #(error-topic %)) node))

(defn sink-pipes
  [node]
  (str node "-error"))

(defn create-pubsubs
  [g]                                                 ;out and error for all sources and pipelines, just error for sinks. nodes with cardinality? of 1 have out/error, 0 have error
  (let [t (bf-traverse g)
        connected (filter #(> (out-degree g %1) 0) t)
        ends (filter #(= (out-degree g %1) 0)  t)]
    (into [] (flatten [(map non-sink-pipes connected)
                       (map sink-pipes ends)]))))

(defn create-sources-with-dependencies
  [g])

(defn create-sinks-with-dependencies
  [g])

(defn create-dataflow-item                                  ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-graph?
  [g node a-graph]
  ;remember to generate dependency on edges like in example ... depth-first?
  (let [output-topics (map output-topic (successors g node))
        input-topics (map output-topic (predecessors g node))
        ancestor-jobs (predecessors g node)
        name node
        class "Main.class"
        ;error topic implied in name and handled in orchestrator
        output-depends (map #(str "google_pubsub." %) output-topics)
        input-depends (map #(str "google_pubsub." %) input-topics)
        ancestor-depends (map #(str "google_dataflow." %) ancestor-jobs)
        depends-on (flatten [output-depends input-depends ancestor-depends])]
    ;pipelines and jobs, TYPE.NAME like aws_instance.web

    (-> (assoc-in {} ["resource" "google_dataflow" name "name"] name)
        (update-in ["resource" "google_dataflow" name ] merge {
                    "name" name "class" class "depends_on" depends-on "project" (get-in a-graph [:provider :project])})
        )

    ))

(defn create-dataflow-jobs [g a-graph]
  (let [t (bf-traverse g)
        jobs (map #(create-dataflow-item g % a-graph) t)
        ]
    jobs
    )

  )



(defn output-terraform-file
  [raw-json file]
    (spit raw-json file)                                    ;NOTE -- need to remove first [ and last ]
  )

(defn output-provider
  [provider-map]
  (first (assoc-in {} ["provider" "google"] (:provider provider-map)))
  )

(defn output-pubsub
  [pubsub-map]
  (map
    ;#({"resource" {"google_pubsub" {"a" {"name" "b"}}}} )
    #(assoc-in {} ["resource" "google_pubsub" % "name"] %)
    pubsub-map))

(defn create-terraform-json
  [a-graph]
  (let [g (create-dag a-graph)
        provider (output-provider a-graph)
        pubsubs (output-pubsub (create-pubsubs g))
        dataflows (create-dataflow-jobs g a-graph)
        ]
    (generate-string (concat provider pubsubs dataflows) {:pretty true})
    )


  )

