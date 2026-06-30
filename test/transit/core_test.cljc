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
