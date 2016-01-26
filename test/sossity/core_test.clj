(ns sossity.core-test
  (:require [sossity.core :refer :all]
            [clojure.test :refer :all]
            [cheshire.core :refer :all]))

(defn create-parsed-output [g]
  (cheshire.core/decode (create-terraform-json
                         g) true))

(def small-test-gr
  {:config    {:remote-composer-classpath "/usr/local/lib/angleddream-bundled.jar"
               :local-angleddream-path    "/home/bradford/proj/angled-dream/target/angleddream-bundled-0.1-ALPHA.jar"
               :remote-libs-path          "/usr/local/lib"
               :test-output "/home/bradford/proj/sossity/test/"
               :error-buckets true}
   :cluster   {:name        "hxhstack" :initial_node_count 3 :master_auth {:username "hx" :password "hstack"}
               :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                            "https://www.googleapis.com/auth/devstorage.read_only"
                                            "https://www.googleapis.com/auth/logging.write"
                                            "https://www.googleapis.com/auth/monitoring"
                                            "https://www.googleapis.com/auth/cloud-platform"]
                             :machine_type "n1-standard-4"}}
   :opts      {:maxNumWorkers   "1" :numWorkers "1" :zone "europe-west1-c" :workerMachineType "n1-standard-1"
               :stagingLocation "gs://hx-test/staging-eu"}
   :provider  {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}" :project "hx-test"}
   :pipelines {"pipeline1bts"
               {:transform-jar   "pipeline3.jar"
                :local-jar-path  "/home/bradford/proj/pipeline-examples/pipeline3/target/pipeline3-bundled-0.1-ALPHA.jar"
                :composer-class "com.acacia.pipeline3.AppendStringComposer"}}
   :sources   {"stream1bts" {:type "kub" :test-input "/home/bradford/proj/pipeline-examples/test-inputs/stream1bts.json"}}
   :sinks     {"sink1bts" {:type "gcs" :bucket "sink1-bts-test"}}
   :edges     [{:origin "stream1bts" :targets ["pipeline1bts"]}
               {:origin "pipeline1bts" :targets ["sink1bts"]}]})

(def sm-provider {:google  {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                            :project     "hx-test",
                            :region      "europe-west1-c"},
                  :googlecli {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                              :project     "hx-test",
                              :region      "europe-west1-c"}})
(def sm-pubsub-tops
  {:stream1bts-to-pipeline1bts {:name "stream1bts-to-pipeline1bts"},
   :pipeline1bts-to-pipeline1bts-error {:name "pipeline1bts-to-pipeline1bts-error"},
   :pipeline1bts-to-sink1bts {:name "pipeline1bts-to-sink1bts"},
   :sink1bts-to-sink1bts-error {:name "sink1bts-to-sink1bts-error"}})

(def sm-pubsub-subs  {:pipeline1bts-to-pipeline1bts-error_sub {:name "pipeline1bts-to-pipeline1bts-error_sub",
                                                               :topic "pipeline1bts-to-pipeline1bts-error",
                                                               :depends_on ["google_pubsub_topic.pipeline1bts-to-pipeline1bts-error"]},
                      :pipeline1bts-to-sink1bts_sub {:name "pipeline1bts-to-sink1bts_sub",
                                                     :topic "pipeline1bts-to-sink1bts",
                                                     :depends_on ["google_pubsub_topic.pipeline1bts-to-sink1bts"]},
                      :sink1bts-to-sink1bts-error_sub {:name "sink1bts-to-sink1bts-error_sub",
                                                       :topic "sink1bts-to-sink1bts-error",
                                                       :depends_on ["google_pubsub_topic.sink1bts-to-sink1bts-error"]}})

