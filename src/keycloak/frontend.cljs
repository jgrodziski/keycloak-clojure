(ns keycloak.frontend
  (:require [taoensso.timbre :as timbre :refer-macros [log debug info error]]
            ["keycloak-js" :as keycloakjs]))

(defn init-and-authenticate
  "Init keycloak, if authentication succeed callback with 2 args keycloak object and user-info"
  [keycloak-config callback-success]
  (info "load keycloak with config " keycloak-config)
  (let [;;Create the javascript Keycloak object giving it the configuration
        keycloak-obj (keycloakjs (clj->js keycloak-config))]
    (-> keycloak-obj
        ;; If the init is correct and the user is not authenticated, the Keycloak will redirect the browser to the Keycloak server login screen for her to be authenticated and then redirect back a second time here properly authenticated.
        (.init #js {"onLoad" "login-required"
                    "checkLoginIframe" false})
        (.success (fn [authenticated]
                    (info "login succeeded?" authenticated)
                    (info "keycloak object " keycloak-obj)
                    ;; Else if the init is correct and at that point the user is authenticated, we provide a lambda that is called back calling the "loadUserInfo" function against the keycloak server
                    (when authenticated
                      (-> keycloak-obj
                          (.loadUserInfo)
                          (.success (fn [user-info]
                                      (let [user-info-clj (js->clj user-info :keywordize-keys true)]
                                        (when (:debug? keycloak-config)
                                        ;(debug "token is " (.-token keycloak-obj))
                                          (info "user-info:"  user-info-clj))
                                        ;; if we succeed to load the user info we dispatch a re-frame event to store the user info in DB
                                        ;; and store the token in a "X-Authorization-Token" cookie
                                        (callback-success keycloak-obj user-info-clj))))))))
        (.error (fn [] (error "Failed to initialize Keycloak with config" keycloak-config))))
    ;; Then we return the stateful Keycloak object that will represent the "state" of the security stuff in our app (see the defstate :start fn below)
    keycloak-obj))

(defn check-to-update-token
  "function that will check if a token refreshing is needed and do it consequently, then callback the function passing it the the refreshed access token"
  [keycloak-obj min-validity callback-success callback-error]
                                        ;(when (:debug? k) (debug "Check to update Keycloak Token" event))
  (let [will-expire? (.isTokenExpired keycloak-obj min-validity)]
    (info "Check if token needs refresh, expire within" min-validity "seconds?" will-expire?)
    (when will-expire?
      (-> keycloak-obj
          (.updateToken min-validity)
          (.success (fn [refreshed?]
                                        ;(debug "token refreshed?" refreshed)
                      (info "Token successfully refreshed?" refreshed?)
                      (when refreshed?
                        ;;update the token in cookies and local storage
                        (callback-success (.-token keycloak-obj)))))
          (.error callback-error)))
    keycloak-obj))

