(ns keycloak.middleware
  (:require
   [clojure.set]
   [clojure.tools.logging :as log :refer [info error]]
   [keycloak.backend :as backend :refer [verify-credential-in-headers]]))

(defn to-header [v]
  (if (coll? v)
    (apply str (interpose ", " v))
    (str v)))

(defn access-control-headers [access-control-headers origin response]
  (let [all-allowed-origins (:allow-origin access-control-headers)
        allow-origin        (cond
                              (= all-allowed-origins "*")   "*"
                              (string? all-allowed-origins) all-allowed-origins
                              (ifn? all-allowed-origins)    (cond
                                                              (vector? all-allowed-origins) (or (get all-allowed-origins (.indexOf all-allowed-origins origin))
                                                                                                (get all-allowed-origins (.indexOf all-allowed-origins "*")))
                                                              (set? all-allowed-origins)    (or (all-allowed-origins origin)
                                                                                                (all-allowed-origins "*"))))]
    (cond-> response
      allow-origin                                          (assoc-in [:headers "Access-Control-Allow-Origin"] allow-origin)
      (contains? access-control-headers :allow-credentials) (assoc-in [:headers "Access-Control-Allow-Credentials"]
                                                                           (to-header (:allow-credentials access-control-headers)))
      (:expose-headers access-control-headers)              (assoc-in [:headers "Access-Control-Expose-Headers"]
                                                                           (to-header (:expose-headers access-control-headers)))
      (:max-age access-control-headers)                     (assoc-in [:headers "Access-Control-Max-Age"]
                                                                           (to-header (:max-age access-control-headers)))
      (:allow-methods access-control-headers)               (assoc-in [:headers "Access-Control-Allow-Methods"]
                                                                           (to-header (map (comp clojure.string/upper-case name)  (:allow-methods access-control-headers))))
      (:allow-headers access-control-headers)               (assoc-in [:headers "Access-Control-Allow-Headers"]
                                                                           (to-header (:allow-headers access-control-headers))))))


(defn wrap-access-control-headers
  "a handler and a function that extract headers from the request"
  [handler extract-headers-fn]
  (fn ([req]
      (let [headers (extract-headers-fn req)]
        (access-control-headers headers (get-in req [:headers "origin"]) (handler req))))
    ([req respond raise]
     (let [headers (extract-headers-fn req)]
       (handler req (fn [response]
                      (respond (access-control-headers headers (get-in req [:headers :origin]) response))) raise)))))


(defn wrap-authentication [handler keycloak-deployment authenticated?-fn]
  (fn
    ([req]
     (if (authenticated?-fn (:request-method req) (:uri req))
       (try
         (let [extracted-access-token (backend/verify-credential-in-headers (:headers req) keycloak-deployment)]
           (handler (assoc req :identity extracted-access-token)))
         (catch Exception e
           (log/error e ::wrap-authentication :msg (.getMessage e))
           {:status 500 :body (.getMessage e)}))
       (handler req)))
    ([req respond raise]
     (if (authenticated?-fn (:request-method req) (:uri req))
       (try
         (let [extracted-access-token (backend/verify-credential-in-headers (:headers req) keycloak-deployment)]
           (handler (assoc req :identity extracted-access-token) respond raise))
         (catch Exception e
           (log/error e ::wrap-authentication :msg (.getMessage e))
           (raise e)))
       (handler req respond raise)))))

(defn verify-authorization [authorized-roles roles]
  (when (empty? (clojure.set/intersection roles (set authorized-roles)))
    (log/info (format "Verify authorization: no roles authorized (%s) in identity roles (%s)" authorized-roles roles))
    (throw (ex-info "Forbidden" {:status 403 :body "Forbidden"}))))


(defn wrap-authorization [handler authenticated-fn? extract-roles-fn]
  (fn
    ([req]
     (if (authenticated-fn? req)
       (try
         (let [authorized-roles (extract-roles-fn (:request-method req) (:uri req))]
           (verify-authorization authorized-roles (get-in req [:identity :roles]))
           (handler req))
         (catch Exception e (ex-data e)))
       (handler req)))
    ([req respond raise]
     (try
       (let [authorized-roles (extract-roles-fn (:request-method req) (:uri req))]
         (verify-authorization authorized-roles (get-in req [:identity :roles]))
         (respond (handler req respond raise)))
       (catch Exception e (respond (ex-data e)))))))


(defn my-wrapper [handler]
  (fn ([req]
       (handler req))
    ([req respond raise]
     (handler req respond raise))))

(defn wrap-log [handler]
  (fn
    ([req]
     (handler req))
    ([req respond raise]
     (handler req respond raise))))

(defn unauthorized-handler
  ([req metadata]
   (println "unauthorized handler " req metadata))
  ([req respond handler]
   (println "unauthorized handler " req )))
