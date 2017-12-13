(ns myapp.front.interval
  (:require [cljs.core.async :refer [chan timeout <! >! alts! close! put!]]
            [re-frame.core :refer [reg-event-db dispatch]])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn interval
  "Returns a control channel to an interval. Commands are:
  - `:start`: Starts counting time. If counter is already active, does nothing;
  - `:restart`: Starts counting time from 0. If stopped, will start anyway.
  - `:stop`: Stops the counter. Next time `:start` is called, counter starts from zero.
  Any other command issued will be treated as an exit signal."
  [timeout-in-msecs f & args]
  (let [control-channel (chan)]
    ;; controlled thread - will exit on any command received
    (go-loop [channels [control-channel]]
      (let [[cmd ch] (alts! channels)]
        (if(identical? ch control-channel) ;; a command was received
          (case cmd
            :start   (recur [control-channel
                             (or (second channels)
                                 (timeout timeout-in-msecs))])
            :restart (recur [control-channel
                             (timeout timeout-in-msecs)])
            :stop    (recur [control-channel])
            nil) ;; will exit on unknown command
          (do ;; timeout channel happened, so it's time to tick and reset counter
            (apply f args)
            (recur [control-channel (timeout timeout-in-msecs)])))))
    control-channel))

(defn register-interval-handlers
  [k-pref middleware timeout-in-msecs]
  (let [pref (name k-pref)
        control-channel (interval timeout-in-msecs
                                  dispatch [(keyword pref "tick")])]
    (doseq [action [:start :stop :restart :exit]]
      (reg-event-db
       (keyword pref (name action))
       middleware
       (fn [db _]
         (put! control-channel action)
         db)))))
