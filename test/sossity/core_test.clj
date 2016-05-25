(ns sossity.core-test
  (:require [sossity.core :refer :all]
            [clojure.test :refer :all]
            [cheshire.core :refer :all]
            [clojure.data :refer [diff]]))

(defn create-parsed-output [g]
  (cheshire.core/decode (create-terraform-json
                         g) true))

(defn is-nil-diff? [l r]
  "tests if the diff between l and r is nil"
  (let  [d (diff l r)]
    (is (= [nil nil l] d) (str "in-l-not-r: " (first d) "\n"
                               "in-r-not-l: " (second d)))))

(def small-test-gr
  {:config     {:remote-composer-classpath     "/usr/local/lib/angleddream-bundled.jar"
                :remote-libs-path              "/usr/local/lib"
                :error-buckets                 true
                :sink-resource-version         "1"
                :source-resource-version       "1"
                :default-pipeline-machine-type "n1-standard-1"
                :appengine-gstoragekey         "hxtest-1.0-SNAPSHOT"
                :default-sink-docker-image     "gcr.io/hx-test/store-sink"
                :system-jar-info               {:angleddream {:name "angleddream-bundled-0.1-ALPHA.jar"
                                                              :pail "build-artifacts-public-eu"
                                                              :key  "angleddream"}
                                                :sossity     {:name "sossity-0.1.0-SNAPSHOT-standalone.jar"
                                                              :pail "build-artifacts-public-eu"
                                                              :key  "sossity"}}}
   :cluster    {:name        "hxhstack" :initial_node_count 3 :master_auth {:username "hx" :password "hstack"}
                :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                             "https://www.googleapis.com/auth/devstorage.read_only"
                                             "https://www.googleapis.com/auth/logging.write"
                                             "https://www.googleapis.com/auth/monitoring"
                                             "https://www.googleapis.com/auth/cloud-platform"]
                              :machine_type "n1-standard-4"}}
   :opts       {:maxNumWorkers   1 :numWorkers 1 :zone "europe-west1-c" :workerMachineType "n1-standard-1"
                :stagingLocation "gs://hx-test/staging-eu"}
   :provider   {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}" :project "hx-test"}
   :containers {"riidb" {:image "gcr.io/hx-trial/responsys-resource:latest" :resource-version "v5"}}
   :pipelines  {"pipeline1bts"
                {:transform-jar "pipeline3.jar"
                 :pail          "build-artifacts-public-eu"
                 :key           "orion-transform"}}
   :sources    {"stream1bts" {:type "kub"}}
   :sinks      {"sink1bts" {:type "gcs" :bucket "sink1-bts-test"}}
   :edges      [{:origin "stream1bts" :targets ["pipeline1bts"]}
                {:origin "pipeline1bts" :targets ["sink1bts"]}]})

(def sm-provider {:google  {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"
                            :project     "hx-test"
                            :region      "europe-west1-c"}
                  :googlecli {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"
                              :project     "hx-test"
                              :region      "europe-west1-c"}
                  :googleappengine {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"
                                    :project     "hx-test"
                                    :region      "europe-west1-c"}
                  :googlebigquery {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"
                                   :project     "hx-test"
                                   :region      "europe-west1-c"}})
(def sm-pubsub-tops
  {:stream1bts {:name "stream1bts"},
   :pipeline1bts-error-out {:name "pipeline1bts-error-out"},
   :pipeline1bts-out {:name "pipeline1bts-out"},
   :sink1bts-error-out {:name "sink1bts-error-out"}})

(def sm-pubsub-subs   {:pipeline1bts-error-out_sub {:name "pipeline1bts-error-out_sub",
                                                    :topic "pipeline1bts-error-out",
                                                    :depends_on ["google_pubsub_topic.pipeline1bts-error-out"]},
                       :pipeline1bts-out_sub {:name "pipeline1bts-out_sub",
                                              :topic "pipeline1bts-out",
                                              :depends_on ["google_pubsub_topic.pipeline1bts-out"]},
                       :sink1bts-error-out_sub {:name "sink1bts-error-out_sub",
                                                :topic "sink1bts-error-out",
                                                :depends_on ["google_pubsub_topic.sink1bts-error-out"]}})

(def sm-container-cluster {:hx_fstack_cluster {:name "hxhstack"
                                               :initial_node_count 3
                                               :master_auth {:username "hx" :password "hstack"}
                                               :zone "europe-west1-c"
                                               :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                                                            "https://www.googleapis.com/auth/devstorage.read_only"
                                                                            "https://www.googleapis.com/auth/logging.write"
                                                                            "https://www.googleapis.com/auth/monitoring"
                                                                            "https://www.googleapis.com/auth/cloud-platform"]
                                                             :machine_type "n1-standard-4"}}})

