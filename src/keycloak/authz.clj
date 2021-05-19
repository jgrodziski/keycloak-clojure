(ns keycloak.authz
  (:require [keycloak.utils :refer [hint-typed-doto]])
  (:import [org.keycloak.authorization.client AuthzClient]
           [org.keycloak.representations.idm.authorization ResourceRepresentation ScopeRepresentation RolePolicyRepresentation]))

;(set! *warn-on-reflection* true)

(defn authz-client
  ^AuthzClient
  [^java.io.InputStream client-conf-input-stream]
  (AuthzClient/create ^java.io.InputStream client-conf-input-stream))

(defn resource [name type-urn scopes-urn]
  (let [])
  (hint-typed-doto "org.keycloak.representations.idm.authorization.ResourceRepresentation"
                   (ResourceRepresentation.)
                   (.setName name)
                   (.setType type-urn)
                   (.addScope ^"[Ljava.lang.String;" (into-array String scopes-urn))))

(defn scope
  "create a scope representation object (eg. \"urn:hello-world-authz:scopes:view\")"
  [scope-urn]
  (ScopeRepresentation. [scope-urn]))

(defn get-authorization-resource
  ^org.keycloak.admin.client.resource.AuthorizationResource
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id]
  (-> keycloak-client (.realms) (.realm realm-name) (.clients) (.get client-id) (.authorization)))

(defn resource-client
  ^org.keycloak.authorization.client.resource.ProtectedResource
  [^AuthzClient authz-client]
  (-> authz-client (.protection) (.resource)))

(defn find-resource-by-id
  ^org.keycloak.representations.idm.authorization.ResourceRepresentation
  [^AuthzClient authz-client id]
      (let [resource-client (resource-client authz-client)]
        (.findById resource-client id)))

(defn find-resource-by-name
  ^org.keycloak.representations.idm.authorization.ResourceRepresentation
  [^AuthzClient authz-client name]
      (let [resource-client (resource-client authz-client)]
           (.findByName resource-client name)))

(defn create-resource!
  [^AuthzClient authz-client name type-urn scopes-urn]
  (let [resource-client (resource-client authz-client)
        existing (find-resource-by-name authz-client name)]
    (when existing
      (.delete resource-client (.getId existing)))
    (let [resp (.create resource-client (resource name type-urn scopes-urn))
          id (.getId resp)]
      (find-resource-by-id authz-client id))))

(defn delete-resource!
  [^AuthzClient authz-client name]
      (let [resource-client (resource-client authz-client)
            existing (.findByName resource-client name)]
           (when existing
                 (.delete resource-client (.getId existing)))))

(defn delete-resource [^AuthzClient authz-client name]
      (let [resource-client (resource-client authz-client)]))

(defn create-role-policy [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id role-name resource-id scopes-id]
  (let [role-policy-representation (doto (RolePolicyRepresentation.)
                                     (.addRole role-name)
                                     (.addResource resource-id)
                                     (.addScope (into-array java.lang.String scopes-id)))
        policies-resource (-> (get-authorization-resource keycloak-client realm-name client-id) (.policies))]
    (-> policies-resource (.create role-policy-representation))
    (doseq [policy (.policies policies-resource)]
      (println policy))))

