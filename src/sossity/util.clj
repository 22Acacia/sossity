(ns sossity.util
  (:require [loom.graph :refer :all]
            [loom.alg :refer :all]
            [loom.io :refer :all]
            [loom.attr :refer :all]
            [pandect.algo.md5 :refer :all]
            [clojure.java.io :as f]
            [clojure.walk :refer [postwalk]])
  (:import (java.security MessageDigest)
           (java.nio.file Paths)))

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

(defn filter-node-attr-exists
  ([g keyword]
   (filter (fn [x] (some? (attr g x keyword))) (nodes g)))
  ([g keyword nodes]
   (filter (fn [x] (some? (attr g x keyword))) nodes)))

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

(defn get-all-node-or-edge-attr ([g k]
                                 (reduce #(let [a (attr g %2 k)]
                                            (if (some? a)
                                              (assoc %1 %2 a)
                                              %1)) {} (nodes g)))
  ([g k & l]
   (reduce #(let [a (get-in (attrs g %2) [k l])]
              (if (some? a)
                (assoc %1 %2 a)
                %1)) {} (nodes g))))

(defn hash-jar [path]
  "create a hash of a jar's contents so we can know if it's updated and re-deploy"
  (try
    (md5-file path)
    (catch Exception e
      #_(println e))))

(defn get-path [^String dir & args]
  "returns well-formed path string. internally implemented this way because java does weird things with variadic fns"
  (.toString (Paths/get dir (into-array String args))))

(defn remove-nils
  [m]
  (let [f (fn [[k v]] (when v [k v]))]
    (postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn deep-merge
  "Deeply merges maps so that nested maps are combined rather than replaced.
  For example:
  (deep-merge {:foo {:bar :baz}} {:foo {:fuzz :buzz}})
  ;;=> {:foo {:bar :baz, :fuzz :buzz}}
  ;; contrast with clojure.core/merge
  (merge {:foo {:bar :baz}} {:foo {:fuzz :buzz}})
  ;;=> {:foo {:fuzz :quzz}} ; note how last value for :foo wins"
  [& vs]
  (if (every? map? vs)
    (apply merge-with deep-merge vs)
    (last vs)))

(defn deep-merge-with
  "Deeply merges like `deep-merge`, but uses `f` to produce a value from the
  conflicting values for a key in multiple maps."
  [f & vs]
  (if (every? map? vs)
    (apply merge-with (partial deep-merge-with f) vs)
    (apply f vs)))