(def sm-container-cluster {:hx_fstack_cluster {:name "hxhstack",
                                               :initial_node_count 3,
                                               :master_auth {:username "hx", :password "hstack"},
                                               :zone "europe-west1-c",
                                               :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                                                            "https://www.googleapis.com/auth/devstorage.read_only"
                                                                            "https://www.googleapis.com/auth/logging.write"
                                                                            "https://www.googleapis.com/auth/monitoring"
                                                                            "https://www.googleapis.com/auth/cloud-platform"]
                                                             :machine_type "n1-standard-4"}}})

(def sm-replica-controllers {:sink1bts-sink           {:name           "sink1bts-sink",
                                                       :docker_image   "gcr.io/hx-test/store-sink",
                                                       :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                       :zone           "europe-west1-c",
                                                       :env_args       {:num_retries 3,
                                                                        :batch_size  1000,
                                                                        :proj_name   "hx-test",
                                                                        :sub_name    "pipeline1bts-to-sink1bts_sub",
                                                                        :bucket_name "sink1-bts-test"}}
                             :pipeline1bts-error-sink {:name           "pipeline1bts-error-sink",
                                                       :docker_image   "gcr.io/hx-test/store-sink",
                                                       :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                       :zone           "europe-west1-c",
                                                       :env_args       {:num_retries 3,
                                                                        :batch_size  1000,
                                                                        :proj_name   "hx-test",
                                                                        :sub_name    "pipeline1bts-to-pipeline1bts-error_sub",
                                                                        :bucket_name "pipeline1bts-error"}}
                             :sink1bts-error-sink     {:name           "sink1bts-error-sink",
                                                       :docker_image   "gcr.io/hx-test/store-sink",
                                                       :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                       :zone           "europe-west1-c",
                                                       :env_args       {:num_retries 3,
                                                                        :batch_size  1000,
                                                                        :proj_name   "hx-test",
                                                                        :sub_name    "sink1bts-to-sink1bts-error_sub",
                                                                        :bucket_name "sink1bts-error"}}})

(def sm-bucket {:pipeline1bts-error {:name          "pipeline1bts-error",
                                     :force_destroy true,
                                     :location      "EU"},
                :sink1-bts-test     {:name "sink1-bts-test", :force_destroy true, :location "EU"},
                :sink1bts-error     {:name "sink1bts-error", :force_destroy true, :location "EU"}})

(def sm-appengine {:stream1bts {:moduleName     "stream1bts",
                                :version        "init",
                                :gstorageKey    "hxtest-1.0-SNAPSHOT",
                                :gstorageBucket "build-artifacts-public-eu",
                                :scaling        {:minIdleInstances  1,
                                                 :maxIdleInstances  1,
                                                 :minPendingLatency "3s",
                                                 :maxPendingLatency "6s"},
                                :topicName      "projects/hx-test/topics/stream1bts-to-pipeline1bts"}})

(def sm-dataflows {:pipeline1bts {:name "pipeline1bts",
                                  :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib/pipeline3.jar",
                                  :class "com.acacia.angleddream.Main",
                                  :depends_on ["google_pubsub_topic.pipeline1bts-to-sink1bts"
                                               "googleappengine_app.stream1bts"
                                               "google_pubsub_topic.pipeline1bts-to-pipeline1bts-error"
                                               "google_pubsub_topic.stream1bts-to-pipeline1bts"],
                                  :optional_args {:pubsubTopic "projects/hx-test/topics/stream1bts-to-pipeline1bts",
                                                  :pipelineName "pipeline1bts",
                                                  :errorPipelineName "projects/hx-test/topics/pipeline1bts-to-pipeline1bts-error",
                                                  :outputTopics "projects/hx-test/topics/pipeline1bts-to-sink1bts"}}})

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
    (testing "appengine nodes"
      (is (= sm-appengine (get-in g [:resource :googleappengine_app]))))
    (testing "Replica controllers"
      (is (= sm-replica-controllers (get-in g [:resource :googlecli_container_replica_controller]))))
    (testing "Storage buckets"
      (is (= sm-bucket (get-in g [:resource :google_storage_bucket]))))
    (testing "Dataflows"
      (is (= sm-dataflows (get-in g [:resource :googlecli_dataflow]))))))

