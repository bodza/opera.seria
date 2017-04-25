(defproject lectio/eruditio "x.y.z"
    :dependencies [[org.clojure/clojure "1.8.0"]
                   [org.clojure/core.match "0.3.0-alpha4"]
                   [org.clojure/data.int-map "0.2.2"]
                 #_[org.clojure/data.xml "0.0.8"]
                 #_[datascript "0.15.0"]
                   [com.datomic/datomic-free "0.9.5350"]]
    :plugins [[lein-try "0.4.3"]]
;   :global-vars {*warn-on-reflection* true}
    :jvm-opts ["-Xmx12g"]
;   :javac-options ["-g"]
    :source-paths ["src"] :java-source-paths ["src"] :resource-paths ["src"] :test-paths ["src"])
