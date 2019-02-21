(defproject myapp/backend "0.1.0-SNAPSHOT"
  :description "myappApp API backend"
  :url "http://myappapp.fr"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.nrepl "0.2.13"]

                 [com.taoensso/timbre "4.10.0"]
                 [cheshire "5.8.0"]
                 ;; Server deps
                 [mount "0.1.11"]
                 [aero "1.1.2"]
                 [bidi "2.1.1"]
                 [hiccup "1.0.5"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [yada "1.2.9" :exclusions [ring-swagger]]
                 [aleph-middleware "0.2.0"]
                 ;; https://github.com/juxt/yada/pull/181
                 [org.clojure/core.async "0.3.443"]
                 [aleph "0.4.4"]
                 [metosin/ring-swagger "0.24.3"]

                 ;; Keycloak Stuff
                 [org.keycloak/keycloak-adapter-core "3.4.0.Final"]
                 [org.keycloak/keycloak-core "3.4.0.Final"]
                 [org.jboss.logging/jboss-logging "3.3.1.Final"]
                 [org.apache.httpcomponents/httpclient "4.5.2"]
                 ]
  :main ^:skip-aot myapp.backend.server
  :repl-options {:init-ns dev}
  :target-path "target/%s"
  :profiles {:dev {:source-paths ["dev"]}
             :uberjar {:aot :all}}

  )
