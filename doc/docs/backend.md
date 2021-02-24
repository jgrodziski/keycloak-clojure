# Securing a backend

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
**Table of Contents**

- [Securing a backend](#securing-a-backend)
    - [Backend with Yada](#backend-with-yada)
    - [Backend with Ring and Buddy-Auth](#backend-with-ring-and-buddy-auth)
    - [Backend with Donkey and Buddy-Auth](#backend-with-donkey-and-buddy-auth)

<!-- markdown-toc end -->

Prerequisite: The client is correctly configured and is sending with each request either a cookie or a header containing a token. The backend then has to:

1. Extract the token from the cookie or the header of the request (specific to the library used)
2. **Verify the token (Keycloak stuff)**
3. Ensure the client is accessing the restricted part only if the token is correct (Library specific)


## Backend with Yada

Regarding Yada security, read [Yada article "Speak friend and Enter!"](https://juxt.pro/blog/posts/yada-authentication.html))

The code is in [server.clj](https://github.com/jgrodziski/keycloak-clojure/blob/master/backend/src/myapp/backend/server.clj) calling some functions in [keycloak.clj](https://github.com/jgrodziski/keycloak-clojure/blob/master/backend/src/myapp/backend/keycloak.clj).

```clojure
;;1. Extract the token from the cookie or the header (Yada framework specific)
(defn authorization-token-cookie [ctx]
  (let [cookies (parse-cookies (:request ctx))]
    (get cookies "X-Authorization-Token")))

(defmethod yada.security/verify :keycloak
  [ctx scheme]
  (let [header-cred (authorization-bearer-cred ctx)
        cookie-cred (authorization-token-cookie ctx)]
    (-> (or header-cred cookie-cred)
        ;; Verify the token (Keycloak stuff)
        (keycloak/verify)
        (keycloak/extract))))
        
(defn extract-account-data [ctx]
  (when-let [extracted-token (get-in ctx [:authentication "default"])]
    (let [group-path (.get (get-in extracted-token [:other-claims :group]) 0)]
      {:username (:username extracted-token)
       :group (when group-path (subs group-path (inc (last-index-of group-path "/"))))
       :roles (:roles extracted-token)})))

(defn- restricted-content [ctx]
  (let [account-data (extract-account-data ctx)]
    (html5
     [:body
      [:h1 (format "Hello %s!"
                   (get-in ctx [:authentication "default" :user]))]
      [:p "You're accessing a restricted resource!"]])))

(def routes
  ["/"
   [
    ["hello" ( handler (as-resource "Hello world!"))]
    ["restricted" (resource {:consumes #{"application/json"}
                             :produces {:media-type "text/html"}
                             :methods {:post (fn [ctx] (restricted-content ctx))}
                             ;;Ensure the client is accessing the restricted part only if the token is correct
                             :access-control {:scheme :keycloak
                                              :allow-origin "http://localhost:3449"
                                              :allow-credentials true
                                              :allow-methods #{:get :post :options}
                                              :allow-headers ["Content-Type"
                                                              "Access-Control-Allow-Headers"
                                                              "Authorization"
                                                              "X-Requested-With"
                                                              "X-Authorization-Token"]
                              :authorization {:methods {:get :employee :post :employee}}}})]
    [true (as-resource nil)]]])

(defstate server
  :start (listener routes {:port 8084})
  :stop ((:close server)))
```


## Backend with Ring and Buddy-Auth

[Buddy-Auth](https://github.com/funcool/buddy-auth) provides a [Ring middleware `wrap-authentication`](https://funcool.github.io/buddy-auth/latest/#token) that accepts a chain of backends, including token based authentication and authorization backends. 

```clojure
(require '[buddy.auth.backends :as buddy-back :refer [token]]
          [buddy.auth.middleware :as buddy-midd :refer [wrap-authentication]]
          [keycloak.deployment :as kc-deploy :refer [deployment client-conf]]
          [keycloak.backend :as kc-backend :refer [buddy-verify-token-fn]])
          
(def keycloak-deployment (kc-deploy/deployment (kc-deploy/client-conf {:auth-server-url "http://localhost:8090/auth"
                                                                       :admin-realm      "master"
                                                                       :realm            "my-realm"
                                                                       :admin-username   "admin"
                                                                       :admin-password   "adminpass"
                                                                       :client-admin-cli "admin-cli"
                                                                       :client-id        "my-backend"
                                                                       :client-secret    "1d741292-74a0-42c8-99b7-6a6a744ebb25"})))

(def app (-> handler
             (wrap-params)
             (wrap-authentication (buddy-back/token {:authfn (kc-backend/buddy-verify-token-fn keycloak-deployment) :token-name "Bearer"}))))

```

## Backend with Donkey and Buddy-Auth

[Donkey](https://github.com/AppsFlyer/donkey) is a Ring compliant HTTP server. You can use the same middleware as above, the configuration is just slightly different.

```clojure

(defn account-data [extracted-token]
  (let [group-path (.get (get-in extracted-token [:other-claims :group]) 0)]
    {:username (:username extracted-token)
     :group (when group-path (subs group-path (inc (last-index-of group-path "/"))))
     :roles (:roles extracted-token)}))

(defn yada-extract-account-data [ctx]
  (when-let [extracted-token (get-in ctx [:authentication "default"])]
    (account-data extracted-token)))

(defn ring-extract-account-data [req]
  (when-let [extracted-token (:identity req)]
    (account-data extracted-token)))


(def routes
   (ring/router [ "/accounts" ["/:account-id" {:handler (fn [req]
                                                              (let [account (ring-extract-account-data req)]))}]]))

(def app (ring/ring-handler routes 
                            (ring/create-default-handler) 
                            {:middleware [[ring.middleware.params/wrap-params]
                                          [wrap-authentication (buddy-back/token {:authfn (kc-backend/buddy-verify-token-fn keycloak-deployment) :token-name "Bearer"})]]}))

(defn start-donkey-server []
  (let [server (-> (donkey/create-donkey)
                   (donkey/create-server {:port 8080
                                          :routes [{:handler app :handler-mode :blocking}]}))]
    (println "Start Backend Donkey Server on port 8080")
    (-> server
        donkey-server/start
        (on-success (fn [_]
                        (println "Backend Donkey server started on port 8080")))
        (on-fail (fn [exception]
                   (println "Failed to start Backend Donkey Server on port 8080")
                   (println (.getMessage exception))
                   (throw exception))))
    server))

```
