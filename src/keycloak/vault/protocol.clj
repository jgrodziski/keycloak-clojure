(ns keycloak.vault.protocol)

(defprotocol Vault
  (write-secret! [vault payload]))
