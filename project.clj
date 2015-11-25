(defproject sossity "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [aysylu/loom "0.5.4"]
                 [cheshire/cheshire "5.5.0"]
                 [traversy "0.4.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [com.google.apis/google-api-services-bigquery "v2-rev187-1.19.1"]]
  :plugins [[lein-cljfmt "0.3.0"]]
  :main sossity.core
  :aot [sossity.core])

