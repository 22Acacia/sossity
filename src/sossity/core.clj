(ns sossity.core
  (:require [clojure.test :refer :all]
            [sossity.core :refer :all]
            [loom.graph :refer :all]
            [loom.alg :refer :all]
            [loom.io :refer :all]
            [loom.attr :refer :all]
            [cheshire.core :refer :all]
            [traversy.lens :as t :refer :all :exclude [view update combine]])
  )

(defn build-items [g items]
  (reduce #(add-attr %1 (key items) (key %2) (val %2)) g (val items)))

(defn build-annot [g item-set]
  (reduce #(build-items %1 %2) g item-set))

;tag sources and sinks

;NEED ERROR CHECKING - make sure all sources and sinks are in execution graph, etc
;CASE-SENSITIVE

(defn output-topic
  [node project]
  (str "projects/" project "/topics/" node  "-out"))

(defn error-topic
  [node project]
  (str "projects/" project "/topics/" node "-err"))

(defn non-sink-pipes
  [node project]
  ((juxt #(output-topic % project) #(error-topic % project)) node))

(defn sink-pipes
  [node project]
  (error-topic node project))

(defn create-pubsubs
  [g project]                                                 ;out and error for all sources and pipelines, just error for sinks. nodes with cardinality? of 1 have out/error, 0 have error
  (let [t (bf-traverse g)
        connected (filter #(> (out-degree g %1) 0) t)
        ends (filter #(= (out-degree g %1) 0)  t)]
    (into [] (flatten [(map #(non-sink-pipes % project) connected)
                       (map #(sink-pipes % project) ends)]))))


(defn non-sink-successors
  [g node]
  (filter #(> (out-degree g %1) 0) (successors g node))
  )

(defn create-sources-with-dependencies
  [g])

(defn create-sinks-with-dependencies
  [g])


#_(defn dataflow-only-node?
  [t a-graph]
  (let [df-source-sinks (set (-> a-graph (t/view (*>
                                                   (+> (in [:sinks]) (in [:sources]))
                                                   each
                                                   (conditionally #(= (:type %) "cdf"))
                                                   (in [:package])))))]
    (some? (some (set [t]) df-source-sinks))
    )
  )


(defn get-submembers-keys
  "good to know if something is a source or sink"
    [a-graph k]
    (let [df-source-sinks (set (-> a-graph (t/view (*>
                                                     (+> (in [k]))
                                                     each
                                                     all-keys))))]
      df-source-sinks
      )
    )



(defn create-dataflow-item                                  ;build the right classpath, etc. composer should take all the jars in the classpath and glue them together like the transform-graph?
  [g node a-graph]
  ;remember to generate dependency on edges like in example ... depth-first?
  (if (or (= nil (attr g node :type)) (= "cdf" (attr g node :type)))
    (let [
          project (get-in a-graph [:provider :project])
          output-topics (map #(output-topic % project) (non-sink-successors g node))
          input-topics (map #(output-topic % project) (predecessors g node))
          ancestor-jobs (predecessors g node)
          error-topic (error-topic node project)
          name node
          class "Main.class"
          output-depends (map #(str "google_pubsub." %) output-topics)
          input-depends (map #(str "google_pubsub." %) input-topics)
          ancestor-depends (map #(str "google_dataflow." %) ancestor-jobs)
          depends-on (flatten [output-depends input-depends ancestor-depends error-topic])]
      ;pipelines and jobs, TYPE.NAME like aws_instance.web

      (-> (assoc-in {} ["resource" "google_dataflow" name "name"] name)
          (update-in ["resource" "google_dataflow" name] merge {"name" name "class" class "depends_on" depends-on "project" project})))
    )

  )



(defn create-dataflow-jobs [g a-graph]
  (let [t (bf-traverse g)                                   ;filter out anything in soruces or sinks without type cdf
        jobs (filter (comp not nil?) (map #(create-dataflow-item g % a-graph) t))]
    jobs))

(defn output-terraform-file
  [a-graph file]
  (spit file (create-terraform-json a-graph) )                                    ;NOTE -- need to remove first [ and last ]
)

(defn output-provider
  [provider-map]
  (first (assoc-in {} ["provider" "google"] (:provider provider-map))))

(defn output-pubsub
  [pubsub-map]
  (map
    ;#({"resource" {"google_pubsub" {"a" {"name" "b"}}}} )
   #(assoc-in {} ["resource" "google_pubsub" % "name"] %)
   pubsub-map))

(defn create-terraform-json
  [a-graph]
  (let [
        project (get-in a-graph [:provider :project])
        g (create-dag a-graph)
        provider (output-provider a-graph)
        pubsubs (output-pubsub (create-pubsubs g project))
        dataflows (create-dataflow-jobs g a-graph)]
    (generate-string (concat provider pubsubs dataflows) {:pretty true})))

(defn create-dag
  [a-graph]
  (let [g (digraph (into {} (map (juxt :origin :targets) (:edges a-graph))))]
    ;decorate nodes
    (-> (build-annot g (:pipelines a-graph))
        (build-annot  (:sources a-graph))
        (build-annot  (:sinks a-graph))
        #_(add-attr-to-nodes :type :source (get-submembers-keys a-graph :sources))
        #_(add-attr-to-nodes :type :sink (get-submembers-keys a-graph :sinks))
        ))                                    ;return the graph?
  )
