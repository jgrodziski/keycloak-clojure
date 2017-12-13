(ns myapp.backend.core-test
  (:require
   [myapp.backend.server :refer :all]
   [yada.test :as test]
   [clojure.test :refer :all]))


(deftest test-authorization-header
  (testing "when an authorization header of type bearer is send then it is correctly extracted"
    (is (= "mytoken123" (authorization-bearer-cred
                         {:request (test/request-for :get
                                                     "restricted-content"
                                                     {:headers {"authorization" "Bearer mytoken123"}})})))))