(def sm-replica-controllers {:pipeline1bts-error-sink {:name "pipeline1bts-error-sink",
                                                       :resource_version ["1"],
                                                       :depends_on ["google_pubsub_subscription.pipeline1bts-error-out_sub"
                                                                    "google_storage_bucket.pipeline1bts-error"],
                                                       :docker_image "gcr.io/hx-test/store-sink",
                                                       :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                       :zone "europe-west1-c",
                                                       :env_args {:num_retries 3,
                                                                  :sub_name "pipeline1bts-error-out_sub",
                                                                  :proj_name "hx-test",
                                                                  :batch_size 1000,
                                                                  :bucket_name "pipeline1bts-error"}},
                             :sink1bts-sink {:name "sink1bts-sink",
                                             :resource_version ["1"],
                                             :depends_on ["google_pubsub_subscription.pipeline1bts-out_sub"
                                                          "google_storage_bucket.sink1-bts-test"],
                                             :docker_image "gcr.io/hx-test/store-sink",
                                             :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                             :zone "europe-west1-c",
                                             :env_args {:num_retries 3,
                                                        :sub_name "pipeline1bts-out_sub",
                                                        :proj_name "hx-test",
                                                        :batch_size 1000,
                                                        :error_topic "projects/hx-test/topics/sink1bts-error-out",
                                                        :bucket_name "sink1-bts-test"}},
                             :sink1bts-error-sink {:name "sink1bts-error-sink",
                                                   :resource_version ["1"],
                                                   :depends_on ["google_pubsub_subscription.sink1bts-error-out_sub"
                                                                "google_storage_bucket.sink1bts-error"],
                                                   :docker_image "gcr.io/hx-test/store-sink",
                                                   :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                   :zone "europe-west1-c",
                                                   :env_args {:num_retries 3,
                                                              :sub_name "sink1bts-error-out_sub",
                                                              :proj_name "hx-test",
                                                              :batch_size 1000,
                                                              :bucket_name "sink1bts-error"}},
                             :riidb {:name "riidb",
                                     :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                     :resource_version ["v5"],
                                     :docker_image "gcr.io/hx-trial/responsys-resource:latest",
                                     :zone "europe-west1-c",
                                     :env_args {:proj_name "hx-test"},
                                     :external_port 8080}})

(def sm-bucket {:pipeline1bts-error {:name "pipeline1bts-error", :location "EU"},
                :sink1-bts-test {:name "sink1-bts-test", :location "EU"},
                :sink1bts-error {:name "sink1bts-error", :location "EU"}})

(def sm-appengine {:stream1bts {:moduleName       "stream1bts"
                                :version          "init"
                                :gstorageKey      "hxtest-1.0-SNAPSHOT"
                                :resource_version ["1"]
                                :depends_on       ["google_pubsub_topic.stream1bts"]
                                :gstorageBucket   "build-artifacts-public-eu"
                                :scaling          {:minIdleInstances  1
                                                   :maxIdleInstances  1
                                                   :minPendingLatency "3s"
                                                   :maxPendingLatency "6s"}
                                :topicName        "projects/hx-test/topics/stream1bts"}})

(def sm-dataflows {:pipeline1bts (dissoc {:name          "pipeline1bts",
                                          :classpath     "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib/pipeline3.jar",
                                          :class         "com.acacia.angleddream.Main",
                                          :depends_on    ["google_pubsub_topic.pipeline1bts-out"
                                                          "googleappengine_app.stream1bts"
                                                          "google_pubsub_topic.pipeline1bts-error-out"
                                                          "google_pubsub_topic.stream1bts"],
                                          :optional_args {:stagingLocation   "gs://hx-test/staging-eu",
                                                          :zone              "europe-west1-c",
                                                          :workerMachineType "n1-standard-1",
                                                          :errorPipelineName "projects/hx-test/topics/pipeline1bts-error-out",
                                                          :pubsubTopic       "projects/hx-test/topics/stream1bts",
                                                          :numWorkers        1,
                                                          :outputTopics      "projects/hx-test/topics/pipeline1bts-out",
                                                          :pipelineName      "pipeline1bts",
                                                          :maxNumWorkers     1}} :resource_hashes)})

