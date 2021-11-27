(ns keycloak.vault.hashicorp
  (:require [vault.core :as vault]
            [vault.client.http]
            [vault.secrets.kvv2 :as vault-kvv2]
            [keycloak.vault.protocol :refer [Vault]]))

(defn vault-url [protocol host port]
  (str (or protocol "http") "://" (or host "localhost") (when port (str ":" port))))

;;
(defn new-client [vault-url]
  (vault/new-client vault-url))

(defn authenticate!  [client token]
  (vault/authenticate! client :token token))

(defn- write-secret! [vault-url token mount path payload]
  (let [client (authenticate! (new-client vault-url) token)]
    (vault-kvv2/write-secret! client mount path payload)))

(defn write-keycloak-client-secret! [vault-url token mount path secret]
  (try
    (write-secret! vault-url token mount path {:secret secret})
    (catch java.lang.Throwable e
      (println (format "Can't write secret to vault at %s with engine %s and path %s because of exception:" vault-url mount path))
      (.printStackTrace e))))

(defrecord HashicorpVault [client token mount path]
  Vault
  (write-secret! [vault payload]))
