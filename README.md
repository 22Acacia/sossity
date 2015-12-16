# sossity

Pipeline orchestration.
  1. Install leiningen https://github.com/technomancy/leiningen
  1. Follow instructions on https://github.com/22Acacia/angled-dream and https://github.com/22Acacia/pipeline-examples to build sample files
  1. Create .clj file for pipelines (see https://github.com/22Acacia/pipeline-examples/blob/master/orchestrate/tiny_config.clj for example)
  1. to build: lein uberjar
  2. java -jar target/sossity-SNAPSHOT-standalone.jar -c "pipeline-examples/orchestrate/tiny_config.clj" -o "terraform-output-file.tf.json"