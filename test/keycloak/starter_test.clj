(ns keycloak.starter-test
  (:require [keycloak.starter :as starter]
            [keycloak.admin :refer :all]
            [keycloak.user :as user]
            [keycloak.utils :as utils :refer [auth-server-url]]
            [keycloak.deployment :as deployment :refer [keycloak-client client-conf]]
            [clojure.test :as t :refer :all]))

(def infra-config {:environment "automatedtest"
                   :color       "blue"
                   :base-domain "example.com"
                   :applications {:name    "myapp"
                                  :version "1.4.12"}
                   :keycloak     {:protocol "http"
                                  :host     "localhost"
                                  :port     "8090"
                                  :login    "admin"
                                  :password "secretadmin"}
                   :vault        {:protocol "http"
                                  :host     "localhost"
                                  :port     "1234"
                                  :token    "myroot"
                                  :mount    "secret"
                                  ;;%1$s is the environment, %2$s is the color, %3$s is the base-domain, %4$s is the client-id (so depends of your realm-config.clj code)
                                  :path     "/env/%1$s/keycloak/clients/%4$s"}})

(def integration-test-conf (deployment/client-conf (utils/auth-server-url infra-config) "master" "admin-cli"))
(def admin-client (deployment/keycloak-client integration-test-conf (get-in infra-config [:keycloak :login]) (get-in infra-config [:keycloak :password])))


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
  (doseq [realm-data static-realm-data]
    (starter/init-realm! admin-client realm-data infra-config nil)))

