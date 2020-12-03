(ns keycloak.admin
  (:require
   [clojure.java.io :as io]
   [clojure.java.data :refer [from-java]]
   [clojure.string :as string :refer [last-index-of]]
   [clojure.tools.logging :as log :refer [info]]
   [cheshire.core :as json :refer [encode]]
   [keycloak.user :as user]
   [keycloak.utils :as utils :refer [setters set-attributes]])
  (:import [org.keycloak.representations.idm CredentialRepresentation RealmRepresentation ClientRepresentation RoleRepresentation GroupRepresentation UserRepresentation ProtocolMapperRepresentation]
           [javax.ws.rs.core Response]))

(set! *warn-on-reflection* true)

(defn first-letter-capitalize [s]
  (str (string/upper-case (first s)) (subs s 1)))

(defn set-all! [obj m]
  (doseq [[k v] m]
    (let [method-name (str "set" (first-letter-capitalize (if (keyword? k) (name k) k)))]
      (clojure.lang.Reflector/invokeInstanceMethod obj method-name (into-array Object [v]))))
  obj)

(defn extract-id [^Response resp]
  (when resp
    (when-let [loc (.getLocation resp)]
      (subs (str loc) (+ (last-index-of (str loc) "/") 1)))))

(defn ks->str
  "convert all keys and values of the map to string"
  [m]
  (into {} (map (fn [[k v]]
                  [(name k) (str v)])) m))

(defn realm-representation
  (^org.keycloak.representations.idm.RealmRepresentation [realm-name]
   (doto (RealmRepresentation.) (.setEnabled true) (.setRealm realm-name) (.setId realm-name)))
  (^org.keycloak.representations.idm.RealmRepresentation [realm-name themes login tokens smtp]
   (let [^org.keycloak.representations.idm.RealmRepresentation realm-rep (realm-representation realm-name)]
     (cond-> realm-rep
             themes (set-all! themes)
             login  (set-all! login)
             tokens (set-all! tokens))
     (when smtp
       (.setSmtpServer realm-rep (ks->str smtp)))
     realm-rep)))

(defn get-realm
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.toRepresentation)))

(defn create-realm!
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
   (info "create realm" realm-name)
   (-> keycloak-client (.realms) (.create (realm-representation realm-name)))
   (info "realm" realm-name "created")
   (get-realm keycloak-client realm-name))
  ([^org.keycloak.admin.client.Keycloak keycloak-client realm-name themes login tokens smtp]
   (info "create realm" realm-name)
   (let [realm-rep (realm-representation realm-name themes login tokens smtp)]
     (-> keycloak-client (.realms) (.create realm-rep)))
   (info "realm" realm-name "created")
   (get-realm keycloak-client realm-name)))

(defn update-realm! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name themes login tokens smtp]
  (info "update realm" realm-name)
  (let [realm-rep (realm-representation realm-name themes login tokens smtp)]
    (-> keycloak-client (.realms) (.realm realm-name) (.update realm-rep)))
  (info "realm" realm-name "updated")
  (get-realm keycloak-client realm-name))

(defn delete-realm!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
  (info "delete realm" realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.remove)))

(defn list-realms
  [^org.keycloak.admin.client.Keycloak keycloak-client]
  (info "list the realms")
  (-> keycloak-client (.realms) (.findAll)))

(defn role-representation "create a RoleRepresentation object" [name]
  (RoleRepresentation. name (str "Role created automatically by admin client") false))

