(ns keycloak.vault.hashicorp
  (:require [vault.client :as vault]
            [vault.client.http]
            [vault.secret.kv.v2 :as vault-kvv2]
            [keycloak.vault.protocol :as vault-protocol :refer [Vault]]))

(defn vault-url
  ([]
   (vault-url nil))
  ([host]
   (vault-url nil host 8200))
  ([protocol host port]
   (str (or protocol "http") "://" (or host "localhost") (when port (str ":" port)))))

;;
(defn new-client
  ([]
   (new-client (vault-url)))
  ([vault-url]
   (vault/new-client vault-url)))

(defn authenticate!  [client token]
  (vault/authenticate! client token))

(defn- write-secret! [client token mount path payload]
  (let [authenticated-client (authenticate! client token)]
    (vault-kvv2/write-secret! authenticated-client mount path payload)))

(defn- read-secret [client token mount path]
  (let [authenticated-client (authenticate! client token)]
    (vault-kvv2/read-secret authenticated-client mount path)))

(defrecord HashicorpVault [client token mount]
  Vault
  (write-secret! [vault path payload]
    (try
      (write-secret! client token mount path {:secret payload})
    (catch java.lang.Throwable e
      (println (format "Can't write secret to vault at %s with engine %s and path %s because of exception:" vault-url mount path))
      (.printStackTrace e)))
    )
  (read-secret [vault path]
    (try
      (read-secret client token mount path)
    (catch java.lang.Throwable e
      (println (format "Can't read secret to vault at %s with engine %s and path %s because of exception:" vault-url mount path))
      (.printStackTrace e)))
    ))


(comment
  (def client (new-client (vault-url)))
  (def vault (->HashicorpVault client "myroot" "secret"))
  (vault-protocol/write-secret! vault "test" "yo")
  )
