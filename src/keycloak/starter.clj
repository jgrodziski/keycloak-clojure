(ns keycloak.starter
  (:gen-class)
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.pprint :as pprint]

   [environ.core :as environ]
   [jsonista.core :as json]
   [me.raynes.fs :as fs]
   [sci.core :as sci]
   [talltale.core :as talltale :refer :all]
   [cli-matic.core :refer [run-cmd]]
   [clj-yaml.core :as yaml]

   [keycloak.admin :as admin :refer :all]
   [keycloak.deployment :as deployment :refer [client-conf keycloak-client]]
   [keycloak.meta :as meta]
   [keycloak.user :as user]
   [keycloak.utils :as utils :refer [list-files]]
   [keycloak.vault.protocol :as vault :refer [Vault write-secret!]]
   [keycloak.vault.hashicorp :as hashicorp-vault]
   [keycloak.vault.google :as google-vault]
   [keycloak.reconciliation :as reconciliation]
   [clojure.pprint :as pp])
  (:import java.io.PushbackReader))

;(set! *warn-on-reflection* true)

(defn export-json [export-dir export-file-without-extension client-id path secrets]
  (let [secrets-file (clojure.java.io/file export-dir (str export-file-without-extension ".json"))]
    (fs/touch secrets-file)
    (println "secrets" secrets)
    (json/write-value secrets-file secrets)
    (println (format "Secret of client \"%s\" exported in files %s at path %s" client-id (str secrets-file) path))))

(defn export-yaml [export-dir export-file-without-extension client-id path secrets]
  (let [secrets-file (clojure.java.io/file export-dir (str export-file-without-extension ".yml"))]
    (fs/touch secrets-file)
    (spit secrets-file (yaml/generate-string secrets))
    (println (format "Secret of client \"%s\" exported in files %s at path %s" client-id (str secrets-file) path))))

(defn- env-var-or-dir? [dir]
  (if (System/getenv dir)
    (System/getenv dir)
    dir))

(defn export-secret-in-files! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id {:keys [name-without-extension export-dir path] :as secret-file}]
  (let [export-dir          (or export-dir "/etc/keycloak")
        path                (conj path (keyword client-id) :secret)
        _                   (println (format "Secret of client \"%s\" will be exported at path %s in files %s in directory %s" client-id path (str name-without-extension ".edn|json|yml") export-dir))
        secret              (get-client-secret keycloak-client realm-name client-id)
        secret-file         (or name-without-extension ".keycloak-secrets")
        resolved-export-dir (env-var-or-dir? export-dir)
        secrets-file        (clojure.java.io/file resolved-export-dir (str secret-file ".edn"))
        _                   (fs/mkdir resolved-export-dir)
        _                   (fs/touch secrets-file)
        secrets             (-> (or (clojure.edn/read-string (slurp secrets-file)) {})
                                (assoc-in path secret))]
    (println (format "Secret of client \"%s\" exported in files %s at path %s" client-id (str secrets-file) path))
    (spit secrets-file secrets)
    (export-json resolved-export-dir secret-file client-id path secrets)
    (export-yaml resolved-export-dir secret-file client-id path secrets)
    secrets-file))

(defmulti export-secret-in-vault!
  "take the vendor value to dispatch the call to proper function, other value are specific to vault implementation:
  - `:hashicorp`: in infra-context, [:vault :path] is a string with placeholders as: %1$s is the environment, %2$s is the color, %3$s is the base-domains, %4$s is the client-id (client-id depends of your realm-config.clj code)
  - `:gcp-sm`: the vault entry of infra-contect must contains project-id and secret-id, also the google_application_credentials must be properly defined and available at runtime "
  (fn [_ infra-context _ _] (get-in infra-context [:vault :vendor])))

