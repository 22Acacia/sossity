(ns sossity.core-test
  (:require [clojure.test :refer :all]
            [sossity.core :refer :all]
            [loom.graph :refer :all]
            [loom.alg :refer :all]
            [loom.io :refer :all]

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
  (digraph (into {} (map (juxt :origin :targets) (:edges a-graph))))
  )

(defn create-pipelines
  [a-graph]

  )

(defn stringify-pipeline
[pipelines]
  )



(defn create-pubsubs
  [g]                                                 ;out and error for all sources and pipelines, just error for sinks. nodes with cardinality? of 1 have out/error, 0 have error
  (let [t (bf-traverse g)
        connected (filter #(> (out-degree g %1) 0) t)
        edges (filter #(= (out-degree g %1) 0)  t)
        ]
    (flatten [
              (map #((juxt (fn [x] (str x "-out")) (fn [x] (str x "-error"))) %1) connected)
              (map #(str %1 "-error") connected)
              ])
    )

  )

(defn create-sources-with-dependencies
  [g]
  )

(defn create-sinks-with-dependencies
  [g]
  )

(defn create-dataflow-jobs                                  ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-graph?
  [g]
  ;remember to generate dependency on edges like in example

  )


(defn output-terraform-file
  [item]
  )


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
