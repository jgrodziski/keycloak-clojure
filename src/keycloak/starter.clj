(ns keycloak.starter
  (:require
   [clojure.string :as str]
   [me.raynes.fs :as fs]
   [sci.core :as sci]
   [cli-matic.core :refer [run-cmd]]
   [clj-yaml.core :as yaml]
   [jsonista.core :as json]
   [talltale.core :as talltale :refer :all]

   [keycloak.admin :refer :all]
   [keycloak.user :as user]
   [keycloak.deployment :as deployment :refer [keycloak-client client-conf]]
   [keycloak.meta :as meta]
   [keycloak.vault :as vault])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn export-json [export-dir export-file-without-extension client-id key secrets]
  (let [secrets-file (clojure.java.io/file export-dir (str export-file-without-extension ".json"))]
    (fs/touch secrets-file)
    (json/write-value secrets-file secrets)
    (println (format "Secret of client \"%s\" exported in files %s at key [:keycloak %s]" client-id (str secrets-file) key))))

(defn export-yaml [export-dir export-file-without-extension client-id key secrets]
  (let [secrets-file (clojure.java.io/file export-dir (str export-file-without-extension ".yml"))]
    (fs/touch secrets-file)
    (spit secrets-file (yaml/generate-string secrets))
    (println (format "Secret of client \"%s\" exported in files %s at key [:keycloak %s]" client-id (str secrets-file) key))))

(defn- env-var-or-dir? [dir]
  (if (System/getenv dir)
    (System/getenv dir)
    dir))

(defn export-secret-in-files! [^org.keycloak.admin.client.Keycloak keycloak-client export-dir secret-file-without-extension realm-name client-id key]
  (println (format "Secret of client \"%s\" will be exported in files %s in directory %s" client-id (str secret-file-without-extension ".edn|json|yml") export-dir))
  (let [secret              (get-client-secret keycloak-client realm-name client-id)
        secret-file         (or secret-file-without-extension ".keycloak-secrets")
        resolved-export-dir (env-var-or-dir? export-dir)
        secrets-file        (clojure.java.io/file resolved-export-dir (str secret-file ".edn"))
        _                   (fs/mkdir resolved-export-dir)
        _                   (fs/touch secrets-file)
        secrets             (-> (or (clojure.edn/read-string (slurp secrets-file)) {})
                         (assoc-in [:keycloak key] secret))]
    (println (format "Secret of client \"%s\" exported in files %s at key [:keycloak %s]" client-id (str secrets-file) key))
    (spit secrets-file secrets)
    (export-json resolved-export-dir secret-file client-id key secrets)
    (export-yaml resolved-export-dir secret-file client-id key secrets)
    secrets-file))

(defn export-secret-in-vault!
  "in infra-config, [:vault :path] is a string with placeholders as: %1$s is the environment, %2$s is the color, %3$s is the base-domain, %4$s is the client-id (client-id depends of your realm-config.clj code)"
  [^org.keycloak.admin.client.Keycloak keycloak-client {:keys [vault environment color base-domain] :as infra-config} realm-name client-id]
  (let [secret                                  (get-client-secret keycloak-client realm-name client-id)
        {:keys [protocol host port path token]} vault
        vault-url                               (vault/vault-url protocol host port)
        vault-path                              (format path environment color base-domain client-id)]
    (println (format "Secret of client \"%s\" will be exported in hashicorp vault at url %s and path %s" client-id vault-url vault-path))
    (vault/write-keycloak-client-secret! vault-url token vault-path secret)))

(defn create-mappers! [^org.keycloak.admin.client.Keycloak keycloak-client realm-name client-id]
  (println "Create protocol mappers for client" client-id)
  (create-protocol-mapper! keycloak-client realm-name client-id
                           (group-membership-mapper "group-mapper" "group"))
  (create-protocol-mapper! keycloak-client realm-name client-id
                           (user-attribute-mapper "org-ref-mapper" "org-ref" "org-ref" "String")))

(defn generate-user [username-creator-fn role group subgroup idx & opts]
  (merge (talltale/person)
         {:username (apply username-creator-fn role group subgroup idx opts)
          :password "password"}))

(defn init-realm! [^org.keycloak.admin.client.Keycloak admin-client {:keys [name themes login tokens smtp] :as realm-data}]
  (println (format "Will create realm \"%s\"" name))
  (try (create-realm! admin-client name themes login tokens smtp)
       (println (format "Realm \"%s\" created" name))
       (catch javax.ws.rs.ClientErrorException cee
         (when (= (-> cee (.getResponse) (.getStatus)) 409)
           (update-realm! admin-client name themes login tokens smtp)
           (println (format "Realm \"%s\" updated" name))))
       (catch Exception e (println "Can't create Realm" e)(get-realm admin-client name))))

