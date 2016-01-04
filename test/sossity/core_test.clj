(ns sossity.core-test
  (:require [sossity.core :refer :all]
            [clojure.test :refer :all]
            [cheshire.core :refer :all]))

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

(def create-parsed-output
  (decode (create-terraform-json small-test-gr) true))

(def sm-provider {:google             {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                                       :project     "hx-test",
                                       :region      "europe-west1-c"},
                  :googlecli {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                              :project     "hx-test",
                              :region      "europe-west1-c"}})

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
  (let [g create-parsed-output]
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

