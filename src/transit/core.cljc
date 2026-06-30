(ns transit.core
  "Small CLJC Transit JSON layer for Kotoba Datomic wire values.

  The public shape is deliberately JSON-compatible so it can be passed to a host
  JSON encoder in Clojure, ClojureScript, babashka, or Kotoba's own runtime."
  (:require [clojure.string :as str]))

(def media-type-json "application/transit+json")
(def media-type-msgpack "application/transit+msgpack")

(defn transit-json? [content-type]
  (= media-type-json
     (some-> content-type
             (str/split #";")
             first
             str/trim
             str/lower-case)))

(defn accepts-transit-json? [accept]
  (boolean
   (some #(= media-type-json
             (-> % (str/split #";") first str/trim str/lower-case))
         (str/split (or accept "") #","))))

(defn- escape-string [s]
  (if (str/starts-with? s "~") (str "~" s) s))

(declare write-json)

(defn- write-key [k]
  (cond
    (keyword? k) (str "~:" (subs (str k) 1))
    (symbol? k)  (str "~$" (str k))
    :else        (write-json k)))

(defn write-json
  "Convert an EDN value to a Transit JSON compatible CLJ/CLJS value.

  Keywords, symbols, sets, lists, and tagged literals are encoded with Transit
  tags. Maps are represented as JSON objects when all keys are Transit string
  tags or plain strings; otherwise they fall back to an explicit ~#map entry
  vector."
  [x]
  (cond
    (nil? x) nil
    (string? x) (escape-string x)
    (keyword? x) (str "~:" (subs (str x) 1))
    (symbol? x) (str "~$" (str x))
    (set? x) ["~#set" (mapv write-json (sort-by pr-str x))]
    (vector? x) (mapv write-json x)
    (seq? x) ["~#list" (mapv write-json x)]
    (map? x) (let [entries (mapv (fn [[k v]] [(write-key k) (write-json v)]) x)
                   object? (every? (comp string? first) entries)]
               (if object?
                 (into {} entries)
                 ["~#map" entries]))
    :else x))

(defn- envelope-key [k]
  (str/replace
   (cond
     (keyword? k) (name k)
     (symbol? k) (name k)
     :else (str k))
   "-" "_"))

(defn- envelope-body [body]
  (into {}
        (map (fn [[k v]] [(envelope-key k) (write-json v)]))
        body))

(defn- read-tagged-string [s]
  (cond
    (str/starts-with? s "~~") (subs s 1)
    (str/starts-with? s "~:") (keyword (subs s 2))
    (str/starts-with? s "~$") (symbol (subs s 2))
    :else s))

(defn read-json
  "Read a value produced by write-json back into EDN."
  [x]
  (cond
    (string? x) (read-tagged-string x)
    (vector? x) (let [[tag payload] x]
                  (case tag
                    "~#set" (set (map read-json payload))
                    "~#list" (apply list (map read-json payload))
                    "~#map" (into {} (map (fn [[k v]] [(read-json k) (read-json v)]) payload))
                    (mapv read-json x)))
    (map? x) (into {} (map (fn [[k v]] [(read-json k) (read-json v)]) x))
    :else x))

(defn datomic-envelope
  "Wrap a Datomic request body with the default Kotoba Transit JSON headers."
  [body]
  {:content-type media-type-json
   :accept media-type-json
   :body (envelope-body body)})
