(ns myapp.backend.server
  (:require
   [myapp.backend.keycloak :as keycloak]
   [taoensso.timbre :as timbre]
   [hiccup.page :refer [html5]]
   [mount.core :as mount :refer [defstate]]
   [aleph.middleware.session :refer [wrap-session]]
   [yada.security :refer [verify]]
   [yada.cookies :refer [parse-cookies]]
   [yada.handler :refer [handler]]
   [yada.yada :refer [listener resource as-resource]]))

(timbre/refer-timbre)

(defn authorization-bearer-cred [ctx]
  (let [header (get-in ctx [:request :headers "authorization"])]
    (when header
      (last (re-find #"^Bearer (.*)$" header)))))

;;1. Extract the token from the cookie or the header (Yada framework specific)
(defn authorization-token-cookie [ctx]
  (let [cookies (parse-cookies (:request ctx))]
    (get cookies "X-Authorization-Token")))

(defmethod yada.security/verify :keycloak
  [ctx scheme]
  (let [header-cred (authorization-bearer-cred ctx)
        cookie-cred (authorization-token-cookie ctx)]
    (-> (or header-cred cookie-cred)
        ;; Verify the token (Keycloak stuff)
        (keycloak/verify)
        (keycloak/extract))))

(defn- restricted-content [ctx]
  (html5
   [:body
    [:h1 (format "Hello %s!"
                 (get-in ctx [:authentication "default" :user]))]
    [:p "You're accessing a restricted resource!"]]))

(def routes
  ["/"
   [
    ["hello" ( handler (as-resource "Hello world!"))]
    ["restricted" (resource {:consumes #{"application/json"} 
                             :produces {:media-type "text/html"}
                             :methods {:post (fn [ctx] (restricted-content ctx))}
                             ;;Ensure the client is accessing the restricted part only if the token is correct
                             :access-control {:scheme :keycloak
                                              :allow-origin "http://localhost:3449"
                                              :allow-credentials true
                                              :allow-methods #{:get :post :options}
                                              :allow-headers ["Content-Type"
                                                              "Access-Control-Allow-Headers"
                                                              "Authorization"
                                                              "X-Requested-With"
                                                              "X-Authorization-Token"]
                              :authorization {:methods {:get :employee :post :employee}}}})]
    ;;["keycloak-callback (resource )"]
    [true (as-resource nil)]]])

(defstate server
  :start (listener routes {:port 8084})
  :stop ((:close server)))

