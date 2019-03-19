(ns keycloak.admin
  (:require [clojure.tools.logging :as log :refer [info]]
            [clojure.java.data :refer [from-java]]
            [cheshire.core :as json :refer [encode]]
            [clojure.java.io :as io])
  (:import [org.keycloak.representations.idm CredentialRepresentation RealmRepresentation ClientRepresentation RoleRepresentation GroupRepresentation UserRepresentation]))

(defn realm-representation
  ([realm-name]
   (doto (RealmRepresentation.) (.setEnabled true) (.setRealm realm-name)))
  ([realm-name login-theme]
   (doto (realm-representation realm-name)
     (.setLoginTheme login-theme))))

(defn get-realm
  [keycloak-client realm-name]
  (-> keycloak-client (.realms) (.realm realm-name)))

(defn create-realm!
  ([keycloak-client realm-name]
   (info "create realm" realm-name)
   (-> keycloak-client (.realms) (.create (realm-representation realm-name))))
  ([keycloak-client realm-name login-theme]
   (info "create realm" realm-name)
   (-> keycloak-client (.realms) (.create (realm-representation realm-name login-theme)))))

(defn delete-realm!
  [keycloak-client realm-name]
  (info "delete realm" realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.remove)))

(defn list-realms!
  [keycloak-client]
  (info "list realms")
  (map #(.getRealm %) (-> keycloak-client (.realms) (.findAll))))

(defn role-representation "create a RoleRepresentation object" [name]
  (RoleRepresentation. name (str "Role created automatically by admin client") false))

(defn create-role!
  [keycloak-client realm-name role-name]
  (info "create role"role-name"in realm"realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.roles) (.create (role-representation role-name))))

(defn group-representation "create a GroupRepresentation object" [group-name]
  (doto (GroupRepresentation.)
    (.setName group-name)
    (.setId group-name)))

(defn create-group!
  [keycloak-client realm-name group-name]
  (info "create group" group-name "in realm" realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.groups) (.add (group-representation group-name))))

(defn get-group
  [keycloak-client realm-name group-name]
  (-> keycloak-client (.realms) (.realm realm-name) (.groups) (.group group-name)))

(defn list-groups!
  [keycloak-client realm-name]
  (info "list groups of realm " realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.groups) (.groups)))

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

(defn user-representation
  [username]
  (doto (UserRepresentation.)
    (.setId username)))

(defn create-user!
  [keycloak-client realm-name group-name username]
  (info "create user" username "in group" group-name "of realm" realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.groups) (.group group-name) (.members) (.add (user-representation username) )))

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
