(ns sossity.core-test
  (:require [sossity.core :refer :all]
            [clojure.test :refer :all]
            [cheshire.core :refer :all]
            [clojure.tools.namespace.repl :refer :all]))

(defn create-parsed-output [g]
  (cheshire.core/decode (create-terraform-json
           g) true))

(def small-test-gr
  {:cluster   {:name "hxhstack" :initial_node_count 3 :master_auth {:username "hx" :password "hstack"}}
   :opts      {:composer-classpath ["/usr/local/lib/angleddream-bundled.jar"]
               :maxNumWorkers      "1" :numWorkers "1" :zone "europe-west1-c" :workerMachineType "n1-standard-1"
               :stagingLocation    "gs://hx-test/staging-eu"}
   :provider           {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}" :project "hx-test"}
   :pipelines {"pipeline1bts"
               {:transform-graph ["/usr/local/lib/pipeline1.jar"]}}
   :sources   {"stream1bts" {:type "kub"}}
   :sinks     {"sink1bts" {:type "gcs" :bucket "sink1-bts-test"}}
   :edges     [{:origin "stream1bts" :targets ["pipeline1bts"]}
               {:origin "pipeline1bts" :targets ["sink1bts"]}]})

(def sm-provider {:google  {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                            :project     "hx-test",
                            :region      "europe-west1-c"},
                  :googlecli {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                              :project     "hx-test",
                              :region      "europe-west1-c"}})

{:stream1bts_err {:name "stream1bts_err"}, :orion_out {:name "orion_out"}, :pipeline2bts_in {:name "pipeline2bts_in"}, :orionpipe_in {:name "orionpipe_in"}, :sink1bts_in {:name "sink1bts_in"}, :orion_err {:name "orion_err"}, :pipeline2bts_err {:name "pipeline2bts_err"}, :sink3bts_in {:name "sink3bts_in"}, :orionsink_err {:name "orionsink_err"}, :sink2bts_in {:name "sink2bts_in"}, :pipeline3bts_err {:name "pipeline3bts_err"}, :stream1bts_out {:name "stream1bts_out"}, :sink1bts_err {:name "sink1bts_err"}, :pipeline1bts_in {:name "pipeline1bts_in"}, :sink3bts_err {:name "sink3bts_err"}, :sink2bts_err {:name "sink2bts_err"}, :orionpipe_err {:name "orionpipe_err"}, :pipeline1bts_err {:name "pipeline1bts_err"}, :pipeline3bts_in {:name "pipeline3bts_in"}, :orionsink_in {:name "orionsink_in"}}
{:stream1bts_err {:name "stream1bts_err"}, :orion_out {:name "orion_out"}, :pipeline2bts_in {:name "pipeline2bts_in"}, :orionbq_err {:name "orionbq_err"}, :orionpipe_in {:name "orionpipe_in"}, :sink1bts_in {:name "sink1bts_in"}, :orion_err {:name "orion_err"}, :pipeline2bts_err {:name "pipeline2bts_err"}, :sink3bts_in {:name "sink3bts_in"}, :orionsink_err {:name "orionsink_err"}, :sink2bts_in {:name "sink2bts_in"}, :pipeline3bts_err {:name "pipeline3bts_err"}, :stream1bts_out {:name "stream1bts_out"}, :sink1bts_err {:name "sink1bts_err"}, :pipeline1bts_in {:name "pipeline1bts_in"}, :sink3bts_err {:name "sink3bts_err"}, :sink2bts_err {:name "sink2bts_err"}, :orionpipe_err {:name "orionpipe_err"}, :pipeline1bts_err {:name "pipeline1bts_err"}, :orionbq_in {:name "orionbq_in"}, :pipeline3bts_in {:name "pipeline3bts_in"}, :orionsink_in {:name "orionsink_in"}}

(def sm-pubsub-tops
  {:pipeline1bts_in {:name "pipeline1bts_in"},
   :pipeline1bts_err {:name "pipeline1bts_err"},
   :sink1bts_in {:name "sink1bts_in"},
   :sink1bts_err {:name "sink1bts_err"},
   :stream1bts_out {:name "stream1bts_out"},
   :stream1bts_err {:name "stream1bts_err"}})

(def sm-pubsub-subs  {:sink1bts_sub {:name "sink1bts_sub",
                                     :topic "sink1bts_in",
                                     :depends_on ["google_pubsub_topic.sink1bts_in"]}})