(def big-test-gr
  {:config    {:remote-composer-classpath "/usr/local/lib/angleddream-bundled.jar"
               :local-angleddream-path    "/home/bradford/proj/angled-dream/target/angleddream-bundled-0.1-ALPHA.jar"
               :remote-libs-path          "/usr/local/lib"
               :error-buckets true}
   :cluster   {:name        "hxhstack" :initial_node_count 3 :master_auth {:username "hx" :password "hstack"}
               :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                            "https://www.googleapis.com/auth/devstorage.read_only"
                                            "https://www.googleapis.com/auth/logging.write"
                                            "https://www.googleapis.com/auth/monitoring"
                                            "https://www.googleapis.com/auth/cloud-platform"]
                             :machine_type "n1-standard-4"}}
   :opts      {:maxNumWorkers   "1" :numWorkers "1" :zone "europe-west1-c" :workerMachineType "n1-standard-1"
               :stagingLocation "gs://hx-test/staging-eu"}
   :provider  {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}" :project "hx-test"}
   :pipelines {"pipeline1bts"
               {:transform-jar "/usr/local/lib/pipeline1.jar"}
               "pipeline2bts"
               {:transform-jar "/usr/local/lib/pipeline2.jar"}
               "pipeline3bts"
               {:transform-jar "/usr/local/lib/pipeline3.jar"}
               "orionpipe"
               {:transform-jar "/usr/local/lib/pipeline1.jar"}}
   :sources   {"stream1bts" {:type "kub"}
               "stream2bts" {:type "kub"}
               "orion"      {:type "kub"}}
   :sinks     {"sink1bts"  {:type "gcs" :bucket "sink1-bts-test"}
               "sink2bts"  {:type "gcs" :bucket "sink2-bts-test"}
               "sink3bts"  {:type "gcs" :bucket "sink3-bts-test"}
               "orionsink" {:type "gcs" :bucket "orionbucket"}}
   :edges     [{:origin "stream1bts" :targets ["pipeline1bts"]}
               {:origin "pipeline1bts" :targets ["pipeline2bts" "pipeline3bts"]}
               {:origin "pipeline2bts" :targets ["sink1bts" "sink3bts"]}
               {:origin "orion" :targets ["orionpipe"]}
               {:origin "orionpipe" :targets ["orionsink"]}
               {:origin "pipeline3bts" :targets ["sink2bts"]}]})

(def big-provider {:google {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                            :project "hx-test",
                            :region "europe-west1-c"},
                   :googlecli {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}",
                               :project "hx-test",
                               :region "europe-west1-c"}})

(def big-pubsub-tops  {:sink3bts-to-sink3bts-error {:name "sink3bts-to-sink3bts-error"},
                       :pipeline3bts-to-sink2bts {:name "pipeline3bts-to-sink2bts"},
                       :orion-to-orionpipe {:name "orion-to-orionpipe"},
                       :pipeline2bts-to-sink1bts {:name "pipeline2bts-to-sink1bts"},
                       :pipeline1bts-to-pipeline1bts-error {:name "pipeline1bts-to-pipeline1bts-error"},
                       :sink1bts-to-sink1bts-error {:name "sink1bts-to-sink1bts-error"},
                       :pipeline2bts-to-sink3bts {:name "pipeline2bts-to-sink3bts"},
                       :pipeline3bts-to-pipeline3bts-error {:name "pipeline3bts-to-pipeline3bts-error"},
                       :stream1bts-to-pipeline1bts {:name "stream1bts-to-pipeline1bts"},
                       :pipeline2bts-to-pipeline2bts-error {:name "pipeline2bts-to-pipeline2bts-error"},
                       :orionsink-to-orionsink-error {:name "orionsink-to-orionsink-error"},
                       :orionpipe-to-orionpipe-error {:name "orionpipe-to-orionpipe-error"},
                       :orionpipe-to-orionsink {:name "orionpipe-to-orionsink"},
                       :pipeline1bts-to-pipeline2bts {:name "pipeline1bts-to-pipeline2bts"},
                       :pipeline1bts-to-pipeline3bts {:name "pipeline1bts-to-pipeline3bts"},
                       :sink2bts-to-sink2bts-error {:name "sink2bts-to-sink2bts-error"}})

