(ns keycloak.admin
  (:require
   [clojure.string :as string :refer [last-index-of]]
   [clojure.tools.logging :as log :refer [info warn]]
   [keycloak.user :as user]
   [keycloak.utils :as utils :refer [setters set-attributes]])
  (:import [org.keycloak.representations.idm CredentialRepresentation RealmRepresentation ClientRepresentation RoleRepresentation GroupRepresentation UserRepresentation ProtocolMapperRepresentation]
           [org.keycloak.admin.client Keycloak]
           [org.keycloak.admin.client.resource ClientResource GroupResource]
           [jakarta.ws.rs.core Response]
           ))

;(set! *warn-on-reflection* true)

(defn first-letter-capitalize [s]
  (str (string/upper-case (first s)) (subs s 1)))

(defn setter [k]
  (str "set" (first-letter-capitalize (if (keyword? k) (name k) k))))

(defn set-all! [obj m]
  (doseq [[k v] m]
    (let [method-name (setter k)]
      (try
        (clojure.lang.Reflector/invokeInstanceMethod obj method-name (into-array Object [v]))
        (catch IllegalArgumentException iae (println "Can't find setter for key " k)))))
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

(defn realm-representation-from-map ^RealmRepresentation [m]
  (let [^RealmRepresentation realm-rep (RealmRepresentation.)]
    (doto realm-rep
      (.setEnabled true)
      (.setRealm   (or (:name m) (:realm-name m) (:realm m)))
      (.setId      (or (:name m) (:realm-name m) (:realm m))))
    (cond-> realm-rep
      (:themes m)     (set-all! (:themes m));;themes, login, tokens and smtp are convenience keys as the RealmRepresentation has every attributes flat and not separated
      (:login m)      (set-all! (:login m))
      (:tokens m)     (set-all! (:tokens m))
      (:attributes m) (utils/set-attributes (:attributes m))
      :always         (set-all! (dissoc m :themes :login :tokens :smtp :name))
      (:smtp m)       (do
                        (.setSmtpServer realm-rep (ks->str (:smtp m)))
                        realm-rep))
    realm-rep))

(defn map-values-Long-to-Integer [m]
  (into {} (mapv (fn [[k v]] (if (instance? java.lang.Long v)
                              [k (java.lang.Integer. v)]
                              [k v])) m)))

(defn realm-representation
  (^RealmRepresentation [realm-name]
   (doto (RealmRepresentation.) (.setEnabled true) (.setRealm realm-name) (.setId realm-name)))
  (^RealmRepresentation [realm-name themes login tokens smtp]
   (let [^RealmRepresentation realm-rep (realm-representation realm-name)]
     (cond-> realm-rep
             themes (set-all! themes)
             login  (set-all! login)
             tokens (set-all! (map-values-Long-to-Integer tokens)))
     (when smtp
       (.setSmtpServer realm-rep (ks->str smtp)))
     realm-rep)))

(defn get-realm
  ^RealmRepresentation
  [^Keycloak keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.toRepresentation)))

(defn create-realm!
  (^RealmRepresentation [^Keycloak keycloak-client realm-rep-map-or-name]
   (let [^RealmRepresentation
         realm-rep (cond (instance? RealmRepresentation realm-rep-map-or-name) realm-rep-map-or-name
                         (map? realm-rep-map-or-name)                          (realm-representation-from-map realm-rep-map-or-name)
                         (string? realm-rep-map-or-name)                       (realm-representation realm-rep-map-or-name))]
     (info "create realm" (.getId realm-rep))
     (-> keycloak-client (.realms) (.create realm-rep))
     (info "realm" (.getId realm-rep) "created")
     (get-realm keycloak-client (.getId realm-rep))))
  (^RealmRepresentation [^Keycloak keycloak-client realm-name themes login tokens smtp]
   (info "create realm" realm-name)
   (let [realm-rep (realm-representation realm-name themes login tokens smtp)]
     (-> keycloak-client (.realms) (.create realm-rep)))
   (info "realm" realm-name "created")
   (get-realm keycloak-client realm-name)))