#_(deftest test-small-graph
  (let [g (create-parsed-output small-test-gr)]
    (testing "Test the minimum viable graph provider"
      (is-nil-diff? sm-provider (:provider g)))
    (testing "Pubsub topics"
      (is-nil-diff? sm-pubsub-tops (get-in g [:resource :google_pubsub_topic])))
    (testing "Pusub subs"
      (is-nil-diff? sm-pubsub-subs (get-in g [:resource :google_pubsub_subscription])))
    (testing "container cluster"
      (is-nil-diff? sm-container-cluster (get-in g [:resource :google_container_cluster])))
    (testing "appengine nodes"
      (is-nil-diff? sm-appengine (get-in g [:resource :googleappengine_app])))
    (testing "Replica controllers"
      (is-nil-diff? sm-replica-controllers (get-in g [:resource :googlecli_container_replica_controller])))
    (testing "Storage buckets"
      (is-nil-diff? sm-bucket (get-in g [:resource :google_storage_bucket])))
    (testing "Dataflows"
      (is-nil-diff? sm-dataflows (get-in g [:resource :googlecli_dataflow])))))

(def big-test-gr
  {:config     {:remote-composer-classpath     "/usr/local/lib/angleddream-bundled.jar"
                :remote-libs-path              "/usr/local/lib"
                :error-buckets                 true
                :sink-resource-version         "1"
                :source-resource-version       "1"
                :default-pipeline-machine-type "n1-standard-1"
                :appengine-gstoragekey         "hxtest-1.0-SNAPSHOT"
                :default-sink-docker-image     "gcr.io/hx-test/store-sink"
                :system-jar-info               {:angleddream {:name "angleddream-bundled-0.1-ALPHA.jar"
                                                              :pail "build-artifacts-public-eu"
                                                              :key  "angleddream"}
                                                :sossity     {:name "sossity-0.1.0-SNAPSHOT-standalone.jar"
                                                              :pail "build-artifacts-public-eu"
                                                              :key  "sossity"}}}
   :cluster    {:name        "hxhstack" :initial_node_count 3 :master_auth {:username "hx" :password "hstack"}
                :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                             "https://www.googleapis.com/auth/devstorage.read_only"
                                             "https://www.googleapis.com/auth/logging.write"
                                             "https://www.googleapis.com/auth/monitoring"
                                             "https://www.googleapis.com/auth/cloud-platform"]
                              :machine_type "n1-standard-4"}}
   :opts       {:maxNumWorkers   1 :numWorkers 1 :zone "europe-west1-c" :workerMachineType "n1-standard-1"
                :stagingLocation "gs://hx-test/staging-eu"}
   :provider   {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}" :project "hx-test"}
   :containers {"riidb" {:image "gcr.io/hx-trial/responsys-resource:latest" :resource-version "v5"}}
   :pipelines  {"pipeline1bts"
                {:transform-jar "/usr/local/lib/pipeline1.jar"
                 :pail          "build-artifacts-public-eu"
                 :key           "orion-transform"}
                "pipeline2bts"
                {:transform-jar "/usr/local/lib/pipeline2.jar"
                 :pail          "build-artifacts-public-eu"
                 :key           "orion-transform"}
                "pipeline3bts"
                {:transform-jar     "/usr/local/lib/pipeline3.jar"
                 :pail              "build-artifacts-public-eu"
                 :key               "orion-transform"
                 :workerMachineType "n1-standard-4"}
                "orionpipe"
                {:transform-jar "/usr/local/lib/pipeline1.jar"
                 :pail          "build-artifacts-public-eu"
                 :key           "orion-transform"}}
   :sources    {"stream1bts" {:type "kub"}
                "stream2bts" {:type "kub"}
                "orion"      {:type "kub"}}
   :sinks      {"sink1bts"  {:type "gcs" :bucket "sink1-bts-test"}
                "sink2bts"  {:type "gcs" :bucket "sink2-bts-test"}
                "sink3bts"  {:type "gcs" :bucket "sink3-bts-test"}
                "orionsink" {:type "gcs" :bucket "orionbucket"}}
   :edges      [{:origin "stream1bts" :targets ["pipeline1bts"]}
                {:origin "pipeline1bts" :targets ["pipeline2bts" "pipeline3bts"]}
                {:origin "pipeline2bts" :targets ["sink1bts" "sink3bts"]}
                {:origin "orion" :targets ["orionpipe"]}
                {:origin "orionpipe" :targets ["orionsink"]}
                {:origin "pipeline3bts" :targets ["sink2bts"]}]})

(def big-provider {:google {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"
                            :project "hx-test"
                            :region "europe-west1-c"}
                   :googlecli {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"
                               :project "hx-test"
                               :region "europe-west1-c"}
                   :googleappengine {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"
                                     :project "hx-test"
                                     :region "europe-west1-c"}
                   :googlebigquery {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}"
                                    :project     "hx-test"
                                    :region      "europe-west1-c"}})

