{:config    {:remote-composer-classpath "/usr/local/lib/angleddream-bundled.jar"
             :local-angleddream-path    "/home/bradford/proj/angled-dream/target/angleddream-bundled-0.1-ALPHA.jar"
             :remote-libs-path          "/usr/local/lib"
             :test-output               "/home/bradford/proj/sossity/test/"
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
             {:transform-jar  "visitorpipe.jar"
              :local-jar-path "/home/bradford/proj/pipeline-examples/visitorpipe/target/visitorpipe-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.visitorpipe.VisitorPipeComposer"}
             "pipelineC"
             {:transform-jar  "visitorpipe.jar"
              :local-jar-path "/home/bradford/proj/pipeline-examples/visitorpipe/target/visitorpipe-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.visitorpipe.VisitorPipeComposer"}
             "pipelineD"
             {:transform-jar  "visitorpipe.jar"
              :local-jar-path "/home/bradford/proj/pipeline-examples/visitorpipe/target/visitorpipe-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.visitorpipe.VisitorPipeComposer"}
             "pipelineE"
             {:transform-jar  "visitorpipe.jar"
              :local-jar-path "/home/bradford/proj/pipeline-examples/visitorpipe/target/visitorpipe-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.visitorpipe.VisitorPipeComposer"}
             "pipelineG"
             {:transform-jar  "visitorpipe.jar"
              :local-jar-path "/home/bradford/proj/pipeline-examples/visitorpipe/target/visitorpipe-bundled-0.1-ALPHA.jar"
              :composer-class "com.acacia.visitorpipe.VisitorPipeComposer"}}
 :sources   {"sourceA" {:type "kub" :test-input "/home/bradford/proj/pipeline-examples/test-inputs/sourceA.json"}
             "sourceF" {:type "kub" :test-input "/home/bradford/proj/pipeline-examples/test-inputs/sourceF.json"}}
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
