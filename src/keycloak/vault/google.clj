(ns keycloak.vault.google
 (:import [com.google.cloud.secretmanager.v1 SecretManagerServiceClient SecretName SecretPayload SecretVersion]
          [com.google.protobuf ByteString]))

(defrecord GoogleSecretManager [project-id secret-id]
  Vault
  (write-secret! vault payload))

(defn ->google-secret-manager [project-id secret-id]
  )
