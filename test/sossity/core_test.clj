(ns sossity.core-test
  (:require [clojure.test :refer :all]
            [sossity.core :refer :all]
            [loom.graph :refer :all]
            [loom.alg :refer :all]
            [loom.io :refer :all]
            [loom.attr :refer :all]
            [cheshire.core :refer :all]
            [traversy.lens :as t :refer :all :exclude [view update combine]]
            )
  (:import (com.google.api.services.bigquery.model TableRow)))

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
  ((juxt (fn [x] (output-topic x)) (fn [x] (error-topic x))) node))

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

(defn create-terraform-json
  [a-graph]
  (let [g (create-dag a-graph)
        pubsubs (output-pubsub (create-pubsubs g))
        dataflows (create-dataflow-jobs g a-graph)
        ]
     (generate-string (concat pubsubs dataflows) {:pretty true})
    )


  )

(defn output-terraform-file
  [raw-json file]
    (spit raw-json file)                                    ;NOTE -- need to remove first [ and last ]
  )

(defn output-provider
  [provider-map]
  (assoc-in {} ["provider" "google"] (:provider provider-map))
  )

(defn output-pubsub
  [pubsub-map]
  (map
    ;#({"resource" {"google_pubsub" {"a" {"name" "b"}}}} )
    #(assoc-in {} ["resource" "google_pubsub" % "name"] %)
    pubsub-map))

(defn output-dataflow
  [dataflow-map])

(defn dissoc-in
  "Dissociates an entry from a nested associative structure returning a new
  nested structure. keys is a sequence of keys. Any empty maps that result
  will not be present in the new structure."
  [m [k & ks :as keys]]
  (if ks
    (if-let [nextmap (get m k)]
      (let [newmap (dissoc-in nextmap ks)]
        (if (seq newmap)
          (dissoc m k)
          (assoc m k newmap)))
      m)
    (dissoc m k)))

;parse graph
(def fields
  [["event"]
   ["owts"]
   ["owts_tmp_v2"]
   ["page_id"]
   ["page_action_id"]
   ["date"]
   ["timestamp"]
   ["orion_writetime"]
   ["client_timestamp"]
   ["protocol"]
   ["domain"]
   ["path"] ["source_ip"]])

(def lvl2-fields
  [["screen" "height"]
   ["screen" "width"]
   ["data" "step"]
   ["data" "test_name"]
   ["data" "variant"]
   ["data" "booking_ref"]])

(def lvl2-fields-b
  [{"screen" ["height" "width"]}
   {"data" ["step" "test_name" "variant" "booking_ref"]}])

(def lvl3-fields
  [["data" "resource_params" "bkg_ref"]
   ["data" "resource_params" "bref"]
   ["data" "resource_params" "bkref"]
   ["data" "action" "name"]])

(defn mm
  [coll]
  (mapv (fn [v] [(apply merge v)]) coll))

(defn combine-maps [& maps]
  (apply merge-with combine-maps maps))

