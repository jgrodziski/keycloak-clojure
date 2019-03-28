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

(defn list-realms
      [keycloak-client]
      (info "list the realms")
      (-> keycloak-client (.realms) (.findAll)))

(defn role-representation "create a RoleRepresentation object" [name]
      (RoleRepresentation. name (str "Role created automatically by admin client") false))

(defn create-role!
      [keycloak-client realm-name role-name]
      (info "create role"role-name"in realm"realm-name)
      (-> keycloak-client (.realms) (.realm realm-name) (.roles) (.create (role-representation role-name))))

(defn group-representation "create a GroupRepresentation object" [group-name]
  (doto (GroupRepresentation.) (.setName group-name)))


(defn create-group!
      [keycloak-client realm-name group-name]
      (info "create group" group-name "in realm" realm-name)
      (-> keycloak-client (.realms) (.realm realm-name) (.groups) (.add (group-representation group-name))))

(defn list-groups
      [keycloak-client realm-name]
      (info "list the groups representation objects of realm" realm-name)
      (-> keycloak-client (.realms) (.realm realm-name) (.groups) (.groups)))

(defn get-group-id
      [keycloak-client realm-name group-name]
      (-> (filter #(= group-name (.getName %)) (list-groups keycloak-client realm-name)) (first) (.getId)))

(defn get-group
      [keycloak-client realm-name group-name]
      (-> keycloak-client (.realms) (.realm realm-name) (.groups) (.group (get-group-id keycloak-client realm-name group-name))))

(defn list-subgroups
      [keycloak-client realm-name group-name]
      (info "List all subgroups of group" group-name "in realm " realm-name)
       (-> (get-group keycloak-client realm-name group-name) (.toRepresentation) (.getSubGroups) ))

(defn create-subgroup!
      [keycloak-client realm-name group-name subgroup-name]
      (info "create subgroup" subgroup-name "in group" group-name "in realm" realm-name)
      (let [group (get-group keycloak-client realm-name group-name)]
            (-> group (.subGroup (group-representation subgroup-name)))
            (list-subgroups keycloak-client realm-name group-name)))


(defn delete-group!
      [keycloak-client realm-name group-name]
      (info "delete group" group-name "in realm" realm-name)
      (-> (get-group keycloak-client realm-name group-name) (.remove)))

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
            (.setUsername username)))

(defn create-user!
      [keycloak-client realm-name group-name username]
      (info "create user" username "in realm" realm-name)
      (let [rep (-> keycloak-client (.realms) (.realm realm-name) (.users) (.create (user-representation username )))]
           (println (.getStatus rep))
           rep))

(defn list-users
      [keycloak-client realm-name]
      (-> keycloak-client (.realms) (.realm realm-name) (.users) (.list)))

(defn get-user-id
      [keycloak-client realm-name username]
      (info "get user id by username" username)
      (-> (filter #(= username (.getUsername %)) (list-users keycloak-client realm-name)) (first) (.getId)))

(defn add-user-to-group-by-name!
      [keycloak-client realm-name group-name username]
      (info "add user" username "in group" group-name "of realm" realm-name)
      (let [group-id (get-group-id keycloak-client realm-name group-name)
            user-id (get-user-id keycloak-client realm-name username)]
           (-> keycloak-client (.realms) (.realm realm-name) (.users) (.get user-id) (.joinGroup group-id))))

(defn add-user-to-group-by-id!
      [keycloak-client realm-name group-id user-id]
      (-> keycloak-client (.realms) (.realm) (.users) (.get user-id) (.join group-id)))

(defn delete-user!
      [keycloak-client realm-name username]
      (-> keycloak-client (.realms) (.realm) (.users) (.delete (get-user-id keycloak-client realm-name username))))


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
