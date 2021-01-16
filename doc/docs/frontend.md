# Securing a frontend

<!-- markdown-toc start - Don't edit this section. Run M-x markdown-toc-refresh-toc -->

- [Installation](#installation)
- [Usage](#usage)
    - [Initial Authentication Steps](#initial-authentication-steps)
    - [Token Storage](#token-storage)
    - [Token Refreshing](#token-refreshing)

<!-- markdown-toc end -->


## Installation

## Usage

### Initial Authentication Steps

The frontend needs to performs the following steps to authenticate the user:

1. Load the Keycloak lib and client configuration
2. Create the javascript Keycloak object giving it the configuration, and call the "init" function:
    1. If the init is correct and the user is not authenticated, the Keycloak will redirect the browser to the Keycloak server login screen for her to be authenticated and then redirect back here properly authenticated.
    2. Else if the init is correct and at that point the user is authenticated, we provide a lambda that is called back calling the "loadUserInfo" function against the keycloak server 
  
  
The following schema describes the steps and the interactions between the browser, the keycloak server and the API server:

<img src="schema.png" width="700" alt="Schema describing the steps and the interactions between the browser, keycloak server and API server" />

Here are the code for each of those steps (in [security.cljs](https://github.com/jgrodziski/keycloak-clojure/blob/master/frontend/src/cljs/myapp/front/security.cljs)):

```clojure
;; We use mount equally on the client and server side, we define a security ns with a mount state inside

;; 1. The keycloak configuration stored in the project.clj file and "load" at compile time
(def config (emit-compile-time-conf))
(when config/debug?
  (info "myapp config loaded" config))

(defn- start-token-refresher []
  ;; event triggered every 30s to check for the keycloak token to be updated
  (interval/register-interval-handlers :myapp.events/token-to-update nil 30000)
  (re-frame/dispatch [:token-to-update/start]))

(defn init-and-authenticate
  [keycloak-config]
  (debug "load keycloak with config " keycloak-config)
  (let [js-keycloak-config (clj->js keycloak-config)
        ;; 2. Create the javascript Keycloak object giving it the configuration
        keycloak-obj (js/Keycloak js-keycloak-config)]
    (-> keycloak-obj
        ;; 3. If the init is correct and the user is not authenticated, the Keycloak will redirect the browser to the Keycloak server login screen for her to be authenticated and then redirect back a second time here properly authenticated.
        (.init #js {"onLoad" "login-required"
                    "checkLoginIframe" false})
        (.success (fn [authenticated]
                    (info "login succeeded?" authenticated)
                    ;; 4. Else if the init is correct and at that point the user is authenticated, we provide a lambda that is called back calling the "loadUserInfo" function against the keycloak server 
                    (when authenticated
                      (-> keycloak-obj
                          (.loadUserInfo)
                          (.success (fn [user-info]
                                      (when config/debug?
                                        (info "token is " (.-token keycloak-obj))
                                        (info "user-info:" (js->clj user-info)))
                                      ;; if we succeed to load the user info we dispatch a re-frame event to store the user info in DB
                                      ;; and store the token in a "X-Authorization-Token" cookie
                                      (re-frame/dispatch [:myapp.events/set-user-info
                                                          (merge {:token (.-token keycloak-obj)}
                                                                 (js->clj user-info :keywordize-keys true))])))))))
        (.error (fn [] (error "Failed to initialize Keycloak"))))
    ;; The token is short lived so we must refresh it regularly, here we start the refresher "background" process thanks to core.async
    (start-token-refresher)
    ;; Then we return the stateful Keycloak object that will represent the "state" of the security stuff in our app (see the defstate :start fn below)
    keycloak-obj))

(declare logout);; declare the logout symbol and bind it after as we must use the keycloak object to logout

(defstate keycloak ;; the value inside the state must be deferred using "@"
  :start (init-and-authenticate (:keycloak config))
  :stop (logout))

(defn logout [] ;; here the logout fn can be properly defined as the keycloak state is initialized
  (.logout @keycloak))

(defn check-to-update-token
  "re-frame compatible function that will check if a token refreshing is needed and do it consequently, dispatch an event with the refreshed token hereafter"
  [cofx event]
  (info "Check to update Keycloak Token" event)
  (-> @keycloak
      (.updateToken 180);refresh token every 180 seconds / 3mn
      (.success (fn [refreshed]
                  (info "token refreshed?" refreshed)
                  (when refreshed
                    ;;update the token in cookies and local storage
                    (re-frame/dispatch [:myapp.events/set-token-updated
                                        {:token (.-token @keycloak)}]))))
      (.error (fn [] (.logout @keycloak))))
  {:db (:db cofx)}) 
```

At that point the user is authenticated with a token and its info is loaded, the rest is traditionnal re-frame machinery to dispatch the event, update the DB and so on.

### Token Storage
Now that we get a token, we need to store it and be able to send it to the backend later on. We have different options for local storage, but mainly two for sending it to the backend: Cookie or HTTP Header. The Cookie option implies that we store it in the cookie store, with the header option we can do whatever we want.
Just be aware that the token is quite secured and is nevertheless short-lived, so in case the token is accessed by rogue hands it should not be a major worry, we can also do both. Have a look at the code in "[cookie.cljs](https://github.com/jgrodziski/keycloak-clojure/blob/master/frontend/src/cljs/myapp/front/cookie.cljs)"

### Token Refreshing
Now the token we get back from the initial authentication process needs to be refreshed regularly. To do that, we setup a "ticker" mechanism that triggers regularly the refresh check and performs it when needed. I won't enter into the details of re-frame event and effect handling, you'll get plenty of top-notch documentation from the re-frame repo for that. Just have a look at the code in "[interval.cljs](https://github.com/jgrodziski/keycloak-clojure/blob/master/frontend/src/cljs/myapp/front/interval.cljs)".

