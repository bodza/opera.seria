(defproject koraujkor "x.y.z"
    :dependencies [[org.clojure/clojure "1.8.0"]
                   [org.clojure/core.async "0.2.374"]
                 #_[org.clojure/core.match "0.3.0-alpha4"]
                 #_[org.clojure/data.int-map "0.2.2"]
                 #_[org.clojure/data.xml "0.0.8"]
                   [org.clojure/clojurescript "1.8.51"]
                   [reagent "0.5.1"]
                 #_[cljsjs/highcharts "4.2.2-2"]
                 #_[com.datomic/datomic-free "0.9.5350"]
                 #_[datascript "0.15.0"]
                 #_[posh "0.3.5"]
                   [ring "1.4.0"]
                   [hiccup "1.0.5"]
                   [com.cognitect/transit-clj "0.8.285"]
                   [com.cognitect/transit-cljs "0.8.237"]]
    :plugins [[lein-try "0.4.3"]
              [lein-cljsbuild "1.1.3"]
              [lein-figwheel "0.5.3"]]
;   :global-vars {*warn-on-reflection* true}
    :jvm-opts ["-Xmx12g"]
;   :javac-options ["-g"]
    :source-paths ["src"] :java-source-paths ["src"] :resource-paths ["resources"] :test-paths ["src"]
    :cljsbuild {:builds {:client {:source-paths ["src"]
                                  :figwheel {:websocket-host :js-client-host}
                                  :compiler {:main "koraujkor.client"
                                             :asset-path "js/out"
                                             :output-dir "resources/public/js/out"
                                             :output-to "resources/public/js/koraujkor.js"
                                             :source-map true
                                             :source-map-timestamp true
                                             :optimizations :none}}}}
    :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                    [org.clojure/tools.nrepl "0.2.12"]]
                     :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}}}
    :figwheel {:server-ip "0.0.0.0"
               :server-port 9210
               :css-dirs ["resources/public/css"]
               :nrepl-port 9389
               :ring-handler koraujkor.server/servlet
               :server-logfile "log/figwheel_server.log"})
