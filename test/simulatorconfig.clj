{:config    {:remote-composer-classpath "/usr/local/lib/angleddream-bundled-0.1-ALPHA.jar"
             :local-angleddream-path    "/home/bradford/proj/angled-dream/target/angleddream-bundled-0.1-ALPHA.jar"
             :remote-libs-path          "/usr/local/lib"
             :test-output               "/home/bradford/proj/sossity-pipeline-java-sample/testoutput/"
             :error-buckets             true}
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
 :pipelines {"pipelineB"
             {:transform-jar  "timestamppipeline-bundled-0.1-ALPHA.jar"
              :local-jar-path "/home/bradford/proj/sossity-pipeline-java-sample/target/timestamppipeline-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.timestamppipeline.TimestampComposer"}
             "pipelineC"
             {:transform-jar  "timestamppipeline.jar-bundled-0.1-ALPHA.jar"
              :local-jar-path "/home/bradford/proj/sossity-pipeline-java-sample/target/timestamppipeline-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.timestamppipeline.TimestampComposer"}
             "pipelineD"
             {:transform-jar  "timestamppipeline.jar-bundled-0.1-ALPHA.jar"
              :local-jar-path "/home/bradford/proj/sossity-pipeline-java-sample/target/timestamppipeline-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.timestamppipeline.TimestampComposer"}
             "pipelineE"
             {:transform-jar  "timestamppipeline.jar-bundled-0.1-ALPHA.jar"
              :local-jar-path "/home/bradford/proj/sossity-pipeline-java-sample/target/timestamppipeline-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.timestamppipeline.TimestampComposer"}
             "pipelineG"
             {:transform-jar  "timestamppipeline.jar-bundled-0.1-ALPHA.jar"
              :local-jar-path "/home/bradford/proj/sossity-pipeline-java-sample/target/timestamppipeline-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.timestamppipeline.TimestampComposer"}}
 :sources   {"sourceA" {:type "kub" :test-input "/home/bradford/proj/sossity-pipeline-java-sample/test-data/sourceA.json"}
             "sourceF" {:type "kub" :test-input "/home/bradford/proj/sossity-pipeline-java-sample/test-data/sourceF.json"}}
 :sinks     {"sinkB" {:type "gcs" :bucket "sinkB-test"}
             "sinkD" {:type "gcs" :bucket "sinkD-test"}
             "sinkE" {:type "gcs" :bucket "sinkE-test"}
             "sinkG" {:type "gcs" :bucket "sinkG-test"}}
 :edges     [{:origin "sourceA" :targets ["pipelineB" "pipelineC"]}
             {:origin "sourceF" :targets ["pipelineG"]}
             {:origin "pipelineB" :targets ["sinkB"]}
             {:origin "pipelineC" :targets ["pipelineD" "pipelineE"]}
             {:origin "pipelineD" :targets ["sinkD"]}
             {:origin "pipelineE" :targets ["sinkE"]}
             {:origin "pipelineG" :targets ["sinkG"]}]}

