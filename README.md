Kafka Avro Serde for Clojure.

![слава советскому народу](dev-resources/слава-советскому-народу.jpg)

# Installation

[![](https://img.shields.io/clojars/v/piotr-yuxuan/slava.svg)](https://clojars.org/piotr-yuxuan/slava)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/slava)](https://cljdoc.org/d/piotr-yuxuan/slava/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/slava)](https://github.com/piotr-yuxuan/slava/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/slava)](https://github.com/piotr-yuxuan/slava/issues)

# TL;DR example

Let's define a `GenericAvroSerde` and a Clojure `Serde` from this project:

``` clojure
(def schema-registry-url "mock://")
(def schema-registry-capacity 128)
(def schema-registry (CachedSchemaRegistryClient. schema-registry-url schema-registry-capacity))
(def avro-config {"schema.registry.url" "mock://"})

(def ^Schema record-schema
  (-> (SchemaBuilder/builder)
      ^SchemaBuilder$NamespacedBuilder (.record "Record")
      ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "field") .type .intType .noDefault
      ^GenericData$Record .endRecord))

(def avro-serde
  (doto (GenericAvroSerde. schema-registry)
    (.configure avro-config (boolean (not :key)))))

(def clojure-serde
  (doto (slava/clojure-serde (CachedSchemaRegistryClient. schema-registry-url schema-registry-capacity))
    (.configure (merge config/opinionated avro-config)
                (boolean (not :key)))))
```

We may now use them:

``` clojure
(->> {:field (int 1)}
     (.serialize (.serializer clojure-serde) topic)
     (.deserialize (.deserializer avro-serde) topic))
;; => (.build (.set (GenericRecordBuilder. schema) "field" (int 1)))

(->> (.build (.set (GenericRecordBuilder. schema) "field" (int 1)))
     (.serialize (.serializer avro-serde) topic)
     (.deserialize (.deserializer clojure-serde) topic))
;; => {:field (int 1)}
```

The Clojure `Serde` returns an idiomatic Clojure map instead of a
record. See
[./test/piotr_yuxuan/slava_test.clj](./test/piotr_yuxuan/slava_test.clj)
for further examples.

# Description

FIXME add cljdoc

This Clojure `Serde` relies on the inner serializer and deserializer
of `GenericAvroSerde`. Because the way they are build, it is not
possible to access their inner instance of `SchemaRegistryClient`
without breaking Object privacy. As we prefer to be good citizens, we
therefore have to declare our own instance and then pass it to the
serdes.

# References

FIXME add cljdoc

For a more complete tool, see
[FundingCircle/jackdaw](https://github.com/FundingCircle/jackdaw).
