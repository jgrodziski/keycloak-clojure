(ns dev
  (:require
   [myapp.backend.server] 
   [mount.core :as mount :refer [start stop]]
   [clojure.tools.namespace.repl :as tnr]))

(defn go []
  (start)
  :ready)

(defn reset []
  (stop)
  (tnr/refresh :after 'dev/go))
