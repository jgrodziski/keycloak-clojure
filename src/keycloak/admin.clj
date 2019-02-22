(ns keycloak.admin
  (:require [clojure.tools.logging :as log :refer [info]]
            [clojure.java.data :refer [from-java]]
            [cheshire.core :as json :refer [encode]]
            [clojure.java.io :as io])
  (:import [org.keycloak.representations.idm CredentialRepresentation RealmRepresentation ClientRepresentation RoleRepresentation]))

(defn realm [realm-name login-theme]
  (doto (RealmRepresentation.) (.setEnabled true) (.setRealm realm-name) (.setLoginTheme login-theme)))

(defn get-realm
  [keycloak-client realm-name]
  (-> keycloak-client (.realms) (.realm realm-name)))

(defn create-realm!
  [keycloak-client realm-name login-theme]
  (info "create realm" realm-name)
  (-> keycloak-client (.realms) (.create (realm realm-name login-theme))))

(defn delete-realm!
  [keycloak-client realm-name]
  (info "delete realm" realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.remove)))

(defn role "create a RoleRepresentation object" [name]
  (RoleRepresentation. name (str "Role created automatically by admin client") false))

(defn create-role!
  [keycloak-client realm-name role-name]
  (info "create role"role-name"in realm"realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.roles) (.create (role role-name))))

(defn client [client-name public?]
  (doto (ClientRepresentation.)
    (.setClientId client-name)
    (.setPublicClient public?)
    (.setStandardFlowEnabled true)
    (.setDirectAccessGrantsEnabled true)
    (.setServiceAccountsEnabled (not public?))
    (.setAuthorizationServicesEnabled (not public?))
    (.setRedirectUris ["http://localhost:3449/*"])
    (.setWebOrigins ["http://localhost:3449"])
    (.setName client-name)))

(defn create-client!
  [keycloak-client realm-name client-id public?]
  (info "create client" client-id "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.clients) (.create (client client-id public?))))

(defn get-client
  [keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first))

(defn get-client-secret
  [keycloak-client realm-name client-id]
  (let [id (-> (get-client keycloak-client realm-name client-id) (.getId))]
    (-> keycloak-client (.realms) (.realm realm-name) (.clients) (.get id) (.getSecret) (.getValue))))