(def big-pubsub-tops  {:orionpipe-error-out {:name "orionpipe-error-out"},
                       :pipeline3bts-error-out {:name "pipeline3bts-error-out"},
                       :sink3bts-error-out {:name "sink3bts-error-out"},
                       :pipeline2bts-error-out {:name "pipeline2bts-error-out"},
                       :pipeline1bts-error-out {:name "pipeline1bts-error-out"},
                       :sink2bts-error-out {:name "sink2bts-error-out"},
                       :orionsink-error-out {:name "orionsink-error-out"},
                       :orion {:name "orion"},
                       :sink1bts-error-out {:name "sink1bts-error-out"},
                       :stream1bts {:name "stream1bts"},
                       :orionpipe-out {:name "orionpipe-out"},
                       :pipeline3bts-out {:name "pipeline3bts-out"},
                       :pipeline2bts-out {:name "pipeline2bts-out"},
                       :pipeline1bts-out {:name "pipeline1bts-out"}})

(def big-pubsub-subs
  {:sink2bts-error-out_sub {:name "sink2bts-error-out_sub",
                            :topic "sink2bts-error-out",
                            :depends_on ["google_pubsub_topic.sink2bts-error-out"]},
   :orionsink-error-out_sub {:name "orionsink-error-out_sub",
                             :topic "orionsink-error-out",
                             :depends_on ["google_pubsub_topic.orionsink-error-out"]},
   :pipeline2bts-out_sub {:name "pipeline2bts-out_sub",
                          :topic "pipeline2bts-out",
                          :depends_on ["google_pubsub_topic.pipeline2bts-out"]},
   :pipeline1bts-error-out_sub {:name "pipeline1bts-error-out_sub",
                                :topic "pipeline1bts-error-out",
                                :depends_on ["google_pubsub_topic.pipeline1bts-error-out"]},
   :orionpipe-error-out_sub {:name "orionpipe-error-out_sub",
                             :topic "orionpipe-error-out",
                             :depends_on ["google_pubsub_topic.orionpipe-error-out"]},
   :sink3bts-error-out_sub {:name "sink3bts-error-out_sub",
                            :topic "sink3bts-error-out",
                            :depends_on ["google_pubsub_topic.sink3bts-error-out"]},
   :pipeline3bts-out_sub {:name "pipeline3bts-out_sub",
                          :topic "pipeline3bts-out",
                          :depends_on ["google_pubsub_topic.pipeline3bts-out"]},
   :sink1bts-error-out_sub {:name "sink1bts-error-out_sub",
                            :topic "sink1bts-error-out",
                            :depends_on ["google_pubsub_topic.sink1bts-error-out"]},
   :orionpipe-out_sub {:name "orionpipe-out_sub",
                       :topic "orionpipe-out",
                       :depends_on ["google_pubsub_topic.orionpipe-out"]},
   :pipeline2bts-error-out_sub {:name "pipeline2bts-error-out_sub",
                                :topic "pipeline2bts-error-out",
                                :depends_on ["google_pubsub_topic.pipeline2bts-error-out"]},
   :pipeline3bts-error-out_sub {:name "pipeline3bts-error-out_sub",
                                :topic "pipeline3bts-error-out",
                                :depends_on ["google_pubsub_topic.pipeline3bts-error-out"]}})

(def big-container-cluster {:hx_fstack_cluster {:name "hxhstack"
                                                :initial_node_count 3
                                                :master_auth {:username "hx" :password "hstack"}
                                                :zone "europe-west1-c"
                                                :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                                                             "https://www.googleapis.com/auth/devstorage.read_only"
                                                                             "https://www.googleapis.com/auth/logging.write"
                                                                             "https://www.googleapis.com/auth/monitoring"
                                                                             "https://www.googleapis.com/auth/cloud-platform"]
                                                              :machine_type "n1-standard-4"}}})

