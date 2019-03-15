(ns keycloak.authz
  (:import [org.keycloak.authorization.client AuthzClient]
           [org.keycloak.representations.idm.authorization ResourceRepresentation ScopeRepresentation]))

(defn authz-client [^java.io.InputStream client-conf-input-stream]
  (AuthzClient/create ^java.io.InputStream client-conf-input-stream))

(defn resource [name type-urn scopes-urn]
  (doto (ResourceRepresentation.) (.setName name) (.setType type-urn) (.addScope (into-array String scopes-urn))))

(defn scope
  "create a scope representation object (eg. \"urn:hello-world-authz:scopes:view\")"
  [scope-urn]
  (ScopeRepresentation. [scope-urn]))

(defn resource-client [authz-client]
  (-> authz-client (.protection) (.resource)))
 
(defn create-resource [authz-client name type-urn scopes-urn]
  (let [resource-client (resource-client authz-client)
        existing (.findByName resource-client name)]
    (when existing
      (.delete resource-client (.getId existing)))
    (let [resp (.create resource-client (resource name type-urn scopes-urn))
          id (.getId resp)]
      (.findById resource-client id))))


