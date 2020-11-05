(ns keycloak.bean
  (:require [bean-dip.core :as bean-dip])
  (:import [org.keycloak.representations.idm ClientRepresentation ProtocolMapperRepresentation]))

(extend-type String
  bean-dip/TranslatableToMap
  (bean->map [s] s))

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
