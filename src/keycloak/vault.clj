(ns keycloak.vault
  (:require [vault.core :as vault]
            [vault.client.http]
            [vault.secrets.kvv2 :as vault-kvv2]))

(defn vault-url [protocol host port]
  (str (or protocol "http") "://" (or host "localhost") (when port (str ":" port))))

;;
(defn new-client [vault-url]
  (vault/new-client vault-url)
  )

(defn authenticate!  [client token]
  (vault/authenticate! client :token token))

(defn- write-secret! [vault-url token mount path data]
  (let [client (authenticate! (new-client vault-url) token)]
    (vault-kvv2/write-secret! client mount path data)))

(defn write-keycloak-client-secret! [vault-url token mount path secret]
  (try
    (write-secret! vault-url token mount path {:secret secret})
    (catch java.lang.Throwable e
      (println (format "Can't write secret to vault at %s with engine %s and path %s because of exception:" vault-url mount path))
      (.printStackTrace e))))
