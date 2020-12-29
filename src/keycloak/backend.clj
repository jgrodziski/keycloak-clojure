(ns keycloak.backend
  (:require
    [clojure.string :as string :refer [last-index-of]]
    [clojure.tools.logging :as log]
    [keycloak.cookies :refer [parse-cookies]]
    [keycloak.deployment :as keycloak]))

(set! *warn-on-reflection* true)

(defmulti authorization-from-headers (fn [headers]
                                       (cond
                                         (contains? headers :authorization) :bearer
                                         (contains? headers :cookie) :cookie
                                         :else nil)))

(defmethod authorization-from-headers :bearer [headers]
  (last (re-find #"^Bearer (.*)$" (:authorization headers))))
(defmethod authorization-from-headers :cookie [headers]
  (let [cookies (parse-cookies (:cookie headers))]
    (get cookies "X-Authorization-Token")))
(defmethod authorization-from-headers :default [_] nil)

(defn verify-credential [headers deployment]
  (let [credential (authorization-from-headers (clojure.walk/keywordize-keys headers))]
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
