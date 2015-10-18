(ns sossity.core-test
  (:require [clojure.test :refer :all]
            [sossity.core :refer :all]))

(deftest parse-graph
  (testing "Parse the graph into a nice thing"
    (is (= 0 1))))

(def test-graph
  {:project-name "hx-test"
   :sources      [{:package "ClickStream"} {:package "ShoppingCartClickStream"} {:package "CartTransaction"} {:package "EmailClickStream"}]
   :pipelines    [{:package "Pipeline1"} {:package "Pipeline2"} {:package "Pipeline3"} {:package "Pipeline4"} {:package "Pipeline5"} {:package "RepairJob"}]
   :sinks        [{:package "S3Sink"} {:package "EnhancedAdwordsSink"} {:package "EnhancedAdwordsSink"} {:package "CartTrans-Redshift"} {:package "EmailClicks-RedShift"} {:package "RawEmailS3"}]
   :edges        [{:origin "ClickStream" :targets ["Pipeline1"]}
                  {:origin "Pipeline1" :targets ["Pipeline2"]}
                  {:origin "Pipeline2" :target-type "error" :handler "GoogleStorage"} ;implied sink of gcs directory "Pipeline2/errors/blah"
                  {:origin "Pipeline2" :origin-type "error" :targets ["RepairJob"]} ;implied source of gcs directory "Pipeline2/errors/blah"
                  {:origin "Pipeline2" :targets ["S3Sink" "EnhancedAdwordsSink"]}
                  {:origin "ShoppingCartClickStream" :targets ["Pipeline3"]}
                  {:origin "Pipeline3" :targets ["Pipeline2"]}
                  {:origin "CartTransaction" :targets ["Pipeline4"]}
                  {:origin "Pipeline4" :targets ["CartTrans-Redshift" {:name "Pipeline2" :type "post"}]} ;implied {:name "CartTrans-Redshift"}, and write to a RedShift table and write to the post-processing queue of Pipeline2?
                  {:origin "EmailClickstream" :targets ["Pipeline5" "RawEmailS3"]}
                  {:origin "Pipeline5" :targets ["Emailclicks-RedShift"]}]})




(defn create-dag
  [text-graph]
  (let
    [nodes (concat (mapv :package (:pipelines test-graph)) (mapv :package (:sources test-graph))
                   (mapv :package (:sinks test-graph)))
     edges

     ]

    )

  )