(defn init-clients! [^org.keycloak.admin.client.Keycloak admin-client realm-name clients-data infra-config secret-export-dir secret-file-without-extension]
  (doseq [{:keys [name public? redirect-uris web-origins] :as client-data} clients-data]
    (let [client (client client-data)
          client-id name]
      (println (format "Create client \"%s\" in realm %s" name realm-name))
      (create-client! admin-client realm-name client)
      (println (format "Client \"%s\" created in realm %s" name realm-name))
      (create-mappers! admin-client realm-name name)
      (when secret-export-dir
        (export-secret-in-files! admin-client secret-export-dir secret-file-without-extension realm-name client-id (keyword name)))
      (when (:vault infra-config)
        (export-secret-in-vault! admin-client infra-config realm-name client-id))))
  (println (format "%s Clients created in realm %s" (count clients-data) realm-name)))

(defn init-roles! [^org.keycloak.admin.client.Keycloak admin-client realm-name roles-data]
  (doseq [role roles-data]
    (try (create-role! admin-client realm-name role) (catch Exception e (get-role admin-client realm-name role)))))

(defn init-generated-users! [^org.keycloak.admin.client.Keycloak admin-client realm-name data ^org.keycloak.representations.idm.GroupRepresentation subgroup]
  (doseq [role (:roles data)]
    (doseq [i (range 1 (inc (:generated-users-by-group-and-role data)))]
      (let [user (generate-user (:username-creator-fn data) role name subgroup i)
            created-user (user/create-or-update-user! admin-client realm-name user [role] nil)]
        (println (format "      User \"%s\" created with realm-roles %s and client-roles %s" (:username user) [role] nil))
        (println (format "      Add user \"%s\" to group \"%s\"" (:username user) (.getName subgroup)))
        (add-user-to-group! admin-client realm-name (.getId subgroup) (.getId created-user))))))

(defn init-groups-and-gen-users! [^org.keycloak.admin.client.Keycloak admin-client realm-name {:keys [groups] :as data}]
  (doseq [{:keys [name subgroups]} groups]
    (let [^org.keycloak.representations.idm.GroupRepresentation group (create-group! admin-client realm-name name)]
      (println (format "Group \"%s\" created" name))
      (doseq [[idx {subgroup-name :name attributes :attributes}] (map-indexed vector subgroups)]
        (let [^org.keycloak.representations.idm.GroupRepresentation subgroup (create-subgroup! admin-client realm-name (.getId group) subgroup-name attributes)]
          (println (format "   Subgroup \"%s\" created in group \"%s\"" subgroup-name name))
          (init-generated-users! admin-client realm-name data subgroup))))))

(defn init-users! [^org.keycloak.admin.client.Keycloak admin-client realm-name users-data]
  (doseq [{:keys [username] :as user} users-data]
    (let [created-user (user/create-or-update-user! admin-client realm-name user (:realm-roles user) (:client-roles user))]
      (println (format "User \"%s\" created with realm-roles %s and client-roles %s" username (:realm-roles user) (:client-roles user)))
      (doseq [subgroup-name (:in-subgroups user)]
        (let [subgroup-id (get-subgroup-id admin-client realm-name (get-group-id admin-client realm-name (:group user)) subgroup-name)]
          (println (format "Add user \"%s\" to group \"%s\"" username subgroup-name))
          (add-user-to-group! admin-client realm-name subgroup-id (.getId created-user)))))))

(defn init!
  "Create a structure of keycloak objects (realm, clients, roles) and fill it with groups and users"
  ([^org.keycloak.admin.client.Keycloak admin-client data]
   (init! admin-client data nil))
  ([^org.keycloak.admin.client.Keycloak admin-client data infra-config secret-export-dir export-file-without-extension]
   (when (or (nil? admin-client) (nil? data))
     (throw (ex-info "Admin client and/or realm config data can't be null")))
   (let [realm-name (get-in data [:realm :name])]
     (init-realm!                admin-client (:realm data))
     (init-clients!              admin-client realm-name (:clients data) infra-config secret-export-dir export-file-without-extension)
     (init-roles!                admin-client realm-name (:roles data))
     (init-groups-and-gen-users! admin-client realm-name data)
     (init-users!                admin-client realm-name (:users data))
     (println (format "Keycloak realm \"%s\" initialized" realm-name))
     data)))

(defn keycloak-auth-server-url [protocol host port]
  (str protocol "://" host ":" port "/auth"))