(def sm-container-cluster {:hx_fstack_cluster {:name "hxhstack",
                                               :initial_node_count 3,
                                               :master_auth {:username "hx", :password "hstack"},
                                               :zone "europe-west1-c",
                                               :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                                                            "https://www.googleapis.com/auth/devstorage.read_only"
                                                                            "https://www.googleapis.com/auth/logging.write"
                                                                            "https://www.googleapis.com/auth/monitoring"
                                                                            "https://www.googleapis.com/auth/cloud-platform"]}}})

(def sm-replica-controllers {:stream1bts-source {:name "stream1bts-source",
                                                 :docker_image "gcr.io/hx-test/source-master",
                                                 :external_port "8080",
                                                 :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                 :zone "europe-west1-c",
                                                 :env_args {:post_route "/stream1bts/post",
                                                            :health_route "/stream1bts/health",
                                                            :stream_name "projects/hx-test/topics/stream1bts_out"}},
                             :sink1bts-sink {:name "sink1bts-sink",
                                             :docker_image "gcr.io/hx-test/store-sink",
                                             :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                             :zone "europe-west1-c",
                                             :env_args {:num_retries 3,
                                                        :batch_size 10000,
                                                        :proj_name "hx-test",
                                                        :sub_name "sink1bts_sub",
                                                        :bucket_name "sink1-bts-test"}}})

(def sm-bucket {:sink1-bts-test {:name "sink1-bts-test", :force_destroy true, :location "EU"}})

(def sm-dataflows {:pipeline1bts {:name "pipeline1bts",
                                  :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib/pipeline1.jar",
                                  :class "com.acacia.angleddream.Main",
                                  :depends_on ["google_pubsub_topic.sink1bts_in"
                                               "googlecli_container_replica_controller.stream1bts-source"
                                               "google_pubsub_topic.pipeline1bts_err"
                                               "google_pubsub_topic.stream1bts_out"],
                                  :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                  :zone "europe-west1-c",
                                                  :workerMachineType "n1-standard-1",
                                                  :errorPipelineName "pipeline1bts_err",
                                                  :pubsubTopic "projects/hx-test/topics/stream1bts_out",
                                                  :numWorkers "1",
                                                  :outputTopics "projects/hx-test/topics/sink1bts_in",
                                                  :pipelineName "pipeline1bts",
                                                  :maxNumWorkers "1"}}})

(deftest test-small-graph
  (let [g (create-parsed-output small-test-gr)]
    (testing "Test the minimum viable graph provider"
      (is (= sm-provider (:provider g))))
    (testing "Pubsub topics"
      (is (= sm-pubsub-tops (get-in g [:resource :google_pubsub_topic]))))
    (testing "Pusub subs"
      (is (= sm-pubsub-subs (get-in g [:resource :google_pubsub_subscription]))))
    (testing "container cluster"
      (is (= sm-container-cluster (get-in g [:resource :google_container_cluster]))))
    (testing "Replica controllers"
      (is (= sm-replica-controllers (get-in g [:resource :googlecli_container_replica_controller]))))
    (testing "Storage buckets"
      (is (= sm-bucket (get-in g [:resource :google_storage_bucket]))))
    (testing "Dataflows"
      (is (= sm-dataflows (get-in g [:resource :googlecli_dataflow]))))))

