(ns keycloak.deployment
  (:require [clojure.tools.logging :as log :refer [info error warn]]
            [clojure.java.data :refer [from-java]]
            [cheshire.core :as json :refer [encode]]
            [clojure.java.io :as io :refer [input-stream]]
            [keycloak.admin :refer [get-client-secret]])
  (:import [org.keycloak.adapters KeycloakDeployment KeycloakDeploymentBuilder]
           [org.keycloak.representations AccessToken]
           [org.keycloak RSATokenVerifier OAuth2Constants]))

(defn build-deployment
  "take the keycloak configuration from the :keycloak-app-xxx keys in config.edn found on the classpath or through env vars and return a KeycloakDeployment object"
  [conf]
  (info "Build keycloak deployment for realm" (:realm conf) "on server" (:auth-server-url conf) "secret starting with" (subs (get-in conf [:credentials :secret]) 0 8))
  (try
    (let [keycloak-json-is (io/input-stream (.getBytes (json/encode conf)))]
      (KeycloakDeploymentBuilder/build keycloak-json-is))
    (catch java.lang.Throwable t
      (error "failed to build the keycloak app client" t))))

(defn keycloak-backend-config [keycloak-app-server-url client-name]
    {:realm nil
     :auth-server-url keycloak-app-server-url
     :credentials {:secret nil}
     :ssl-required "external"
     :resource client-name
     :confidential-port 0
                                        ;:use-resource-role-mappings true
     :policy-enforcer {}})

(defn deployment-for-realms
  "take an array of realm name and return a map with realm-name as key and the keycloak deployment as value build dynamically with the admin client"
  [keycloak-client keycloak-app-server-url client-name realms-name]
  (into {} (map (fn [realm-name]
                  (info "Get client secret for realm" realm-name "and client \"" client-name "\"")
                  (try
                    (let [client-secret (get-client-secret keycloak-client realm-name client-name)]
                      [realm-name (build-deployment (-> (keycloak-backend-config keycloak-app-server-url client-name)
                                                        (assoc :realm realm-name)
                                                        (assoc-in [:credentials :secret] client-secret)))])
                    (catch javax.ws.rs.NotFoundException nfe
                      (error (str "The client '"client-name"' was not found in realm '" realm-name "', maybe you should create it at the repl with: (fill! \""realm-name"\")"))
                      nil)))
                realms-name)))

(defn verify [deployments realm-name token]
  (let [deployment (get deployments realm-name)
        verifier (-> token RSATokenVerifier/create (.realmUrl (.getRealmInfoUrl deployment)))
        kid (-> verifier (.getHeader) (.getKeyId))
        public-key (.getPublicKey (.getPublicKeyLocator deployment) kid deployment)]
    (-> verifier (.publicKey public-key) (.verify) (.getToken))))

(defn access-token [deployment keycloak-client username password]
  (let [access-token-string (-> keycloak-client (.tokenManager) (.getAccessToken) (.getToken))
        access-token (->> access-token-string (verify deployment) (extract))]
    (assoc access-token :token access-token-string)))

(defn extract
  "return a map with :user and :roles keys with values extracted from the Keycloak access token along with all the props of the AccessToken bean"
  [access-token]
  {:username              (.getPreferredUsername access-token)
   :roles                 (set (map keyword (.getRoles (.getRealmAccess access-token))))
   :nonce                 (.getNonce access-token)
   :auth-time             (.getAuthTime access-token)
   :session-state         (.getSessionState access-token)
   :access-token-hash     (.getAccessTokenHash access-token)
   :code-hash             (.getCodeHash access-token)
   :name                  (.getName access-token)
   :given-name            (.getGivenName access-token)
   :family-name           (.getFamilyName access-token)
   :middle-name           (.getMiddleName access-token)
   :nick-name             (.getNickName access-token)
   :preferred-username    (.getPreferredUsername access-token)
   :profile               (.getProfile access-token)
   :picture               (.getPicture access-token)
   :website               (.getWebsite access-token)
   :email                 (.getEmail access-token)
   :email-verified        (.getEmailVerified access-token)
   :gender                (.getGender access-token)
   :birthdate             (.getBirthdate access-token)
   :zoneinfo              (.getZoneinfo access-token)
   :locale                (.getLocale access-token)
   :phone-number          (.getPhoneNumber access-token)
   :phone-number-verified (.getPhoneNumberVerified access-token)
   :address               (.getAddress access-token)
   :updated-at            (.getUpdatedAt access-token)
   :claims-locales        (.getClaimsLocales access-token)
   :acr                   (.getAcr access-token)
   :state-hash            (.getStateHash access-token)})

