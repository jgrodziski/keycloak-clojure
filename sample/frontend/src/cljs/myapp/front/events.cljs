(ns myapp.front.events
  (:require [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre :refer-macros [log  trace  debug  info  warn  error  fatal  report
                                                       logf tracef debugf infof warnf errorf fatalf reportf
                                                       spy get-env]]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx]
            [myapp.front.security :as sec]
            [myapp.front.db :as db]))

(re-frame/reg-event-db
 ::initialize-db
 (fn  [_ _]
   db/default-db))

(re-frame/reg-event-db
 ::set-active-panel
 (fn [db [_ active-panel]]
   (assoc db :active-panel active-panel)))

(re-frame/reg-event-fx
 ::set-user-info
 [(re-frame/inject-cofx :cookie)]
 (fn [{:keys [db]} [_ user-info]]
   {:cookie {"X-Authorization-Token" (:token user-info)}
    :db (assoc db :user-info user-info)}))

(defn logout []
  ;;remove cookie
  ;;cancel the keycloak token-to-update interval ticker
  (sec/logout))

(re-frame/reg-event-fx
 :token-to-update/tick
 (fn [cofx event]
   ;;call keycloak to update the token if it has more than 3 minutes of life
   (sec/check-to-update-token cofx event)))

(re-frame/reg-event-fx
 ::set-token-updated
 [(re-frame/inject-cofx :cookie)]
 (fn [cofx [_ {:keys [token]}]]
   (info "set token updated to" token)
   {:cookie (merge (:cookie cofx) {"X-Authorization-Token" token})}))


(re-frame/reg-event-fx
 :post-data
 (fn [{:keys [db]} [_ val]]
   (info "post data " val)
   {:db db
    :http-xhrio {:method :post
                 :uri "http://localhost:8084/restricted"
                 :params val
                 :with-credentials true
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:good-result]}}))