(defn get-role [^org.keycloak.admin.client.Keycloak keycloak-client realm-name role-name]
  (info "get role" role-name "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.get role-name)))

(defn list-roles [^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
  (info "list roles in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.list)))

(defn create-role!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name role-name]
  (info "create role" role-name "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.create (role-representation role-name))))

(defn group-representation "create a GroupRepresentation object" [group-name]
  (doto (GroupRepresentation.)
    (.setName group-name)))

(defn list-groups
  (^java.util.List [^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
   (info "list the groups representation objects of realm" realm-name)
   (-> keycloak-client (.realm realm-name) (.groups) (.groups)))
  (^java.util.List [^org.keycloak.admin.client.Keycloak keycloak-client realm-name s]
   (info "list the groups representation objects of realm " realm-name "with name" s)
   (-> keycloak-client (.realm realm-name) (.groups) (.groups s (int 0) (int 1000)))))

(defn get-group-id
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-name]
  (some (fn group-name-match? [^org.keycloak.representations.idm.GroupRepresentation group]
          (when (= group-name (.getName group))
            (.getId group))) (list-groups keycloak-client realm-name)))

(defn get-group
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id]
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation)))

(defn create-group!
  ^org.keycloak.representations.idm.GroupRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-name]
  (info "create group" group-name "in realm" realm-name)
  (let [resp (-> keycloak-client (.realm realm-name) (.groups) (.add (group-representation group-name)))
        group-id (extract-id resp)]
    (when resp (.close resp))
    (if group-id
      (do
        (info "group" group-name "created in realm" realm-name " with group id" group-id)
        (get-group keycloak-client realm-name group-id))
      (do 
        (info "group" group-name "already exist in realm" realm-name " with group id" group-id)
        (first (list-groups keycloak-client realm-name group-name))))))

(defn delete-group!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id]
  (info "delete group [id=" group-id "] in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.remove)))

(defn get-subgroup
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id subgroup-id]
  (some (fn [^org.keycloak.representations.idm.GroupRepresentation group]
          (when (= subgroup-id (.getId group)) group))
        (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation) (.getSubGroups))))

(defn get-group-members
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id]
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.members)))

(defn list-subgroups
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id]
  (info "List all subgroups of group" group-id "in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation) (.getSubGroups)))

(defn get-subgroup-id
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id subgroup-name]
  (some (fn [^org.keycloak.representations.idm.GroupRepresentation group]
          (when (= subgroup-name (.getName group))
            (.getId group))) (list-subgroups keycloak-client realm-name group-id)))

(defn create-subgroup!
  (^org.keycloak.representations.idm.GroupRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id subgroup-name]
   (create-subgroup! keycloak-client realm-name group-id subgroup-name nil))
  (^org.keycloak.representations.idm.GroupRepresentation[^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id subgroup-name attributes]
   (info "create subgroup" subgroup-name "in group" group-id "in realm" realm-name)
   (let [group-rep (set-attributes ^org.keycloak.representations.idm.GroupRepresentation (group-representation subgroup-name) attributes)
         resp (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.subGroup group-rep))
         subgroup-id (extract-id resp)]
     (when resp (.close resp))
     (if subgroup-id
       (do
         (info "subgroup" subgroup-name "created in group" group-id "in realm" realm-name "with subgroup-id" subgroup-id)
         (get-group keycloak-client realm-name subgroup-id))
       (do
         (info "subgroup" subgroup-name "already exist in group" group-id "in realm" realm-name)
         (first (list-groups keycloak-client realm-name subgroup-name)))))))

(defn find-users
  ^java.util.List [^org.keycloak.admin.client.Keycloak keycloak-client realm-name s]
  (info "find user by username, email etc." s)
  (-> keycloak-client (.realm realm-name) (.users) (.search s (int 0) (int 1000))))

(defn get-user-by-username
  ^org.keycloak.representations.idm.UserRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (first (find-users keycloak-client realm-name username)))

(defn add-user-to-group!
  "Make the user join group, return the group"
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id user-id]
  (info "user" user-id "will join group" group-id "of realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.joinGroup group-id))
  (info "user" user-id "joined group" group-id "of realm" realm-name)
  (get-group keycloak-client realm-name group-id))

(defn add-username-to-group-name! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-name username]
  (let [group-id (get-group-id keycloak-client realm-name group-name)
        user-id (.getId (get-user-by-username keycloak-client realm-name username))]
    (add-user-to-group! keycloak-client realm-name group-id user-id)))

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
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.users) (.list)))

(defn ^:deprecated get-user-id
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username]
  (info "get user id by username" username)
  (-> (some (fn [^org.keycloak.representations.idm.UserRepresentation user]
              (when (= username (.getUsername user)) (.getId user))) (list-users keycloak-client realm-name))))