(defn extract
  [raw-string]
  (let [data (decode raw-string)]
    (if (= (get data "service") "webapp")
      (let [lv1 (apply merge (mapv #(select-keys data %) fields))
            lv2 {"screen" {"height" (get-in data ["screen" "height"])
                           "width" (get-in data ["screen" "height"])}
                 "data" {"step" (get-in data ["data" "step"])
                         "test_name" (get-in data ["data" "test_name"])
                         "variant" (get-in data ["data" "variant"])
                         "booking_ref" (get-in data ["data" "booking_ref"])
                         "resource_params" {"bkg_ref" (get-in data ["data" "resource_params" "bkg_ref"])
                                            "bref" (get-in data ["data" "resource_params" "bref"])
                                            "bkref" (get-in data ["data" "resource_params" "bkref"])}
                         "action" {"name" (get-in data ["data" "action" "name"])}}}]

        (apply merge lv1 lv2)))))

(defn extract-clean [raw-string]
  (apply merge (extract raw-string)))

(def test-string
  "{\"browser\": {\"cookie_enabled\": \"True\",\n              \"inner_height\": 714,\n              \"inner_width\": 1440,\n              \"page_height\": 714,\n              \"user_agent\": \"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.122 Safari/537.36\"},\n \"client_timestamp\": 1412942308097,\n \"date\": \"2014-10-10\",\n \"domain\": \"v2staging.holidayextras.co.uk\",\n \"email\": \"andrew.castle+testtest@holidayextras.com\",\n \"event\": \"Loaded a Page\",\n \"event_type\": \"availability\",\n \"is_client\": \"True\",\n \"orion_timestamp\": \"1412942100000\",\n \"orion_uuid\": \"09084093-a810-48f6-bcde-593de9ad5867\",\n \"orion_writetime\": 1412942307234,\n \"owts\": \"75b6f1752fd946ef75c3bc4b8ed9a47e\",\n \"owts_tmp\": \"e8b0ed6a67e0a4f10384421cf2163f9b\",\n \"page_id\": \"cda1dc16c226c1a4c576504f31e9c165\",\n \"path\": \"/carpark\",\n \"port\": \"\",\n \"product_type\": \"carpark\",\n \"protocol\": \"https\",\n \"referrer\": \"http://www.holidayextras.co.uk/\",\n \"request_params\": {\"arrival_date\": \"NaN-NaN-NaN\",\n                     \"arrival_time\": \"NaN:NaN:NaN\",\n                     \"customer_ref\": \"\",\n                     \"depart\": \"LGW\",\n                     \"depart_date\": \"2014-10-17\",\n                     \"depart_time\": \"01:00:00\",\n                     \"flight\": \"TBC\",\n                     \"park_to\": \"13:00\",\n                     \"rrive\": \"\",\n                     \"term\": \"\",\n                     \"terminal\": \"\"},\n \"screen\": {\"height\": 900, \"pixelDepth\": 24, \"width\": 1440},\n \"server_ip\": \"10.210.175.195\",\n \"service\": \"webapp\",\n \"source_ip\": \"62.254.236.250\",\n \"time\": \"12:58:28\",\n \"title\": \"Parking 17 October 2014 | HolidayExtras.com\",\n \"url\": \"https://v2staging.holidayextras.co.uk/?agent=WEB1&ppcmsg=\"}")

(def test-2
  "{\"product_type\":\"carpark\",\"location\":\"LPL\",\"sort_criteria\":\"recommended\",\"sort_order\":\"asc\",\"products\":[{\"code\":\"HPLPY2\",\"price\":2510,\"position\":1},{\"code\":\"HPLPY1\",\"price\":2672,\"position\":2},{\"code\":\"HPLPX9\",\"price\":2915,\"position\":3},{\"code\":\"HPLPL1\",\"price\":3158,\"position\":4},{\"code\":\"HPLPY3\",\"price\":2429,\"position\":5},{\"code\":\"HPLPX1\",\"price\":5759,\"position\":6},{\"code\":\"HPLPL0\",\"price\":6209,\"position\":7},{\"code\":\"HPLPX8\",\"price\":2699,\"position\":8},{\"code\":\"HPLPL6\",\"price\":2789,\"position\":9},{\"code\":\"HPLPY5\",\"price\":5400,\"position\":10},{\"code\":\"HPLPX3\",\"price\":5400,\"position\":11},{\"code\":\"HPLPY8\",\"price\":4616,\"position\":12}],\"path\":\"/carpark\",\"referrer\":\"http://www.holidayextras.co.uk/email/email-parking.html?prodcode=ADM-HX-PKG-APR15-2&reset=1&agent=WJ389&email=ladee.ja7ne@yahoo.co.uk&location=MAN&linkid=05\",\"title\":\"Liverpool Parking 15 October 2015 | HolidayExtras.com\",\"url\":\"https://v2.holidayextras.co.uk/?agent=WJ389&ppcmsg=&lang=en\",\"service\":\"webapp\",\"environment\":\"production\",\"domain\":\"v2.holidayextras.co.uk\",\"date\":\"2015-05-06\",\"time\":\"01:00:00\",\"client_timestamp\":1430870400403,\"is_client\":true,\"owts\":\"bbe13e8bb011c8d369b7257218b31fab\",\"owts_tmp\":\"65c85875ab8aa1f1d00a936bf9e0feed\",\"email\":\"ladee.ja7ne@yahoo.co.uk\",\"page_id\":\"2401fd1f8c494fa2a9e6f3463a6ef695\",\"page_type\":\"availability\",\"event\":\"load\",\"screen\":{\"pixelDepth\":32,\"width\":320,\"height\":480},\"browser\":{\"user_agent\":\"Mozilla/5.0 (iPhone; CPU iPhone OS 8_2 like Mac OS X) AppleWebKit/600.1.4 (KHTML, like Gecko) Version/8.0 Mobile/12D508 Safari/600.1.4\",\"cookie_enabled\":true,\"inner_width\":320,\"inner_height\":372,\"page_height\":372},\"request_params\":{\"agent\":\"WJ389\",\"ppcmsg\":\"\",\"lang\":\"en\",\"arrive\":\"\",\"customer_ref\":\"\",\"flight\":\"\",\"park_to\":\"19:30\",\"term\":\"\",\"terminal\":\"\",\"arrival_date\":\"NaN-NaN-NaN\",\"arrival_time\":\"NaN:NaN:NaN\",\"depart_date\":\"2015-10-15\",\"depart_time\":\"01:00:00\",\"location\":\"LPL\"},\"url_hash\":\"#carpark?arrive=&customer_ref=&depart=LPL&flight=&in=2015-10-23&out=2015-10-15&park_from=17:00&park_to=19:30&term=&terminal=\",\"referrer_params\":{\"prodcode\":\"ADM-HX-PKG-APR15-2\",\"reset\":\"1\",\"agent\":\"WJ389\",\"email\":\"ladee.ja7ne@yahoo.co.uk\",\"location\":\"MAN\",\"linkid\":\"05\",\"search\":\"?prodcode=ADM-HX-PKG-APR15-2&reset=1&agent=WJ389&email=ladee.ja7ne@yahoo.co.uk&location=MAN&linkid=05\",\"url\":\"http://www.holidayextras.co.uk/email/email-parking.html?prodcode=ADM-HX-PKG-APR15-2&reset=1&agent=WJ389&email=ladee.ja7ne@yahoo.co.uk&location=MAN&linkid=05\",\"protocol\":\"http\",\"path\":\"/email/email-parking.html\",\"domain\":\"www.holidayextras.co.uk\"},\"protocol\":\"https\",\"port\":\"\",\"server_ip\":\"10.208.49.181\",\"orion_uuid\":\"799faea5-0711-479f-8631-61de7eee0cab\",\"source_ip\":\"82.40.9.234\",\"orion_timestamp\":\"1430870400000\",\"orion_writetime\":1430870401089}\n")