(def big-replica-controllers {:sink3bts-error-sink {:name "sink3bts-error-sink",
                                                    :resource_version ["1"],
                                                    :depends_on ["google_pubsub_subscription.sink3bts-error-out_sub"
                                                                 "google_storage_bucket.sink3bts-error"],
                                                    :docker_image "gcr.io/hx-test/store-sink",
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                    :zone "europe-west1-c",
                                                    :env_args {:num_retries 3,
                                                               :sub_name "sink3bts-error-out_sub",
                                                               :proj_name "hx-test",
                                                               :batch_size 1000,
                                                               :bucket_name "sink3bts-error"}},
                              :sink1bts-error-sink {:name "sink1bts-error-sink",
                                                    :resource_version ["1"],
                                                    :depends_on ["google_pubsub_subscription.sink1bts-error-out_sub"
                                                                 "google_storage_bucket.sink1bts-error"],
                                                    :docker_image "gcr.io/hx-test/store-sink",
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                    :zone "europe-west1-c",
                                                    :env_args {:num_retries 3,
                                                               :sub_name "sink1bts-error-out_sub",
                                                               :proj_name "hx-test",
                                                               :batch_size 1000,
                                                               :bucket_name "sink1bts-error"}},
                              :sink2bts-sink {:name "sink2bts-sink",
                                              :resource_version ["1"],
                                              :depends_on ["google_pubsub_subscription.pipeline3bts-out_sub"
                                                           "google_storage_bucket.sink2-bts-test"],
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :sub_name "pipeline3bts-out_sub",
                                                         :proj_name "hx-test",
                                                         :batch_size 1000,
                                                         :error_topic "projects/hx-test/topics/sink2bts-error-out",
                                                         :bucket_name "sink2-bts-test"}},
                              :sink1bts-sink {:name "sink1bts-sink",
                                              :resource_version ["1"],
                                              :depends_on ["google_pubsub_subscription.pipeline2bts-out_sub"
                                                           "google_storage_bucket.sink1-bts-test"],
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :sub_name "pipeline2bts-out_sub",
                                                         :proj_name "hx-test",
                                                         :batch_size 1000,
                                                         :error_topic "projects/hx-test/topics/sink1bts-error-out",
                                                         :bucket_name "sink1-bts-test"}},
                              :pipeline1bts-error-sink {:name "pipeline1bts-error-sink",
                                                        :resource_version ["1"],
                                                        :depends_on ["google_pubsub_subscription.pipeline1bts-error-out_sub"
                                                                     "google_storage_bucket.pipeline1bts-error"],
                                                        :docker_image "gcr.io/hx-test/store-sink",
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                        :zone "europe-west1-c",
                                                        :env_args {:num_retries 3,
                                                                   :sub_name "pipeline1bts-error-out_sub",
                                                                   :proj_name "hx-test",
                                                                   :batch_size 1000,
                                                                   :bucket_name "pipeline1bts-error"}},
                              :riidb {:name "riidb",
                                      :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                      :resource_version ["v5"],
                                      :docker_image "gcr.io/hx-trial/responsys-resource:latest",
                                      :zone "europe-west1-c",
                                      :env_args {:proj_name "hx-test"},
                                      :external_port 8080},
                              :orionpipe-error-sink {:name "orionpipe-error-sink",
                                                     :resource_version ["1"],
                                                     :depends_on ["google_pubsub_subscription.orionpipe-error-out_sub"
                                                                  "google_storage_bucket.orionpipe-error"],
                                                     :docker_image "gcr.io/hx-test/store-sink",
                                                     :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                     :zone "europe-west1-c",
                                                     :env_args {:num_retries 3,
                                                                :sub_name "orionpipe-error-out_sub",
                                                                :proj_name "hx-test",
                                                                :batch_size 1000,
                                                                :bucket_name "orionpipe-error"}},
                              :orionsink-error-sink {:name "orionsink-error-sink",
                                                     :resource_version ["1"],
                                                     :depends_on ["google_pubsub_subscription.orionsink-error-out_sub"
                                                                  "google_storage_bucket.orionsink-error"],
                                                     :docker_image "gcr.io/hx-test/store-sink",
                                                     :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                     :zone "europe-west1-c",
                                                     :env_args {:num_retries 3,
                                                                :sub_name "orionsink-error-out_sub",
                                                                :proj_name "hx-test",
                                                                :batch_size 1000,
                                                                :bucket_name "orionsink-error"}},
                              :pipeline3bts-error-sink {:name "pipeline3bts-error-sink",
                                                        :resource_version ["1"],
                                                        :depends_on ["google_pubsub_subscription.pipeline3bts-error-out_sub"
                                                                     "google_storage_bucket.pipeline3bts-error"],
                                                        :docker_image "gcr.io/hx-test/store-sink",
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                        :zone "europe-west1-c",
                                                        :env_args {:num_retries 3,
                                                                   :sub_name "pipeline3bts-error-out_sub",
                                                                   :proj_name "hx-test",
                                                                   :batch_size 1000,
                                                                   :bucket_name "pipeline3bts-error"}},
                              :sink2bts-error-sink {:name "sink2bts-error-sink",
                                                    :resource_version ["1"],
                                                    :depends_on ["google_pubsub_subscription.sink2bts-error-out_sub"
                                                                 "google_storage_bucket.sink2bts-error"],
                                                    :docker_image "gcr.io/hx-test/store-sink",
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                    :zone "europe-west1-c",
                                                    :env_args {:num_retries 3,
                                                               :sub_name "sink2bts-error-out_sub",
                                                               :proj_name "hx-test",
                                                               :batch_size 1000,
                                                               :bucket_name "sink2bts-error"}},
                              :orionsink-sink {:name "orionsink-sink",
                                               :resource_version ["1"],
                                               :depends_on ["google_pubsub_subscription.orionpipe-out_sub"
                                                            "google_storage_bucket.orionbucket"],
                                               :docker_image "gcr.io/hx-test/store-sink",
                                               :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                               :zone "europe-west1-c",
                                               :env_args {:num_retries 3,
                                                          :sub_name "orionpipe-out_sub",
                                                          :proj_name "hx-test",
                                                          :batch_size 1000,
                                                          :error_topic "projects/hx-test/topics/orionsink-error-out",
                                                          :bucket_name "orionbucket"}},
                              :pipeline2bts-error-sink {:name "pipeline2bts-error-sink",
                                                        :resource_version ["1"],
                                                        :depends_on ["google_pubsub_subscription.pipeline2bts-error-out_sub"
                                                                     "google_storage_bucket.pipeline2bts-error"],
                                                        :docker_image "gcr.io/hx-test/store-sink",
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                                        :zone "europe-west1-c",
                                                        :env_args {:num_retries 3,
                                                                   :sub_name "pipeline2bts-error-out_sub",
                                                                   :proj_name "hx-test",
                                                                   :batch_size 1000,
                                                                   :bucket_name "pipeline2bts-error"}},
                              :sink3bts-sink {:name "sink3bts-sink",
                                              :resource_version ["1"],
                                              :depends_on ["google_pubsub_subscription.pipeline2bts-out_sub"
                                                           "google_storage_bucket.sink3-bts-test"],
                                              :docker_image "gcr.io/hx-test/store-sink",
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                              :zone "europe-west1-c",
                                              :env_args {:num_retries 3,
                                                         :sub_name "pipeline2bts-out_sub",
                                                         :proj_name "hx-test",
                                                         :batch_size 1000,
                                                         :error_topic "projects/hx-test/topics/sink3bts-error-out",
                                                         :bucket_name "sink3-bts-test"}}})

