(ns keycloak.vault.google
  (:require [clojure.string :as str]
            [keycloak.vault.protocol :refer [Vault write-secret! read-secret]])
  (:import [com.google.cloud.secretmanager.v1 SecretManagerServiceClient ProjectName SecretName SecretPayload SecretVersion Secret Replication Replication$Automatic]
           [com.google.protobuf ByteString]))

(defrecord GoogleSecretManager [project-id]
  Vault
  (write-secret! [vault secret-id payload]
    (if (not (str/blank? payload))
      (let [sec-mgr-client (SecretManagerServiceClient/create)]
        (try
          (let [
                project-name   (ProjectName/of project-id)
                parent-secret  (-> (Secret/newBuilder)
                                   (.setReplication (doto (Replication/newBuilder)
                                                      (.setAutomatic (.build (Replication$Automatic/newBuilder)))
                                                      (.build)))
                                   (.build))
                created-secret (try (.createSecret sec-mgr-client project-name secret-id parent-secret)
                                    (catch com.google.api.gax.rpc.AlreadyExistsException aee
                                      (println (format "Secret %s already exist, now add a version with the payload" secret-id))))
                secret-payload (-> (SecretPayload/newBuilder)
                                   (.setData (ByteString/copyFromUtf8 payload))
                                   (.build))
                secret-name    (SecretName/of project-id secret-id)
                added-version  (.addSecretVersion sec-mgr-client secret-name secret-payload)]
            added-version)
          (finally (.close sec-mgr-client))))
      (println (format "payload is nil"))))
  (read-secret [vault secret-id]
    (let [sec-mgr-client (SecretManagerServiceClient/create)]
      (try
        (let [project-name   (ProjectName/of project-id)
              secret-version (format "projects/%s/secrets/%s/versions/latest" project-id secret-id)
              version        (try (.accessSecretVersion sec-mgr-client secret-version)
                                  (catch com.google.api.gax.rpc.NotFoundException nfe
                                    (println (format "Secret %s not found" secret-id))))]
          (println "read-secret" project-id secret-id)
          (when version (-> version (.getPayload) (.getData) (.toStringUtf8)) ))
        (finally (.close sec-mgr-client))))))

(comment
  (def sec-mgr (->GoogleSecretManager "adixe-1168"))
  (write-secret! sec-mgr  "secret-test-2" "yo2")
  (read-secret sec-mgr "secret-test-3")

  (setenv "GOOGLE_APPLICATION_CREDENTIALS" "./resources/adixe-1168-fe1fc6bddbbf.json")
  )