(defmethod export-secret-in-vault! :hashicorp [^org.keycloak.admin.client.Keycloak keycloak-client {:keys [vault environment color base-domains] :as infra-context} realm-name client-id]
  (let [secret                                  (get-client-secret keycloak-client realm-name client-id)
        {:keys [protocol host port mount path token vendor]} vault
        vault-url                               (hashicorp-vault/vault-url protocol host port)
        vault-client                            (hashicorp-vault/new-client vault-url)
        hashicorp-vault                         (hashicorp-vault/->HashicorpVault vault-client token mount)
        vault-path                              (format path environment color base-domains client-id)]
    (println (format "Secret of client \"%s\" will be exported in hashicorp vault at url %s, mount %s and path %s" client-id vault-url mount vault-path))
    (vault/write-secret! hashicorp-vault vault-path secret)))

(defmethod export-secret-in-vault! :gcp-sm  [^org.keycloak.admin.client.Keycloak keycloak-client {:keys [vault environment color base-domains] :as infra-context} realm-name client-id]
  (let [secret                      (get-client-secret keycloak-client realm-name client-id)
        {:keys [project-id secret-id vendor]} vault
        secret-name                 (format "realm/%s/client/%s" realm-name client-id)
        google-secret-manager       (google-vault/->GoogleSecretManager project-id)]
    (vault/write-secret! google-secret-manager (or secret-id secret-name) secret)
    (println (format "Secret of client \"%s\" will be exported in google secret-manager for project-id %s and secret-id %s secret %s" client-id project-id (or secret-id secret-name) secret))))

(defn generate-user [username-creator-fn role group subgroup idx & opts]
  (merge (talltale/person)
         {:username (apply username-creator-fn role group subgroup idx opts)
          :password "password"}))

(defn init-realm! [^org.keycloak.admin.client.Keycloak admin-client {:keys [name themes login tokens smtp user-admin] :as realm-data}]
  (println (format "Will create realm \"%s\"" name))
  (try (create-realm! admin-client name themes login tokens smtp)
       (println (format "Realm \"%s\" created" name))
       (if-let [user-admin-id (when user-admin (user/user-id admin-client "master" (:username user-admin)))]
         (do
           (println (format "Will update the admin user %s (user-id %s) with %s" (:username user-admin) user-admin-id user-admin))
           (user/update-user! admin-client "master" user-admin-id user-admin))
         (do
           (println (format "Will create the admin users %s with data as %s" (:username user-admin) user-admin))
           (user/create-user! admin-client "master" user-admin)))
       (catch javax.ws.rs.ClientErrorException cee
         (if (= (-> cee (.getResponse) (.getStatus)) 409)
           (do
             (update-realm! admin-client name themes login tokens smtp)
             (println (format "Realm \"%s\" updated" name)))
           (println "Can't create realm " name ", " cee))
         realm-data)
       (catch javax.ws.rs.InternalServerErrorException isee
         (println "Can't create realm " isee)
         (update-realm! admin-client name themes login tokens smtp)
         (println (format "Realm \"%s\" updated" name))
         realm-data)
       (catch Exception e
         (println (format "Can't create Realm, exception message %s and realm data:" (.getMessage e)))
         (pp/pprint realm-data)
         realm-data)))

(defn create-mappers! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id mappers]
  (when (and mappers (not (empty? mappers)))
    (println "Create protocol mappers for client" client-id)
    (for [{:keys [name type config] :as mapper} mappers]
      (do (println (format "  Create protocol mapper for client%s: name %s type %s config %s" client-id name type config))
          (create-protocol-mapper! keycloak-client realm-name client-id (admin/mapper name type config))))))

(defn init-clients! [^org.keycloak.admin.client.Keycloak admin-client realm-name clients-data infra-context]
  (doseq [{:keys [name public? redirect-uris web-origins mappers] :as client-data} clients-data]
    (let [client (client client-data)
          client-id name
          _ (println (format "Create client \"%s\" in realm %s and client data %s" client-id realm-name client-data))
          created-client (create-or-update-client! admin-client realm-name client)]
      (if (not created-client) (throw (Exception. (format "Client %s not created in realm %s" client-id realm-name))))
      (println (format "Client with Id \"%s\" and clientId \"%s\" created in realm %s" (.getId created-client) (.getClientId created-client) realm-name))
      (create-mappers! admin-client realm-name client-id mappers)
      (when (:secret-file infra-context )
        (export-secret-in-files! admin-client realm-name client-id (:secret-file infra-context)))
      (when (:vault infra-context)
        ;;by default we use hashicorp vault (downward compatibility with previous version)
        (let [infra-context (if (get-in infra-context [:vault :vendor]) infra-context (assoc-in infra-context [:vault :vendor] :hashicorp))]
          (export-secret-in-vault! admin-client infra-context realm-name client-id)))))
  (println (format "%s Clients created in realm %s" (count clients-data) realm-name)))