(def big-bucket  {:sink1bts-error {:name "sink1bts-error", :location "EU"},
                  :sink3-bts-test {:name "sink3-bts-test", :location "EU"},
                  :pipeline2bts-error {:name "pipeline2bts-error", :location "EU"},
                  :sink3bts-error {:name "sink3bts-error", :location "EU"},
                  :sink2-bts-test {:name "sink2-bts-test", :location "EU"},
                  :orionbucket {:name "orionbucket", :location "EU"},
                  :pipeline3bts-error {:name "pipeline3bts-error", :location "EU"},
                  :pipeline1bts-error {:name "pipeline1bts-error", :location "EU"},
                  :orionsink-error {:name "orionsink-error", :location "EU"},
                  :sink1-bts-test {:name "sink1-bts-test", :location "EU"},
                  :sink2bts-error {:name "sink2bts-error", :location "EU"},
                  :orionpipe-error {:name "orionpipe-error", :location "EU"}})

(def big-dataflows {:pipeline1bts (dissoc {:name "pipeline1bts",
                                           :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline1.jar",
                                           :class "com.acacia.angleddream.Main",
                                           :depends_on ["google_pubsub_topic.pipeline1bts-out"
                                                        "googleappengine_app.stream1bts"
                                                        "google_pubsub_topic.pipeline1bts-error-out"
                                                        "google_pubsub_topic.stream1bts"],
                                           :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                           :zone "europe-west1-c",
                                                           :workerMachineType "n1-standard-1",
                                                           :errorPipelineName "projects/hx-test/topics/pipeline1bts-error-out",
                                                           :pubsubTopic "projects/hx-test/topics/stream1bts",
                                                           :numWorkers 1,
                                                           :outputTopics "projects/hx-test/topics/pipeline1bts-out",
                                                           :pipelineName "pipeline1bts",
                                                           :maxNumWorkers 1}} :resource_hashes)
                    :pipeline3bts (dissoc  {:name "pipeline3bts",
                                            :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline3.jar",
                                            :class "com.acacia.angleddream.Main",
                                            :depends_on ["google_pubsub_topic.pipeline3bts-out"
                                                         "googlecli_dataflow.pipeline1bts"
                                                         "google_pubsub_topic.pipeline3bts-error-out"
                                                         "google_pubsub_topic.pipeline1bts-out"],
                                            :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                            :zone "europe-west1-c",
                                                            :workerMachineType "n1-standard-4",
                                                            :errorPipelineName "projects/hx-test/topics/pipeline3bts-error-out",
                                                            :pubsubTopic "projects/hx-test/topics/pipeline1bts-out",
                                                            :numWorkers 1,
                                                            :outputTopics "projects/hx-test/topics/pipeline3bts-out",
                                                            :pipelineName "pipeline3bts",
                                                            :maxNumWorkers 1}} :resource_hashes)
                    :pipeline2bts (dissoc {:name "pipeline2bts",
                                           :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline2.jar",
                                           :class "com.acacia.angleddream.Main",
                                           :depends_on ["google_pubsub_topic.pipeline2bts-out"
                                                        "googlecli_dataflow.pipeline1bts"
                                                        "google_pubsub_topic.pipeline2bts-error-out"
                                                        "google_pubsub_topic.pipeline1bts-out"],
                                           :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                           :zone "europe-west1-c",
                                                           :workerMachineType "n1-standard-1",
                                                           :errorPipelineName "projects/hx-test/topics/pipeline2bts-error-out",
                                                           :pubsubTopic "projects/hx-test/topics/pipeline1bts-out",
                                                           :numWorkers 1,
                                                           :outputTopics "projects/hx-test/topics/pipeline2bts-out",
                                                           :pipelineName "pipeline2bts",
                                                           :maxNumWorkers 1}} :resource_hashes)
                    :orionpipe    (dissoc {:name "orionpipe",
                                           :classpath "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline1.jar",
                                           :class "com.acacia.angleddream.Main",
                                           :depends_on ["google_pubsub_topic.orionpipe-out"
                                                        "googleappengine_app.orion"
                                                        "google_pubsub_topic.orionpipe-error-out"
                                                        "google_pubsub_topic.orion"],
                                           :optional_args {:stagingLocation "gs://hx-test/staging-eu",
                                                           :zone "europe-west1-c",
                                                           :workerMachineType "n1-standard-1",
                                                           :errorPipelineName "projects/hx-test/topics/orionpipe-error-out",
                                                           :pubsubTopic "projects/hx-test/topics/orion",
                                                           :numWorkers 1,
                                                           :outputTopics "projects/hx-test/topics/orionpipe-out",
                                                           :pipelineName "orionpipe",
                                                           :maxNumWorkers 1}} :resource_hashes)})

