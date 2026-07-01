# Architecture

`kotoba-lang/transit` is the protocol-level Transit layer for Kotoba. It is the
default internal wire protocol for Kotoba-owned app/resource APIs.

## Authority

The authority boundary is CLJC source in this repository:

- `src/transit/core.cljc` defines media types and EDN-to-Transit JSON shapes.
- Tests in `test/transit` pin Datomic and app-resource values that must
  round-trip.
- Host runtimes may encode/decode bytes, but protocol semantics remain here.

## App/resource wire

Kotoba-owned app APIs should use versioned Transit envelopes. The current
office-family resource kinds include:

- `:slides/deck`
- `:sheets/workbook`
- `:docs/document`
- `:office/update`
- `:office/selection`
- `:office/presence`

Other families may add resource kinds, but should reuse the shared Transit tag
semantics and `application/transit+json` media type.

## Datomic first

Kotoba's tier-1 database API is Datomic-shaped:

- datoms are the source of truth
- EDN forms describe transactions and Datalog queries
- Transit JSON is the HTTP wire media type

SPARQL, Cypher, and GraphQL are tier-2 adapters over this graph.

Plain JSON, OpenAPI, ActivityStreams, XRPC, provider REST, and OpenAI-compatible
request shapes are adapter surfaces. They are not the internal source of truth
for keywords, symbols, sets, datoms, CIDs, operations, or patches.

## Rust-free migration

The current `kotoba` Rust CLI/server may still send or receive
`application/transit+json` while those components exist. That does not make Rust
the Transit implementation. Rust hosts should:

- import no protocol semantics beyond media type compatibility
- delegate value-shape decisions to this CLJC library or generated artifacts
- disappear as Kotoba-native CLI/server components replace the compatibility host