(def big-pubsub-subs
  {:pipeline2bts-to-pipeline2bts-error_sub {:name "pipeline2bts-to-pipeline2bts-error_sub",
                                            :topic "pipeline2bts-to-pipeline2bts-error",
                                            :depends_on ["google_pubsub_topic.pipeline2bts-to-pipeline2bts-error"]},
   :pipeline2bts-to-sink1bts_sub {:name "pipeline2bts-to-sink1bts_sub",
                                  :topic "pipeline2bts-to-sink1bts",
                                  :depends_on ["google_pubsub_topic.pipeline2bts-to-sink1bts"]},
   :orionsink-to-orionsink-error_sub {:name "orionsink-to-orionsink-error_sub",
                                      :topic "orionsink-to-orionsink-error",
                                      :depends_on ["google_pubsub_topic.orionsink-to-orionsink-error"]},
   :sink3bts-to-sink3bts-error_sub {:name "sink3bts-to-sink3bts-error_sub",
                                    :topic "sink3bts-to-sink3bts-error",
                                    :depends_on ["google_pubsub_topic.sink3bts-to-sink3bts-error"]},
   :sink2bts-to-sink2bts-error_sub {:name "sink2bts-to-sink2bts-error_sub",
                                    :topic "sink2bts-to-sink2bts-error",
                                    :depends_on ["google_pubsub_topic.sink2bts-to-sink2bts-error"]},
   :pipeline3bts-to-pipeline3bts-error_sub {:name "pipeline3bts-to-pipeline3bts-error_sub",
                                            :topic "pipeline3bts-to-pipeline3bts-error",
                                            :depends_on ["google_pubsub_topic.pipeline3bts-to-pipeline3bts-error"]},
   :pipeline1bts-to-pipeline1bts-error_sub {:name "pipeline1bts-to-pipeline1bts-error_sub",
                                            :topic "pipeline1bts-to-pipeline1bts-error",
                                            :depends_on ["google_pubsub_topic.pipeline1bts-to-pipeline1bts-error"]},
   :orionpipe-to-orionsink_sub {:name "orionpipe-to-orionsink_sub",
                                :topic "orionpipe-to-orionsink",
                                :depends_on ["google_pubsub_topic.orionpipe-to-orionsink"]},
   :pipeline2bts-to-sink3bts_sub {:name "pipeline2bts-to-sink3bts_sub",
                                  :topic "pipeline2bts-to-sink3bts",
                                  :depends_on ["google_pubsub_topic.pipeline2bts-to-sink3bts"]},
   :pipeline3bts-to-sink2bts_sub {:name "pipeline3bts-to-sink2bts_sub",
                                  :topic "pipeline3bts-to-sink2bts",
                                  :depends_on ["google_pubsub_topic.pipeline3bts-to-sink2bts"]},
   :sink1bts-to-sink1bts-error_sub {:name "sink1bts-to-sink1bts-error_sub",
                                    :topic "sink1bts-to-sink1bts-error",
                                    :depends_on ["google_pubsub_topic.sink1bts-to-sink1bts-error"]},
   :orionpipe-to-orionpipe-error_sub {:name "orionpipe-to-orionpipe-error_sub",
                                      :topic "orionpipe-to-orionpipe-error",
                                      :depends_on ["google_pubsub_topic.orionpipe-to-orionpipe-error"]}})

(def big-container-cluster {:hx_fstack_cluster {:name "hxhstack",
                                                :initial_node_count 3,
                                                :master_auth {:username "hx", :password "hstack"},
                                                :zone "europe-west1-c",
                                                :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                                                             "https://www.googleapis.com/auth/devstorage.read_only"
                                                                             "https://www.googleapis.com/auth/logging.write"
                                                                             "https://www.googleapis.com/auth/monitoring"
                                                                             "https://www.googleapis.com/auth/cloud-platform"]
                                                              :machine_type "n1-standard-4"}}})