(def big-appengine {:stream1bts {:moduleName       "stream1bts",
                                 :version          "init",
                                 :depends_on       ["google_pubsub_topic.stream1bts"],
                                 :gstorageKey      "hxtest-1.0-SNAPSHOT",
                                 :resource_version ["1"],
                                 :gstorageBucket   "build-artifacts-public-eu",
                                 :scaling          {:minIdleInstances  1,
                                                    :maxIdleInstances  1,
                                                    :minPendingLatency "3s",
                                                    :maxPendingLatency "6s"},
                                 :topicName        "projects/hx-test/topics/stream1bts"},
                    :orion      {:moduleName       "orion",
                                 :version          "init",
                                 :depends_on       ["google_pubsub_topic.orion"],
                                 :gstorageKey      "hxtest-1.0-SNAPSHOT",
                                 :resource_version ["1"],
                                 :gstorageBucket   "build-artifacts-public-eu",
                                 :scaling          {:minIdleInstances  1,
                                                    :maxIdleInstances  1,
                                                    :minPendingLatency "3s",
                                                    :maxPendingLatency "6s"},
                                 :topicName        "projects/hx-test/topics/orion"}})

#_(deftest test-big-graph
  (let [g (create-parsed-output big-test-gr)]
    (testing "Test the minimum viable graph provider"
      (is-nil-diff? big-provider (:provider g)))
    (testing "Pubsub topics"
      (is-nil-diff? big-pubsub-tops (get-in g [:resource :google_pubsub_topic])))
    (testing "Pusub subs"
      (is-nil-diff? big-pubsub-subs (get-in g [:resource :google_pubsub_subscription])))
    (testing "container cluster"
      (is-nil-diff? big-container-cluster (get-in g [:resource :google_container_cluster])))
    (testing "app engine"
      (is-nil-diff? big-appengine (get-in g [:resource :googleappengine_app])))
    (testing "Replica controllers"
      (is-nil-diff? big-replica-controllers (get-in g [:resource :googlecli_container_replica_controller])))
    (testing "Storage buckets"
      (is-nil-diff? big-bucket (get-in g [:resource :google_storage_bucket])))
    (testing "Dataflows"
      (is-nil-diff? big-dataflows (get-in g [:resource :googlecli_dataflow])))))

