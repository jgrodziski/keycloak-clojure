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
  "return the access token decoded as a clojure data struct."
  [auth-server-url realm-name client-id username password]
  (info "Authenticate against " (oidc-connect-url auth-server-url realm-name))
  (-> (http/post (oidc-connect-url auth-server-url realm-name)
                    {:form-params (client-credentials client-id username password)})
        :body
        (parse-string true)))

(defn access-token [client username password]
  (-> client (.obtainAccessToken username password)))