(defn update-realm! [^Keycloak keycloak-client realm-name themes login tokens smtp]
  (info "update realm" realm-name)
  (let [realm-rep (realm-representation realm-name themes login tokens smtp)]
    (-> keycloak-client (.realms) (.realm realm-name) (.update realm-rep)))
  (info "realm" realm-name "updated")
  (get-realm keycloak-client realm-name))

(defn delete-realm!
  [^Keycloak keycloak-client realm-name]
  (info "delete realm" realm-name)
  (-> keycloak-client (.realms) (.realm realm-name) (.remove)))

(defn list-realms
  [^Keycloak keycloak-client]
  (info "list the realms")
  (-> keycloak-client (.realms) (.findAll)))

(defn role-representation "create a RoleRepresentation object" [name]
  (RoleRepresentation. name (str "Role created automatically by admin client") false))

(defn get-role [^Keycloak keycloak-client realm-name role-name]
  (info "get role" role-name "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.get role-name)))

(defn list-roles [^Keycloak keycloak-client realm-name]
  (info "list roles in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.list)))

(defn create-role!
  "Create the realm role `role-name` in realm `realm-name`"
  [^Keycloak keycloak-client realm-name role-name]
  (info "create role" role-name "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.create (role-representation role-name))))

(defn create-roles!
    "Create the realm roles `role-names`, accept also a seq of role-name in realm `realm-name`"
  [^Keycloak keycloak-client realm-name role-names]
  (info "create roles" role-names "in realm" realm-name)
  (doseq [name role-names]
      (-> keycloak-client (.realm realm-name) (.roles) (.create (role-representation name))) ))

(defn delete-role!
  "Delete the realm role `role-name` in realm `realm-name`"
  [^Keycloak keycloak-client realm-name role-name]
  (info "delete role" role-name "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.roles) (.deleteRole role-name)))

(defn group-representation "create a GroupRepresentation object" [group-name]
  (doto (GroupRepresentation.)
    (.setName group-name)))

(defn count-groups [^Keycloak keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.groups) (.count) (.get "count")))

(defn list-groups
  (^java.util.List [^Keycloak keycloak-client realm-name]
   (info "list the groups representation objects of realm" realm-name)
   (-> keycloak-client (.realm realm-name) (.groups) (.groups)))
  (^java.util.List [^Keycloak keycloak-client realm-name s]
   (info "list the groups representation objects of realm " realm-name "with name" s)
   (-> keycloak-client (.realm realm-name) (.groups) (.groups s (int 0) (int 1000)))))

(defn- group-name-match? [group-name ^GroupRepresentation group]
  (when (= group-name (.getName group)) group))

(defn- group-path-match? [group-path ^GroupRepresentation group]
  (when (= group-path (.getPath group)) group))

(defn get-group-id
  [^Keycloak keycloak-client realm-name group-name]
  (some (fn [^GroupRepresentation group]
          (let [group (group-name-match? group-name group)]
            (when group
              (.getId group)))) (list-groups keycloak-client realm-name)))

(defn get-group-id-by-path [^Keycloak keycloak-client realm-name path]
  (some (fn [^GroupRepresentation group]
          (let [group (group-path-match? path group)]
            (when group
              (.getId group)))) (list-groups keycloak-client realm-name)))

(defn get-group
  [^Keycloak keycloak-client realm-name group-id]
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation)))

(defn create-group!
  ^GroupRepresentation [^Keycloak keycloak-client realm-name group-name]
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
        (some (partial group-name-match? group-name)
              (list-groups keycloak-client realm-name group-name))))))

(defn create-groups! [^Keycloak keycloak-client realm-name group-names]
  (for [group-name group-names]
    (create-group! keycloak-client realm-name group-name)))

(defn delete-group!
  [^Keycloak keycloak-client realm-name group-id]
  (info "delete group [id=" group-id "] in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.remove))
  true)

(defn get-group-resource [^Keycloak keycloak-client realm-name group-id]
  (when group-id
    (-> keycloak-client (.realm realm-name) (.groups) (.group group-id))))

(defn- get-realm-roles-representations ^RoleRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name roles]
  (doall (map (fn [role]
                (try
                  (-> keycloak-client
                      (.realm realm-name)
                      (.roles)
                      (.get role)
                      (.toRepresentation))
                  (catch jakarta.ws.rs.NotFoundException nfe
                    (warn "Realm role" role "not found in realm" realm-name)))) (map name roles))))

(def memoized-get-realm-roles-representations (memoize get-realm-roles-representations))

(defn- resource-and-roles [^Keycloak keycloak-client realm-name group-name-or-path roles]
  (when (and group-name-or-path roles)
    {:group-resource (get-group-resource keycloak-client realm-name (or (get-group-id keycloak-client realm-name group-name-or-path)
                                                                        (get-group-id-by-path keycloak-client realm-name group-name-or-path)))
     :roles          (memoized-get-realm-roles-representations keycloak-client realm-name roles)}))

(defn assert-all-realm-roles-exists [^Keycloak keycloak-client realm-name roles]
  (let [existing-roles (set (map (fn [role] (.getName role)) (-> keycloak-client (.realm realm-name) (.roles) (.list))))
        not-found (clojure.set/difference (set roles) existing-roles)]
    (when (seq not-found)
      (warn "Realm roles" not-found "not found in realm" realm-name)
      (throw (ex-info (format "roles %s not found in realm %s" not-found realm-name) {:checked-roles roles :realm-name realm-name :existing-roles-in-realm existing-roles :roles-not-found not-found})))))

(defn add-realm-roles-to-group!
  "Add roles to a group given its name or path"
  [^Keycloak keycloak-client realm-name group-name-or-path roles-to-add]
  (assert-all-realm-roles-exists keycloak-client realm-name roles-to-add)
  (let [{:keys [group-resource roles]} (resource-and-roles keycloak-client realm-name group-name-or-path roles-to-add)]
    (when group-resource
      (-> ^GroupResource group-resource
          (.roles)
          (.realmLevel)
          (.add (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles))))))
    roles-to-add))

