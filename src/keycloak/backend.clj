(ns keycloak.backend
  (:require
   [clojure.walk :as walk :refer [keywordize-keys]]
   [clojure.tools.logging :as log]
   [keycloak.cookies :refer [parse-cookies]]
   [keycloak.deployment :as keycloak]))

(set! *warn-on-reflection* true)

(defmulti credential-from-headers (fn [headers]
                                       (cond
                                         (contains? headers :authorization) :bearer
                                         (contains? headers :cookie) :cookie
                                         :else nil)))

(defmethod credential-from-headers :bearer [headers]
  (last (re-find #"^Bearer (.*)$" (:authorization headers))))

(defmethod credential-from-headers :cookie [headers]
  (let [cookies (parse-cookies (:cookie headers))]
    (get cookies "X-Authorization-Token")))

(defn bearer-header [ctx]
  (let [header (get-in ctx [:request :headers "authorization"])]
    (when header
      (last (re-find #"^Bearer (.*)$" header)))))

(defn cookie [ctx]
  (let [cookies (parse-cookies (:request ctx))]
    (get cookies "X-Authorization-Token")))

(defn verify-credential
  "Take a web context object, extract the credential from the headers, verify it and if correct return the AccessToken correctly extracted"
  ^keycloak.deployment.ClojureAccessToken [ctx ^org.keycloak.adapters.KeycloakDeployment deployment]
   (let [credential  (->> [:request :headers]
                          (get-in ctx)
                          walk/keywordize-keys
                          credential-from-headers)]
     (when (nil? credential)
       (log/info {:service ::yada.security.verify}
                 (str "Credential must be present in \"authorization\" header or \"X-Authorization-Token\" header cookie"))
       (throw (Exception. "Credential must be present in \"authorization\" header or \"X-Authorization-Token\" header cookie")))
     (let [extracted-token (->> credential
                                (keycloak/verify deployment)
                                (keycloak/extract))]
       (log/debug (str "extracted-token " extracted-token))
       extracted-token)))

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