(defn get-user
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id]
  (info "get user [id=" user-id "] in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.toRepresentation)))

(defn create-user!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name username password]
  (user/create-user! keycloak-client realm-name {:username username :password  password}))

(defn add-user-to-group-by-username!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id username]
  (info "add user" username "in group" group-id "of realm" realm-name)
  (let [users-resources (-> keycloak-client (.realms) (.realm realm-name) (.users))
        ^org.keycloak.representations.idm.UserRepresentation user (-> users-resources (.search username) first)
        user-id (.getId user)]
    (-> users-resources (.get user-id) (.joinGroup group-id))))


(defn remove-user-from-group! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-id user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.leaveGroup group-id)))

(defn delete-user-by-id!
  "delete user by its id"
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.delete user-id) .close))

(defn get-user-groups
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.groups)))

(defn client
  "create a ClientRepresentation with client-name the public/private flag"
  (^org.keycloak.representations.idm.ClientRepresentation [{:keys [client-id name public-client public? standard-flow-enabled service-accounts-enabled authorization-services-enabled redirect-uris web-origins direct-access-grants-enabled root-url base-url admin-url attributes] :as client}]
   (let [^org.keycloak.representations.idm.ClientRepresentation client-representation (ClientRepresentation.)]
     (-> ((setters {:client-id                      (or client-id name)
                    :name                           name
                    :public-client                  (or public? public-client)
                    :standard-flow-enabled          (or standard-flow-enabled true)
                    :direct-access-grants-enabled   (or direct-access-grants-enabled true)
                    :service-accounts-enabled       (or service-accounts-enabled (not (or public? public-client)))
                    :authorization-services-enabled (or authorization-services-enabled (not (or public? public-client)))
                    :redirect-uris                  redirect-uris
                    :root-url                       root-url
                    :base-url                       base-url
                    :admin-url                      admin-url
                    :web-origins                    web-origins} "org.keycloak.representations.idm.ClientRepresentation") client-representation)
         (set-attributes ^org.keycloak.representations.idm.ClientRepresentation attributes))))
  (^org.keycloak.representations.idm.ClientRepresentation [name public? redirect-uris web-origins]
   (client {:client-id                      name
            :public-client                  public?
            :standard-flow-enabled          true
            :direct-access-grants-enabled   true
            :service-accounts-enabled       (not public?)
            :authorization-services-enabled (not public?)
            :redirect-uris                  redirect-uris
            :web-origins                    web-origins}))
  (^org.keycloak.representations.idm.ClientRepresentation [name public?]
   (client name public? ["http://localhost:3449/*"] ["http://localhost:3449"])))

(defn get-client
  ^org.keycloak.representations.idm.ClientRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first))

(defn get-client-resource
  "Return a [org.keycloak.admin.client.resource.ClientResource](https://www.keycloak.org/docs-api/11.0/javadocs/org/keycloak/admin/client/resource/ClientResource.html)
  given a keycloak-client, realm-name and id. Be careful the id is the UUID given by Keycloak not the clientId given by the user" 
  ^org.keycloak.admin.client.resource.ClientResource [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.get client-id)))

(defn find-client
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-name]
  (-> (some (fn [^org.keycloak.representations.idm.ClientRepresentation client]
              (when (= name (.getName client)) client)) (-> keycloak-client (.realm realm-name) (.clients) (.findAll)))))

(defn delete-client! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id]
  (info "Delete client" client-id " in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.clients) (.get client-id) (.remove)))

(defn create-client!
  (^org.keycloak.representations.idm.ClientRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name ^org.keycloak.representations.idm.ClientRepresentation client]
   (info "create client" (.getClientId client) "in realm" realm-name)
   (when-let [retrieved-client (get-client keycloak-client realm-name (.getClientId client))]
     (delete-client! keycloak-client realm-name (.getId retrieved-client)))
   (-> keycloak-client (.realm realm-name) (.clients) (.create client))
   (info "client" (.getClientId client) " created in realm" realm-name)
   (get-client keycloak-client realm-name (.getClientId client)))
  (^org.keycloak.representations.idm.ClientRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id public?]
   (create-client! keycloak-client realm-name (client {:client-id client-id :public-client public?}))))

