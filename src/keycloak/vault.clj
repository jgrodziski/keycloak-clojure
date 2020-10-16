(ns keycloak.vault
  ;(:require [vault.core :as vault] [vault.secrets.kvv2 :as vault-kvv2])
  )

(defn vault-url [protocol host port]
  (str (or protocol "http") "://" (or host "localhost") (when port (str ":" port))))

;;
(defn new-client [vault-url]
  ;(require 'vault.client.http)
                                        ; (vault/new-client vault-url)
  )

(defn authenticate!  [client token]
  ;(vault/authenticate! client :token token)
  )

(defn- write-secret! [vault-url token mount path data]
  (let [client (authenticate! (new-client vault-url) token)]
   ; (vault-kvv2/write-secret! client mount path data)
    )
  )

(defn write-keycloak-client-secret! [vault-url token path secret]
  (write-secret! vault-url token "secret" path {:secret secret}))
