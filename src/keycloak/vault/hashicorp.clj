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

(defn- read-secret [vault-url token mount path]
  (let [client (authenticate! (new-client vault-url) token)]
    (vault-kvv2/read-secret client mount path)))

(defrecord HashicorpVault [client token mount]
  Vault
  (write-secret! [vault path payload]
    (try
      (write-secret! vault-url token mount path {:secret payload})
    (catch java.lang.Throwable e
      (println (format "Can't write secret to vault at %s with engine %s and path %s because of exception:" vault-url mount path))
      (.printStackTrace e)))
    )
  (read-secret [vault path]
    (try
      (read-secret vault-url token mount path)
    (catch java.lang.Throwable e
      (println (format "Can't read secret to vault at %s with engine %s and path %s because of exception:" vault-url mount path))
      (.printStackTrace e)))
    ))
