(ns sossity.util
  (:require    [loom.graph :refer :all]
               [loom.alg :refer :all]
               [loom.io :refer :all]
               [loom.attr :refer :all]))

(defn filter-node-attrs
  ([g keyword value]
   (filter (fn [x] (= value (attr g x keyword))) (nodes g)))
  ([g keyword value nodes]
   (filter (fn [x] (= value (attr g x keyword))) nodes)))

(defn filter-not-node-attrs
  ([g keyword value]
   (filter (fn [x] (not= value (attr g x keyword))) (nodes g)))
  ([g keyword value nodes]
   (filter (fn [x] (not= value (attr g x keyword))) nodes)))

(defn filter-not-edge-attrs
  ([g keyword value]
   (filter (fn [x] (not= value (attr g x keyword))) (edges g)))
  ([g keyword value edges]
   (filter (fn [x] (not= value (attr g x keyword))) edges)))

(defn filter-edge-attrs
  ([g keyword value]
   (filter (fn [x] (= value (attr g x keyword))) (edges g)))
  ([g keyword value edges]
   (filter (fn [x] (= value (attr g x keyword))) edges)))

(defn get-all-node-or-edge-attr [g k]
  (reduce #(let [a (attr g %2 k)]
             (if (some? a)
               (assoc %1 %2 a)
               %1)) {} (nodes g)))