(def big-replica-controllers {:sink3bts-error-sink {:name "sink3bts-error-sink",
                                                    :docker_image "gcr.io/hx-test/store-sink",
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                    :zone "europe-west1-c",
                                                    :env_args {:num_retries 3,
                                                               :batch_size 1000,
                                                               :proj_name "hx-test",
                                                               :sub_name "sink3bts-to-sink3bts-error_sub",
                                                               :bucket_name "sink3bts-error"}},
                              :sink1bts-error-sink {:name "sink1bts-error-sink",
                                                    :docker_image "gcr.io/hx-test/store-sink",
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                    :zone "europe-west1-c",
                                                    :env_args {:num_retries 3,
                                                               :batch_size 1000,
                                                               :proj_name "hx-test",
                                                               :sub_name "sink1bts-to-sink1bts-error_sub",
                                                               :bucket_name "sink1bts-error"}},
                              :sink2bts-sink {:name "sink2bts-sink",
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :batch_size 1000,
                                                         :proj_name "hx-test",
                                                         :sub_name "pipeline3bts-to-sink2bts_sub",
                                                         :bucket_name "sink2-bts-test"}},
                              :sink1bts-sink {:name "sink1bts-sink",
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :batch_size 1000,
                                                         :proj_name "hx-test",
                                                         :sub_name "pipeline2bts-to-sink1bts_sub",
                                                         :bucket_name "sink1-bts-test"}},
                              :pipeline1bts-error-sink {:name "pipeline1bts-error-sink",
                                                        :docker_image "gcr.io/hx-test/store-sink",
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                        :zone "europe-west1-c",
                                                        :env_args {:num_retries 3,
                                                                   :batch_size 1000,
                                                                   :proj_name "hx-test",
                                                                   :sub_name "pipeline1bts-to-pipeline1bts-error_sub",
                                                                   :bucket_name "pipeline1bts-error"}},
                              :orionpipe-error-sink {:name "orionpipe-error-sink",
                                                     :docker_image "gcr.io/hx-test/store-sink",
                                                     :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                     :zone "europe-west1-c",
                                                     :env_args {:num_retries 3,
                                                                :batch_size 1000,
                                                                :proj_name "hx-test",
                                                                :sub_name "orionpipe-to-orionpipe-error_sub",
                                                                :bucket_name "orionpipe-error"}},
                              :orionsink-error-sink {:name "orionsink-error-sink",
                                                     :docker_image "gcr.io/hx-test/store-sink",
                                                     :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                     :zone "europe-west1-c",
                                                     :env_args {:num_retries 3,
                                                                :batch_size 1000,
                                                                :proj_name "hx-test",
                                                                :sub_name "orionsink-to-orionsink-error_sub",
                                                                :bucket_name "orionsink-error"}},
                              :pipeline3bts-error-sink {:name "pipeline3bts-error-sink",
                                                        :docker_image "gcr.io/hx-test/store-sink",
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                        :zone "europe-west1-c",
                                                        :env_args {:num_retries 3,
                                                                   :batch_size 1000,
                                                                   :proj_name "hx-test",
                                                                   :sub_name "pipeline3bts-to-pipeline3bts-error_sub",
                                                                   :bucket_name "pipeline3bts-error"}},
                              :sink2bts-error-sink {:name "sink2bts-error-sink",
                                                    :docker_image "gcr.io/hx-test/store-sink",
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                    :zone "europe-west1-c",
                                                    :env_args {:num_retries 3,
                                                               :batch_size 1000,
                                                               :proj_name "hx-test",
                                                               :sub_name "sink2bts-to-sink2bts-error_sub",
                                                               :bucket_name "sink2bts-error"}},
                              :orionsink-sink {:name "orionsink-sink",
                                               :docker_image "gcr.io/hx-test/store-sink",
                                               :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                               :zone "europe-west1-c",
                                               :env_args {:num_retries 3,
                                                          :batch_size 1000,
                                                          :proj_name "hx-test",
                                                          :sub_name "orionpipe-to-orionsink_sub",
                                                          :bucket_name "orionbucket"}},
                              :pipeline2bts-error-sink {:name "pipeline2bts-error-sink",
                                                        :docker_image "gcr.io/hx-test/store-sink",
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                        :zone "europe-west1-c",
                                                        :env_args {:num_retries 3,
                                                                   :batch_size 1000,
                                                                   :proj_name "hx-test",
                                                                   :sub_name "pipeline2bts-to-pipeline2bts-error_sub",
                                                                   :bucket_name "pipeline2bts-error"}},
                              :sink3bts-sink {:name "sink3bts-sink",
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :batch_size 1000,
                                                         :proj_name "hx-test",
                                                         :sub_name "pipeline2bts-to-sink3bts_sub",
                                                         :bucket_name "sink3-bts-test"}}})

