(ns keycloak.starter-test
  (:require
   [clojure.test :as t :refer :all]
   [clojure.string :as str]
   [clojure.pprint :as pp]

   [sci.core :as sci]
   [testit.core :refer :all]

   [keycloak.starter :as starter]
   [keycloak.admin :refer :all]
   [keycloak.user :as user]
   [keycloak.utils :as utils :refer [auth-server-url]]
   [keycloak.deployment :as deployment :refer [keycloak-client client-conf]]
   [keycloak.vault.protocol :as vault :refer [Vault write-secret! read-secret]]
   [keycloak.vault.hashicorp :as hashicorp-vault]
   [keycloak.vault.google :as google-vault]
   [keycloak.reconciliation :as reconciliation]
   [clojure.pprint :as pp]))

(def infra-context {:environment "automatedtest"
                    :color       "blue"
                    :base-domain "example.com"
                    :applications {:name    "myapp"
                                   :version "1.4.12"}
                    :keycloak     {:protocol "http"
                                   :host     "localhost"
                                   :port     "8090"
                                   :login    "admin"
                                   :password "secretadmin"}})

(def hashicorp-vault {:protocol "http"
                      :host     "localhost"
                      :port     8200
                      :token    "myroot"
                      :mount    "secret"
                      :vendor   :hashicorp
                      ;;%1$s is the environment, %2$s is the color, %3$s is the base-domain, %4$s is the client-id (so depends of your realm-config.clj code)
                      :path     "/env/%1$s/keycloak/clients/%4$s"})

(def google-sm-vault {:project-id  "adixe-1168"
                      :vendor      :gcp-sm
                      :secret-id   "secret-name-test"
                      :replication-policy "automatic"})

(def integration-test-conf (deployment/client-conf (utils/auth-server-url infra-context) "master" "admin-cli"))
(def admin-client (deployment/keycloak-client integration-test-conf (get-in infra-context [:keycloak :login]) (get-in infra-context [:keycloak :password])))

(def static-realm-data [{:realm {:name "example2",
                                 :themes
                                 {:defaultLocale "fr",
                                  :emailTheme "keycloak",
                                  :internationalizationEnabled true,
                                  :adminTheme "keycloak",
                                  :supportedLocales #{"en" "fr"},
                                  :loginTheme "keycloak",
                                  :accountTheme "keycloak"},
                                 :login {:resetPasswordAllowed true, :bruteForceProtected true, :rememberMe true},
                                 :smtp {:starttls true, :password "", :port 587, :auth true, :host "smtp.eu.mailgun.org", :replyTo "example", :from "admin@example.com", :user "postmaster@mg.example.com"},
                                 :tokens {:ssoSessionIdleTimeoutRememberMe (int 172800,) :ssoSessionMaxLifespanRememberMe (int 172800)}},
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
                         :username-creator-fn (fn [role group subgroup idx & opts] (str (str group) "-" (subs (str role) 0 3) "-" idx))
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

(deftest ^:integration vault-test
   (testing "Hashicorp vault"
    )
  (testing "Google secret manager "
    ;;this test needs the environment variable GOOGLE_APPLICATION_CREDENTIALS defined with value as the path of the JSON file that contains your service account key.
    (let [realm-name (str "test-realm-" (rand-int 1000))]
      (doseq [realm-data static-realm-data]
        (starter/init! admin-client (assoc-in realm-data [:realm :name] realm-name) (assoc infra-context :vault google-sm-vault))
        (let [vault (google-vault/->GoogleSecretManager (:project-id google-sm-vault))]
          (doseq [{:keys [name public?] :as client} (:clients realm-data)]
            (let [secret-value (vault/read-secret vault name)]
              ;(println "secret-value" secret-value)
              (when (not public?)
                (fact secret-value => (comp not str/blank?)))))))
      (testing "realm deletion"
        (delete-realm! admin-client realm-name)
        (is (thrown? javax.ws.rs.NotFoundException (get-realm admin-client realm-name)))
        ))))

(deftest ^:integration starter-reconciliation-test
  (testing "Applying a full configuration to an empty realm"
    (let [realm-name (str "reconciliation-test-realm" (rand-int 1000))
          config     (first (clojure.edn/read-string (slurp "resources/config-init-realm.edn")))]
      (starter/init! admin-client (assoc-in config [:realm :name] realm-name) infra-context {:dry-run? false :apply-deletions? true})
      (println "roles")
      (pp/pprint (:roles config))
      (testing "Getting a plan should now be empty"
        (let [users-plan         (reconciliation/users-plan admin-client realm-name (:users config))
              groups-plan        (reconciliation/groups-plan admin-client realm-name (:groups config))
              role-mappings-plan (reconciliation/role-mappings-plan admin-client realm-name (:roles config) (utils/associate-by :username (:users config)))]
          (facts (get users-plan :users/additions) => empty?
                 (get users-plan :users/deletions) => empty?
                 (get users-plan :users/updates)   => empty?
                 (get groups-plan :groups/additions) => empty?
                 (get groups-plan :groups/deletions) => empty?
                 (get role-mappings-plan :realm-role-mappings/additions) => empty?
                 (get role-mappings-plan :realm-role-mappings/deletions) => empty?)))
      (testing "apply again the same configuration"
        (starter/init! admin-client (assoc-in config [:realm :name] realm-name) infra-context {:dry-run? true :apply-deletions? true}))
      (testing "realm deletion"
        (delete-realm! admin-client realm-name)
        (is (thrown? javax.ws.rs.NotFoundException (get-realm admin-client realm-name)))))))

(deftest config-test
  (let [environment  (sci/new-var 'environment  "staging")
        applications (sci/new-var 'applications [{:name "myapp"
                                                  :version "1.2.3"
                                                  :clients-uris [{:client-id "api-client"
                                                                  :public? true
                                                                  :root "https://api.example.com"
                                                                  :base "/"
                                                                  :redirects ["https://api.example.com/*"]
                                                                  :origins ["https://api.example.com"]}
                                                                 {:client-id "backend"
                                                                  :public? false
                                                                  :root "https://backend.example.com"
                                                                  :base "/"
                                                                  :redirects ["https://backend.example.com/*"]
                                                                  :origins ["https://backend.example.com"]}]}])
        color        (sci/new-var 'color        "red")
        config-code  (slurp "resources/realm-config-clients.clj")
        config-data  (sci/eval-string config-code {:bindings {'environment  environment
                                                              'applications applications
                                                              'color        color}})]
    (fact config-data => [{:realm {:name "example2"},
                           :clients [{:name "api-client" :redirect-uris ["https://api.example.com/*"], :base-url "/" :web-origins ["https://api.example.com"], :public? true, :root-url "https://api.example.com"}
                                     {:name "backend" :redirect-uris ["https://backend.example.com/*"], :base-url "/" :web-origins ["https://backend.example.com"], :public? false, :root-url "https://backend.example.com"}]}])))
