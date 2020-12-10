
(def default-url {"myapp"  "http://localhost:3000"})

(defn url-or-default [base-domain client env]
  (if (not= env "local")
    (format (str "https://%s.%s." base-domain) client env)
    (get default-url client)))

(defn client-id [app-name app-version client-name]
  (str app-name "-" client-name (when (not (clojure.string/blank? app-version)) (str "-" app-version))))

(defn client [{:keys [client-id root base redirects origins] :as client}]
    {:name          client-id
     :public?       true
     :redirect-uris redirects
     :root-url      root
     :base-url      base
     :web-origins   origins})

(defn basic-realm-data [env color app-name app-version clients-uris]
  {:realm   {:name "example2"}
   :clients (into [] (map client clients-uris))})

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

(defn realm-data [base-domains env]
  (let [realm-config (basic-realm-data base-domains env)]
    (condp = env
      "demo" (merge realm-config demo-clients-conf)
      "prod" (merge realm-config prod-clients-conf)
       realm-config)))

(into [] (map (fn [application]
                (basic-realm-data environment color (:name application) (:version application) (:clients-uris application))) applications))









