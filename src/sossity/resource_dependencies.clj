(ns sossity.resource-dependencies)



(def dependency-graph
  "a visualization of terraform resource dependencies. could be a prismatic schema as well"
  {
   :source [:appengine]
   :appengine [:pubsub]
   :sink   [:pubsub-subscription :container-replica-controller :google-storage-bucket :bigquery-dataset :bigquery-table]
   :pubsub-subscription [:pubsub]
   :pipeline [:pubsub :cloud-dataflow]
   :container-replica-controller [:container-cluster]

   })