(def big-test-gr
  {:cluster   {:name "hxhstack" :initial_node_count 3 :master_auth {:username "hx" :password "hstack"}}
   :opts      {:composer-classpath    ["/usr/local/lib/angleddream-bundled.jar"] ;where all the jar files live. no trailing slash. may be overriden by env var in production? also be sure to build thick jars from angled-dream for deps
               :maxNumWorkers "1" :numWorkers "1" :zone "europe-west1-c" :workerMachineType "n1-standard-1"
               :stagingLocation "gs://hx-test/staging-eu"}
   :provider  {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"  :project "hx-test"}
   :pipelines {"pipeline1bts"
               {:transform-graph ["/usr/local/lib/pipeline1.jar"]}
               "pipeline2bts"
               {:transform-graph ["/usr/local/lib/pipeline2.jar"]}
               "pipeline3bts"
               {:transform-graph ["/usr/local/lib/pipeline3.jar"]}
               "orionpipe"
               {:transform-graph ["/usr/local/lib/pipeline1.jar"]}}
   :sources   {"stream1bts" {:type "kub"}
               "stream2bts" {:type "kub"}
               "orion" {:type "kub"}}
   :sinks     {"sink1bts" {:type "gcs" :bucket "sink1-bts-test"}
               "sink2bts" {:type "gcs" :bucket "sink2-bts-test"}
               "sink3bts" {:type "gcs" :bucket "sink3-bts-test"}
               "orionsink" {:type "gcs" :bucket "orionbucket"}}
   :edges     [{:origin "stream1bts" :targets ["pipeline1bts"]}
               {:origin "pipeline1bts" :targets ["pipeline2bts" "pipeline3bts"]}
               {:origin "pipeline2bts" :targets ["sink1bts"  "sink3bts"]}
               {:origin "orion" :targets ["orionpipe"]}
               {:origin "orionpipe" :targets ["orionsink"]}
               {:origin "pipeline3bts" :targets ["sink2bts"]}]})

(def big-provider {:google {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                            :project "hx-test",
                            :region "europe-west1-c"},
                   :googlecli {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                               :project "hx-test",
                               :region "europe-west1-c"}})

(def big-pubsub-tops {:stream1bts_err {:name "stream1bts_err"},
                      :orion_out {:name "orion_out"},
                      :pipeline2bts_in {:name "pipeline2bts_in"},
                      :orionpipe_in {:name "orionpipe_in"},
                      :sink1bts_in {:name "sink1bts_in"},
                      :orion_err {:name "orion_err"},
                      :pipeline2bts_err {:name "pipeline2bts_err"},
                      :sink3bts_in {:name "sink3bts_in"},
                      :orionsink_err {:name "orionsink_err"},
                      :sink2bts_in {:name "sink2bts_in"},
                      :pipeline3bts_err {:name "pipeline3bts_err"},
                      :stream1bts_out {:name "stream1bts_out"},
                      :sink1bts_err {:name "sink1bts_err"},
                      :pipeline1bts_in {:name "pipeline1bts_in"},
                      :sink3bts_err {:name "sink3bts_err"},
                      :sink2bts_err {:name "sink2bts_err"},
                      :orionpipe_err {:name "orionpipe_err"},
                      :pipeline1bts_err {:name "pipeline1bts_err"},
                      :pipeline3bts_in {:name "pipeline3bts_in"},
                      :orionsink_in {:name "orionsink_in"}})

(def big-pubsub-subs
  {:sink1bts_sub {:name "sink1bts_sub",
                  :topic "sink1bts_in",
                  :depends_on ["google_pubsub_topic.sink1bts_in"]},
   :sink2bts_sub {:name "sink2bts_sub",
                  :topic "sink2bts_in",
                  :depends_on ["google_pubsub_topic.sink2bts_in"]},
   :sink3bts_sub {:name "sink3bts_sub",
                  :topic "sink3bts_in",
                  :depends_on ["google_pubsub_topic.sink3bts_in"]},
   :orionsink_sub {:name "orionsink_sub",
                   :topic "orionsink_in",
                   :depends_on ["google_pubsub_topic.orionsink_in"]}})

(def big-container-cluster {:hx_fstack_cluster {:name "hxhstack",
                                                :initial_node_count 3,
                                                :master_auth {:username "hx", :password "hstack"},
                                                :zone "europe-west1-c",
                                                :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                                                             "https://www.googleapis.com/auth/devstorage.read_only"
                                                                             "https://www.googleapis.com/auth/logging.write"
                                                                             "https://www.googleapis.com/auth/monitoring"
                                                                             "https://www.googleapis.com/auth/cloud-platform"]}}})

