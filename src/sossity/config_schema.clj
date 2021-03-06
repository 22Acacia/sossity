(ns sossity.config-schema
  (:require
   [schema.core :as s]))

(def sys-jar {:name s/Str :pail s/Str :key s/Str})

(def config {:remote-composer-classpath s/Str
             :remote-libs-path s/Str
             :error-buckets s/Bool
             :sink-resource-version s/Str
             :default-error-bucket-batch-size s/Int
             :source-resource-version s/Str
             :appengine-gstoragekey s/Str
             :default-sink-docker-image s/Str
             :appengine-sinkkey s/Str
             :system-jar-info {:angleddream sys-jar :sossity sys-jar}
             :default-pipeline-machine-type s/Str
             (s/optional-key :test-output) s/Str})

(def cluster {:name s/Str :initial_node_count s/Int :master_auth {:username s/Str :password s/Str} :node_config {:oauth_scopes [s/Str] :machine_type s/Str}})

(def opts {:maxNumWorkers s/Int :numWorkers s/Int :zone s/Str :autoscalingAlgorithm s/Str
           :stagingLocation s/Str})

(def provider {:credentials s/Str :project s/Str})

(def pipeline-item {:transform-jar                   s/Str
                    :pail                                s/Str
                    :key s/Str
                    (s/optional-key :workerMachineType) s/Str
                    (s/optional-key :container-deps) [s/Str]
                    (s/optional-key :local-jar-path) s/Str
                    (s/optional-key :error-out) s/Bool
                    (s/optional-key :composer-class) s/Str})

(def pipelines {s/Str pipeline-item})

(def source-item {:type s/Str (s/optional-key :test-input) s/Str})

(def sources {s/Str source-item})

(def container-item {:gstorageBucket s/Str
                     :gstorageKey s/Str
                     :scriptName s/Str
                     :resource-version      s/Str
                     (s/optional-key :error-out) s/Bool
                     (s/optional-key :args) {s/Any s/Any}})

(def containers {s/Str container-item})

;need to have conditionals for bigquery
(def sink-item {:type s/Str
                (s/optional-key :bucket) s/Str
                (s/optional-key :replicas) s/Int
                (s/optional-key :batch_size) s/Int
                (s/optional-key :bigQueryDataset) s/Str
                (s/optional-key :bigQueryTable) s/Str
                (s/optional-key :error-out) s/Bool
                (s/optional-key :bigQuerySchema) s/Str
                (s/optional-key :sink_type) s/Str
                (s/optional-key :dataset_name) s/Str
                (s/optional-key :rsys_pass) s/Str
                (s/optional-key :rsys_user) s/Str
                (s/optional-key :rsys_table) s/Str
                (s/optional-key :merge_insert) s/Bool})

(def sinks {s/Str sink-item})

(def edges [{:origin s/Str :targets [s/Str]}])

(def base {:config config :cluster cluster :opts opts :provider provider (s/optional-key :containers) containers :pipelines pipelines :sources sources :sinks sinks :edges edges})

