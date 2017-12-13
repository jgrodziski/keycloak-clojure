(ns myapp.front.security
  (:require [mount.core :refer [defstate]]
            [re-frame.core :as re-frame]
            [taoensso.timbre :as timbre :refer-macros [log  trace  debug  info  warn  error  fatal  report
                                                       logf tracef debugf infof warnf errorf fatalf reportf
                                                       spy get-env]]
            [myapp.front.interval :as interval]
            [myapp.front.config :as config]
            [keycloak-js :as keycloak-js])
  (:require-macros [myapp.front.static-config :refer [emit-compile-time-conf]]))

;; The keycloak configuration stored in the project.cloj file and "load" at compile time
(def config (emit-compile-time-conf))
(when config/debug?
  (info "myapp.front config loaded" config))

(defn- start-token-refresher []
  ;; event triggered every 30s to check for the keycloak token to be updated
  (interval/register-interval-handlers :myapp.front.events/token-to-update nil 30000)
  (re-frame/dispatch [:token-to-update/start]))

(defn init-and-authenticate
  [keycloak-config]
  (debug "load keycloak with config " keycloak-config)
  (let [js-keycloak-config (clj->js keycloak-config)
        ;;Create the javascript Keycloak object giving it the configuration
        keycloak-obj (js/Keycloak js-keycloak-config)]
    (-> keycloak-obj
        ;; If the init is correct and the user is not authenticated, the Keycloak will redirect the browser to the Keycloak server login screen for her to be authenticated and then redirect back a second time here properly authenticated.
        (.init #js {"onLoad" "login-required"
                    "checkLoginIframe" false})
        (.success (fn [authenticated]
                    (info "login succeeded?" authenticated)
                    ;; Else if the init is correct and at that point the user is authenticated, we provide a lambda that is called back calling the "loadUserInfo" function against the keycloak server 
                    (when authenticated
                      (-> keycloak-obj
                          (.loadUserInfo)
                          (.success (fn [user-info]
                                      (when config/debug?
                                        (info "token is " (.-token keycloak-obj))
                                        (info "user-info:" (js->clj user-info)))
                                      ;; if we succeed to load the user info we dispatch a re-frame event to store the user info in DB
                                      ;; and store the token in a "X-Authorization-Token" cookie
                                      (re-frame/dispatch [:myapp.front.events/set-user-info
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
                    (re-frame/dispatch [:myapp.front.events/set-token-updated
                                        {:token (.-token @keycloak)}]))))
      (.error (fn [] (.logout @keycloak))))
  {:db (:db cofx)}) 