(defn process-args [{:keys [realm-config infra-config] :as args}]
  (let [{:keys [environment color base-domain applications vault keycloak]}           infra-config
        {:keys [auth-server-url login password protocol host port secret-export-dir]} (or keycloak args);either the params are in the keyclaok config file or each params is passed through a direct param
        auth-server-url (or auth-server-url (keycloak-auth-server-url protocol host port))
        processed-args {:auth-server-url auth-server-url
                        :login login
                        :password password
                        :environment environment
                        :base-domain base-domain
                        :color color
                        :applications applications
                        :vault-config vault
                        :realm-config realm-config
                        :infra-config infra-config
                        :secret-export-dir (or (get-in infra-config [:keycloak :secret-export-dir]) (:secret-export-dir args))
                        :secret-file-without-extension (or (get-in infra-config [:keycloak :secret-file-without-extension]) (:secret-file-without-extension args))}]
    (when (or (nil? auth-server-url) (nil? password) (nil? login) (nil? base-domain) (nil? realm-config))

      (println "Usage: clj -m keycloak.starter <auth-server-url> <login> <password> <environment> <base-domain> <realm-config> <infra-config>" )
      (throw (ex-info "Usage: clj -m keycloak.starter <auth-server-url> <login> <password> <environment> <base-domain> <realm-config>" processed-args)))
    processed-args))

(defn init-cli! [args]
  (let [{:keys [infra-config realm-config secret-export-dir secret-file-without-extension auth-server-url login password environment color base-domain applications]} (process-args args)]
    (let [admin-client (-> (deployment/client-conf auth-server-url "master" "admin-cli")
                           (deployment/keycloak-client login password))
          sci-base-domain  (sci/new-var 'base-domain base-domain)
          sci-environment  (sci/new-var 'environment environment)
          sci-color        (sci/new-var 'color color)
          sci-applications (sci/new-var 'applications applications)
          config-data  (sci/eval-string realm-config {:bindings {'base-domain  sci-base-domain
                                                                 'environment  sci-environment
                                                                 'applications sci-applications
                                                                 'color        sci-color}})]
      (println (format "Keycloak init script target %s in env %s with %s realm(s)" auth-server-url (or environment "localhost" ) (count config-data)))
      (if (map? config-data)
        (do
          (println (format "Init realm %s" (get-in config-data [:realm :name])))
          (init! admin-client config-data infra-config secret-export-dir secret-file-without-extension))
        (when (or (vector? config-data) (seq? config-data))
          (doseq [realm-data config-data]
            (println (format "Init realm %s" (get-in realm-data [:realm :name])))
            (init! admin-client realm-data infra-config secret-export-dir secret-file-without-extension))))
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

   {:as "Base domain of the environment for which the init is done, has no impact but is passed during evaluation of the config file"
    :option "base-domain"
    :type :string}

   {:as "Secret directory output"
    :option "secret-export-dir"
    :default "/etc/keycloak"
    :type :string}

   {:as "Secret file output"
    :option "secret-file-without-extension"
    :default ".keycloak-secrets"
    :type :string}

   {:as "An EDN file containing the following keys:
         - :environment: a string of the target environment, no impact but is passed during evaluation of the realm config file\n
         - :color: a string of a \"color\" for discriminating the target (can be omitted), no impact but is passed during evaluation of the realm config file\n
         - :base-domain: a string for the DNS base domain of the target, no impact but is passed during evaluation of the realm config file\n
         - :applications: a vector of map with :name and :version key, no impact but is passed during evaluation of the realm config file\n
         - :keycloak: a map with :protocol, :host, :port, :login, :password, :base-domain, :secret-export-dir, :secret-file-without-extension\n
         - :vault: a map with :protocol :host :port :token :mount :path\n
         if present it overrides all the other options"
    :option "infra-config"
    :type :ednfile}

   {:as "A clj file that is evaluated with SCI (https://github.com/borkdude/sci) that must return a map with the keys: realm, clients, roles"
    :option "realm-config"
    :type :slurp}])

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
   :base-domain "BASE_DOMAIN"
   :application {:name    "APPLICATION_NAME"
                 :version "APPLICATION_VERSION"}
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
  (def base-domain  (sci/new-var 'base-domain "example.com"))
  (def environment  (sci/new-var 'environment "staging"))
  (def applications (sci/new-var 'applications [{:name "diffusion" :version "1.2.3"}]))
  (def color        (sci/new-var 'color       "red"))
  (def evaluated-realm-data (sci/eval-string (slurp config-file) {:bindings {'base-domain  base-domain 'environment  environment 'applications applications 'color        color}}))

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
  (def applications (sci/new-var 'applications [{:name "diffusion" :version "1.2.3"}]))
  (def color        (sci/new-var 'color       "red"))

  (def config-data  (sci/eval-string realm-config {:bindings {'base-domain  base-domain
                                                              'environment  environment
                                                              'applications applications
                                                              'color        color}}))
  )