(defn remove-realm-roles-of-group!
  [^Keycloak keycloak-client realm-name group-name-or-path roles-to-remove]
  (assert-all-realm-roles-exists keycloak-client realm-name roles-to-remove)
  (let [{:keys [group-resource roles]} (resource-and-roles keycloak-client realm-name group-name-or-path roles-to-remove)]
    (when group-resource
      (-> ^GroupResource group-resource
          (.roles)
          (.realmLevel)
          (.remove (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles))))))
    roles-to-remove))

(defn set-realm-roles-of-group!
  [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-name-or-path roles-to-set]
  (assert-all-realm-roles-exists keycloak-client realm-name roles-to-set)
  (let [{:keys [group-resource roles]} (resource-and-roles keycloak-client realm-name group-name-or-path roles-to-set)
        role-scope-resource            (-> ^GroupResource group-resource (.roles) (.realmLevel))]
    (when role-scope-resource
      (.remove role-scope-resource (.listEffective role-scope-resource))
      (.add role-scope-resource (java.util.ArrayList. ^java.util.Collection (vec (filter some? roles))))
      roles-to-set)))

(defn get-realm-roles-of-group [^org.keycloak.admin.client.Keycloak keycloak-client realm-name group-name-or-path]
  (.getRealmRoles (get-group keycloak-client realm-name (or (get-group-id keycloak-client realm-name group-name-or-path)
                                                            (get-group-id-by-path keycloak-client realm-name group-name-or-path)))))

(defn get-subgroup
  [^Keycloak keycloak-client realm-name group-id subgroup-id]
  (some (fn [^GroupRepresentation group]
          (when (= subgroup-id (.getId group)) group))
        (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation) (.getSubGroups))))

(defn get-group-members
  [^Keycloak keycloak-client realm-name group-id]
  (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.members)))

(defn list-subgroups
  [^Keycloak keycloak-client realm-name group-id]
  (info "List all subgroups of group" group-id "in realm " realm-name)
  (when group-id
    (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.toRepresentation) (.getSubGroups))))

(defn get-subgroup-id
  [^Keycloak keycloak-client realm-name group-id subgroup-name]
  (some (fn [^GroupRepresentation group]
          (when (= subgroup-name (.getName group))
            (.getId group))) (list-subgroups keycloak-client realm-name group-id)))

