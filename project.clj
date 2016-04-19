(defproject sossity "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["jitpack" "https://jitpack.io"]]
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [aysylu/loom "0.5.4"]
                 [cheshire/cheshire "5.5.0"]
                 [traversy "0.4.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [org.slf4j/slf4j-nop "1.7.5"]
                 [commons-io/commons-io "2.4"]
                 [org.flatland/useful "0.11.3"]
                 [com.cemerick/pomegranate "0.3.0"]
                 [org.clojure/core.async "0.2.374"]
                 [com.github.22Acacia/angled-dream "-SNAPSHOT" :exclusions [org.clojure/clojure]]
                 [danlentz/clj-uuid "0.1.6"]
                 [prismatic/schema "1.0.4"]
                 [pandect "0.5.4"]
                 [digest "1.4.4"]
                 [com.rpl/specter "0.9.2"]
                 [clj-time "0.11.0"]]

  :plugins [[lein-cljfmt "0.3.0"]]

  :main sossity.core
  :aot [sossity.core])

