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
  {:config    {:remote-composer-classpath "/usr/local/lib/angleddream-bundled.jar"
               :remote-libs-path          "/usr/local/lib"
               :error-buckets             true
               :sink-resource-version     "1"
               :source-resource-version   "1"
               :default-pipeline-machine-type "n1-standard-1"
               :appengine-gstoragekey     "hxtest-1.0-SNAPSHOT"
               :default-sink-docker-image "gcr.io/hx-test/store-sink"
               :system-jar-info           {:angleddream {:name "angleddream-bundled-0.1-ALPHA.jar"
                                                         :pail "build-artifacts-public-eu"
                                                         :key  "angleddream"}
                                           :sossity     {:name "sossity-0.1.0-SNAPSHOT-standalone.jar"
                                                         :pail "build-artifacts-public-eu"
                                                         :key  "sossity"}}}
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
                :pail "build-artifacts-public-eu"
                :key "orion-transform"}}
   :sources   {"stream1bts" {:type "kub"}}
   :sinks     {"sink1bts" {:type "gcs" :bucket "sink1-bts-test"}}
   :edges     [{:origin "stream1bts" :targets ["pipeline1bts"]}
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
  {:sink1bts-error-out {:name "sink1bts-error-out"} :pipeline1bts-out {:name "pipeline1bts-out"} :pipeline1bts-error-out {:name "pipeline1bts-error-out"} :stream1bts {:name "stream1bts"}})

(def sm-pubsub-subs  {:sink1bts-error-out_sub {:name "sink1bts-error-out_sub", :topic "sink1bts-error-out", :depends_on ["google_pubsub_topic.sink1bts-error-out"]}, :pipeline1bts-out_sub {:name "pipeline1bts-out_sub", :topic "pipeline1bts-out", :depends_on ["google_pubsub_topic.pipeline1bts-out"]}, :pipeline1bts-error-out_sub {:name "pipeline1bts-error-out_sub", :topic "pipeline1bts-error-out", :depends_on ["google_pubsub_topic.pipeline1bts-error-out"]}})

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

(def sm-replica-controllers {:sink1bts-sink           {:name           "sink1bts-sink"
                                                       :docker_image   "gcr.io/hx-test/store-sink"
                                                       :resource_version ["1"]
                                                       :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                       :zone           "europe-west1-c"
                                                       :depends_on ["google_storage_bucket.sink1-bts-test" "google_pubsub_subscription.pipeline1bts-to-sink1bts_sub"]
                                                       :env_args       {:num_retries 3
                                                                        :batch_size  1000
                                                                        :proj_name   "hx-test"
                                                                        :sub_name    "pipeline1bts-to-sink1bts_sub"
                                                                        :bucket_name "sink1-bts-test"
                                                                        :error_topic "projects/hx-test/topics/sink1bts-to-sink1bts-error"}}
                             :pipeline1bts-error-sink {:name           "pipeline1bts-error-sink"
                                                       :docker_image   "gcr.io/hx-test/store-sink"
                                                       :resource_version ["1"]
                                                       :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                       :zone           "europe-west1-c"
                                                       :depends_on ["google_storage_bucket.pipeline1bts-error" "google_pubsub_subscription.pipeline1bts-to-pipeline1bts-error_sub"]
                                                       :env_args       {:num_retries 3
                                                                        :batch_size  1000
                                                                        :proj_name   "hx-test"
                                                                        :sub_name    "pipeline1bts-to-pipeline1bts-error_sub"
                                                                        :bucket_name "pipeline1bts-error"}}
                             :sink1bts-error-sink     {:name           "sink1bts-error-sink"
                                                       :docker_image   "gcr.io/hx-test/store-sink"
                                                       :depends_on ["google_storage_bucket.sink1bts-error" "google_pubsub_subscription.sink1bts-to-sink1bts-error_sub"]
                                                       :resource_version ["1"]
                                                       :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                       :zone           "europe-west1-c"
                                                       :env_args       {:num_retries 3
                                                                        :batch_size  1000
                                                                        :proj_name   "hx-test"
                                                                        :sub_name    "sink1bts-to-sink1bts-error_sub"
                                                                        :bucket_name "sink1bts-error"}}})

(def sm-bucket {:pipeline1bts-error {:name          "pipeline1bts-error"
                                     :location      "EU"}
                :sink1-bts-test     {:name "sink1-bts-test"  :location "EU"}
                :sink1bts-error     {:name "sink1bts-error"  :location "EU"}})

