(ns transit.core-test
  (:require [clojure.test :refer [deftest is]]
            [transit.core :as transit]))

(deftest media-types
  (is (transit/transit-json? "application/transit+json; charset=utf-8"))
  (is (transit/accepts-transit-json? "application/json, application/transit+json"))
  (is (not (transit/transit-json? "application/json"))))

(deftest datomic-values-round-trip
  (let [value {:db/id 1
               :user/name "Ada"
               :user/roles #{:role/admin :role/editor}
               :query (list '?e :user/name "Ada")
               :literal "~already-transit-like"}
        encoded (transit/write-json value)]
    (is (= {"~:db/id" 1
            "~:user/name" "Ada"
            "~:user/roles" ["~#set" ["~:role/admin" "~:role/editor"]]
            "~:query" ["~#list" ["~$?e" "~:user/name" "Ada"]]
            "~:literal" "~~already-transit-like"}
           encoded))
    (is (= value (transit/read-json encoded)))))

(deftest datomic-envelope-defaults-to-transit-json
  (is (= {:content-type "application/transit+json"
          :accept "application/transit+json"
          :body {"graph" "people"
                 "query_edn" "[:find ?e :where [?e :user/name \"Ada\"]]"}}
         (transit/datomic-envelope
          {:graph "people"
           :query-edn "[:find ?e :where [?e :user/name \"Ada\"]]"}))))

(deftest office-envelope-round-trips-docs-sheets-slides-payloads
  (let [payload {:slides/id "deck"
                 :slides/slides [{:slides/id "s1"}]}
        envelope (transit/office-envelope :slides/deck payload
                                           {:request-id "req-1"
                                            :cid "bafydeck"
                                            :operation :patch})
        decoded (transit/read-office-envelope-body (:body envelope))]
    (is (= "application/transit+json" (:content-type envelope)))
    (is (= :kotoba.protocol/office (:kotoba.protocol/family decoded)))
    (is (= 1 (:kotoba.protocol/version decoded)))
    (is (= :slides/deck (:kotoba.resource/kind decoded)))
    (is (= payload (:kotoba.resource/payload decoded)))
    (is (= "req-1" (:kotoba.request/id decoded)))
    (is (= "bafydeck" (:kotoba.resource/cid decoded)))
    (is (= :patch (:kotoba.operation/kind decoded)))))

(deftest office-envelope-rejects-unknown-resource-kind
  (is (thrown? #?(:clj clojure.lang.ExceptionInfo
                  :cljs cljs.core.ExceptionInfo)
               (transit/office-envelope :unknown/resource {}))))