(def bq-graph
  (-> big-test-gr
      (assoc-in [:sinks "orionbq"] {:type "bq" :bigQueryDataset "hx-test" :bigQueryTable "hx-test" :bigQuerySchema "schema.json"})
      (assoc-in [:edges]  [{:origin "stream1bts" :targets ["pipeline1bts"]}
                           {:origin "pipeline1bts" :targets ["pipeline2bts" "pipeline3bts"]}
                           {:origin "pipeline2bts" :targets ["sink1bts"  "sink3bts"]}
                           {:origin "orion" :targets ["orionpipe"]}
                           {:origin "orionpipe" :targets ["orionsink" "orionbq"]}
                           {:origin "pipeline3bts" :targets ["sink2bts"]}])))

(def bq-dataflow {:name          "orionbq"
                  :classpath     "/usr/local/lib/angleddream-bundled.jar"
                  :class         "com.acacia.angleddream.Main"
                  :depends_on    ["googlecli_dataflow.orionpipe"
                                  "google_pubsub_topic.orionbq-error-out" "google_pubsub_topic.orionpipe-out"]
                  :optional_args {:stagingLocation   "gs://hx-test/staging-eu"
                                  :zone              "europe-west1-c"
                                  :workerMachineType "n1-standard-1"
                                  :bigQueryTable     "hx-test"
                                  :errorPipelineName "projects/hx-test/topics/orionbq-error-out"
                                  :bigQueryDataset   "hx-test"
                                  :bigQuerySchema    "schema.json"
                                  :pubsubTopic       "projects/hx-test/topics/orionpipe-out"
                                  :numWorkers        1
                                  :pipelineName      "orionbq"
                                  :maxNumWorkers     1}})

(def bq-pubsub-tops (->
                     big-pubsub-tops
                     (assoc :orionbq-error-out {:name "orionbq-error-out"})))

(def bq-datasets {:hx-test {:datasetId "hx-test"}})

(def bq-tables {:hx-test {:tableId    "hx-test" :datasetId "${googlebigquery_dataset.hx-test.datasetId}"
                          :schemaFile "schema.json" :depends_on ["googlebigquery_dataset.hx-test"]}})

(def bq-buckets (-> big-bucket (assoc :orionbq-error {:name "orionbq-error", , :location "EU"})))

(def bq-replica-controllers (-> big-replica-controllers
                                (assoc :orionbq-error-sink
                                       {:name "orionbq-error-sink",
                                        :resource_version ["1"], :docker_image "gcr.io/hx-test/store-sink",
                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                        :depends_on ["google_pubsub_subscription.orionbq-error-out_sub" "google_storage_bucket.orionbq-error"]
                                        :zone "europe-west1-c", :env_args {:num_retries 3, :batch_size 1000,
                                                                           :proj_name "hx-test", :sub_name "orionbq-error-out_sub",
                                                                           :bucket_name "orionbq-error"}})))

(def bq-subs (-> big-pubsub-subs
                 (assoc :orionbq-error-out_sub  {:name "orionbq-error-out_sub", :topic "orionbq-error-out", :depends_on ["google_pubsub_topic.orionbq-error-out"]})))

;NOTE -- need to have some kind of 'refresh' workflow since we may be defing/undefing in a work session

#_(deftest add-bq
  (let [g (create-parsed-output bq-graph)]
    (testing "Test new dataflow for bigquery"
      (is-nil-diff? bq-dataflow (get-in g [:resource :googlecli_dataflow :orionbq])))
    (testing "Test the minimum viable graph provider"
      (is-nil-diff? big-provider (:provider g)))
    (testing "Pubsub topics"
      (is-nil-diff? bq-pubsub-tops (get-in g [:resource :google_pubsub_topic])))
    (testing "Pusub subs"
      (is-nil-diff? bq-subs (get-in g [:resource :google_pubsub_subscription])))
    (testing "container cluster"
      (is-nil-diff? big-container-cluster (get-in g [:resource :google_container_cluster])))
    (testing "Replica controllers"
      (is-nil-diff? bq-replica-controllers (get-in g [:resource :googlecli_container_replica_controller])))
    (testing "Datasets"
      (is-nil-diff? bq-datasets (get-in g [:resource :googlebigquery_dataset])))
    (testing "Tables"
      (is-nil-diff? bq-tables (get-in g [:resource :googlebigquery_table])))
    (testing "Storage buckets"
      (is-nil-diff? bq-buckets (get-in g [:resource :google_storage_bucket])))))