(def sm-appengine {:stream1bts {:moduleName     "stream1bts"
                                :version        "init"
                                :gstorageKey    "hxtest-1.0-SNAPSHOT"
                                :resource_version ["1"]
                                :depends_on "google_pubsub_topic.stream1bts"
                                :gstorageBucket "build-artifacts-public-eu"
                                :scaling        {:minIdleInstances  1
                                                 :maxIdleInstances  1
                                                 :minPendingLatency "3s"
                                                 :maxPendingLatency "6s"}
                                :topicName      "projects/hx-test/topics/stream1bts"}})

(def sm-dataflows {:pipeline1bts (dissoc {:name          "pipeline1bts"
                                          :classpath     "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib/pipeline3.jar"
                                          :class         "com.acacia.angleddream.Main"
                                          :depends_on    ["google_pubsub_topic.pipeline1bts-to-sink1bts"
                                                          "googleappengine_app.stream1bts"
                                                          "google_pubsub_topic.pipeline1bts-to-pipeline1bts-error"
                                                          "google_pubsub_topic.stream1bts"]
                                          :optional_args {:pubsubTopic       "projects/hx-test/topics/stream1bts"
                                                          :pipelineName      "pipeline1bts"
                                                          :errorPipelineName "projects/hx-test/topics/pipeline1bts-to-pipeline1bts-error"
                                                          :outputTopics      "projects/hx-test/topics/pipeline1bts-to-sink1bts"
                                                          :stagingLocation   "gs://hx-test/staging-eu"
                                                          :zone              "europe-west1-c"
                                                          :workerMachineType "n1-standard-1"
                                                          :numWorkers        "1"
                                                          :maxNumWorkers     "1"}} :resource_hashes)})

