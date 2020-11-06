(ns keycloak.backend
  (:require
   [clojure.string :as string :refer [last-index-of]]
   [clojure.tools.logging :as log]
   [yada.cookies :refer [parse-cookies]]
   [keycloak.deployment :as keycloak]))

(set! *warn-on-reflection* true)


(defn authorization-bearer-cred [ctx]
  (let [header (get-in ctx [:request :headers "authorization"])]
    (when header
      (last (re-find #"^Bearer (.*)$" header)))))

(defn authorization-token-cookie [ctx]
  (let [cookies (parse-cookies (:request ctx))]
    (get cookies "X-Authorization-Token")))


(defn verify-credential [ctx deployment]
  (let [header-cred (authorization-bearer-cred ctx)
        cookie-cred (authorization-token-cookie ctx)
        cred-missing? (nil? (or header-cred cookie-cred))]
                                        ;{:username "manager" :roles #{:employee :manager}}
    (when cred-missing?
      (log/info {:service ::yada.security.verify :header-cred header-cred :cookie-cred cookie-cred}
                (str "Credential must be present in \"authorization\" header or \"X-Authorization-Token\" cookie"))
      (throw (Exception. "Credential must be present in \"authorization\" header or \"X-Authorization-Token\" cookie")))
    (let [extracted-token (->> (or header-cred cookie-cred)
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
