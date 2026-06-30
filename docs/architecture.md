# Architecture

`kotoba-lang/transit` is the protocol-level Transit layer for Kotoba.

## Authority

The authority boundary is CLJC source in this repository:

- `src/transit/core.cljc` defines media types and EDN-to-Transit JSON shapes.
- Tests in `test/transit` pin Datomic values that must round-trip.
- Host runtimes may encode/decode bytes, but protocol semantics remain here.

## Datomic first

Kotoba's tier-1 database API is Datomic-shaped:

- datoms are the source of truth
- EDN forms describe transactions and Datalog queries
- Transit JSON is the HTTP wire media type

SPARQL, Cypher, and GraphQL are tier-2 adapters over this graph.

## Rust-free migration

The current `kotoba` Rust CLI/server may still send or receive
`application/transit+json` while those components exist. That does not make Rust
the Transit implementation. Rust hosts should:

- import no protocol semantics beyond media type compatibility
- delegate value-shape decisions to this CLJC library or generated artifacts
- disappear as Kotoba-native CLI/server components replace the compatibility host

