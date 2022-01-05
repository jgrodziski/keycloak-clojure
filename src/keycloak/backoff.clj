(ns keycloak.backoff
  (:require [clojure.core.async :as async]))

(defn timeout [timeout-ms callback]
     (let [fut (future (callback))
           ret (deref fut timeout-ms ::timed-out)]
       (when (= ret ::timed-out)
         (future-cancel fut))
       ret))

(timeout 2000 #(Thread/sleep 5000))

(defn exponential-backoff
  "Implements exponential backoff.

  * af is a function which accepts 3 channels (af =success= =error= =retry=), and should do exactly one of the following operations without blocking:
    - put a successful value in =success=
    - put an error in =error= (will break the loop)
    - put an error which causes a retry in =retry=.
  * the exponential backoff loop can be configured with :get-delay-ms, a function which returns a (potentially infinite) seq of backoff intervals,
   and :imprecision-ms, a maximum number of milliseconds with which to randomly blurr the backoff intervals.

  Returns a channel which will receive either the completed result or an error."
  [{:as opts
    :keys [get-delays-ms imprecision-ms]
    :or {get-delays-ms (constantly [1000 2000 4000 8000 16000])
         imprecision-ms 1000}}
   af]
  (let [=success= (async/chan 1)
        =retry=   (async/chan 1)
        =error=   (async/chan 1)]
    (async/go
      (loop [delays (get-delays-ms)]
        (try
          (af =success= =retry= =error=)
          (catch Throwable err
            (throw (ex-info "Error in keycloak.backoff/exponential-backoff body. Means that `af` was badly implemented, as it should never throw."
                     {} err))))
        (async/alt!
          =success= ([v] v)
          =error= ([err] (throw err))
          =retry= ([err] (if-let [delay-ms (first delays)]
                           (do
                             (async/<! (async/timeout (+ delay-ms (rand-int imprecision-ms))))
                             (recur (next delays)))
                           (throw (ex-info "Failed exponential backoff" {} err)))))))))

(exponential-backoff nil (fn [success retry error]
                           (if (= 0 (rand-int 5))
                             (do (prn "yo")
                                 (async/put! success "yo"))
                             (do (prn "nope")
                                 (async/put! retry "nope")))))
