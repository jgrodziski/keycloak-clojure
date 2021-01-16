(ns keycloak.backend-test
  (:require
   [clojure.test :as t]
   [keycloak.backend :as backend]
   [keycloak.deployment :as deployment]
   [keycloak.admin :as admin]
   [keycloak.authz :as authz]
   [keycloak.authn :as authn]))

(def LOGIN "admin")
(def PWD "secretadmin")
(def AUTH_SERVER_URL "http://localhost:8090/auth")

(def kc-admin-client (deployment/keycloak-client (deployment/client-conf AUTH_SERVER_URL "master" "admin-cli" ) LOGIN PWD))

(def secret (admin/get-client-secret kc-admin-client "electre" "diffusion-backend"))
(def kc-authz (authz/authz-client (deployment/client-conf-input-stream AUTH_SERVER_URL "electre" "diffusion-backend" secret)))
(def at (authn/access-token kc-authz "jgrodziski" "jgrodziski"))

(def electre-deployment (deployment/deployment-for-realm kc-admin-client AUTH_SERVER_URL "diffusion-frontend" "electre"))
(def token (authn/authenticate AUTH_SERVER_URL "electre" "diffusion-frontend" "jgrodziski" "jgrodziski"))
(def clj-at (->> (:access_token token)
                 (deployment/verify electre-deployment)
                 deployment/extract))

(defn setup-keycloak []
  (let [deployments (deployment/deployment-for-realms kc-admin-client AUTH_SERVER_URL "diffusion-backend" ["electre"])]
    (backend/register-deployments deployments)
    deployments))

(t/deftest verify-credential-test
  (let [access-token (authn/authenticate AUTH_SERVER_URL "master" "admin-cli" LOGIN PWD)
        cookie (authn/auth-cookie access-token)
        header (authn/auth-header access-token)]
    (t/testing "Given an AccessToken embed it in a context then verify it"
      ()
      (println access-token)
      

      )))