(deftest test-small-graph
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
  {:config    {:remote-composer-classpath "/usr/local/lib/angleddream-bundled.jar"
               :remote-libs-path          "/usr/local/lib"
               :error-buckets             true
               :sink-resource-version     "1"
               :source-resource-version   "1"
               :default-pipeline-machine-type "n1-standard-1"
               :appengine-gstoragekey     "hxtest-1.0-SNAPSHOT"
               :default-sink-docker-image "gcr.io/hx-test/store-sink"
               :system-jar-info           {:angleddream {:name "angleddream-bundled-0.1-ALPHA.jar"
                                                         :pail "build-artifacts-public-eu"
                                                         :key  "angleddream"}
                                           :sossity     {:name "sossity-0.1.0-SNAPSHOT-standalone.jar"
                                                         :pail "build-artifacts-public-eu"
                                                         :key  "sossity"}}}
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
               {:transform-jar "/usr/local/lib/pipeline1.jar"
                :pail "build-artifacts-public-eu"
                :key "orion-transform"}
               "pipeline2bts"
               {:transform-jar "/usr/local/lib/pipeline2.jar"
                :pail "build-artifacts-public-eu"
                :key "orion-transform"}
               "pipeline3bts"
               {:transform-jar "/usr/local/lib/pipeline3.jar"
                :pail "build-artifacts-public-eu"
                :key "orion-transform"
                :workerMachineType "n1-standard-4"}
               "orionpipe"
               {:transform-jar "/usr/local/lib/pipeline1.jar"
                :pail "build-artifacts-public-eu"
                :key "orion-transform"}}
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

(def big-pubsub-tops  {:sink3bts-to-sink3bts-error {:name "sink3bts-to-sink3bts-error"}
                       :pipeline3bts-to-sink2bts {:name "pipeline3bts-to-sink2bts"}
                       :orion {:name "orion"}
                       :pipeline2bts-to-sink1bts {:name "pipeline2bts-to-sink1bts"}
                       :pipeline1bts-to-pipeline1bts-error {:name "pipeline1bts-to-pipeline1bts-error"}
                       :sink1bts-to-sink1bts-error {:name "sink1bts-to-sink1bts-error"}
                       :pipeline2bts-to-sink3bts {:name "pipeline2bts-to-sink3bts"}
                       :pipeline3bts-to-pipeline3bts-error {:name "pipeline3bts-to-pipeline3bts-error"}
                       :stream1bts {:name "stream1bts"}
                       :pipeline2bts-to-pipeline2bts-error {:name "pipeline2bts-to-pipeline2bts-error"}
                       :orionsink-to-orionsink-error {:name "orionsink-to-orionsink-error"}
                       :orionpipe-to-orionpipe-error {:name "orionpipe-to-orionpipe-error"}
                       :orionpipe-to-orionsink {:name "orionpipe-to-orionsink"}
                       :pipeline1bts-to-pipeline2bts {:name "pipeline1bts-to-pipeline2bts"}
                       :pipeline1bts-to-pipeline3bts {:name "pipeline1bts-to-pipeline3bts"}
                       :sink2bts-to-sink2bts-error {:name "sink2bts-to-sink2bts-error"}})

(def big-pubsub-subs
  {:pipeline2bts-to-pipeline2bts-error_sub {:name "pipeline2bts-to-pipeline2bts-error_sub"
                                            :topic "pipeline2bts-to-pipeline2bts-error"
                                            :depends_on ["google_pubsub_topic.pipeline2bts-to-pipeline2bts-error"]}
   :pipeline2bts-to-sink1bts_sub {:name "pipeline2bts-to-sink1bts_sub"
                                  :topic "pipeline2bts-to-sink1bts"
                                  :depends_on ["google_pubsub_topic.pipeline2bts-to-sink1bts"]}
   :orionsink-to-orionsink-error_sub {:name "orionsink-to-orionsink-error_sub"
                                      :topic "orionsink-to-orionsink-error"
                                      :depends_on ["google_pubsub_topic.orionsink-to-orionsink-error"]}
   :sink3bts-to-sink3bts-error_sub {:name "sink3bts-to-sink3bts-error_sub"
                                    :topic "sink3bts-to-sink3bts-error"
                                    :depends_on ["google_pubsub_topic.sink3bts-to-sink3bts-error"]}
   :sink2bts-to-sink2bts-error_sub {:name "sink2bts-to-sink2bts-error_sub"
                                    :topic "sink2bts-to-sink2bts-error"
                                    :depends_on ["google_pubsub_topic.sink2bts-to-sink2bts-error"]}
   :pipeline3bts-to-pipeline3bts-error_sub {:name "pipeline3bts-to-pipeline3bts-error_sub"
                                            :topic "pipeline3bts-to-pipeline3bts-error"
                                            :depends_on ["google_pubsub_topic.pipeline3bts-to-pipeline3bts-error"]}
   :pipeline1bts-to-pipeline1bts-error_sub {:name "pipeline1bts-to-pipeline1bts-error_sub"
                                            :topic "pipeline1bts-to-pipeline1bts-error"
                                            :depends_on ["google_pubsub_topic.pipeline1bts-to-pipeline1bts-error"]}
   :orionpipe-to-orionsink_sub {:name "orionpipe-to-orionsink_sub"
                                :topic "orionpipe-to-orionsink"
                                :depends_on ["google_pubsub_topic.orionpipe-to-orionsink"]}
   :pipeline2bts-to-sink3bts_sub {:name "pipeline2bts-to-sink3bts_sub"
                                  :topic "pipeline2bts-to-sink3bts"
                                  :depends_on ["google_pubsub_topic.pipeline2bts-to-sink3bts"]}
   :pipeline3bts-to-sink2bts_sub {:name "pipeline3bts-to-sink2bts_sub"
                                  :topic "pipeline3bts-to-sink2bts"
                                  :depends_on ["google_pubsub_topic.pipeline3bts-to-sink2bts"]}
   :sink1bts-to-sink1bts-error_sub {:name "sink1bts-to-sink1bts-error_sub"
                                    :topic "sink1bts-to-sink1bts-error"
                                    :depends_on ["google_pubsub_topic.sink1bts-to-sink1bts-error"]}
   :orionpipe-to-orionpipe-error_sub {:name "orionpipe-to-orionpipe-error_sub"
                                      :topic "orionpipe-to-orionpipe-error"
                                      :depends_on ["google_pubsub_topic.orionpipe-to-orionpipe-error"]}})

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

(def big-replica-controllers {:sink3bts-error-sink {:name "sink3bts-error-sink"
                                                    :docker_image "gcr.io/hx-test/store-sink"
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                    :depends_on ["google_storage_bucket.sink3bts-error" "google_pubsub_subscription.sink3bts-to-sink3bts-error_sub"]
                                                    :zone "europe-west1-c"
                                                    :resource_version ["1"]
                                                    :env_args {:num_retries 3
                                                               :batch_size 1000
                                                               :proj_name "hx-test"
                                                               :sub_name "sink3bts-to-sink3bts-error_sub"
                                                               :bucket_name "sink3bts-error"}}
                              :sink1bts-error-sink {:name "sink1bts-error-sink"
                                                    :docker_image "gcr.io/hx-test/store-sink"
                                                    :depends_on ["google_storage_bucket.sink1bts-error" "google_pubsub_subscription.sink1bts-to-sink1bts-error_sub"]
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                    :zone "europe-west1-c"
                                                    :resource_version ["1"]
                                                    :env_args {:num_retries 3
                                                               :batch_size 1000
                                                               :proj_name "hx-test"
                                                               :sub_name "sink1bts-to-sink1bts-error_sub"
                                                               :bucket_name "sink1bts-error"}}
                              :sink2bts-sink {:name "sink2bts-sink"
                                              :docker_image "gcr.io/hx-test/store-sink"
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                              :depends_on ["google_storage_bucket.sink2-bts-test" "google_pubsub_subscription.pipeline3bts-to-sink2bts_sub"]
                                              :zone "europe-west1-c"
                                              :resource_version ["1"]
                                              :env_args {:num_retries 3
                                                         :batch_size 1000
                                                         :error_topic "projects/hx-test/topics/sink2bts-to-sink2bts-error"
                                                         :proj_name "hx-test"
                                                         :sub_name "pipeline3bts-to-sink2bts_sub"
                                                         :bucket_name "sink2-bts-test"}}
                              :sink1bts-sink {:name "sink1bts-sink"
                                              :docker_image "gcr.io/hx-test/store-sink"
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                              :depends_on ["google_storage_bucket.sink1-bts-test" "google_pubsub_subscription.pipeline2bts-to-sink1bts_sub"]
                                              :zone "europe-west1-c"
                                              :resource_version ["1"]
                                              :env_args {:num_retries 3
                                                         :batch_size 1000
                                                         :proj_name "hx-test"
                                                         :sub_name "pipeline2bts-to-sink1bts_sub"
                                                         :bucket_name "sink1-bts-test"
                                                         :error_topic "projects/hx-test/topics/sink1bts-to-sink1bts-error"}}
                              :pipeline1bts-error-sink {:name "pipeline1bts-error-sink"
                                                        :docker_image "gcr.io/hx-test/store-sink"
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                        :depends_on ["google_storage_bucket.pipeline1bts-error" "google_pubsub_subscription.pipeline1bts-to-pipeline1bts-error_sub"]
                                                        :resource_version ["1"]
                                                        :zone "europe-west1-c"
                                                        :env_args {:num_retries 3
                                                                   :batch_size 1000
                                                                   :proj_name "hx-test"
                                                                   :sub_name "pipeline1bts-to-pipeline1bts-error_sub"
                                                                   :bucket_name "pipeline1bts-error"}}
                              :orionpipe-error-sink {:name "orionpipe-error-sink"
                                                     :docker_image "gcr.io/hx-test/store-sink"
                                                     :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                     :depends_on ["google_storage_bucket.orionpipe-error" "google_pubsub_subscription.orionpipe-to-orionpipe-error_sub"]
                                                     :resource_version ["1"]
                                                     :zone "europe-west1-c"
                                                     :env_args {:num_retries 3
                                                                :batch_size 1000
                                                                :proj_name "hx-test"
                                                                :sub_name "orionpipe-to-orionpipe-error_sub"
                                                                :bucket_name "orionpipe-error"}}
                              :orionsink-error-sink {:name "orionsink-error-sink"
                                                     :docker_image "gcr.io/hx-test/store-sink"
                                                     :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                     :depends_on ["google_storage_bucket.orionsink-error" "google_pubsub_subscription.orionsink-to-orionsink-error_sub"]
                                                     :resource_version ["1"]
                                                     :zone "europe-west1-c"
                                                     :env_args {:num_retries 3
                                                                :batch_size 1000
                                                                :proj_name "hx-test"
                                                                :sub_name "orionsink-to-orionsink-error_sub"
                                                                :bucket_name "orionsink-error"}}
                              :pipeline3bts-error-sink {:name "pipeline3bts-error-sink"
                                                        :docker_image "gcr.io/hx-test/store-sink"
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                        :depends_on ["google_storage_bucket.pipeline3bts-error" "google_pubsub_subscription.pipeline3bts-to-pipeline3bts-error_sub"]
                                                        :resource_version ["1"]
                                                        :zone "europe-west1-c"
                                                        :env_args {:num_retries 3
                                                                   :batch_size 1000
                                                                   :proj_name "hx-test"
                                                                   :sub_name "pipeline3bts-to-pipeline3bts-error_sub"
                                                                   :bucket_name "pipeline3bts-error"}}
                              :sink2bts-error-sink {:name "sink2bts-error-sink"
                                                    :docker_image "gcr.io/hx-test/store-sink"
                                                    :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                    :resource_version ["1"]
                                                    :depends_on ["google_storage_bucket.sink2bts-error" "google_pubsub_subscription.sink2bts-to-sink2bts-error_sub"]
                                                    :zone "europe-west1-c"
                                                    :env_args {:num_retries 3
                                                               :batch_size 1000
                                                               :proj_name "hx-test"
                                                               :sub_name "sink2bts-to-sink2bts-error_sub"
                                                               :bucket_name "sink2bts-error"}}
                              :orionsink-sink {:name "orionsink-sink"
                                               :docker_image "gcr.io/hx-test/store-sink"
                                               :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                               :depends_on ["google_storage_bucket.orionbucket" "google_pubsub_subscription.orionpipe-to-orionsink_sub"]
                                               :resource_version ["1"]
                                               :zone "europe-west1-c"
                                               :env_args {:num_retries 3
                                                          :batch_size 1000
                                                          :proj_name "hx-test"
                                                          :error_topic "projects/hx-test/topics/orionsink-to-orionsink-error"
                                                          :sub_name "orionpipe-to-orionsink_sub"
                                                          :bucket_name "orionbucket"}}
                              :pipeline2bts-error-sink {:name "pipeline2bts-error-sink"
                                                        :docker_image "gcr.io/hx-test/store-sink"
                                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                                        :depends_on ["google_storage_bucket.pipeline2bts-error" "google_pubsub_subscription.pipeline2bts-to-pipeline2bts-error_sub"]
                                                        :resource_version ["1"]
                                                        :zone "europe-west1-c"
                                                        :env_args {:num_retries 3
                                                                   :batch_size 1000
                                                                   :proj_name "hx-test"
                                                                   :sub_name "pipeline2bts-to-pipeline2bts-error_sub"
                                                                   :bucket_name "pipeline2bts-error"}}
                              :sink3bts-sink {:name "sink3bts-sink"
                                              :docker_image "gcr.io/hx-test/store-sink"
                                              :container_name "${google_container_cluster.hx_fstack_cluster.name}"
                                              :resource_version ["1"]
                                              :depends_on ["google_storage_bucket.sink3-bts-test" "google_pubsub_subscription.pipeline2bts-to-sink3bts_sub"]
                                              :zone "europe-west1-c"
                                              :env_args {:num_retries 3
                                                         :batch_size 1000
                                                         :proj_name "hx-test"
                                                         :error_topic "projects/hx-test/topics/sink3bts-to-sink3bts-error"
                                                         :sub_name "pipeline2bts-to-sink3bts_sub"
                                                         :bucket_name "sink3-bts-test"}}})

(def big-bucket  {:sink1bts-error {:name "sink1bts-error"  :location "EU"}
                  :sink3-bts-test {:name "sink3-bts-test"  :location "EU"}
                  :pipeline2bts-error {:name "pipeline2bts-error"
                                       
                                       :location "EU"}
                  :sink3bts-error {:name "sink3bts-error"  :location "EU"}
                  :sink2-bts-test {:name "sink2-bts-test"  :location "EU"}
                  :orionbucket {:name "orionbucket"  :location "EU"}
                  :pipeline3bts-error {:name "pipeline3bts-error"
                                       
                                       :location "EU"}
                  :pipeline1bts-error {:name "pipeline1bts-error"
                                       
                                       :location "EU"}
                  :orionsink-error {:name "orionsink-error"  :location "EU"}
                  :sink1-bts-test {:name "sink1-bts-test"  :location "EU"}
                  :sink2bts-error {:name "sink2bts-error"  :location "EU"}
                  :orionpipe-error {:name "orionpipe-error"  :location "EU"}})

(def big-dataflows {:pipeline1bts (dissoc {:name          "pipeline1bts"
                                           :classpath     "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline1.jar"
                                           :class         "com.acacia.angleddream.Main"
                                           :depends_on    ["google_pubsub_topic.pipeline1bts-to-pipeline3bts"
                                                           "google_pubsub_topic.pipeline1bts-to-pipeline2bts"
                                                           "googleappengine_app.stream1bts"
                                                           "google_pubsub_topic.pipeline1bts-to-pipeline1bts-error"
                                                           "google_pubsub_topic.stream1bts"]
                                           :optional_args {:pubsubTopic       "projects/hx-test/topics/stream1bts"
                                                           :pipelineName      "pipeline1bts"
                                                           :errorPipelineName "projects/hx-test/topics/pipeline1bts-to-pipeline1bts-error"
                                                           :outputTopics      "projects/hx-test/topics/pipeline1bts-to-pipeline3bts,projects/hx-test/topics/pipeline1bts-to-pipeline2bts"
                                                           :stagingLocation   "gs://hx-test/staging-eu"
                                                           :zone              "europe-west1-c"
                                                           :workerMachineType "n1-standard-1"
                                                           :numWorkers        "1"
                                                           :maxNumWorkers     "1"}} :resource_hashes)
                    :pipeline3bts (dissoc {:name          "pipeline3bts"
                                           :classpath     "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline3.jar"
                                           :class         "com.acacia.angleddream.Main"
                                           :depends_on    ["google_pubsub_topic.pipeline3bts-to-sink2bts"
                                                           "googlecli_dataflow.pipeline1bts"
                                                           "google_pubsub_topic.pipeline3bts-to-pipeline3bts-error"
                                                           "google_pubsub_topic.pipeline1bts-to-pipeline3bts"]
                                           :optional_args {:pubsubTopic       "projects/hx-test/topics/pipeline1bts-to-pipeline3bts"
                                                           :pipelineName      "pipeline3bts"
                                                           :errorPipelineName "projects/hx-test/topics/pipeline3bts-to-pipeline3bts-error"
                                                           :outputTopics      "projects/hx-test/topics/pipeline3bts-to-sink2bts"
                                                           :stagingLocation   "gs://hx-test/staging-eu"
                                                           :zone              "europe-west1-c"
                                                           :workerMachineType "n1-standard-4"
                                                           :numWorkers        "1"
                                                           :maxNumWorkers     "1"}} :resource_hashes)
                    :pipeline2bts (dissoc {:name          "pipeline2bts"
                                           :classpath     "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline2.jar"
                                           :class         "com.acacia.angleddream.Main"
                                           :depends_on    ["google_pubsub_topic.pipeline2bts-to-sink3bts"
                                                           "google_pubsub_topic.pipeline2bts-to-sink1bts"
                                                           "googlecli_dataflow.pipeline1bts"
                                                           "google_pubsub_topic.pipeline2bts-to-pipeline2bts-error"
                                                           "google_pubsub_topic.pipeline1bts-to-pipeline2bts"]
                                           :optional_args {:pubsubTopic       "projects/hx-test/topics/pipeline1bts-to-pipeline2bts"
                                                           :pipelineName      "pipeline2bts"
                                                           :errorPipelineName "projects/hx-test/topics/pipeline2bts-to-pipeline2bts-error"
                                                           :outputTopics      "projects/hx-test/topics/pipeline2bts-to-sink3bts,projects/hx-test/topics/pipeline2bts-to-sink1bts"
                                                           :stagingLocation   "gs://hx-test/staging-eu"
                                                           :zone              "europe-west1-c"
                                                           :workerMachineType "n1-standard-1"
                                                           :numWorkers        "1"
                                                           :maxNumWorkers     "1"}} :resource_hashes)
                    :orionpipe    (dissoc {:name          "orionpipe"
                                           :classpath     "/usr/local/lib/angleddream-bundled.jar:/usr/local/lib//usr/local/lib/pipeline1.jar"
                                           :class         "com.acacia.angleddream.Main"
                                           :depends_on    ["google_pubsub_topic.orionpipe-to-orionsink"
                                                           "googleappengine_app.orion"
                                                           "google_pubsub_topic.orionpipe-to-orionpipe-error"
                                                           "google_pubsub_topic.orion"]
                                           :optional_args {:pubsubTopic       "projects/hx-test/topics/orion"
                                                           :pipelineName      "orionpipe"
                                                           :errorPipelineName "projects/hx-test/topics/orionpipe-to-orionpipe-error"
                                                           :outputTopics      "projects/hx-test/topics/orionpipe-to-orionsink"
                                                           :stagingLocation   "gs://hx-test/staging-eu"
                                                           :zone              "europe-west1-c"
                                                           :workerMachineType "n1-standard-1"
                                                           :numWorkers        "1"
                                                           :maxNumWorkers     "1"}} :resource_hashes)})

(def big-appengine {:stream1bts {:moduleName     "stream1bts"
                                 :version        "init"
                                 :gstorageKey    "hxtest-1.0-SNAPSHOT"
                                 :depends_on "google_pubsub_topic.stream1bts"
                                 :resource_version ["1"]
                                 :gstorageBucket "build-artifacts-public-eu"
                                 :scaling        {:minIdleInstances  1
                                                  :maxIdleInstances  1
                                                  :minPendingLatency "3s"
                                                  :maxPendingLatency "6s"}
                                 :topicName      "projects/hx-test/topics/stream1bts"}
                    :orion      {:moduleName     "orion"
                                 :version        "init"
                                 :depends_on "google_pubsub_topic.orion"
                                 :gstorageKey    "hxtest-1.0-SNAPSHOT"
                                 :resource_version ["1"]
                                 :gstorageBucket "build-artifacts-public-eu"
                                 :scaling        {:minIdleInstances  1
                                                  :maxIdleInstances  1
                                                  :minPendingLatency "3s"
                                                  :maxPendingLatency "6s"}
                                 :topicName      "projects/hx-test/topics/orion"}})

(deftest test-big-graph
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

(def bq-dataflow {:name "orionbq"
                  :classpath "/usr/local/lib/angleddream-bundled.jar"
                  :class "com.acacia.angleddream.Main"
                  :depends_on ["googlecli_dataflow.orionpipe"
                               "google_pubsub_topic.orionbq-to-orionbq-error"
                               "google_pubsub_topic.orionpipe-to-orionbq"]
                  :optional_args {:stagingLocation "gs://hx-test/staging-eu"
                                  :zone "europe-west1-c"
                                  :workerMachineType "n1-standard-1"
                                  :bigQueryTable "hx-test"
                                  :errorPipelineName "projects/hx-test/topics/orionbq-error-out"
                                  :bigQueryDataset "hx-test"
                                  :bigQuerySchema "schema.json"
                                  :pubsubTopic "projects/hx-test/topics/orionpipe-out"
                                  :numWorkers "1"
                                  :pipelineName "orionbq"
                                  :maxNumWorkers "1"}})

(def bq-pubsub-tops (->
                     big-pubsub-tops
                     (assoc :orionpipe-to-orionpipe-error {:name "orionpipe-to-orionpipe-error"})
                     (assoc :orionbq-to-orionbq-error {:name "orionbq-to-orionbq-error"})
                     (assoc :orionpipe-to-orionbq {:name "orionpipe-to-orionbq"})))

(def bq-datasets {:hx-test {:datasetId "hx-test"}})

(def bq-tables {:hx-test {:tableId    "hx-test" :datasetId "${googlebigquery_dataset.hx-test.datasetId}"
                          :schemaFile "schema.json" :depends_on ["googlebigquery_dataset.hx-test"]}})

(def bq-buckets (-> big-bucket (assoc :orionbq-error {:name "orionbq-error", , :location "EU"})))

(def bq-replica-controllers (-> big-replica-controllers
                                (assoc :orionbq-error-sink
                                       {:name "orionbq-error-sink",
                                        :depends_on ["google_storage_bucket.orionbq-error" "google_pubsub_subscription.orionbq-to-orionbq-error_sub"]
                                        :resource_version ["1"], :docker_image "gcr.io/hx-test/store-sink",
                                        :container_name "${google_container_cluster.hx_fstack_cluster.name}",
                                        :zone "europe-west1-c", :env_args {:num_retries 3, :batch_size 1000,
                                                                           :proj_name "hx-test", :sub_name "orionbq-to-orionbq-error_sub",
                                                                           :bucket_name "orionbq-error"}})))

(def bq-subs (-> big-pubsub-subs (assoc :orionbq-to-orionbq-error_sub {:name "orionbq-to-orionbq-error_sub", :topic "orionbq-to-orionbq-error", :depends_on ["google_pubsub_topic.orionbq-to-orionbq-error"]})))

;NOTE -- need to have some kind of 'refresh' workflow since we may be defing/undefing in a work session

(deftest add-bq
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

