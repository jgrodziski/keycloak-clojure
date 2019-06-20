(ns keycloak.deployment-test
  (:require [keycloak.deployment :as deployment :refer [deployment-for-realms]]
            [clojure.test :as t]))


(comment 
  (defn setup-keycloak []
    (let [{:keys [admin-realm client-admin-cli auth-server-url admin-username admin-password client-account-backend secret-account-backend]} (conf/keycloak config)
          kc-admin-client (keycloak-client (client-conf admin-realm client-admin-cli auth-server-url secret-account-backend) admin-username admin-password)
          deployments (deployment-for-realms kc-admin-client auth-server-url client-account-backend ["electre"])]
      (security/register-deployments deployments))))

