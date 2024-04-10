(ns keycloak.authn
  (:require
   [clojure.tools.logging :as log :refer [info]]
   [clj-http.client :as http]
   [cheshire.core :refer [parse-string]]
   [keycloak.deployment :as deployment]))

;(set! *warn-on-reflection* true)

(defn oidc-connect-url [auth-server-url realm-name]
  (str auth-server-url "/realms/" realm-name "/protocol/openid-connect/token"))

(defn client-credentials
  ([client-id client-secret username password]
   {:grant_type "password"
    :client_id client-id
    :client_secret client-secret
    :username username
    :password password})
  ([client-id client-secret]
   {:grant_type "client_credentials"
    :client_id client-id
    :client_secret client-secret}))


(defn authenticate
  "Return the bearer token decoded as a clojure map with `:access_token` and `:refresh_token` keys, beware underscore `_` not hyphen `-`.
   Also contains the `:expires_in` and `:refresh_expires_in` values of token duration in seconds.
   The keycloak conf needs the `:auth-server-url`, `realm` and `client-id` keys"
  ([{:keys [auth-server-url realm client-id] :as conf} client-secret]
   (authenticate auth-server-url realm client-id client-secret))
  ([{:keys [auth-server-url realm client-id client-secret] :as conf} username password]
   (authenticate auth-server-url realm client-id client-secret username password))
  ([auth-server-url realm client-id client-secret]
   (info "Authenticate against" (oidc-connect-url auth-server-url realm) "for client-id"  client-id)
   (-> (http/post (oidc-connect-url auth-server-url realm)
                  {:form-params (client-credentials client-id client-secret) :content-type "application/x-www-form-urlencoded"})
       :body
       (parse-string true)))
  ([auth-server-url realm client-id client-secret username password]
   (info "Authenticate against" (oidc-connect-url auth-server-url realm) "for client-id"  client-id "with user" username)
   (-> (http/post (oidc-connect-url auth-server-url realm)
                  {:form-params (client-credentials client-id client-secret username password) :content-type "application/x-www-form-urlencoded"})
       :body
       (parse-string true))))


(defn access-token [^org.keycloak.authorization.client.AuthzClient client username password]
  (-> client (.obtainAccessToken username password)))

(defn auth-cookie [bearer]
  {"X-Authorization-Token" {:discard true, :path "/", :value (:access_token bearer), :version 0}})

(defn auth-header
  "Return a map with \"authorization\" key and value the access token with \"Bearer \" prefix. Argument is the data structure returned by the `keycloak.authn/authenticate` function"
  [bearer]
  {"authorization" (str "Bearer " (:access_token bearer))})

(defn near-expiration?
  "Does the token from the `token-store` expires in less than 20 seconds?"
  [token-store username]
  (when (contains? token-store username)
    (let [elapsed-time (when (get-in token-store [username :issued-at])
                         (- (System/currentTimeMillis) (get-in token-store [username :issued-at])))]
      ;(println (get-in token-store [username :issued-at]) (System/currentTimeMillis) (get-in token-store [username :expires_in]))
      (when-let [now-expires-in (- (* 1000 (get-in token-store [username :expires_in])) elapsed-time)]
        ;(println "expired in " now-expires-in)
        (< now-expires-in 20000)))))

(defn token!
  "Given a map in an atom as a token store, issue, cache and re-issue a token from a keycloak with a username and password.
   Swap the map entry with the `username` key with this function result (actually the same response as the `keycloak.authn/authenticate` function)"
  [token-store keycloak-conf username password]
  (let [exists?          (contains? @token-store username)
        near-expiration? (near-expiration? @token-store username)]
    ;(println keycloak-conf username password exists? near-expiration? @token-store)
    (if (and exists? (not near-expiration?))
      (get @token-store username)
      (let [authn-result (assoc (authenticate keycloak-conf username password) :issued-at (System/currentTimeMillis))]
        (swap! token-store assoc username authn-result)
        authn-result))))
