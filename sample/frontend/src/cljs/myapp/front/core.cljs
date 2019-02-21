(ns myapp.front.core
  (:require [reagent.core :as reagent]
            [mount.core :as mount]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre :refer-macros [log  trace  debug  info  warn  error  fatal  report
                                                       logf tracef debugf infof warnf errorf fatalf reportf
                                                       spy get-env]]
            [myapp.front.security :as security :refer [keycloak]]
            [myapp.front.events :as events]
            [myapp.front.cookie :as cookie]
            [myapp.front.routes :as routes]
            [myapp.front.views :as views]
            [myapp.front.config :as config]))



(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (info "dev mode")))

(defn mount-root []
  (info "Mount root component")
  (re-frame/clear-subscription-cache!)
  (reagent/render [views/main-panel]
                  (.getElementById js/document "app")))

(defn ^:export init []
  (info "init myapp.front App")
  (routes/app-routes)
  (re-frame/dispatch-sync [::events/initialize-db])
  (dev-setup)
  (mount-root))

(mount/start)