(defn create-subgroup!
  (^GroupRepresentation [^Keycloak keycloak-client realm-name group-id subgroup-name]
   (create-subgroup! keycloak-client realm-name group-id subgroup-name nil))
  (^GroupRepresentation[^Keycloak keycloak-client realm-name group-id subgroup-name attributes]
   (info "create subgroup" subgroup-name "in group" group-id "in realm" realm-name)
   (let [group-rep (set-attributes ^GroupRepresentation (group-representation subgroup-name) attributes)
         resp (-> keycloak-client (.realm realm-name) (.groups) (.group group-id) (.subGroup group-rep))
         subgroup-id (extract-id resp)]
     (when resp (.close resp))
     (if subgroup-id
       (do
         (info "subgroup" subgroup-name "created in group" group-id "in realm" realm-name "with subgroup-id" subgroup-id)
         (get-group keycloak-client realm-name subgroup-id))
       (do
         (info "subgroup" subgroup-name "already exist in group" group-id "in realm" realm-name)
         (some (partial group-name-match? subgroup-name) (list-groups keycloak-client realm-name subgroup-name)))))))

(defn find-users
  ^java.util.List [^Keycloak keycloak-client realm-name s]
  (info "find user by username, email etc." s)
  (-> keycloak-client (.realm realm-name) (.users) (.search s (int 0) (int 1000))))

(defn get-user-by-username
  ^UserRepresentation [^Keycloak keycloak-client realm-name username]
  (first (filter (fn [^UserRepresentation user] (= username (.getUsername user))) (find-users keycloak-client realm-name username))))

(defn add-user-to-group!
  "Make the user join group, return the group"
  [^Keycloak keycloak-client realm-name group-id user-id]
  (info "user" user-id "will join group" group-id "of realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.joinGroup group-id))
  (info "user" user-id "joined group" group-id "of realm" realm-name)
  (get-group keycloak-client realm-name group-id))

(defn add-username-to-group-name! [^Keycloak keycloak-client realm-name group-name username]
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
  [^Keycloak keycloak-client realm-name]
  (-> keycloak-client (.realm realm-name) (.users) (.list)))

(defn ^:deprecated get-user-id
  [^Keycloak keycloak-client realm-name username]
  (info "get user id by username" username)
  (-> (some (fn [^UserRepresentation user]
              (when (= username (.getUsername user)) (.getId user))) (list-users keycloak-client realm-name))))

(defn get-user
  [^Keycloak keycloak-client realm-name user-id]
  (info "get user [id=" user-id "] in realm " realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.toRepresentation)))

(defn create-user!
  ([^Keycloak keycloak-client realm-name username password]
   (create-user! keycloak-client realm-name {:username username :password  password}))
  (^org.keycloak.representations.idm.UserRepresentation [^org.keycloak.admin.client.Keycloak keycloak-client realm-name {:keys [username first-name last-name email password is-manager group in-subgroups] :as person}]
   (info "create user" username "in realm" realm-name)
   (let [resp    (-> keycloak-client (.realm realm-name) (.users) (.create (user/user-for-creation person)))
         user-id (extract-id resp)]
     (when resp (.close resp))
     (info "user with username " username "created in realm" realm-name " with id" user-id)
     (when (and group (seq in-subgroups) user-id)
       (doseq [subgroup-name in-subgroups]
         (let [parent-group-id (get-group-id keycloak-client realm-name group)
               subgroup-id     (get-subgroup-id keycloak-client realm-name parent-group-id subgroup-name)]
           (println (format "Add user \"%s\" to group \"%s\"" username subgroup-name))
           (add-user-to-group! keycloak-client realm-name subgroup-id user-id))))
     (if user-id
       (get-user keycloak-client realm-name user-id)
       (get-user-by-username keycloak-client realm-name username)))))

(defn update-user! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name user-id {:keys [username first-name last-name email password group in-subgroups] :as person}]
  (info "update user" username "in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.update (user/user-for-update person)))
  (when (and group (seq in-subgroups) user-id)
       (doseq [subgroup-name in-subgroups]
         (let [parent-group-id (get-group-id keycloak-client realm-name group)
               subgroup-id     (get-subgroup-id keycloak-client realm-name parent-group-id subgroup-name)]
           (println (format "Add user \"%s\" to group \"%s\"" username subgroup-name))
           (add-user-to-group! keycloak-client realm-name subgroup-id user-id))))
  (get-user keycloak-client realm-name user-id))

