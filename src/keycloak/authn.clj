(ns keycloak.authn
  (:require
   [clojure.tools.logging :as log :refer [info]]
   [clj-http.client :as http]
   [cheshire.core :refer [parse-string]]))

(defn oidc-connect-url [auth-server-url realm-name]
  (str auth-server-url "/realms/" realm-name "/protocol/openid-connect/token"))

(defn client-credentials
  ([client-id username password]
   {:grant_type "password"
    :client_id client-id
    :username username
    :password password})
  ([client-id client-secret]
   {:grant_type "client_credentials"
    :client_id client-id
    :client_secret client-secret}))

(defn authenticate
  "return the bearer token decoded as a clojure data struct. (with :access_token and :refresh_token keys, beware 'underscore _' not 'hyphen -')"
  ([{:keys [auth-server-url realm client-id] :as conf} username password]
   (authenticate auth-server-url realm client-id username password))
  ([auth-server-url realm client-id username password]
   (info "Authenticate against" (oidc-connect-url auth-server-url realm) "for client-id"  client-id "with user" username)
   (-> (http/post (oidc-connect-url auth-server-url realm)
                  {:form-params (client-credentials client-id username password)})
       :body
       (parse-string true))))

(defn access-token [client username password]
  (-> client (.obtainAccessToken username password)))

(defn auth-cookie [bearer]
  {"X-Authorization-Token" {:discard true, :path "/", :value (:access_token bearer), :version 0}})

(defn auth-header [bearer]
  {"authorization" (:access_token bearer)})
