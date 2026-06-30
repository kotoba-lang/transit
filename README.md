# kotoba-lang/transit

Transit JSON for Kotoba's tier-1 Datomic wire.

Kotoba's data model is EDN/Datomic: keywords, symbols, sets, tagged literals,
and immutable datoms are semantic values, not plain JSON strings. This library
defines the shared CLJC Transit layer used by Kotoba browser, JVM, bb, and
server-side tooling.

## Tiering

Tier 1:

- Datomic API: transact, q, pull, datoms, entity, history, tx, sync
- EDN/Datomic semantics
- Transit JSON HTTP media type: `application/transit+json`

Tier 2:

- SPARQL
- Cypher
- GraphQL

Tier-2 query languages compile to or project from the tier-1 Datomic graph.
They are interoperability surfaces, not independent sources of truth.

## Usage

```clojure
(require '[transit.core :as transit])

(transit/write-json {:db/id 1 :user/name "Ada"})
;; => {"~:db/id" 1, "~:user/name" "Ada"} as a JSON-compatible value

(transit/datomic-envelope {:graph "people"
                           :query-edn "[:find ?e :where [?e :user/name \"Ada\"]]"})
;; => {:content-type "application/transit+json"
;;     :accept "application/transit+json"
;;     :body {"graph" "people", "query_edn" "..."}}
```

## Design

This repo intentionally starts as a small CLJC surface:

- `write-json`: EDN values to Transit JSON compatible values.
- `read-json`: Transit JSON compatible values back to EDN values.
- `datomic-envelope`: standard HTTP envelope metadata for Kotoba Datomic APIs.

The next layer can add binary Transit MessagePack and streaming readers without
changing callers.
