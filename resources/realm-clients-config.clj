
(def default-url {"myapp"  "http://localhost:3000"})

(defn url-or-default [base-domain client env]
  (if (not= env "local")
    (format (str "https://%s.%s." base-domain) client env)
    (get default-url client)))


(defn basic-realm-data [base-domain env]
  {:realm   {:name "example2"}
   :clients [{:name          "api-client"
              :public?       true
              :redirect-uris [(str (url-or-default base-domain "myapp" env) "/*")]
              :root-url      (url-or-default base-domain "myapp" env)
              :base-url      (url-or-default base-domain "myapp" env)
              :web-origins   [(url-or-default base-domain "myapp" env)]}
             {:name          "myfrontend"
              :public?       true
              :redirect-uris [(str (url-or-default base-domain "myapp" env) "/*")]
              :root-url      (url-or-default base-domain "myapp" env)
              :base-url      (url-or-default base-domain "myapp" env)
              :web-origins   [(url-or-default base-domain "myapp" env)]}
             {:name          "mybackend"
              :public?       false
              :redirect-uris ["http://localhost:3449/*"]
              :web-origins   ["http://localhost:3449"]}]})

(def demo-clients-conf
  {:clients [{:name "api-client",
              :public? true,
              :redirect-uris ["https://demo.example.com/*"],
              :root-url "https://demo.example.com",
              :base-url "https://demo.example.com",
              :web-origins ["https://demo.example.com"]}
             {:name "myapp-frontend",
              :public? true,
              :redirect-uris ["https://demo.example.com/*"],
              :root-url "https://demo.example.com",
              :base-url "https://demo.example.com",
              :web-origins ["https://demo.example.com"]}
             {:name "myapp-backend",
              :public? false,
              :redirect-uris ["http://localhost:3449/*"],
              :web-origins ["http://localhost:3449"]}
             {:name "subscription-frontend",
              :public? true,
              :redirect-uris ["https://subscription.demo.example.com/*"],
              :root-url "https://subscription.demo.example.com",
              :base-url "https://subscription.demo.example.com",
              :web-origins ["https://subscription.demo.example.com"]}
             {:name "subscription-backend",
              :public? false,
              :redirect-uris ["http://localhost:3449/*"],
              :web-origins ["http://localhost:3449"]}
             {:name "account-frontend",
              :public? true,
              :redirect-uris ["https://account.demo.example.com/*"],
              :root-url "https://account.demo.example.com",
              :base-url "https://account.demo.example.com",
              :web-origins ["https://account.demo.example.com"]}
             {:name "account-backend",
              :public? false,
              :redirect-uris ["http://localhost:3449/*"],
              :web-origins ["http://localhost:3449"]}]})

(def prod-clients-conf
  {:clients [{:name "api-client",
              :public? true,
              :redirect-uris ["https://example.com/*"],
              :root-url "https://example.com",
              :base-url "https://example.com",
              :web-origins ["https://example.com"]}
             {:name "myapp-frontend",
              :public? true,
              :redirect-uris ["https://example.com/*"],
              :root-url "https://example.com",
              :base-url "https://example.com",
              :web-origins ["https://example.com"]}
             {:name "myapp-backend",
              :public? false,
              :redirect-uris ["http://localhost:3449/*"],
              :web-origins ["http://localhost:3449"]}
             {:name "subscription-frontend",
              :public? true,
              :redirect-uris ["https://subscription.example.com/*"],
              :root-url "https://subscription.example.com",
              :base-url "https://subscription.example.com",
              :web-origins ["https://subscription.example.com"]}
             {:name "subscription-backend",
              :public? false,
              :redirect-uris ["http://localhost:3449/*"],
              :web-origins ["http://localhost:3449"]}
             {:name "account-frontend",
              :public? true,
              :redirect-uris ["https://account.example.com/*"],
              :root-url "https://account.example.com",
              :base-url "https://account.example.com",
              :web-origins ["https://account.example.com"]}
             {:name "account-backend",
              :public? false,
              :redirect-uris ["http://localhost:3449/*"],
              :web-origins ["http://localhost:3449"]}]})

(defn realm-data [base-domain env]
  (let [realm-config (basic-realm-data base-domain env)]
    (condp = env
      "demo" (merge realm-config demo-clients-conf)
      "prod" (merge realm-config prod-clients-conf)
       realm-config)))


[(realm-data base-domain environment)]