(def big-replica-controllers {:stream1bts-source {:name "stream1bts-source",
                                                  :docker_image "gcr.io/hx-test/source-master",
                                                  :external_port "8080",
                                                  :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                  :zone "europe-west1-c",
                                                  :env_args {:post_route "/stream1bts/post",
                                                             :health_route "/stream1bts/health",
                                                             :stream_name "projects/hx-test/topics/stream1bts_out"}},
                              :stream2bts-source {:name "stream2bts-source",
                                                  :docker_image "gcr.io/hx-test/source-master",
                                                  :external_port "8080",
                                                  :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                  :zone "europe-west1-c",
                                                  :env_args {:post_route "/stream2bts/post",
                                                             :health_route "/stream2bts/health",
                                                             :stream_name "projects/hx-test/topics/stream2bts_out"}},
                              :orion-source {:name "orion-source",
                                             :docker_image "gcr.io/hx-test/source-master",
                                             :external_port "8080",
                                             :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                             :zone "europe-west1-c",
                                             :env_args {:post_route "/orion/post",
                                                        :health_route "/orion/health",
                                                        :stream_name "projects/hx-test/topics/orion_out"}},
                              :sink1bts-sink {:name "sink1bts-sink",
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :batch_size 10000,
                                                         :proj_name "hx-test",
                                                         :sub_name "sink1bts_sub",
                                                         :bucket_name "sink1-bts-test"}},
                              :sink2bts-sink {:name "sink2bts-sink",
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :batch_size 10000,
                                                         :proj_name "hx-test",
                                                         :sub_name "sink2bts_sub",
                                                         :bucket_name "sink2-bts-test"}},
                              :sink3bts-sink {:name "sink3bts-sink",
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :batch_size 10000,
                                                         :proj_name "hx-test",
                                                         :sub_name "sink3bts_sub",
                                                         :bucket_name "sink3-bts-test"}},
                              :orionsink-sink {:name "orionsink-sink",
                                               :docker_image "gcr.io/hx-test/store-sink",
                                               :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                               :zone "europe-west1-c",
                                               :env_args {:num_retries 3,
                                                          :batch_size 10000,
                                                          :proj_name "hx-test",
                                                          :sub_name "orionsink_sub",
                                                          :bucket_name "orionbucket"}}})

(def big-bucket  {:sink1-bts-test {:name "sink1-bts-test", :force_destroy true, :location "EU"},
                  :sink2-bts-test {:name "sink2-bts-test", :force_destroy true, :location "EU"},
                  :sink3-bts-test {:name "sink3-bts-test", :force_destroy true, :location "EU"},
                  :orionbucket {:name "orionbucket", :force_destroy true, :location "EU"}})

(def big-dataflows {:pipeline1bts {:name "pipeline1bts",
                                   :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib/pipeline1.jar",
                                   :class "com.acacia.angleddream.Main",
                                   :depends_on ["google_pubsub_topic.pipeline3bts_in"
                                                "google_pubsub_topic.pipeline2bts_in"
                                                "googlecli_container_replica_controller.stream1bts-source"
                                                "google_pubsub_topic.pipeline1bts_err"
                                                "google_pubsub_topic.stream1bts_out"],
                                   :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                   :zone "europe-west1-c",
                                                   :workerMachineType "n1-standard-1",
                                                   :errorPipelineName "pipeline1bts_err",
                                                   :pubsubTopic "projects/hx-test/topics/stream1bts_out",
                                                   :numWorkers "1",
                                                   :outputTopics "projects/hx-test/topics/pipeline3bts_in,projects/hx-test/topics/pipeline2bts_in",
                                                   :pipelineName "pipeline1bts",
                                                   :maxNumWorkers "1"}},
                    :pipeline3bts {:name "pipeline3bts",
                                   :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib/pipeline3.jar",
                                   :class "com.acacia.angleddream.Main",
                                   :depends_on ["google_pubsub_topic.sink2bts_in"
                                                "googlecli_dataflow.pipeline1bts"
                                                "google_pubsub_topic.pipeline3bts_err"
                                                "google_pubsub_topic.pipeline3bts_in"],
                                   :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                   :zone "europe-west1-c",
                                                   :workerMachineType "n1-standard-1",
                                                   :errorPipelineName "pipeline3bts_err",
                                                   :pubsubTopic "projects/hx-test/topics/pipeline3bts_in",
                                                   :numWorkers "1",
                                                   :outputTopics "projects/hx-test/topics/sink2bts_in",
                                                   :pipelineName "pipeline3bts",
                                                   :maxNumWorkers "1"}},
                    :pipeline2bts {:name "pipeline2bts",
                                   :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib/pipeline2.jar",
                                   :class "com.acacia.angleddream.Main",
                                   :depends_on ["google_pubsub_topic.sink3bts_in"
                                                "google_pubsub_topic.sink1bts_in"
                                                "googlecli_dataflow.pipeline1bts"
                                                "google_pubsub_topic.pipeline2bts_err"
                                                "google_pubsub_topic.pipeline2bts_in"],
                                   :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                   :zone "europe-west1-c",
                                                   :workerMachineType "n1-standard-1",
                                                   :errorPipelineName "pipeline2bts_err",
                                                   :pubsubTopic "projects/hx-test/topics/pipeline2bts_in",
                                                   :numWorkers "1",
                                                   :outputTopics "projects/hx-test/topics/sink3bts_in,projects/hx-test/topics/sink1bts_in",
                                                   :pipelineName "pipeline2bts",
                                                   :maxNumWorkers "1"}},
                    :orionpipe {:name "orionpipe",
                                :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib/pipeline1.jar",
                                :class "com.acacia.angleddream.Main",
                                :depends_on ["google_pubsub_topic.orionsink_in"
                                             "googlecli_container_replica_controller.orion-source"
                                             "google_pubsub_topic.orionpipe_err"
                                             "google_pubsub_topic.orion_out"],
                                :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                :zone "europe-west1-c",
                                                :workerMachineType "n1-standard-1",
                                                :errorPipelineName "orionpipe_err",
                                                :pubsubTopic "projects/hx-test/topics/orion_out",
                                                :numWorkers "1",
                                                :outputTopics "projects/hx-test/topics/orionsink_in",
                                                :pipelineName "orionpipe",
                                                :maxNumWorkers "1"}}})

