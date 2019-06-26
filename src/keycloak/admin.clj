(ns keycloak.admin
  (:require
   [clojure.java.io :as io]
   [clojure.java.data :refer [from-java]]
   [clojure.string :as string :refer [last-index-of]]
   [clojure.tools.logging :as log :refer [info]]
   [cheshire.core :as json :refer [encode]])
  (:import [org.keycloak.representations.idm CredentialRepresentation RealmRepresentation ClientRepresentation RoleRepresentation GroupRepresentation UserRepresentation]
           [javax.ws.rs.core Response]))

(defn extract-id [^Response resp]
  (let [uri (-> resp .getLocation .toString)]
    (subs uri (+ (last-index-of uri "/") 1))))

(defn realm-representation
  ([realm-name]
   (doto (RealmRepresentation.) (.setEnabled true) (.setRealm realm-name) (.setId realm-name)))
  ([realm-name theme]
   (doto (realm-representation realm-name)
     (.setAccountTheme theme)
     (.setEmailTheme theme)
     (.setLoginTheme theme))))

(defn get-realm
  [keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.toRepresentation)))

(defn create-realm!
  ([keycloak-client realm-name]
   (info "create realm" realm-name)
   (-> keycloak-client (.realms) (.create (realm-representation realm-name)))
   (info "realm" realm-name "created")
   (get-realm keycloak-client realm-name))
  ([keycloak-client realm-name login-theme]
   (info "create realm" realm-name)
   (-> keycloak-client (.realms) (.create (realm-representation realm-name login-theme)))
   (info "realm" realm-name "created")
   (get-realm keycloak-client realm-name)))

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
  (info "create role" role-name "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.create (role-representation role-name))))

(defn group-representation "create a GroupRepresentation object" [group-name]
  (doto (GroupRepresentation.) (.setName group-name)))

(defn list-groups
  ([keycloak-client realm-name]
   (info "list the groups representation objects of realm" realm-name)
   (-> keycloak-client (.realm realm-name) (.groups) (.groups)))
  ([keycloak-client realm-name s]
   (info "list the groups representation objects of realm " realm-name "with name" s)
   (-> keycloak-client (.realm realm-name) (.groups) (.groups s (int 0) (int 1000)))))

(defn get-group-id
  [keycloak-client realm-name group-name]
  (some #(if (= group-name (.getName %)) (.getId %)) (list-groups keycloak-client realm-name)))

(defn get-group
  [keycloak-client realm-name group-id]
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation)))

(defn create-group!
  [keycloak-client realm-name group-name]
  (info "create group" group-name "in realm" realm-name)
  (let [group-id (-> keycloak-client (.realm realm-name) (.groups) (.add (group-representation group-name)) extract-id)]
    (info "group" group-name "created in realm" realm-name " with group id" group-id)
    (get-group keycloak-client realm-name group-id)))

(defn delete-group!
  [keycloak-client realm-name group-id]
  (info "delete group [id=" group-id "] in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.remove)))

(defn get-subgroup
  [keycloak-client realm-name group-id subgroup-id]
  (some #(if (= subgroup-id (.getId %)) %)
        (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation) (.getSubGroups))))

(defn get-group-members
  [keycloak-client realm-name group-id]
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.members)))