(defn update-client!
  ^org.keycloak.representations.idm.ClientRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name ^org.keycloak.representations.idm.ClientRepresentation client]
  (info "Update client" (.getClientId client) "in realm" realm-name)
  (let [client-rep-with-id (or (get-client keycloak-client realm-name (.getClientId client))
                               (.toRepresentation (get-client-resource keycloak-client realm-name (.getId client))))
        ^org.keycloak.admin.client.resource.ClientResource client-res (get-client-resource keycloak-client realm-name (.getId client-rep-with-id))]
    (-> client-res (.update client))
    (info "client" (.getClientId client) " updated in realm" realm-name)
    (get-client keycloak-client realm-name (.getClientId client))))

(defn create-or-update-client!
  ^org.keycloak.representations.idm.ClientRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name ^org.keycloak.representations.idm.ClientRepresentation client]
  (let [existing-client (or (get-client keycloak-client realm-name (.getClientId client))
                            (first (find-client keycloak-client realm-name (.getName client))))]
    (if existing-client
      (update-client! keycloak-client realm-name client)
      (create-client! keycloak-client realm-name client))))

(defn get-client-secret
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id]
  (let [id (-> (get-client keycloak-client realm-name client-id) (.getId))]
    (println "Get secret for client" client-id)
    (-> keycloak-client (.realm realm-name) (.clients) (.get id) (.getSecret) (.getValue))))

(defn group-membership-mapper [name claim-name]
  (let [config (doto (java.util.HashMap.)
                 (.put "full.path" "true")
                 (.put "access.token.claim" "true")
                 (.put "id.token.claim" "true")
                 (.put "userinfo.token.claim" "true")
                 (.put "claim.name" claim-name))]
        (doto (ProtocolMapperRepresentation.)
          (.setProtocol "openid-connect")
          (.setProtocolMapper "oidc-group-membership-mapper")
          (.setName name)
          (.setConfig config))))

(defn user-attribute-mapper [name user-attribute claim-name json-type]
  (let [config (doto (java.util.HashMap.)
                 (.put "user.attribute" user-attribute)
                 (.put "access.token.claim" "true")
                 (.put "id.token.claim" "true")
                 (.put "userinfo.token.claim" "true")
                 (.put "jsonType.label" json-type)
                 (.put "claim.name" claim-name))]
    (doto (ProtocolMapperRepresentation.)
      (.setProtocol "openid-connect")
      (.setProtocolMapper "oidc-usermodel-attribute-mapper")
      (.setName name)
      (.setConfig config))))

(defn get-mapper [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id mapper-id]
  (let [^org.keycloak.representations.idm.ClientRepresentation client (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first)
        internal-client-id (.getId client)
        client-resource (-> keycloak-client (.realm realm-name) (.clients) (.get internal-client-id))
        mapper (-> client-resource .getProtocolMappers (.getMapperById mapper-id) )]
    mapper))

(defn create-protocol-mapper! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id ^org.keycloak.representations.idm.ProtocolMapperRepresentation mapper]
  (let [^org.keycloak.representations.idm.ClientRepresentation client (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first)]
    (when client
      (let [internal-client-id (.getId client)
            client-resource (-> keycloak-client (.realm realm-name) (.clients) (.get internal-client-id))
            resp (-> client-resource .getProtocolMappers (.createMapper mapper))
            mapper-id (extract-id resp)
            retrieved-mapper (when mapper-id (get-mapper keycloak-client realm-name client-id mapper-id))]
        retrieved-mapper))))

(comment
  (def integration-test-conf (keycloak.deployment/client-conf "http://localhost:8090/auth" "master" "admin-cli"))
  (def admin-client (keycloak.deployment/keycloak-client integration-test-conf "admin" "secretadmin"))


  (create-protocol-mapper! c "electre" "diffusion-frontend"
                           (group-membership-mapper "testjs" "group"))
  (create-protocol-mapper! c "electre" "diffusion-frontend"
                           (user-attribute-mapper "testuam" "org-ref" "org-ref" "String")))
