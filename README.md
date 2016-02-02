# sossity

Pipeline orchestration.
  1. Install leiningen, be sure to set it to env var https://github.com/technomancy/leiningen
  1. Follow instructions on https://github.com/22Acacia/angled-dream and https://github.com/22Acacia/pipeline-examples to build sample files
  1. Create .clj file for pipelines (see https://github.com/22Acacia/pipeline-examples/blob/master/orchestrate/tiny_config.clj for example)
  1. to build: lein uberjar
  2. java -jar target/sossity-0.1.0-SNAPSHOT-standalone.jar -c "/home/proj/pipeline-examples/orchestrate/tiny_config.clj,path/to/another/config.clj" -o "/home/bradford/terraform/terraform-output-file.tf.json"