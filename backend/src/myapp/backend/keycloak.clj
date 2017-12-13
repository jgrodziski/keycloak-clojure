(ns myapp.backend.keycloak
  (:require [mount.core :refer [defstate]]
            [clojure.java.io :as io])
  (:import [org.keycloak.adapters KeycloakDeployment KeycloakDeploymentBuilder]
           [org.keycloak.representations AccessToken]
           [org.keycloak RSATokenVerifier]))

(defn load-keycloak-deployment
  "take the keycloak configuration json file location on the classpath and return a KeycloakDeployment object"
  ([]
   (load-keycloak-deployment "keycloak.json"))
  ([keycloak-json-file]
   (with-open [keycloak-json-is (io/input-stream (io/resource keycloak-json-file))]
     (KeycloakDeploymentBuilder/build keycloak-json-is))))

(defstate keycloak-deployment
  :start (load-keycloak-deployment))

(defn verify
  ([token]
   (verify keycloak-deployment token))
  ([deployment token]
   (let [kid "8xtQRD7EVIx4oKmmERIvOVSBOFSyfyziVEgy358rfKU" ;; TODO put that in config file
         public-key (.getPublicKey (.getPublicKeyLocator deployment) kid deployment)]
     (RSATokenVerifier/verifyToken token public-key (.getRealmInfoUrl deployment)))))

(defn extract
  "return a map with :user and :roles keys with values extracted from the Keycloak access token"
  [access-token]
  {:user (.getPreferredUsername access-token)
   :roles (set (map keyword (.getRoles (.getRealmAccess access-token))))})
