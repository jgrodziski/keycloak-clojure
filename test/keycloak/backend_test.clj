(ns keycloak.backend-test
  (:require
   [clojure.test :as t :refer [deftest testing is ]]
   [clojure.tools.logging :as log :refer [info]]

   [byte-streams :as b]
   [ring.mock.request :as ring-test]

   [keycloak.backend :as kc-backend]
   [keycloak.authn :as kc-authn]
   [keycloak.deployment :as kc-deployment]
   [keycloak.admin :as kc-admin :refer :all]
   [keycloak.user :as kc-user]))

(def LOGIN "admin")
(def PWD "password")
(def AUTH_SERVER_URL "http://localhost:8080")
;(def AUTH_SERVER_URL "http://login.default.minikube.devmachine/auth")
(def ADMIN_REALM "master")
(def ADMIN_CLIENT_ID "admin-cli")

(def kc-admin-client (kc-deployment/keycloak-client
                      (kc-deployment/client-conf AUTH_SERVER_URL ADMIN_REALM ADMIN_CLIENT_ID ) LOGIN PWD))

(defn access-token
  ([]
   (access-token LOGIN PWD))
  ([login password]
   ;(kc-authn/authenticate AUTH_SERVER_URL ADMIN_REALM ADMIN_CLIENT_ID login password)
   ;;TODO define a user realm in the test fixture
   ;(kc-authn/authenticate AUTH_SERVER_URL USER_REALM USER_CLIENT_ID_PUBLIC login password)
   ))

(defn headers-with-token-as-header
  ([] (headers-with-token-as-header {} (access-token)))
  ([headers token]
   {:headers (merge {"content-type" "application/transit+json"
                     "accept"       "application/transit+json"}
                    headers
                    (kc-authn/auth-header token))}))

(defn headers-with-token-as-cookie
  ([] (headers-with-token-as-cookie {} (access-token)))
  ([headers token]
   {:headers (merge {"content-type" "application/transit+json"
                     "accept"       "application/transit+json"}
                    headers
                    (kc-authn/auth-cookie token))}))

(defn yada-test-context [token]
  {:request (request-for :get "/protected" (headers-with-token-as-header {} token))})