(def big-bucket  {:sink1bts-error {:name "sink1bts-error", :force_destroy true, :location "EU"},
                  :sink3-bts-test {:name "sink3-bts-test", :force_destroy true, :location "EU"},
                  :pipeline2bts-error {:name "pipeline2bts-error",
                                       :force_destroy true,
                                       :location "EU"},
                  :sink3bts-error {:name "sink3bts-error", :force_destroy true, :location "EU"},
                  :sink2-bts-test {:name "sink2-bts-test", :force_destroy true, :location "EU"},
                  :orionbucket {:name "orionbucket", :force_destroy true, :location "EU"},
                  :pipeline3bts-error {:name "pipeline3bts-error",
                                       :force_destroy true,
                                       :location "EU"},
                  :pipeline1bts-error {:name "pipeline1bts-error",
                                       :force_destroy true,
                                       :location "EU"},
                  :orionsink-error {:name "orionsink-error", :force_destroy true, :location "EU"},
                  :sink1-bts-test {:name "sink1-bts-test", :force_destroy true, :location "EU"},
                  :sink2bts-error {:name "sink2bts-error", :force_destroy true, :location "EU"},
                  :orionpipe-error {:name "orionpipe-error", :force_destroy true, :location "EU"}})

(def big-dataflows {:pipeline1bts {:name "pipeline1bts",
                                   :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline1.jar",
                                   :class "com.acacia.angleddream.Main",
                                   :depends_on ["google_pubsub_topic.pipeline1bts-to-pipeline3bts"
                                                "google_pubsub_topic.pipeline1bts-to-pipeline2bts"
                                                "googleappengine_app.stream1bts"
                                                "google_pubsub_topic.pipeline1bts-to-pipeline1bts-error"
                                                "google_pubsub_topic.stream1bts-to-pipeline1bts"],
                                   :optional_args {:pubsubTopic "projects/hx-test/topics/stream1bts-to-pipeline1bts",
                                                   :pipelineName "pipeline1bts",
                                                   :errorPipelineName "projects/hx-test/topics/pipeline1bts-to-pipeline1bts-error",
                                                   :outputTopics "projects/hx-test/topics/pipeline1bts-to-pipeline3bts,projects/hx-test/topics/pipeline1bts-to-pipeline2bts"}},
                    :pipeline3bts {:name "pipeline3bts",
                                   :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline3.jar",
                                   :class "com.acacia.angleddream.Main",
                                   :depends_on ["google_pubsub_topic.pipeline3bts-to-sink2bts"
                                                "googlecli_dataflow.pipeline1bts"
                                                "google_pubsub_topic.pipeline3bts-to-pipeline3bts-error"
                                                "google_pubsub_topic.pipeline1bts-to-pipeline3bts"],
                                   :optional_args {:pubsubTopic "projects/hx-test/topics/pipeline1bts-to-pipeline3bts",
                                                   :pipelineName "pipeline3bts",
                                                   :errorPipelineName "projects/hx-test/topics/pipeline3bts-to-pipeline3bts-error",
                                                   :outputTopics "projects/hx-test/topics/pipeline3bts-to-sink2bts"}},
                    :pipeline2bts {:name "pipeline2bts",
                                   :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline2.jar",
                                   :class "com.acacia.angleddream.Main",
                                   :depends_on ["google_pubsub_topic.pipeline2bts-to-sink3bts"
                                                "google_pubsub_topic.pipeline2bts-to-sink1bts"
                                                "googlecli_dataflow.pipeline1bts"
                                                "google_pubsub_topic.pipeline2bts-to-pipeline2bts-error"
                                                "google_pubsub_topic.pipeline1bts-to-pipeline2bts"],
                                   :optional_args {:pubsubTopic "projects/hx-test/topics/pipeline1bts-to-pipeline2bts",
                                                   :pipelineName "pipeline2bts",
                                                   :errorPipelineName "projects/hx-test/topics/pipeline2bts-to-pipeline2bts-error",
                                                   :outputTopics "projects/hx-test/topics/pipeline2bts-to-sink3bts,projects/hx-test/topics/pipeline2bts-to-sink1bts"}},
                    :orionpipe {:name "orionpipe",
                                :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline1.jar",
                                :class "com.acacia.angleddream.Main",
                                :depends_on ["google_pubsub_topic.orionpipe-to-orionsink"
                                             "googleappengine_app.orion"
                                             "google_pubsub_topic.orionpipe-to-orionpipe-error"
                                             "google_pubsub_topic.orion-to-orionpipe"],
                                :optional_args {:pubsubTopic "projects/hx-test/topics/orion-to-orionpipe",
                                                :pipelineName "orionpipe",
                                                :errorPipelineName "projects/hx-test/topics/orionpipe-to-orionpipe-error",
                                                :outputTopics "projects/hx-test/topics/orionpipe-to-orionsink"}}})

