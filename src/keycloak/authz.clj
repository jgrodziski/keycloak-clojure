(ns keycloak.authz
  (:import [org.keycloak.authorization.client AuthzClient]
           [org.keycloak.representations.idm.authorization ResourceRepresentation ScopeRepresentation RolePolicyRepresentation]))

(defn authz-client [^java.io.InputStream client-conf-input-stream]
  (AuthzClient/create ^java.io.InputStream client-conf-input-stream))

(defn resource [name type-urn scopes-urn]
  (doto (ResourceRepresentation.) (.setName name) (.setType type-urn) (.addScope (into-array String scopes-urn))))

(defn scope
  "create a scope representation object (eg. \"urn:hello-world-authz:scopes:view\")"
  [scope-urn]
  (ScopeRepresentation. [scope-urn]))

(defn get-authorization-resource
  [keycloak-client]
  (-> keycloak-client (.realms) (.realm realm-name) (.clients) (.get client-id) (.authorization)))

(defn resource-client [authz-client]
  (-> authz-client (.protection) (.resource)))

(defn find-resource-by-id
      [authz-client id]
      (let [resource-client (resource-client authz-client)])
      (.findById resource-client id))

(defn find-resource-by-name
      [authz-client name]
      (let [resource-client (resource-client authz-client)]
           (.findByName resource-client name)))

(defn create-resource!
  [authz-client name type-urn scopes-urn]
  (let [resource-client (resource-client authz-client)
        existing (find-resource-by-name authz-client name)]
    (when existing
      (.delete resource-client (.getId existing)))
    (let [resp (.create resource-client (resource name type-urn scopes-urn))
          id (.getId resp)]
      (find-resource-by-id authz-client id))))

(defn delete-resource!
  [authz-client name]
      (let [resource-client (resource-client authz-client)
            existing (.findByName resource-client name)]
           (when existing
                 (.delete resource-client (.getId existing)))))

(defn delete-resource [authz-client name]
      (let [resource-client (resource-client authz-client)]))

(defn create-role-policy [keycloak-client realm-name client-id role-name resource-id scopes-id]
  (let [role-policy-representation (doto (RolePolicyRepresentation.)
                                     (.addRole role-name)
                                     (.addResource resource-id)
                                     (.addScope (into-array java.lang.String scopes-id)))
        policies-resource (-> (get-authorization-resource keycloak-client) (.policies))]
    (-> policies-resource (.create role-policy-representation))
    (doseq [policy (.policies policies-resource)]
      (println policy))))

(defn create-scope)
