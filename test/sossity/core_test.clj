(ns sossity.core-test
  (:require [clojure.test :refer :all]
            [sossity.core :refer :all]
            [loom.graph :refer :all]
            [loom.alg :refer :all]
            [loom.io :refer :all]
            [loom.attr :refer :all]
            [cheshire.core :refer :all]
            [traversy.lens :as t :refer :all :exclude [view update combine]]))

(deftest parse-graph
  (testing "Parse the graph into a nice thing"
    (is (= 0 1))))

;need to use prismatic schema to verify map


(def test-graph  ;each pipeline can only be defined in an origin once? ;IDEA: if a pipeline has 2 predecessors, combine their writes into one queue?
  {
   :opts      {:classpaths ["/home/bradfordstephens/proj", "/home/bradfordstephens/proj/transforms"] ;where all the jar files live. no trailing slash. may be overriden by env var in production? also be sure to build thick jars from angled-dream for deps
                :maxNumwWorkers "1" :numWorkers "1" :zone "europe-west1-c" :workerMachineType "n1-standard-1" :stagingLocation "gs://hx-test/staging-eu"
               }
   :provider  {:account-file "blah.json" :project "hx-test"}
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
   :sources   {"ClickStream"             {:type "cdf"}
               "ShoppingCartClickStream" {:type "kub"}
               "CartTransaction"         {:type "kub"}
               "EmailClickstream"        {:type "kub"}
               }
   :sinks     {"S3Sink"               {:type "kub"}
               "EnhancedAdwordsSink"  {:type "kub"}
               "CartTrans-BigQuery"   {:type "cdf"}
               "EmailClicks-BigQuery" {:type "cdf"}
               "RawEmailS3"           {:type "cdf"}}
   :edges     [{:origin "Pipeline1" :targets ["Pipeline2"]}
               {:origin "ClickStream" :targets ["Pipeline1"]}
               {:origin        "Pipeline2"
                :error-sink    :google-storage ;implied sink of gcs directory "Pipeline2/errors/blah"
                :error-handler "RepairJob" ;implied source of gcs directory "Pipeline2/errors/blah"
                :targets       ["S3Sink" "EnhancedAdwordsSink"]}
               {:origin "ShoppingCartClickStream" :targets ["Pipeline3"]}
               {:origin "Pipeline3" :targets ["Pipeline2"]}
               {:origin "CartTransaction" :targets ["Pipeline4"]}
               {:origin "Pipeline4" :targets ["CartTrans-BigQuery"]} ;implied {:name "CartTrans-BigQuery"}, and write to a BigQuery table and write to the post-processing queue of Pipeline2? {"Pipeline2" :type "post"}
               {:origin "EmailClickstream" :targets ["Pipeline5" "RawEmailS3"]}
               {:origin "Pipeline5" :targets ["EmailClicks-BigQuery"]}]})

(def mini-grapha
  {"A" ["B" "C"] "B" ["D"] "C" [] "D" ["E" "F"]})

(def mini-attrsb
  {"A" {:k 1 :v "a1" :q 9}                                  ; -->reduce on "A" {:k 1} "A" {:v 2}
   "C" {:k 1 :v "c1" :q 11}})

