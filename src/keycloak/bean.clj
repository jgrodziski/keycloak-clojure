(ns keycloak.bean
  (:require [bean-dip.core :as bean-dip])
  (:import [org.keycloak.representations.idm ClientRepresentation ProtocolMapperRepresentation RealmRepresentation]))

(extend-type String
  bean-dip/TranslatableToMap
  (bean->map [s] s))

(extend-type clojure.lang.MapEntry
  bean-dip/TranslatableToMap
  (bean->map [[k v]] {k v}))

(extend-type java.util.Map
             bean-dip/TranslatableToMap
             (bean->map [m] m))

(bean-dip/def-translation ProtocolMapperRepresentation #{:id
                                                         :name
                                                         :protocol
                                                         :protocol-mapper
                                                         :consent-required?
                                                         :consent-text
                                                         :config})

(bean-dip/def-translation ClientRepresentation #{:client-id :name
                                                 :description :admin-url
                                                 :redirect-uris
                                                 :protocol-mappers
                                                 :protocol
                                                 :client-template :authorization-services-enabled :registered-nodes
                                                 :root-url :client-authenticator-type :base-url :secret :id :not-before
                                                 :authentication-flow-binding-overrides
                                                 :access :origin :node-re-registration-timeout
                                                 :default-roles
                                                 :authorization-settings :optional-client-scopes
                                                 :attributes
                                                 :registration-access-token :default-client-scopes :web-origins :bearer-only? :consent-required?
                                                 :standard-flow-enabled? :implicit-flow-enabled? :direct-access-grants-enabled?
                                                 :service-accounts-enabled? :direct-grants-only? :surrogate-auth-required?
                                                 :public-client? :frontchannel-logout? :full-scope-allowed? :enabled? :always-display-in-console?})

(bean-dip/def-translation RealmRepresentation #{:access-code-lifespan :access-code-lifespan-login :access-code-lifespan-user-action
                                                :access-token-lifespan :access-token-lifespan-for-implicit-flow :account-theme
                                                :action-token-generated-by-admin-lifespan :action-token-generated-by-user-lifespan
                                                :admin-events-details-enabled? :admin-events-enabled? :admin-theme
                                                :application-scope-mappings :applications :attributes
                                                :authentication-flows :authenticator-config :browser-flow :browser-security-headers
                                                :brute-force-protected? :certificate :client-authentication-flow :client-offline-session-idle-timeout
                                                :client-offline-session-max-lifespan :client-scope-mappings :client-scopes
                                                :client-session-idle-timeout :client-session-max-lifespan :client-templates
                                                :clients :code-secret :components :default-default-client-scopes :default-groups
                                                :default-locale :default-optional-client-scopes :default-roles :default-signature-algorithm
                                                :direct-grant-flow :display-name :display-name-html :docker-authentication-flow
                                                :duplicate-emails-allowed? :edit-username-allowed? :email-theme :enabled-event-types
                                                :enabled? :events-enabled? :events-expiration :events-listeners :failure-factor
                                                :federated-users :groups :id :identity-provider-mappers :identity-providers
                                                :internationalization-enabled? :keycloak-version :login-theme :login-with-email-allowed?
                                                :max-delta-time-seconds :max-failure-wait-seconds :minimum-quick-login-wait-seconds :not-before
                                                :oauth-clients :offline-session-idle-timeout :offline-session-max-lifespan :offline-session-max-lifespan-enabled
                                                :otp-policy-algorithm :otp-policy-digits :otp-policy-initial-counter :otp-policy-look-ahead-window
                                                :otp-policy-period :otp-policy-type :otp-supported-applications :password-credential-grant-allowed?
                                                :password-policy :permanent-lockout? :private-key :protocol-mappers
                                                :public-key :quick-login-check-milli-seconds :realm :refresh-token-max-reuse
                                                :registration-allowed? :registration-email-as-username? :registration-flow
                                                :remember-me? :required-actions :required-credentials
                                                :reset-credentials-flow :reset-password-allowed? :revoke-refresh-token
                                                :roles :scope-mappings :smtp-server :social-providers :social?
                                                :ssl-required :sso-session-idle-timeout :sso-session-idle-timeout-remember-me
                                                :sso-session-max-lifespan :sso-session-max-lifespan-remember-me :supported-locales
                                                :update-profile-on-initial-social-login? :user-federation-mappers :user-federation-providers
                                                :user-managed-access-allowed? :users :verify-email? :wait-increment-seconds
                                                :web-authn-policy-acceptable-aaguids :web-authn-policy-attestation-conveyance-preference :web-authn-policy-authenticator-attachment
                                                :web-authn-policy-avoid-same-authenticator-register? :web-authn-policy-create-timeout :web-authn-policy-passwordless-acceptable-aaguids
                                                :web-authn-policy-passwordless-attestation-conveyance-preference :web-authn-policy-passwordless-authenticator-attachment
                                                :web-authn-policy-passwordless-avoid-same-authenticator-register?
                                                :web-authn-policy-passwordless-create-timeout
                                                :web-authn-policy-passwordless-require-resident-key
                                                :web-authn-policy-passwordless-rp-entity-name
                                                :web-authn-policy-passwordless-rp-id
                                                :web-authn-policy-passwordless-signature-algorithms
                                                :web-authn-policy-passwordless-user-verification-requirement
                                                :web-authn-policy-require-resident-key
                                                :web-authn-policy-rp-entity-name
                                                :web-authn-policy-rp-id
                                                :web-authn-policy-signature-algorithms
                                                :web-authn-policy-user-verification-requirement})
