(ns keycloak.backend
  (:require
   [clojure.walk :as walk]
   [clojure.tools.logging :as log]
   [keycloak.cookies :refer [parse-cookies]]
   [keycloak.deployment :as keycloak]))

(set! *warn-on-reflection* true)

(defmulti token-from-headers* (fn [headers]
                                     (cond
                                       (contains? headers :authorization) :bearer
                                       (contains? headers :cookie) :cookie
                                       :else :default)))

(def TOKEN_HEADER_KEY :authorization)
(def TOKEN_NAME "Bearer")

(defmethod token-from-headers* :bearer [headers]
  (last (re-find (re-pattern (str "^" TOKEN_NAME " (.*)$")) (get headers TOKEN_HEADER_KEY))))

(def COOKIE_HEADER_KEY :cookie)
(def COOKIE_NAME "X_Authorization-Token")

(defmethod token-from-headers* :cookie [headers]
  (-> headers
      (get COOKIE_HEADER_KEY)
      keycloak.cookies/parse-cookies
      (get COOKIE_NAME)))

(defmethod token-from-headers* :default [headers]
  (throw (ex-info "Missing token from headers: \"authorization\" header with Bearer value or \"X-Authorization-Token\" cookie"
                  {:headers headers :type :missing-token})))

(defn verify-then-extract
  "Fist argument is a [_Keycloak Deployment_ object](https://github.com/keycloak/keycloak/blob/master/adapters/oidc/adapter-core/src/main/java/org/keycloak/adapters/KeycloakDeployment.java), second is a token. Return the extracted token"
  ^keycloak.deployment.ClojureAccessToken [^org.keycloak.adapters.KeycloakDeployment deployment token]
  (let [extracted-token (->> token
                             (keycloak/verify deployment)
                             (keycloak/extract))]
    (log/debug (str "extracted-token " extracted-token))
    extracted-token))

(defn verify-credential
  "Take a Yada web context object, extract the token from the headers, verify it and if correct return the AccessToken correctly extracted"
  ^keycloak.deployment.ClojureAccessToken [ctx ^org.keycloak.adapters.KeycloakDeployment deployment]
   (let [token (->> [:request :headers]
                    (get-in ctx)
                    walk/keywordize-keys
                    token-from-headers*)]
     (verify-then-extract deployment token)))

(defn verify-credential-in-headers
  "Verify credentials from raw headers and if correct return the AccessToken correctly extracted"
  ^keycloak.deployment.ClojureAccessToken [headers ^org.keycloak.adapters.KeycloakDeployment deployment & opts]
  (let [token (-> headers
                  clojure.walk/keywordize-keys
                  token-from-headers*)]
    (verify-then-extract deployment token)))

(defn buddy-verify-token-fn
  [^org.keycloak.adapters.KeycloakDeployment deployment]
  (fn ^keycloak.deployment.ClojureAccessToken [req token]
    (when (nil? token)
      (throw (Exception. "Token cannot be nil")))
    (verify-then-extract deployment token)))

;;map of realm-name to KeycloakDeployment object
(def deployments (atom {}))

(defn register-deployment [realm-name deployment]
  (swap! deployments assoc realm-name deployment)
  deployment)

(defn unregister-deployment [^org.keycloak.adapters.KeycloakDeployment deployment]
  (swap! deployments dissoc (.getRealm deployment)))

(defn register-deployments [deployments]
  (doseq [[realm-name deploy] deployments]
    (register-deployment realm-name deploy)))
