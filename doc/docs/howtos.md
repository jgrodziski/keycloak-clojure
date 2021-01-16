
# HOWTOs

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->

- [How-to obtain an Access Token?](#how-to-obtain-an-access-token)
    - [With an HTTP request through curl](#with-an-http-request-through-curl)
    - [With an HTTP request programmatically through Clojure](#with-an-http-request-programmatically-through-clojure)
    - [With Keycloak-Clojure then verifying and extracting the token](#with-keycloak-clojure-then-verifying-and-extracting-the-token)

<!-- markdown-toc end -->


## How-to obtain an Access Token?

The access token represents the authorization of a specific application to access specific parts of a user’s data.
Access Token are short lived token given by the authorization server (Keycloak) to the client to access user's data. Access Token can be retrieved programmatically for some automation needs or testing. See the [OAuth2 concepts section](/concepts.html).


### With an HTTP request through curl

```bash
curl -k --data "grant_type=client_credentials&client_id=admin-cli&client_secret=dfefe9a3-7781-439a-b37f-74de0db03b11" https://localhost:8090/auth/realms/<your-realm>/protocol/openid-connect/token`
```

### With an HTTP request programmatically through Clojure

```clojure
;;using only HTTP client libs
(keycloak.authn/authenticate "http://localhost:8090/auth" "your-realm" "your-client-id" "username" "password")
;; {:access_token
;;  "eyJhbGciOiJSUzI1NiIsInR5cCIgOiA...",
;;  :expires_in 300,
;;  :refresh_expires_in 1800,
;;  :refresh_token "eyJhbGciOiJIUzI1Ni....",
;;  :token_type "bearer",
;;  :not-before-policy 0,
;;  :session_state "2e04423e-ab23-4b74-9776-946d26687353",
;;  :scope "email profile"}
```

### With Keycloak-Clojure then verifying and extracting the token

```clojure

(require '[keycloak.backend    :as backend]
         '[keycloak.deployment :as deployment]
         '[keycloak.admin      :as admin]
         '[keycloak.authz      :as authz]
         '[keycloak.authn      :as authn])
(def ADMIN_LOGIN     "admin")
(def ADMIN_PWD       "password")
(def AUTH_SERVER_URL "http://localhost:8090/auth")

;;first build an admin client
(def kc-client    (deployment/keycloak-client (deployment/client-conf AUTH_SERVER_URL "master" "admin-cli") ADMIN_LOGIN ADMIN_PWD))
;;extract the client secret programmatically 
(def secret       (admin/get-client-secret kc-client "your-realm" "your-client-id"))
;;build an authorization client to get an access token
(def kc-authz     (authz/authz-client (deployment/client-conf-input-stream AUTH_SERVER_URL "electre" "diffusion-backend" secret)))
;;get the access token
(def access-token (authn/access-token kc-authz "user" "password"))

;;build a deployment (Keycloak lib being embedded in a client)
(def deployment (deployment/deployment-for-realm kc-client AUTH_SERVER_URL "diffusion-frontend" "electre"))
;; then verify and extract the access token we get (with whatever method we want)
(def clojure-access-token (->> (:access_token access-token)
                               (deployment/verify deployment)
                               deployment/extract))

```
