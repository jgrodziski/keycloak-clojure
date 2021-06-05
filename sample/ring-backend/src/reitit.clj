(ns reitit
  (:require [reitit.core :as rc]))

(defn reitit-extract-headers [router]
  (memoize (fn [method uri]
             (get-in (rc/match-by-path router uri)
                     [:data method :access-control :headers]))))

(defn reitit-authenticated? [router]
  (memoize (fn [method uri]
             (or (= method :options)
                 (get-in (rc/match-by-path router uri)
                         [:data method :access-control :authenticated?])))))

(defn reitit-extract-roles [router]
  (memoize (fn [method uri] (get-in (rc/match-by-path router uri) [:data method :access-control :roles]))))

(defn ->access-control [allow-origin]
  {:scheme            :keycloak
   :allow-origin      allow-origin
   :allow-credentials true
   :allow-methods     #{:get :post :options :delete :put}
   :allow-headers     #{"Content-Type" "Access-Control-Allow-Headers" "Access-Control-Allow-Origin" "Authorization" "X-Requested-With" "X-Authorization-Token" "Access-Control-Request-Method" "x-request-id"}
   :expose-headers    #{"Content-Type" "Access-Control-Allow-Headers" "Access-Control-Allow-Origin" "Authorization" "X-Requested-With" "X-Authorization-Token" "Access-Control-Request-Method"}})

(defn request [allow-origin authorized_roles handler]
  {:produces       ["application/json" "application/transit+json" "application/edn"]
   :consumes       ["application/json" "application/transit+json" "application/edn"]
   :access-control {:roles authorized_roles :headers (->access-control allow-origin)}
   :handler handler})


(defn options_request [allow-origin type]
  {:produces       ["application/json" "application/transit+json" "application/edn"]
   :consumes       ["application/json" "application/transit+json" "application/edn" "*/*"]
   :access-control {:roles [] :headers (->access-control allow-origin)}
   :handler (fn [req]
              (let [query (->query (ring-extract-account-data req) type)]
                {:body {} :status  200}))})
