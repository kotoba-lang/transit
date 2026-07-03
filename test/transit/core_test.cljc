(ns transit.core-test
  (:require [clojure.test :refer [deftest is]]
            [transit.core :as transit]))

(deftest media-types
  (is (transit/json-content-type? "application/json; charset=utf-8"))
  (is (transit/accepts-json? "text/plain, application/json"))
  (is (not (transit/json-content-type? "application/transit+json"))))

(deftest datomic-values-write-as-plain-json
  (let [value {:db/id 1
               :user/name "Ada"
               :user/roles #{:role/admin :role/editor}
               :query (list '?e :user/name "Ada")}]
    (is (= {"db/id" 1
            "user/name" "Ada"
            "user/roles" ["role/admin" "role/editor"]
            "query" ["?e" "user/name" "Ada"]}
           (transit/write-json value)))))

(deftest datomic-envelope-defaults-to-plain-json
  (is (= {:content-type "application/json"
          :accept "application/json"
          :body {"graph" "people"
                 "query_edn" "[:find ?e :where [?e :user/name \"Ada\"]]"}}
         (transit/datomic-envelope
          {:graph "people"
           :query-edn "[:find ?e :where [?e :user/name \"Ada\"]]"}))))

(deftest datomic-envelope-can-opt-into-gzip
  (is (= "gzip"
         (:content-encoding
          (transit/datomic-envelope {:graph "people"} {:gzip? true})))))

(deftest office-envelope-round-trips-known-fields
  (let [payload {:slides/id "deck"
                 :slides/slides [{:slides/id "s1"}]}
        envelope (transit/office-envelope :slides/deck payload
                                           {:request-id "req-1"
                                            :cid "bafydeck"
                                            :operation :patch})
        decoded (transit/read-office-envelope-body (:body envelope))]
    (is (= "application/json" (:content-type envelope)))
    (is (nil? (:content-encoding envelope)))
    (is (= :kotoba.protocol/office (:kotoba.protocol/family decoded)))
    (is (= 1 (:kotoba.protocol/version decoded)))
    (is (= :slides/deck (:kotoba.resource/kind decoded)))
    (is (= {"slides/id" "deck" "slides/slides" [{"slides/id" "s1"}]}
           (:kotoba.resource/payload decoded)))
    (is (= "req-1" (:kotoba.request/id decoded)))
    (is (= "bafydeck" (:kotoba.resource/cid decoded)))
    (is (= :patch (:kotoba.operation/kind decoded)))))

(deftest office-envelope-can-opt-into-gzip
  (is (= "gzip"
         (:content-encoding
          (transit/office-envelope :slides/deck {} {:gzip? true})))))

(deftest office-envelope-rejects-unknown-resource-kind
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo
                  :cljs cljs.core.ExceptionInfo)
               (transit/office-envelope :unknown/resource {}))))
