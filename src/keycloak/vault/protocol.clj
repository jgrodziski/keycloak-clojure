(ns keycloak.vault.protocol)

(defprotocol Vault
  (write-secret! [vault id payload])
  (read-secret   [vault id]))