(def big-appengine {:stream1bts {:moduleName     "stream1bts",
                                 :version        "init",
                                 :gstorageKey    "hxtest-1.0-SNAPSHOT",
                                 :gstorageBucket "build-artifacts-public-eu",
                                 :scaling        {:minIdleInstances  1,
                                                  :maxIdleInstances  1,
                                                  :minPendingLatency "3s",
                                                  :maxPendingLatency "6s"},
                                 :topicName      "projects/hx-test/topics/stream1bts-to-pipeline1bts"},
                    :orion      {:moduleName     "orion",
                                 :version        "init",
                                 :gstorageKey    "hxtest-1.0-SNAPSHOT",
                                 :gstorageBucket "build-artifacts-public-eu",
                                 :scaling        {:minIdleInstances  1,
                                                  :maxIdleInstances  1,
                                                  :minPendingLatency "3s",
                                                  :maxPendingLatency "6s"},
                                 :topicName      "projects/hx-test/topics/orion-to-orionpipe"}})

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
    (testing "app engine"
      (is (= big-appengine (get-in g [:resource :googleappengine_app]))))
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
                                  :errorPipelineName "projects/hx-test/topics/orionbq_err",
                                  :bigQueryDataset "hx-test",
                                  :pubsubTopic "projects/hx-test/topics/orionbq_in",
                                  :numWorkers "1",
                                  :pipelineName "orionbq",
                                  :maxNumWorkers "1"}})

(def bq-pubsub-tops (->
                     big-pubsub-tops
                     (assoc :orionbq_err {:name "orionbq_err"})
                     (assoc :orionbq_in {:name "orionbq_in"})))

;NOTE -- need to have some kind of 'refresh' workflow since we may be defing/undefing in a work session

(deftest add-bq
  #_(let [g (create-parsed-output bq-graph)]
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
          (is (= big-bucket (get-in g [:resource :google_storage_bucket])))))))

