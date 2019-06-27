(ns keycloak.deployment
  (:require [clojure.tools.logging :as log :refer [info error warn]]
            [clojure.java.data :refer [from-java]]
            [cheshire.core :as json :refer [encode]]
            [clojure.java.io :as io :refer [input-stream]]
            [keycloak.admin :refer [get-client-secret]])
  (:import [org.keycloak.adapters KeycloakDeployment KeycloakDeploymentBuilder]
           [org.keycloak.admin.client KeycloakBuilder]
           [org.keycloak RSATokenVerifier OAuth2Constants]
           [org.keycloak.representations AccessToken]
           [org.jboss.resteasy.client.jaxrs ResteasyClientBuilder]))

(defn deployment
  "take a keycloak configuration as EDN and return a KeycloakDeployment object"
  [client-conf]
  (try
    (let [keycloak-json-is (io/input-stream (.getBytes (json/encode client-conf)))
          truncated-secret (when-let [secret (get-in client-conf [:credentials :secret])] (subs secret 0 8))]
      (info (format "Build keycloak deployment for realm %s on server %s secret starting with %s" (:realm client-conf) (:auth-server-url client-conf) truncated-secret))
      (KeycloakDeploymentBuilder/build keycloak-json-is))
    (catch java.lang.Throwable t
      (throw (ex-info "Failed to build the keycloak app client" client-conf t)))))

(defn client-conf
  ([{:keys [realm auth-server-url client-id client-secret] :as conf}]
   (client-conf auth-server-url realm client-id client-secret))
  ([auth-server-url realm-name client-id]
   (client-conf auth-server-url realm-name client-id nil))
  ([auth-server-url realm-name client-id client-secret]
   (->> {:realm realm-name
         :auth-server-url auth-server-url
         :credentials {:secret client-secret}
         :ssl-required "external"
         :verify-token-audience true
         :use-resource-role-mappings true
         :resource client-id
         :confidential-port 0
         :policy-enforcer {}}
        (#(if client-secret (assoc % :credentials {:secret client-secret}) %)))))

(defn client-conf-input-stream
  ([auth-server-url realm-name client-id]
   (client-conf-input-stream auth-server-url realm-name client-id nil))
  ([auth-server-url realm-name client-id client-secret]
   (io/input-stream (.getBytes (json/encode (client-conf auth-server-url realm-name client-id client-secret))))))

(defn- base-keycloak-builder [conf]
  (-> (KeycloakBuilder/builder)
      (.realm (:realm conf))
      (.serverUrl (:auth-server-url conf))
      (.clientId (:resource conf))
      (.resteasyClient (-> (ResteasyClientBuilder.)
                           (.connectionPoolSize 2)
                           (.build)))))

(defn keycloak-client
  ([conf secret]
   (info "Build keycloak client with config for realm" (:realm conf) "on server" (:auth-server-url conf) "with secret starting with" (when-let [secret  (get-in conf [:credentials :secret])] (subs secret 0 8)))
   (-> (base-keycloak-builder conf)
       (.clientSecret secret)
       (.grantType OAuth2Constants/CLIENT_CREDENTIALS)
       (.build)))
  ([conf username password]
   (info "Build keycloak client with config for realm" (:realm conf) "on server" (:auth-server-url conf) "with username" username)
   (-> (base-keycloak-builder conf)
       (.username username)
       (.password password)
       (.grantType OAuth2Constants/PASSWORD)
       (.build))))

(defn deployment-for-realms
  "Given an keycloak client with admin privilege, an array of realm name, retrieve the secrets and build dynamically a map with realm-name as key and the keycloak deployment as value, useful for large number of realms and multi-tenant applications or tests, otherwise define them statically"
  [keycloak-client auth-server-url client-id realms-name]
  (into {} (map (fn [realm-name]
                  (info (format "Get client secret on server %s for realm %s and client \"%s\"" auth-server-url realm-name client-id))
                  (try
                    (let [client-secret (get-client-secret keycloak-client realm-name client-id)]
                      [realm-name (deployment (client-conf auth-server-url realm-name client-id client-secret))])
                    (catch javax.ws.rs.NotFoundException nfe
                      (error (str "The client '"client-id "' was not found in realm '" realm-name "', maybe you should create it at the repl with: (fill! \""realm-name"\")"))
                      nil)))
                realms-name)))

(defn verify
  ([^KeycloakDeployment deployment ^org.keycloak.representations.AccessToken token]
   (let [verifier (-> token RSATokenVerifier/create (.realmUrl (.getRealmInfoUrl deployment)))
         kid (-> verifier (.getHeader) (.getKeyId))
         public-key (.getPublicKey (.getPublicKeyLocator deployment) kid deployment)]
     (-> verifier (.publicKey public-key) (.verify) (.getToken))))
  ([deployments realm-name token]
   (verify (get deployments realm-name) token)))

(defrecord ClojureAccessToken
    [username roles nonce auth-time session-state access-token-hash code-hash name given-name family-name middle-name
     nick-name preferred-username profile picture website email email-verified gender birthdate zoneinfo
     locale phone-number phone-number-verified address updated-at claims-locales acr state-hash other-claims]
  Object
  (toString [access-token] (pr-str access-token)))

(defn extract
  "return a map with :user and :roles keys with values extracted from the Keycloak access token along with all the props of the AccessToken bean"
  [^org.keycloak.representations.AccessToken access-token]
  (map->ClojureAccessToken {:username              (.getPreferredUsername access-token)
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
                            :state-hash            (.getStateHash access-token)
                            :other-claims          (into {} (map (fn [[k v]] [(keyword k) v]) (.getOtherClaims access-token)))}))


(defn access-token [deployment keycloak-client username password]
  (let [access-token-string (-> keycloak-client (.tokenManager) (.getAccessToken) (.getToken))
        access-token (->> access-token-string (verify deployment) (extract))]
    (assoc access-token :token access-token-string)))
