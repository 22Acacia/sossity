{:config    {:remote-composer-classpath     "/usr/local/lib/angleddream-bundled.jar"
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
 :cluster   {:name        "hxhstack" :initial_node_count 3 :master_auth {:username "hx" :password "hstack"}
             :node_config {:oauth_scopes ["https://www.googleapis.com/auth/compute"
                                          "https://www.googleapis.com/auth/devstorage.read_only"
                                          "https://www.googleapis.com/auth/logging.write"
                                          "https://www.googleapis.com/auth/monitoring"
                                          "https://www.googleapis.com/auth/cloud-platform"]
                           :machine_type "n1-standard-4"}}
 :opts      {:maxNumWorkers   1 :numWorkers 1 :zone "europe-west1-c"
             :stagingLocation "gs://hx-test/staging-eu"}
 :containers {"riidb" {:image "gcr.io/hx-trial/responsys-resource:latest" :resource-version "v5"}}
 :provider  {:credentials "${file(\"/home/ubuntu/demo-config/account.json\")}" :project "hx-test"}
 :pipelines {"pipeline1bts"
             {:transform-jar "pipeline3.jar"
              :pail          "build-artifacts-public-eu"
              :key           "orion-transform"}}
 :sources   {"stream1bts" {:type "kub"}}
 :sinks     {"sink1bts" {:type "gcs" :bucket "sink1-bts-test"}}
 :edges     [{:origin "stream1bts" :targets ["pipeline1bts"]}
             {:origin "pipeline1bts" :targets ["sink1bts"]}]}