(def EXPIRED_TOKEN_BAD_REALM {:access_token
 "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJxYnIwakNmanZXRXJfV0tkT1FSVzdkTkNiZmZHeXZQaVROTVA2ZjdYSVpZIn0.eyJleHAiOjE2MTM3NTk5MTAsImlhdCI6MTYxMzc1OTYxMCwianRpIjoiNTdmZjFmNjktNDk2ZC00MTA5LWI1ZTctZGFlYTk1NDY4Mzg0IiwiaXNzIjoiaHR0cDovL2xvZ2luLmRlZmF1bHQubWluaWt1YmUuZGV2bWFjaGluZS9hdXRoL3JlYWxtcy9lbGVjdHJlIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6ImRkMTdlODkzLTFmYWYtNDBjMi04MzA0LTFiZjI1NjMxOTkxOCIsInR5cCI6IkJlYXJlciIsImF6cCI6ImRpZmZ1c2lvbi1mcm9udGVuZCIsInNlc3Npb25fc3RhdGUiOiI3MzgzMGM1Mi03YWE2LTRmMWYtOTFmZS0xMWQ1NzllMDZkZmEiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbImh0dHA6Ly9sb2NhbGhvc3Q6MzAwMCJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsib3JnLWFkbWluIiwiYXBpLXJlY2hlcmNoZS1saWJyZSIsIm1hbmFnZXIiLCJncm91cC1hZG1pbiIsImVsZWN0cmUtYWRtaW4iLCJvZmZsaW5lX2FjY2VzcyIsImFwaS1hbGltZW50YXRpb24tdW5pdGFpcmUiLCJhcGktYWxpbWVudGF0aW9uIiwiYXBpLWFsaW1lbnRhdGlvbi1pbml0IiwidW1hX2F1dGhvcml6YXRpb24iLCJlbXBsb3llZSIsImFwaS1yZWNoZXJjaGUtY2libGVlIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJlbWFpbCBwcm9maWxlIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJvcmctcmVmIjoiZWxlY3RyZU5HIiwibmFtZSI6IkrDqXLDqW1pZSBHUk9EWklTS0kiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJqZ3JvZHppc2tpIiwiZ2l2ZW5fbmFtZSI6IkrDqXLDqW1pZSIsImZhbWlseV9uYW1lIjoiR1JPRFpJU0tJIiwiZW1haWwiOiJqZ3JvZHppc2tpQGVsZWN0cmUuY29tIiwiZ3JvdXAiOlsiL2VsZWN0cmVORy9pdCJdfQ.by7EFdx2TSVg7Zmd2EtAUD0eUKIgQvCoDvETNqN-qrbWCLVEc1wpCOhXp1DPa8W14p6xTIhgkCxt000hSeKE9YobG8W9GdxBZTWHtbG8L6YtCePW4pW9MF-YxAcsclOhAXlCukTXAlozz6RXV6byZqgL9HVvfVSxQskdoJd2zXGQZWCgpvSnWcpMkVrrXIXD-9EfkpwMCr9OHYKe50YRax5RjH72fY7m2WIpZyeaS6ZNi_Ud5nxTRFJHBhhRARLltjaUpB_Guv-6TGYDI46jNCozgJMlYiJkaTOy1o3s10jVowqXNxxaCNQP1zOX7Swri6UCkleuhiG4AOxA1XjRcg",
 :expires_in 300,
 :refresh_expires_in 1800,
 :refresh_token
 "eyJhbGciOiJIUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJmZTU5OTg0NC0zYzQwLTRlN2ItOGI3NC1mZTY1ZmRjYzU2ZGYifQ.eyJleHAiOjE2MTM3NjE0MTAsImlhdCI6MTYxMzc1OTYxMCwianRpIjoiNGQxNDNlZmUtNDE3MC00MmNiLWE3ZTAtZDczNDQ2N2JiNjRiIiwiaXNzIjoiaHR0cDovL2xvZ2luLmRlZmF1bHQubWluaWt1YmUuZGV2bWFjaGluZS9hdXRoL3JlYWxtcy9lbGVjdHJlIiwiYXVkIjoiaHR0cDovL2xvZ2luLmRlZmF1bHQubWluaWt1YmUuZGV2bWFjaGluZS9hdXRoL3JlYWxtcy9lbGVjdHJlIiwic3ViIjoiZGQxN2U4OTMtMWZhZi00MGMyLTgzMDQtMWJmMjU2MzE5OTE4IiwidHlwIjoiUmVmcmVzaCIsImF6cCI6ImRpZmZ1c2lvbi1mcm9udGVuZCIsInNlc3Npb25fc3RhdGUiOiI3MzgzMGM1Mi03YWE2LTRmMWYtOTFmZS0xMWQ1NzllMDZkZmEiLCJzY29wZSI6ImVtYWlsIHByb2ZpbGUifQ.aRUY8RMHA6mDWybk5ovdpANBcBhj-0DZH9NrnbGuRr0",
 :token_type "bearer",
 :not-before-policy 0,
 :session_state "73830c52-7aa6-4f1f-91fe-11d579e06dfa",
 :scope "email profile"})

(def EXPIRED_TOKEN_GOOD_REALM "")
(def EXPIRED_TOKEN "")