(defn init-roles! [^org.keycloak.admin.client.Keycloak admin-client realm-name roles-data]
  (doseq [role roles-data]
    (try (create-role! admin-client realm-name role)
         (println (format "Role %s created in realm %s" role realm-name))
         (catch Exception e (get-role admin-client realm-name role)))))

(defn gen-users! [^org.keycloak.admin.client.Keycloak admin-client realm-name {:keys [groups] :as data}]
  (when (and (:generated-users-by-group-and-role data) (> 0 (:generated-users-by-group-and-role data)))
    (doseq [{:keys [name subgroups]} groups]
      (let [^org.keycloak.representations.idm.GroupRepresentation group (admin/get-group admin-client realm-name (get-group-id admin-client realm-name name))]
        (doseq [[idx {subgroup-name :name attributes :attributes}] (map-indexed vector subgroups)]
          (let [^org.keycloak.representations.idm.GroupRepresentation subgroup (admin/get-subgroup admin-client realm-name (.getId group) (get-subgroup-id admin-client realm-name (.getId group) subgroup-name))]
            (doseq [role (:roles data)]
              (doseq [i (range 1 (inc (:generated-users-by-group-and-role data)))]
                (let [user         (generate-user (:username-creator-fn data) role (.getName group) (.getName subgroup) i)
                      created-user (user/create-or-update-user! admin-client realm-name user [role] nil)]
                  (println (format "      User \"%s\" created with realm-roles %s and client-roles %s" (:username user) [role] nil))
                  (println (format "      Add user \"%s\" to group \"%s\"" (:username user) (.getName subgroup)))
                  (add-user-to-group! admin-client realm-name (.getId subgroup) (.getId created-user)))))))))))

(defn init-users! [^org.keycloak.admin.client.Keycloak admin-client realm-name users-data & [opts]]
  (doseq [{:keys [username] :as user} users-data]
    (let [created-user (user/create-or-update-user! admin-client realm-name user (:realm-roles user) (:client-roles user))]
      (println (format "User \"%s\" created with realm-roles %s and client-roles %s" username (pr-str (:realm-roles user)) (:client-roles user)))
      (doseq [subgroup-name (:in-subgroups user)]
        (let [subgroup-id (get-subgroup-id admin-client realm-name (get-group-id admin-client realm-name (:group user)) subgroup-name)]
          (println (format "Add user \"%s\" to group \"%s\"" username subgroup-name))
          (add-user-to-group! admin-client realm-name subgroup-id (.getId created-user)))))))

