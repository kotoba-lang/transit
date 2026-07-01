(ns transit.core
  "Small CLJC Transit JSON layer for Kotoba Datomic wire values.

  The public shape is deliberately JSON-compatible so it can be passed to a host
  JSON encoder in Clojure, ClojureScript, babashka, or Kotoba's own runtime."
  (:require [clojure.string :as str]))

(def media-type-json "application/transit+json")
(def media-type-msgpack "application/transit+msgpack")

(def office-family :kotoba.protocol/office)
(def office-envelope-version 1)
(def office-resource-kinds
  #{:slides/deck
    :sheets/workbook
    :docs/document
    :office/update
    :office/selection
    :office/presence})

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

(defn office-envelope
  "Wrap a Slides/Sheets/Docs payload with Kotoba's shared Transit wire envelope.

  `resource-kind` is an application resource such as :slides/deck,
  :sheets/workbook, or :docs/document. Hosts serialize `:body` as JSON bytes and
  use the returned Transit media headers unchanged."
  ([resource-kind payload] (office-envelope resource-kind payload {}))
  ([resource-kind payload opts]
   (when-not (contains? office-resource-kinds resource-kind)
     (throw (ex-info "unknown Kotoba office resource kind"
                     {:kind resource-kind
                      :allowed office-resource-kinds})))
   {:content-type media-type-json
    :accept media-type-json
    :body (write-json
           (cond-> {:kotoba.protocol/family office-family
                    :kotoba.protocol/version office-envelope-version
                    :kotoba.resource/kind resource-kind
                    :kotoba.resource/payload payload}
             (:request-id opts) (assoc :kotoba.request/id (:request-id opts))
             (:cid opts) (assoc :kotoba.resource/cid (:cid opts))
             (:operation opts) (assoc :kotoba.operation/kind (:operation opts))))}))

(defn read-office-envelope-body
  "Read the JSON-compatible `:body` from office-envelope back to EDN."
  [body]
  (let [decoded (read-json body)]
    (when (not= office-family (:kotoba.protocol/family decoded))
      (throw (ex-info "not a Kotoba office Transit envelope"
                      {:family (:kotoba.protocol/family decoded)})))
    decoded))