(defn request-for [method uri options]
  (let [uri (new java.net.URI uri)]
    (merge
     {:server-port 80
      :server-name "localhost"
      :remote-addr "localhost"
      :uri (.getPath uri)
      :query-string (.getRawQuery uri)
      :scheme :http
      :request-method method}
     (cond-> options
             (:body options) (update :body b/to-byte-buffers)
             true (update :headers #(merge {"host" "localhost"} %))))))

(defn yada-test-context-missing-token []
  {:request (request-for :get "/protected" {:headers {"content-type" "application/transit+json"
                                                      "accept"       "application/transit+json"}})})
(defn ring-test-request [token]
  (let [req (ring-test/request :get "/protected")]
    (merge req (update (headers-with-token-as-header {} token) :headers merge (:headers req)))))

(defn ring-test-request-expired-token []
  (let [req (ring-test/request :get "/protected")]
    (merge req (update (headers-with-token-as-header {} EXPIRED_TOKEN) :headers merge (:headers req)))))

(defn ring-test-request-missing-token []
  (ring-test/request :get "/protected"))

(deftest ^:integration verify-credential-test
  (testing "realm creation "
      (let [realm-name (str "test-realm-" (rand-int 1000))
            realm (kc-admin/create-realm! kc-admin-client  {:name realm-name
                                                            :themes {:defaultLocale "fr",
                                                                     :emailTheme "keycloak",
                                                                     :internationalizationEnabled true,
                                                                     :adminTheme "keycloak",
                                                                     :supportedLocales #{"en" "fr"},
                                                                     :loginTheme "keycloak",
                                                                     :accountTheme "keycloak"}
                                                            :accessTokenLifespan (Integer. 2)})]
        (is (= realm-name (.getRealm realm)))
        (log/info "realm created")
        (testing "create a client, then a deployment for that client"
          (let [client-id (str "test-client-" (rand-int 1000))
                created-client (kc-admin/create-client! kc-admin-client realm-name client-id true)
                deployment (kc-deployment/deployment-for-realm kc-admin-client AUTH_SERVER_URL client-id realm-name)]
            (is (= client-id (.getClientId created-client)))
            (log/info "client created and deployments created")
            (testing "user creation in the realm then join to group"
              (let [username (str "user-" (rand-int 1000))
                    password (str "pass" (rand-int 100))
                    user (kc-user/delete-and-create-user! kc-admin-client realm-name {:username username :password password})]
                (is (= username (.getUsername user)))
                (testing "authentication and token verification and extraction"
                  (let [token (kc-authn/authenticate AUTH_SERVER_URL realm-name client-id username password)
                        access-token (kc-deployment/verify deployment  (:access_token token))
                        extracted-token (kc-deployment/extract access-token)]
                    (testing "Given correct token embed the AccessToken in a Yada Context then verify it"
                      (let [access-token (kc-backend/verify-credential (yada-test-context token) deployment)]
                        (t/is (not (nil? access-token)))))
                    (testing "Given correct token embed the AccessToken in a Ring request then verify it"
                      (let [access-token (kc-backend/verify-credential-in-headers (:headers (ring-test-request token)) deployment)]
                        (t/is (not (nil? access-token)))))
                    (testing "Given expired token, embed it in a Yada context and verify the failure"
                      (Thread/sleep 3000);Access Token lifespan is 2 seconds, we sleep for 3 seconds then check
                      (is (thrown? org.keycloak.exceptions.TokenNotActiveException (kc-backend/verify-credential (yada-test-context token) deployment))))
                    (testing "Given expired token, embed it in a Ring context and verify the failure"
                      (is (thrown? org.keycloak.exceptions.TokenNotActiveException (kc-backend/verify-credential-in-headers (:headers (ring-test-request token)) deployment))))
                    (is (= username (:username extracted-token)))))))
            (testing "Given missing token in a Yada context, verify the failure"
              (is (thrown? clojure.lang.ExceptionInfo (kc-backend/verify-credential (yada-test-context-missing-token) deployment))))
            (testing "Given missing token in a Ring context, verify the failure"
              (is (thrown? clojure.lang.ExceptionInfo (kc-backend/verify-credential (ring-test-request-missing-token) deployment))))))
        (testing "realm deletion"
          (kc-admin/delete-realm! kc-admin-client realm-name)
          (is (thrown? jakarta.ws.rs.NotFoundException (kc-admin/get-realm kc-admin-client realm-name)))))))


#_(yt/response-for (route-notice/resources "*") :get "/notices/ean/9782742708581" {:headers (test-utils/headers "unknow-user" "password")})
