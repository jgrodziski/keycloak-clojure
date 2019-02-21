(ns myapp.front.cookie
  (:refer-clojure :exclude [reset!])
  (:require [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre :refer-macros [log  trace  debug  info  warn  error  fatal  report
                                                       logf tracef debugf infof warnf errorf fatalf reportf
                                                       spy get-env]]
            [cljs.reader :refer [read-string]])
  (:import goog.net.cookies))

(defn get-cookie
  ([key]
   (get-cookie key nil))
  ([key not-found]
   (let [cookies goog.net.cookies
         key (str key)]
     (if (.containsKey cookies key)
       (read-string (.get cookies key))
       not-found))))

(defn reset! [key val]
  (.set goog.net.cookies
        (str key)
        val))

(defn remove! [key]
  (.remove goog.net.cookies (str key)))

(re-frame/reg-cofx
 :cookie
 (fn cookie-coeffect-handler
   [coeffects key]
   (debug "cookie-coeffect=handler" coeffects key)
   (assoc coeffects :cookie {key (.get goog.net.cookies key)})))

(re-frame/reg-fx
 :cookie
 (fn cookie-fx-handler
   [cookies]
   (debug "cookie-fx-handler" cookies)
   (doseq [[k v] cookies]
     (reset! k v))))
