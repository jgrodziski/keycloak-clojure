(ns keycloak.backoff
  (:require [clojure.core.async :as async]))

(defn timeout [timeout-ms f]
     (let [fut (future (f))
           ret (deref fut timeout-ms :timed-out)]
       (when (= ret :timed-out)
         (future-cancel fut))
       ret))


(defn exponential-backoff
  "Implements exponential backoff.

  * f is a function which accepts 3 channels (f =success= =error= =retry=), and should do exactly one of the following operations without blocking:
    - put a successful value in =success=
    - put an error in =error= (will break the loop)
    - put an error which causes a retry in =retry=.
  * the exponential backoff loop can be configured with :get-delay-ms, a function which returns a (potentially infinite) seq of backoff intervals,
   and :imprecision-ms, a maximum number of milliseconds with which to randomly blurr the backoff intervals.

  Returns a channel which will receive either the completed result or an error."
  ([f]
   (exponential-backoff f nil))
  ([f {:as opts :keys [get-delays-ms imprecision-ms] :or {get-delays-ms (constantly [1000 2000 4000 8000 16000]) imprecision-ms 1000}}]
   (let [=success= (async/chan 1)
         =retry=   (async/chan 1)
         =error=   (async/chan 1)]
     (async/go
       (loop [delays (get-delays-ms)]
         (try
           (f =success= =retry= =error=)
           (catch Throwable err
             (throw (ex-info "Error in keycloak.backoff/exponential-backoff body. Means that `f` was badly implemented, as it should never throw."
                             {} err))))
         (async/alt!
           =success= ([v] v)
           =error=   ([err] err)
           =retry=   ([err]
                       (if-let [delay-ms (first delays)]
                         (do
                           ;(prn "retry in " delay-ms "ms")
                           (async/<! (async/timeout (+ delay-ms (rand-int imprecision-ms))))
                           (recur (next delays)))
                         (do ;(prn "Failed exp backoff")
                             (throw (ex-info "Failed exponential backoff" {} err)))))))))))

(comment

(timeout 2000 #(Thread/sleep 5000))
(timeout 2000 (fn [] (Thread/sleep 1999)(prn "yo") "yo"))
(timeout 2000 (fn [] (Thread/sleep 500) (throw (ex-info "yo"))))
(exponential-backoff (fn [success retry error]
                       (if (= 0 (rand-int 5))
                         (if (= 0 (rand-int 2))
                           (do
                             (async/put! success "yo"))
                           (do (prn "error")
                               (async/put! error (ex-info "Error" {:a 1}))))
                         (do (prn "nope")
                             (async/put! retry "nope")))))

(async/go
  (let [result-chan (exponential-backoff (fn [success retry error]
                                           (if (= 0 (rand-int 5))
                                             (if (= 0 (rand-int 2))
                                               (do
                                                 (async/put! success "yo"))
                                               (do (prn "error")
                                                   (async/put! error "test"
                                                              ; (ex-info "Error" {:a 1})
                                                               )))
                                             (do (prn "nope")
                                                 (async/put! retry "nope")))))]
    (println "result is" (async/<! result-chan))))


)