(defn init!
  "Create a structure of keycloak entities (realm, clients, roles) and fill it with groups and users. Arguments are:
  * `admin-client`: [admin client's _Keycloak_ object](https://www.keycloak.org/docs-api/11.0/javadocs/org/keycloak/admin/client/Keycloak.html) obtained with:  `(keycloak.deployment/keycloak-client (keycloak.deployment/client-conf \"http://localhost:8090\" \"master\"  \"admin-cli\") admin-login admin-password)`
  * `data`: the configuration data with keys `realm`, `roles`, `groups` and `users` (see the documentation for more details https://cljdoc.org/d/keycloak-clojure/keycloak-clojure/1.18.0/doc/administrative-tasks#declarative-creation-of-keycloak-objects)
  * `infra-context`: a description of the keycloak infrastructure, the keys needed here is the :vault for secret export during clients creation
  * `opts`: with two boolean entries `dry-run?` and `apply-deletions?`, respectively not applying the detected steps for attaining the desired state in `data` and whether the steps should delete the entities in Keycloak not in that state (sort of a very strict mode)
  "
([^org.keycloak.admin.client.Keycloak admin-client data infra-context & [opts]]
 (when (or (nil? admin-client) (nil? data))
   (throw (ex-info "Admin client and/or realm config data can't be null")))
 (let [realm-name (get-in data [:realm :name])
       dry-run?   (:dry-run opts)]
   (when (not dry-run?)
     (init-realm!   admin-client (:realm data))
     (init-clients! admin-client realm-name (:clients data) infra-context)
     (init-roles!   admin-client realm-name (:roles data))
     (gen-users!    admin-client realm-name data))
   (reconciliation/reconciliate-groups!        admin-client realm-name (:groups data) opts)
   (reconciliation/reconciliate-users!         admin-client realm-name (:users data)  opts)
   (reconciliation/reconciliate-role-mappings! admin-client realm-name (:roles data)  (:users data) opts)
     ;(init-users!   admin-client realm-name (:users data))
   (when (not dry-run?)
     (println (format "Keycloak realm \"%s\" synchronized" realm-name)))
   data)))

(defn keycloak-auth-server-url [protocol host port]
  (str protocol "://" host ":" port "/auth"))

(defn process-args [{:keys [realm-config infra-context] :as args}]
  (let [{:keys [environment color applications vault keycloak secret-file]} infra-context
        {:keys [auth-server-url protocol host port]}                        (or keycloak args) ;either the params are in the keyclaok config file or each params is passed through a direct param
        login                                                               (or (:login keycloak)    (:login args)    (environ/env :login))
        password                                                            (or (:password keycloak) (:password args) (environ/env :password))
        auth-server-url                                                     (or (when (not-empty (:auth-server-url args)) (:auth-server-url args))
                                                                                (:auth-server-url keycloak)
                                                                                (environ/env :auth-server-url)
                                                                                (keycloak-auth-server-url protocol host port))
        dry-run?                                                            (or (:dry-run args) (environ/env :dry-run))
        apply-deletions?                                                    (or (:apply-deletions args) (environ/env :apply-deletions))
        processed-args                                                      {:auth-server-url               auth-server-url
                                                                             :login                         login
                                                                             :keycloak                      keycloak
                                                                             :password                      password
                                                                             :environment                   environment
                                                                             :color                         color
                                                                             :applications                  applications
                                                                             :vault-config                  vault
                                                                             :realm-config                  realm-config
                                                                             :infra-context                 infra-context
                                                                             :dry-run?                      dry-run?
                                                                             :apply-deletions?              apply-deletions?
                                                                             :resources-dir                 (:resources-dir args)
                                                                             :secret-export-dir             (or (get-in infra-context [:secret-file :export-dir]) (:secret-export-dir args))
                                                                             :secret-file-without-extension (or (get-in infra-context [:secret-file :name-without-extension]) (:secret-file-without-extension args))
                                                                             :secret-path                   (or (get-in infra-context [:secret-file :path]))
                                                                             }]
    (when (or (empty? auth-server-url) (empty? password) (empty? login) (nil? realm-config))
      (println "Usage: clj -m keycloak.starter <auth-server-url> <login> <password> <environment> <realm-config> <infra-context>" )
      (throw (ex-info "Usage: clj -m keycloak.starter <auth-server-url> <login> <password> <environment> <realm-config>" processed-args)))
    processed-args))

(defn dissoc-sensitive-data [config-data]
  ;remove password entry of all users
  (update config-data :users (fn [users] (mapv #(update % :password (fn [_] :XXXXXXXX)) users))))

(defn edn-resources-bindings [resources-dir]
  (when resources-dir
    (let [edn-files (utils/list-files resources-dir (fn [f] (= ".edn" (:ext (utils/parse-path f)))))]
      (into {} (map (fn [f]
                      (when f
                        (let [{:keys [name ext]} (utils/parse-path f)
                              edn-content  (when (and (utils/file-exists? f) (utils/file-not-empty? f))
                                             (edn/read (PushbackReader. (io/reader f))))]
                          (when edn-content
                            [(symbol name) (sci/new-var (symbol name) edn-content)])))) edn-files)))))

(defn init-cli! [args]
  (let [{:keys [infra-context resources-dir realm-config secret-export-dir secret-file-without-extension secret-path auth-server-url
                login password environment color applications vault-config keycloak dry-run? apply-deletions?] :as processed-args} (process-args args)]
    (let [admin-client (->  (deployment/client-conf auth-server-url "master" "admin-cli")
                            (deployment/keycloak-client login password))
          sci-environment   (sci/new-var 'environment environment)
          sci-color         (sci/new-var 'color color)
          sci-applications  (sci/new-var 'applications applications)
          sci-keycloak      (sci/new-var 'keycloak keycloak)
          sci-infra-context (sci/new-var 'infra-context infra-context)
          sci-bindings      {:bindings (merge {'environment   sci-environment
                                               'applications  sci-applications
                                               'keycloak      sci-keycloak
                                               'infra-context sci-infra-context
                                               'color         sci-color}
                                              (edn-resources-bindings resources-dir))}
          _                 (println "Execute SCI config with bindings of the resources: " (map str (utils/list-files resources-dir (fn [f] (= ".edn" (:ext (utils/parse-path f)))))))
          ;_                 (println (pr-str (clojure.pprint/pprint sci-bindings)))
          config-data       (sci/eval-string realm-config sci-bindings)]
      (when dry-run?
        (println "Dry run execution of Keycloak-clojure-starter!")
        (println "Infra-context used is:")
        (utils/pprint-to-stdout infra-context)
        ;(println "Keycloak configuration data resulting of script evaluation is:")
        ;(utils/pprint-to-stdout config-data)
        )
      (println (format "Keycloak init script target %s in env %s with %s realm(s)" auth-server-url (or environment "localhost") (count config-data)))
      (println (format "Login to %s realm, clientId %s with username %s" "master" "admin-cli" login))
      (if (map? config-data)
        (do
          (println (format "Sync realm %s with following configuration:" (get-in config-data [:realm :name])))
          ;(clojure.pprint/pprint (dissoc-sensitive-data config-data))
          (init! admin-client config-data infra-context processed-args))
        (when (or (vector? config-data) (seq? config-data))
          (doseq [realm-data config-data]
            (println (format "Sync realm %s with following configuration:" (get-in realm-data [:realm :name])))
            ;(clojure.pprint/pprint (dissoc-sensitive-data realm-data))
            (init! admin-client realm-data infra-context processed-args))))
      (shutdown-agents))))

(def init-cli-opts
  [{:as "URL of the Keycloak Authentication Server"
    :option "auth-server-url"
    :type :string}

   {:as "Username of an administrator in the master realm"
    :option "login"
    :type :string}

   {:as "Password of an administrator in the master realm"
    :option "password"
    :type :string}

   {:as "Name of the environment for which the init is done, has no impact but is passed during evaluation of the config file"
    :option "environment"
    :type :string}

   {:as "Secret directory output"
    :option "secret-export-dir"
    :default "/etc/keycloak"
    :type :string}

   {:as "Secret file output"
    :option "secret-file-without-extension"
    :default ".keycloak-secrets"
    :type :string}

   {:as "Dry run - print configuration data after evaluating realm-config with supplied realm-config and EDN files in resources-dir"
    :option "dry-run"
    :default false
    :type :flag}

   {:as "Apply deletions - when conputing the necessary steps to reach the desired state given as input to the starter, should the process delete the current entities (users and groups) already existing in Keycloak"
    :option "apply-deletions"
    :default false
    :type :flag}

   {:as "An EDN file containing the following keys:
         - :environment: a string of the target environment, no impact but is passed during evaluation of the realm config file\n
         - :color: a string of a \"color\" for discriminating the target (can be omitted), no impact but is passed during evaluation of the realm config file\n
         - :applications: a vector of map with :name, :version and clients-uris key, clients-uris is a map with 4 keys: base, root, redirects and origins,, no impact but is passed during evaluation of the realm config file\n
         - :keycloak: a map with :protocol, :host, :port, :login, :password,
         - :secret-file: a map with :export-dir, :name-without-extension, :path a vector of keyword
         - :vault: a map with :protocol :host :port :token :mount :path :vendor (with :vendor between hashicorp and google)\n
         if present it overrides all the other options"
    :option "infra-context"
    :type :ednfile}

   {:as "A clj file that is evaluated with SCI (https://github.com/borkdude/sci) that must return a map with the keys: realm, clients, roles, groups and users"
    :option "realm-config"
    :type :slurp}

   {:as "Dir where resources are found and injected into the config file"
    :option "resources-dir"
    :default "/etc/keycloak"
    :type :string}])

(def CLI_CONFIG
  {:command "init"
   :description "Keycloak-clojure starter's init function to initialize a realm with clients, roles, etc."
   :version meta/version
   :runs init-cli!
   :opts init-cli-opts})

(defn -main [& args]
  (run-cmd args CLI_CONFIG))

(comment
  {:keycloak {:protocol "KEYCLOAK_PROTOCOL"
              :host     "KEYCLOAK_HOST"
              :port     "KEYCLOAK_PORT"
              :login    "KEYCLOAK_LOGIN"
              :password "KEYCLOAK_PASSWORD"
              :auth-url "KEYCLOAK_AUTH_URL"}}
  {:environment "ENVIRONMENT"
   :color       "ENVIRONMENT"
   :applications [{:name    "APPLICATION_NAME"
                   :version "APPLICATION_VERSION"
                   :clients-uris {:root "ROOT" :base "BASE" :redirects [""] :origins [""]}}]
   :keycloak    {:protocol "KEYCLOAK_PROTOCOL"
                 :host     "KEYCLOAK_HOST"
                 :port     "KEYCLOAK_PORT"
                 :login    "KEYCLOAK_LOGIN"
                 :password "KEYCLOAK_PASSWORD"
                 :auth-url "KEYCLOAK_AUTH_URL"}
   :vault       {:protocol "VAULT_PROTOCOL"
                 :host     "VAULT_HOST"
                 :port     "VAULT_PORT"
                 :token    "VAULT_TOKEN"
                 :mount    "VAULT_SECRET_ENGINE"
                 :path     "VAULT_SECRET_PATH"}}

  (def config-file "resources/realm-config.clj")
  (def config-resource "config.clj")
  (def environment  (sci/new-var 'environment "staging"))
  (def applications (sci/new-var 'applications [{:name "diffusion" :version "1.2.3" :clients-uris {:root "https://www.example.com"
                                                                                                   :base "/"
                                                                                                   :redirects ["https://www.example.com/*"]
                                                                                                   :origins ["https://www.example.com"]}}]))
  (def color        (sci/new-var 'color       "red"))
  (def evaluated-realm-data (sci/eval-string (slurp config-file) {:bindings {'environment  environment
                                                                             'applications applications
                                                                             'color        color}}))

  (def admin-login "admin")
  (def admin-password "secretadmin")
  (def auth-server-url "http://localhost:8090/auth")

  (def integration-test-conf (deployment/client-conf "http://localhost:8090/auth" "master" "admin-cli"))
  (def admin-client (deployment/keycloak-client integration-test-conf admin-login admin-password))

  (def static-realm-data [{:realm {:name "example2",
                                   :themes
                                   {:defaultLocale "fr",
                                    :emailTheme "keycloak",
                                    :internationalizationEnabled true,
                                    :adminTheme nil,
                                    :supportedLocales #{"en" "fr"},
                                    :loginTheme "keycloak",
                                    :accountTheme "keycloak"},
                                   :login {:resetPasswordAllowed true, :bruteForceProtected true, :rememberMe true},
                                   :user-admin {:username "admin" :firstname "John" :lastname "Doe" :email "admin@example.com"}
                                   :smtp {:starttls true, :password "", :port 587, :auth true, :host "smtp.eu.mailgun.org", :replyTo "example", :from "admin@example.com", :user "postmaster@mg.example.com"},
                                   :tokens {:ssoSessionIdleTimeoutRememberMe 172800, :ssoSessionMaxLifespanRememberMe 172800}},
                           :roles #{"org-admin" "example-admin" "group-admin" "api-consumer" "employee" "manager"},
                           :clients [{:name "api-client",
                                      :redirect-uris ["https://myapp.staging.example.com/*"],
                                      :base-url "https://myapp.staging.example.com",
                                      :web-origins ["https://myapp.staging.example.com"],
                                      :public? true,
                                      :root-url "https://myapp.staging.example.com"}
                                     {:name "myfrontend",
                                      :redirect-uris ["https://myapp.staging.example.com/*"],
                                      :base-url "https://myapp.staging.example.com",
                                      :web-origins ["https://myapp.staging.example.com"],
                                      :public? true,
                                      :root-url "https://myapp.staging.example.com"}
                                     {:name "mybackend",
                                      :redirect-uris ["http://localhost:3449/*"],
                                      :web-origins ["http://localhost:3449"],
                                      :public? false}],
     :generated-users-by-group-and-role 2,
     :groups [{:name "test"} {:name "Example", :subgroups [{:name "IT"} {:name "Sales"} {:name "Logistics"}]}],
     :users [{:email "britt@hotmail.com", :last-name "Britt", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "s0w5roursg3i284", :username "britt", :first-name "James", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "charlotte.peters@gmail.com", :last-name "Peters", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "7o9573867", :username "cpeters", :first-name "Charlotte", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "skylar91@yahoo.com", :last-name "Nielsen", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "02e9nx6y6", :username "snielsen", :first-name "Skylar", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "brayden441@me.com", :last-name "Pratt", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "q1435mle0a4sez5u7vp", :username "brayden", :first-name "Brayden", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "torres@yahoo.com", :last-name "Torres", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "nvvx2brthnxt62hmma", :username "torres", :first-name "Makayla", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "alyssa@hotmail.com", :last-name "Cantrell", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "0db8ck", :username "alyssa529", :first-name "Alyssa", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "luke@hotmail.com", :last-name "Graves", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "009y77udi1", :username "luke222", :first-name "Luke", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "zachary660@yahoo.com", :last-name "Lynn", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "fq24gfjrpdhaq3z", :username "zachary734", :first-name "Zachary", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "landon@me.com", :last-name "Odom", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "6k421x21x8", :username "landon", :first-name "Landon", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "angel402@gmail.com", :last-name "Sloan", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "99t9myhvn", :username "angel", :first-name "Angel", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "kayden.griffin@gmail.com", :last-name "Griffin", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "8zk7whnoic", :username "kayden278", :first-name "Kayden", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "evan@yahoo.com", :last-name "Patrick", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "vt5n7ni5zfsbe0abx", :username "patrick", :first-name "Evan", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "fox@gmail.com", :last-name "Fox", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "u8cj0m", :username "bentley", :first-name "Bentley", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "genesis@yahoo.com", :last-name "Lindsey", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "7bo1fvm98gahhgwiv", :username "glindsey", :first-name "Genesis", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:email "xavier765@yahoo.com", :last-name "Mccall", :group "Example", :realm-roles ["employee" "manager" "example-admin" "org-admin" "group-admin" "api-consumer"], :password "s0xha8r7w9w", :username "mccall", :first-name "Xavier", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}
             {:last-name "Carter", :group "Example", :realm-roles ["employee" "manager" "example-admin"], :password "secretstuff", :username "testaccount", :first-name "Bob", :attributes {"org-ref" ["Example"]}, :in-subgroups ["IT"]}]}])

  (init-realm! admin-client static-realm-data)


  (def base-domain  (sci/new-var 'base-domain "example.com"))
  (def environment  (sci/new-var 'environment "staging"))
  (def keycloak     (sci/new-var 'keycloak    {:realm "myrealm"}))
  (def applications (sci/new-var 'applications [{:name "diffusion" :version "1.2.3"}]))
  (def color        (sci/new-var 'color       "red"))

  (def config-data  (sci/eval-string realm-config {:bindings {'base-domain  base-domain
                                                              'environment  environment
                                                              'keycloak     keycloak
                                                              'applications applications
                                                              'color        color}}))
  )
