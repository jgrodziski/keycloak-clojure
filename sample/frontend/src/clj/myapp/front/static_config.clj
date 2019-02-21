(ns myapp.front.static-config
  (:require  [cljs.env :as env]))

(defn get-config
  []
  (if env/*compiler*
    (get-in @env/*compiler* [:options :external-config :myapp])))

(defmacro emit-compile-time-conf
  "Emits expression based on the value of project's compiler options."
  []
  (get-config))
