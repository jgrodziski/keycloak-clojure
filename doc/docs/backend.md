# Securing a backend

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->
- [Backend with Yada](#backend-with-yada)
- [Backend with Ring and Buddy-Auth](#backend-with-ring-and-buddy-auth)

<!-- markdown-toc end -->

## Backend with Yada

The client is correctly configured and is sending either a cookie or a header containing a token with every request. The backend must:

1. Extract the token from the cookie or the header (Yada framework specific)
2. **Verify the token (Keycloak stuff)**
3. Ensure the client is accessing the restricted part only if the token is correct (this is Yada specific, read the excellent [Yada article "Speak friend and Enter!"](https://juxt.pro/blog/posts/yada-authentication.html))


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

(defn- restricted-content [ctx]
  (html5
   [:body
    [:h1 (format "Hello %s!"
                 (get-in ctx [:authentication "default" :user]))]
    [:p "You're accessing a restricted resource!"]]))

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

TODO
