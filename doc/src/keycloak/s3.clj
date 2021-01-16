(ns keycloak.s3
  (:require
   [clojure.java.io :as io]
   [cognitect.aws.credentials :as creds]
   [cognitect.aws.client.api :as aws]
   [pantomime.mime :refer [mime-type-of]]
   [environ.core :refer [env]]
   [keycloak.file :as file]))


(def credentials (creds/basic-credentials-provider
                  {:access-key-id (env :aws-access-key-id)
                   :secret-access-key (env :aws-secret-access-key)}))
(def s3 (aws/client {:api :s3 :credentials-provider credentials}))

(defn create-bucket [bucket location]
  (aws/invoke s3 {:op :CreateBucket :request {:Bucket bucket :CreateBucketConfiguration {:LocationConstraint location}}}))


(defn name-in-bucket [dir file]
  (str (file/relative-path (.getAbsolutePath (io/file dir)) file) "/" (.getName file)))

(defn put-object
  ([bucket dir file]
   (println (format "Put file %s to bucket %s/%s" (.getName file) bucket (name-in-bucket dir file)))
   (let [result (aws/invoke s3 {:op :PutObject :request {:Bucket bucket
                                                         :Key  (name-in-bucket dir file)
                                                         :ContentType (mime-type-of file)
                                                         :Body (io/input-stream file)}})]
     (println (format "result is %s" result))))
  )

(comment
  ([bucket filename content]
   (aws/invoke s3 {:op :PutObject :request {:Bucket bucket
                                            :Key filename
                                            :Body (io/input-stream (.getBytes content))}})))