(defn list-subgroups
  [keycloak-client realm-name group-id]
  (info "List all subgroups of group" group-id "in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation) (.getSubGroups)))

(defn get-subgroup-id
  [keycloak-client realm-name group-id subgroup-name]
  (some #(if (= subgroup-name (.getName %)) (.getId %)) (list-subgroups keycloak-client realm-name group-id)))

(defn create-subgroup!
  [keycloak-client realm-name group-id subgroup-name]
  (info "create subgroup" subgroup-name "in group" group-id "in realm" realm-name)
  (let [subgroup-id (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.subGroup (group-representation subgroup-name)) extract-id)]
    (info "subgroup" subgroup-name "created in group" group-id "in realm" realm-name "with subgroup-id" subgroup-id)
    (get-group keycloak-client realm-name subgroup-id)))

(defn credential-representation [type value]
  (doto (CredentialRepresentation.)
    (.setType type)
    (.setValue value)))

(defn user-representation
  ([username]
   (doto (UserRepresentation.)
     (.setUsername username)
     (.setEnabled true)))
  ([username password]
   (doto (UserRepresentation.)
     (.setUsername username)
     (.setEnabled true)
     (.setCredentials [(credential-representation CredentialRepresentation/PASSWORD password)]))))

(defn list-users
  [keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.users) (.list)))

(defn ^:deprecated get-user-id
  [keycloak-client realm-name username]
  (info "get user id by username" username)
  (-> (some #(if (= username (.getUsername %)) (.getId %)) (list-users keycloak-client realm-name))))

(defn find-users
  [keycloak-client realm-name s]
  (info "find user by username, email etc." s)
  (-> keycloak-client (.realm realm-name) (.users) (.search s (int 0) (int 1000))))

(defn get-user-by-username
  [keycloak-client realm-name username]
  (first (find-users keycloak-client realm-name username)))

(defn get-user
  [keycloak-client realm-name user-id]
  (info "get user [id=" user-id "] in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.toRepresentation)))

(defn create-user!
  [keycloak-client realm-name username password]
  (info "create user" username "in realm" realm-name)
  (let [user-id (-> keycloak-client (.realm realm-name) (.users) (.create (if password (user-representation username password) (user-representation username))) extract-id)]
    (info "user with username " username "created in realm" realm-name " with id" user-id)
    (get-user keycloak-client realm-name user-id)))

(defn add-user-to-group-by-username!
  [keycloak-client realm-name group-id username]
  (info "add user" username "in group" group-id "of realm" realm-name)
  (let [users-resources (-> keycloak-client (.realms) (.realm realm-name) (.users))
        user-id (-> users-resources (.search username) (first) (.getId))]
    (-> users-resources (.get user-id) (.joinGroup group-id))))

(defn add-user-to-group!
  "Make the user join group, return the group"
  [keycloak-client realm-name group-id user-id]
  (info "user" user-id "will join group" group-id "of realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.joinGroup group-id))
  (info "user" user-id "joined group" group-id "of realm" realm-name)
  (get-group keycloak-client realm-name group-id))

(defn remove-user-from-group! [keycloak-client realm-name group-id user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.leaveGroup group-id)))

(defn delete-user!
  [keycloak-client realm-name user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.delete user-id)))

(defn get-user-groups
  [keycloak-client realm-name user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.groups)))

(defn client
  "create a ClientRepresentation with client-name the public/private flag"
  ([client-name public?]
   (client client-name public? ["http://localhost:3449/*"] ["http://localhost:3449"]))
  ([client-name public? redirect-uris web-origins]
   (doto (ClientRepresentation.)
     (.setClientId client-name)
     (.setPublicClient public?)
     (.setStandardFlowEnabled true)
     (.setDirectAccessGrantsEnabled true)
     (.setServiceAccountsEnabled (not public?))
     (.setAuthorizationServicesEnabled (not public?))
     (.setRedirectUris redirect-uris)
     (.setWebOrigins web-origins)
     (.setName client-name))))

(defn get-client
  [keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first ))

(defn create-client!
  [keycloak-client realm-name client-id public?]
  (info "create client" client-id "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.clients) (.create (client client-id public?)))
  (info "client" client-id " created in realm" realm-name)
  (get-client keycloak-client realm-name client-id))

(defn get-client-secret
  [keycloak-client realm-name client-id]
  (let [id (-> (get-client keycloak-client realm-name client-id) (.getId))]
    (-> keycloak-client (.realm realm-name) (.clients) (.get id) (.getSecret) (.getValue))))
