# Sossity: Collaborative Pipeline Orchestration.


See [How Sossity Works](https://github.com/22Acacia/sossity/wiki/How-Sossity-Works) for an overview.

## Artifact

You should almost never need to build Sossity for testing or simulation. Instead, download the artifact from:

[https://storage.googleapis.com/build-artifacts-public-eu/sossity/sossity-0.1.0-SNAPSHOT-standalone.jar](https://storage.googleapis.com/build-artifacts-public-eu/sossity/sossity-0.1.0-SNAPSHOT-standalone.jar)


## Building Sossity



1. Install Oracle JDK8
1. Install leiningen, be sure to set it to the appropriate environment variable https://github.com/technomancy/leiningen
1. `lein uberjar`
1. Use the `sossity{ver}-standalone.jar`


## Running Sossity

### Create Terraform output file (primary usage)

`java -jar sossity{ver}-standalone.jar -c <config1.clj>,<config2.clj>,<config3.clj> -o <terraform-output.tf.json>`

### View Resource Graph

`java -jar sossity{ver}-standalone.jar -c <config1.clj>,<config2.clj>,<config3.clj> -o <terraform-output.tf.json>`

### Run Simulator

`java -jar sossity{ver}-standalone.jar -s -c <config1.clj>,<config2.clj>,<config3.clj> --testfile <test_config.clj>`

Per-sink test output files are created in `test-output/`