(defproject myapp/frontend "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.946"]
                 [mount "0.1.11"]
                 [com.taoensso/timbre "4.10.0"]
                 [reagent "0.7.0"]
                 [re-frame "0.10.2"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [org.clojure/core.async "0.3.465"]
                 [re-com "2.1.0"]
                 [secretary "1.2.3"]
                 [garden "1.3.3"]
                 [ns-tracker "0.3.1"]

                 [figwheel-sidecar "0.5.14"]
                 [com.cemerick/piggieback "0.2.2"]
                 [day8.re-frame/http-fx "0.1.4"]
                 ]

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-garden "0.2.8"]]

  :min-lein-version "2.5.3"

  :source-paths ["src/clj"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"
                                    "test/js"
                                    "resources/public/css"]


  :garden {:builds [{:id           "screen"
                     :source-paths ["src/clj"]
                     :stylesheet   myapp.front.css/screen
                     :compiler     {:output-to     "resources/public/css/screen.css"
                                    :pretty-print? true}}]}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :aliases {"dev" ["do" "clean"
                        ["pdo" ["figwheel" "dev"]
                               ["garden" "auto"]]]
            "build" ["do" "clean"
                          ["cljsbuild" "once" "min"]
                          ["garden" "once"]]}

  :profiles
  {:dev
   {:dependencies [[binaryage/devtools "0.9.7"]
                   [day8.re-frame/trace "0.1.13"]
                   [figwheel-sidecar "0.5.14"]
                   [proto-repl "0.3.1"]
                   [com.cemerick/piggieback "0.2.2"
                    :exclude [org.clojure/clojurescript]]
                   [org.clojure/tools.nrepl "0.2.13"]
                   [re-frisk "0.5.2"]]
    :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
    :plugins      [[lein-figwheel "0.5.14"]
                   [lein-doo "0.1.8"]
                   [lein-pdo "0.1.1"]]}}

  :figwheel {:css-dirs ["resources/public/css"]
             :server-port 3449
             :nrepl-port 7002
             :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :cljsbuild
  {:builds
   [{:id           "dev"
     :source-paths ["src/cljs"]
     :figwheel     {:on-jsload "myapp.front.core/mount-root"}
     :compiler     {:main                 myapp.front.core
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload
                                           day8.re-frame.trace.preload
                                           re-frisk.preload]
                    :closure-defines      {"re_frame.trace.trace_enabled_QMARK_" true}
                    :external-config      {:devtools/config {:features-to-install :all}
                                           :myapp {:keycloak {:url "https://keycloak.mydomain.com/auth"
                                                             :realm "myrealm"
                                                             :clientId "myapp"
                                                             :credentials {:secret "93e34684-f889-4021-851a-4dd278138612"}}}}
                    :externs ["lib/keycloak/keycloak-externs.js"]
                    :foreign-libs [{:file "lib/keycloak/keycloak.js"
                                    :provides ["keycloak-js"]}]}}

    {:id           "min"
     :source-paths ["src/cljs"]
     :jar true
     :compiler     {:main            myapp.front.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false}
                    :externs ["lib/keycloak/keycloak-externs.js"]
                    :foreign-libs [{:file "lib/keycloak/keycloak.min.js"
                                    :provides ["keycloak-js"]}]
                    :pretty-print    false}}
    {:id           "test"
     :source-paths ["src/cljs" "test/cljs"]
     :compiler     {:main          myapp.front.runner
                    :output-to     "resources/public/js/compiled/test.js"
                    :output-dir    "resources/public/js/compiled/test/out" :foreign-libs [{:file "lib/keycloak/keycloak.js"
                                    :provides ["keycloak-js"]}]
                    :optimizations :none}}]}

  :jvm-opts ["--add-modules" "java.xml.bind"])