(deftest test-big-graph
  (let [g (create-parsed-output big-test-gr)]
    (testing "Test the minimum viable graph provider"
      (is (= big-provider (:provider g))))
    (testing "Pubsub topics"
      (is (= big-pubsub-tops (get-in g [:resource :google_pubsub_topic]))))
    (testing "Pusub subs"
      (is (= big-pubsub-subs (get-in g [:resource :google_pubsub_subscription]))))
    (testing "container cluster"
      (is (= big-container-cluster (get-in g [:resource :google_container_cluster]))))
    (testing "Replica controllers"
      (is (= big-replica-controllers (get-in g [:resource :googlecli_container_replica_controller]))))
    (testing "Storage buckets"
      (is (= big-bucket (get-in g [:resource :google_storage_bucket]))))
    (testing "Dataflows"
      (is (= big-dataflows (get-in g [:resource :googlecli_dataflow]))))))

(def bq-graph
  (-> big-test-gr
      (assoc-in [:sinks "orionbq"] {:type "bq" :bigQueryDataset "hx-test" :bigQueryTable "hx-test"})
      (assoc-in [:edges]  [{:origin "stream1bts" :targets ["pipeline1bts"]}
                           {:origin "pipeline1bts" :targets ["pipeline2bts" "pipeline3bts"]}
                           {:origin "pipeline2bts" :targets ["sink1bts"  "sink3bts"]}
                           {:origin "orion" :targets ["orionpipe"]}
                           {:origin "orionpipe" :targets ["orionsink" "orionbq"]}
                           {:origin "pipeline3bts" :targets ["sink2bts"]}])))

(def bq-dataflow {:name "orionbq",
                  :classpath "/usr/local/lib/angleddream-bundled.jar",
                  :class "com.acacia.angleddream.Main",
                  :depends_on ["googlecli_dataflow.orionpipe"
                               "google_pubsub_topic.orionbq_err"
                               "google_pubsub_topic.orionbq_in"],
                  :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                  :zone "europe-west1-c",
                                  :workerMachineType "n1-standard-1",
                                  :bigQueryTable "hx-test",
                                  :errorPipelineName "orionbq_err",
                                  :bigQueryDataset "hx-test",
                                  :pubsubTopic "projects/hx-test/topics/orionbq_in",
                                  :numWorkers "1",
                                  :outputTopics "",
                                  :pipelineName "orionbq",
                                  :maxNumWorkers "1"}})

(def bq-pubsub-tops (->
                      big-pubsub-tops
                      (assoc :orionbq_err {:name "orionbq_err"})
                      (assoc :orionbq_in {:name "orionbq_in"})
                      ))

;NOTE -- need to have some kind of 'refresh' workflow since we may be defing/undefing in a work session

(deftest add-bq
  (let [g (create-parsed-output bq-graph)]
    (testing "Test new dataflow for bigquery"
      (is (= bq-dataflow (get-in g [:resource :googlecli_dataflow :orionbq])))
      (testing "Test the minimum viable graph provider"
        (is (= big-provider (:provider g))))
      (testing "Pubsub topics"
        (is (= bq-pubsub-tops (get-in g [:resource :google_pubsub_topic]))))
      (testing "Pusub subs"
        (is (= big-pubsub-subs (get-in g [:resource :google_pubsub_subscription]))))
      (testing "container cluster"
        (is (= big-container-cluster (get-in g [:resource :google_container_cluster]))))
      (testing "Replica controllers"
        (is (= big-replica-controllers (get-in g [:resource :googlecli_container_replica_controller]))))
      (testing "Storage buckets"
        (is (= big-bucket (get-in g [:resource :google_storage_bucket]))))
      )))