(defn add-user-to-group-by-username!
  [^Keycloak keycloak-client realm-name group-id username]
  (info "add user" username "in group" group-id "of realm" realm-name)
  (let [users-resources (-> keycloak-client (.realms) (.realm realm-name) (.users))
        ^UserRepresentation user (-> users-resources (.search username) first)
        user-id (.getId user)]
    (-> users-resources (.get user-id) (.joinGroup group-id))))


(defn remove-user-from-group! [^Keycloak keycloak-client realm-name group-id user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.leaveGroup group-id)))

(defn delete-user-by-id!
  "delete user by its id"
  [^Keycloak keycloak-client realm-name user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.delete user-id) .close))

(defn get-user-groups
  [^Keycloak keycloak-client realm-name user-id]
  (-> keycloak-client (.realm realm-name) (.users) (.get user-id) (.groups)))

(defn client
  "Create a [ClientRepresentation](https://www.keycloak.org/docs-api/11.0/javadocs/org/keycloak/representations/idm/ClientRepresentation.html) object to be used with [[create-client!]], [[update-client!]] or [[create-or-update-client!]] functions.
  `client` argument is a map. Different arities are proposed for convenience with default value for the rest of the client's map keys:

  - `client-id`: client-id as a string, client identifier for OIDC requests. Optional: default value is the name of the client.
  - `name`: display name for the client whenever it is displayed in a Keycloak UI screen.name. Mandatory.
  - `public?` or `public-client`: boolean, `true` if the client is of the `public` _Access Type_, `false` if the client is of the `confidential` _Access Type_.
    - _confidential_: Confidential access type is for server-side clients that need to perform a browser login and require a client secret when they turn an access code into an access token, (see Access Token Request in the OAuth 2.0 spec for more details). This type should be used for server-side applications.
public
    - _public_: Public access type is for client-side clients that need to perform a browser login. With a client-side application there is no way to keep a secret safe. Instead it is very important to restrict access by configuring correct redirect URIs for the client.
  - `standard-flow-enabled`: boolean, if `true` clients are allowed to use the OIDC [Authorization Code Flow](https://www.keycloak.org/docs/latest/server_admin/#_oidc-auth-flows). Default to `true`.
  - `direct-access-grants-enabled`: boolean, if `true`, clients are allowed to use the OIDC [Direct Access Grants](https://www.keycloak.org/docs/latest/server_admin/#_oidc-auth-flows) . Default to true.
  - `service-accounts-enabled`: boolean, if `true`, Service account is enabled for this client, only for `confidential` client. See [Service Accounts](https://www.keycloak.org/docs/latest/server_admin/#_service_accounts). Default to the logical expression: `(not (public?))`.
  - `authorization-services-enabled`: boolean, if `true` [authorization services](https://www.keycloak.org/docs/latest/authorization_services/) are enabled for this client.
  - `redirect-uris`: vector of String representing URL Patterns. Required if `public?`. Wildcards (*) are only allowed at the end of a URI, i.e. http://host.com/*
  - `root-url`: String, If Keycloak uses any configured relative URLs, this value is prepended to them.
  - `base-url`: String, If Keycloak needs to link to the client, this URL is used.
  - `admin-url`: String, For Keycloak specific client adapters, this is the callback endpoint for the client. The Keycloak server will use this URI to make callbacks like pushing revocation policies, performing backchannel logout, and other administrative operations. For Keycloak servlet adapters, this can be the root URL of the servlet application. For more information see [Securing Applications and Services Guide](https://www.keycloak.org/docs/latest/securing_apps/).
  - `web-origins`: vector of String representing domains. The domains listed in the Web Origins setting for the client are embedded within the access token sent to the client application. The client application can then use this information to decide whether or not to allow a CORS request to be invoked on it. This is an extension to the OIDC protocol so only Keycloak client adapters support this feature. See [Securing Applications and Services Guide](https://www.keycloak.org/docs/latest/securing_apps/) for more information.
  - `attributes`: map with keys and values as String. Transformed to a `java.util.Map<String, String>`. Some attributes for the client are passed in this map, an attribute of interest is the `access.token.lifespan` that override the _Access Token lifespan_ of the realm for that client.

  "
  (^ClientRepresentation [{:keys [client-id name public-client public? standard-flow-enabled service-accounts-enabled authorization-services-enabled redirect-uris web-origins direct-access-grants-enabled root-url base-url admin-url attributes client-authenticator-type] :as client}]
   (let [^ClientRepresentation client-representation (ClientRepresentation.)]
     (-> ((setters {:client-id                      (or client-id name)
                    :name                           name
                    :public-client                  (or public? public-client)
                    :standard-flow-enabled          (or standard-flow-enabled true)
                    :direct-access-grants-enabled   (or direct-access-grants-enabled true)
                    :service-accounts-enabled       (or service-accounts-enabled (not (or public? public-client)))
                    :authorization-services-enabled (or authorization-services-enabled false)
                    :redirect-uris                  redirect-uris
                    :root-url                       root-url
                    :base-url                       base-url
                    :admin-url                      admin-url
                    :client-authenticator-type      (or client-authenticator-type "client-secret")
                    :web-origins                    web-origins} "org.keycloak.representations.idm.ClientRepresentation") client-representation)
         (set-attributes ^ClientRepresentation attributes))))
  (^ClientRepresentation [name public? redirect-uris web-origins]
   (client {:client-id                      name
            :public-client                  public?
            :redirect-uris                  redirect-uris
            :web-origins                    web-origins}))
  (^ClientRepresentation [name public?]
   (client name public? ["http://localhost:3449/*"] ["http://localhost:3449"])))

(defn get-client
  "Get a _Client_ from a `client-id` (caution: it's not the `client-name`). Return a [ClientRepresentation](https://www.keycloak.org/docs-api/11.0/javadocs/org/keycloak/representations/idm/ClientRepresentation.html) object. It's the _Client_ concept of Keycloak, not the Keycloak admin client used to interact with the API SDK and given as a first argument of every function in that namespace.

  **keycloak-client and realm-name**

  Fist argument is an [admin client's _Keycloak_ object](https://www.keycloak.org/docs-api/11.0/javadocs/org/keycloak/admin/client/Keycloak.html) obtained with:
  ```clojure
  (require 'keycloak.deployment)
  (keycloak.deployment/keycloak-client (keycloak.deployment/client-conf \"http://localhost:8090\" \"master\"  \"admin-cli\") admin-login admin-password)
  ```
  Second argument is the _Realm_ name as a String.
  "
  ^ClientRepresentation [^Keycloak keycloak-client realm-name client-id]
  (some (fn client-id-exact-match? [^ClientRepresentation client]
          (when (= client-id (.getClientId client))
            client)) (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id))))

(defn get-client-resource
  "Return a [org.keycloak.admin.client.resource.ClientResource](https://www.keycloak.org/docs-api/11.0/javadocs/org/keycloak/admin/client/resource/ClientResource.html)
  given a `keycloak-client`, `realm-name` and `id`. Be careful the id is the UUID attributed by Keycloak during the creation of the client and not the `clientId` given by the user"
  ^ClientResource [^Keycloak keycloak-client realm-name client-id]
  (-> keycloak-client (.realm realm-name) (.clients) (.get client-id)))

(defn find-client
  "Find client from its `name`, provide a `keycloak-client` and `realm-name`, return a collection"
  [^Keycloak keycloak-client realm-name client-name]
  (-> (some (fn [^ClientRepresentation client]
              (when (= name (.getName client)) client)) (-> keycloak-client (.realm realm-name) (.clients) (.findAll)))))

(defn delete-client! [^Keycloak keycloak-client realm-name client-id]
  (info "Delete client" client-id " in realm" realm-name)
  (-> keycloak-client (.realm realm-name) (.clients) (.get client-id) (.remove)))

(defn regenerate-secret
  "Regenerate a client secret, must be invoked once a client is created as the secret is null.. the id is obtained with `(.getId client)` from a ClientRepresentation"
  [^Keycloak keycloak-client realm-name id]
  (let [client-resource (get-client-resource keycloak-client realm-name id)]
    (.generateNewSecret client-resource)
    (info "Client secret regenerated for client with Id " id " in realm " realm-name )
    client-resource))

(defn create-client!
  "Creates a client with its 'realm-name' and a [ClientRepresentation](https://www.keycloak.org/docs-api/11.0/javadocs/org/keycloak/representations/idm/ClientRepresentation.html) object,
  obtained with 'client' function."
  (^ClientRepresentation [^Keycloak keycloak-client realm-name ^ClientRepresentation client]
   (info "create client" (.getClientId client) "in realm" realm-name)
   (when-let [retrieved-client (get-client keycloak-client realm-name (.getClientId client))]
     (delete-client! keycloak-client realm-name (.getId retrieved-client)))
   (let [resp   (-> keycloak-client (.realm realm-name) (.clients) (.create client))
         client (get-client keycloak-client realm-name (.getClientId client))]
     (info "client" (.getClientId client) " created in realm " realm-name " status " (.getStatus resp))
     (regenerate-secret keycloak-client realm-name (.getId client))
     (when resp (.close resp))
     client))
  (^ClientRepresentation [^Keycloak keycloak-client realm-name client-id public?]
   (create-client! keycloak-client realm-name (client {:client-id client-id :public-client public?}))))

(defn update-client!
  ^ClientRepresentation [^Keycloak keycloak-client realm-name ^ClientRepresentation client]
  (info "Update client" (.getClientId client) "in realm" realm-name)
  (let [^ClientResource client-res (some->> (or (get-client keycloak-client realm-name (.getClientId client))
                                                (some->> (.getId client)
                                                         (get-client-resource keycloak-client realm-name)
                                                         (.toRepresentation)))
                                            (.getId)
                                            (get-client-resource keycloak-client realm-name))]
    (when client-res
      (-> client-res (.update client))
      (info "client" (.getClientId client) " updated in realm" realm-name)
      (get-client keycloak-client realm-name (.getClientId client)))))

(defn create-or-update-client!
  ^ClientRepresentation [^Keycloak keycloak-client realm-name ^ClientRepresentation client]
  (let [existing-client (or (get-client keycloak-client realm-name (.getClientId client))
                            (some (fn [^ClientRepresentation client-rep] (= (.getName client) (.getName client-rep))) (find-client keycloak-client realm-name (.getName client))))]
    (if existing-client
      (update-client! keycloak-client realm-name client)
      (create-client! keycloak-client realm-name client))))

(defn get-client-secret
  [^Keycloak keycloak-client realm-name client-id]
  (let [client-id (get-client keycloak-client realm-name client-id)]
    (when client-id
      (let [id (.getId client-id)]
        (-> keycloak-client (.realm realm-name) (.clients) (.get id) (.getSecret) (.getValue))))))

(def oidc-usersessionmodel-note-mapper  "oidc-usersessionmodel-note-mapper")
(def oidc-group-membership-mapper       "oidc-group-membership-mapper")
(def oidc-usermodel-attribute-mapper    "oidc-usermodel-attribute-mapper")
(def oidc-usermodel-realm-role-mapper   "oidc-usermodel-realm-role-mapper")
(def oidc-audience-mapper               "oidc-audience-mapper")
(def oidc-usermodel-property-mapper     "oidc-usermodel-property-mapper")
(def oidc-hardcoded-claim-mapper        "oidc-hardcoded-claim-mapper")
(def oidc-hardcoded-role-mapper         "oidc-hardcoded-role-mapper")
(def oidc-allowed-origins-mapper        "oidc-allowed-origins-mapper")
(def oidc-audience-resolve-mapper       "oidc-audience-resolve-mapper")
(def oidc-claims-param-token-mapper     "oidc-claims-param-token-mapper")
(def oidc-usermodel-client-role-mapper  "oidc-usermodel-client-role-mapper")
(def oidc-full-name-mapper              "oidc-full-name-mapper")
(def oidc-address-mapper                "oidc-address-mapper")
(def oidc-role-name-mapper              "oidc-role-name-mapper")

(def protocol-mappers-default-config {"oidc-usersessionmodel-note-mapper" {"id.token.claim" "true" "access.token.claim" "true" "access.tokenResponse.claim" "false"}
                                      "oidc-group-membership-mapper"      {"full.path" "true", "id.token.claim" "true", "access.token.claim" "true", "userinfo.token.claim" "true"}
                                      "oidc-usermodel-attribute-mapper"   {"id.token.claim" "true", "access.token.claim" "true", "userinfo.token.claim" "true"}
                                      "oidc-usermodel-realm-role-mapper"  {"id.token.claim" "true", "access.token.claim" "true", "multivalued" "true", "userinfo.token.claim" "true"}
                                      "oidc-audience-mapper"              {"id.token.claim" "false", "access.token.claim" "true"}
                                      "oidc-usermodel-property-mapper"    {"id.token.claim" "true", "access.token.claim" "true", "userinfo.token.claim" "true"}
                                      "oidc-hardcoded-claim-mapper"       {"id.token.claim" "true", "access.token.claim" "true", "userinfo.token.claim" "true", "access.tokenResponse.claim" "false"}
                                      "oidc-hardcoded-role-mapper"        {}
                                      "oidc-allowed-origins-mapper"       {}
                                      "oidc-audience-resolve-mapper"      {}
                                      "oidc-claims-param-token-mapper"    {"id.token.claim" "true", "userinfo.token.claim" "true"}
                                      "oidc-usermodel-client-role-mapper" {"id.token.claim" "true", "access.token.claim" "true", "multivalued" "true", "userinfo.token.claim" "true"}
                                      "oidc-full-name-mapper"             {"id.token.claim" "true", "access.token.claim" "true", "userinfo.token.claim" "true"}
                                      "oidc-address-mapper"               {"user.attribute.formatted" "formatted", "user.attribute.country" "country", "user.attribute.postal_code" "postal_code", "userinfo.token.claim" "true", "user.attribute.street" "street", "id.token.claim" "true", "user.attribute.region" "region", "access.token.claim" "true", "user.attribute.locality" "locality"}
                                      "oidc-role-name-mapper"             {}})

(defn mapper "Create a mapper with name and mapper among the one provided" [name mapper custom-config]
  (let [default-config (get protocol-mappers-default-config mapper)
        config         (merge default-config custom-config)]
    (doto (ProtocolMapperRepresentation.)
      (.setProtocol "openid-connect")
      (.setProtocolMapper mapper)
      (.setName name)
      (.setConfig (utils/map->HashMap config)))))


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

(defn get-mapper [^Keycloak keycloak-client realm-name client-id mapper-id]
  (let [^ClientRepresentation client (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first)
        internal-client-id (.getId client)
        client-resource (-> keycloak-client (.realm realm-name) (.clients) (.get internal-client-id))
        mapper (-> client-resource .getProtocolMappers (.getMapperById mapper-id) )]
    mapper))

(defn create-protocol-mapper! [^Keycloak keycloak-client realm-name client-id ^ProtocolMapperRepresentation mapper]
  (let [^ClientRepresentation client (-> keycloak-client (.realm realm-name) (.clients) (.findByClientId client-id) first)]
    (when client
      (let [internal-client-id (.getId client)
            client-resource (-> keycloak-client (.realm realm-name) (.clients) (.get internal-client-id))
            resp (-> client-resource .getProtocolMappers (.createMapper mapper))
            mapper-id (extract-id resp)
            retrieved-mapper (when mapper-id (get-mapper keycloak-client realm-name client-id mapper-id))]
        (when resp (.close resp))
        retrieved-mapper))))

(comment
  (def integration-test-conf (keycloak.deployment/client-conf "http://localhost:8090/auth" "master" "admin-cli"))
  (def admin-client (keycloak.deployment/keycloak-client integration-test-conf "admin" "secretadmin"))

  (def group-mapper (mapper "group-mapper"   oidc-group-membership-mapper {"claim.name" "yo"}))
  (def attr-mapper  (mapper "org-ref-mapper" oidc-usermodel-attribute-mapper {"user.attribute" "org-ref"
                                                                              "claim.name" "org-ref"
                                                                              "jsonType.label" "String"} ))

  (create-protocol-mapper! c "electre" "diffusion-frontend"
                           (group-membership-mapper "testjs" "group"))
  (create-protocol-mapper! c "electre" "diffusion-frontend"
                           (user-attribute-mapper "testuam" "org-ref" "org-ref" "String")))
