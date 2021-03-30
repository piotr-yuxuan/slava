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
(require '[piotr-yuxuan.slava.config :as config]
         '[piotr-yuxuan.slava :as slava])

(import '(io.confluent.kafka.schemaregistry.client CachedSchemaRegistryClient)
        '(org.apache.avro Schema SchemaBuilder SchemaBuilder$NamespacedBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler)
        '(org.apache.avro.generic GenericData$Record)
        '(io.confluent.kafka.streams.serdes.avro GenericAvroSerde))

(def schema-registry-url "mock://")
(def schema-registry-capacity 128)
(def schema-registry (CachedSchemaRegistryClient. schema-registry-url schema-registry-capacity))
(def avro-config {"schema.registry.url" schema-registry-url})

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
  (doto (slava/clojure-serde schema-registry)
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

This library will not reach version 1.0.0 before more extensive tests
have been written, and more detailed documentation has been added.

The serialiser and deserialiser by this library provided wrap around
their counterparts from `io.confluent.kafka.serializers`. Given a
confluent config and a schema registry instance, a Clojure serde is
built with:

``` clojure
(require '[piotr-yuxuan.slava.config :as config]
         '[piotr-yuxuan.slava :as slava])

(import '(io.confluent.kafka.schemaregistry.client CachedSchemaRegistryClient)
        '(org.apache.kafka.common.serialization Serde))

(def schema-registry-url "mock://")
(def schema-registry-capacity 128)
(def schema-registry (CachedSchemaRegistryClient. schema-registry-url schema-registry-capacity))
(def avro-config {"schema.registry.url" schema-registry-url})

(def ^Serde clojure-serde
  (doto (slava/clojure-serde schema-registry)
    (.configure (merge config/default avro-config)
                (boolean (not :key)))))
```

In order to provide enough flexibility to adapt to most needs, you
have complete access to the config. The value `config/default` will
add no opinionated behaviours: an Avro enum will stay an instance of
`GenericData$EnumSymbol`, an Avro string may be decoded as an instance
of `org.apache.avro.util.Utf8` which implements CharSequence and is
different from a `java.lang.String`; field keys will be decoded as
strings, not keywords, and so on.

An opinionated config uses different choices to provide a more
Clojuresque look-and-feel. For example, let's look at this schema:

``` clojure
(def EnumField
  (-> (SchemaBuilder/builder) (.enumeration "enum") (.symbols (into-array String ["A" "B" "C"]))))

(def MyRecord
  (-> (SchemaBuilder/builder)
      ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "intField") .type .intType .noDefault
      (.name "booleanField") .type .booleanType .noDefault
      (.name "enumField") (.type EnumField) .noDefault
      ^GenericData$Record .endRecord))
```

If we define a `Serde` with `config/opinionated`:

``` clojure
(def ^Serde clojure-serde
  (doto (slava/clojure-serde schema-registry)
    (.configure (merge config/opinionated avro-config)
                (boolean (not :key)))))
```

then the deserialisation / serialisation logic will be equivalent to:

``` clojure
(defn ^Map my-record-decoder
  [^GenericData$Record record]
  {:int-field (.get record "intField")
   :boolean-field (.get record "booleanField")
   :enum-field (csk/->kebab-case-keyword (.toString (.get record "enumField")))})

(defn ^GenericData$Record my-record-encoder
  [^Map m]
  (.build (doto (GenericRecordBuilder. MyRecord)
            (.set "intField" (get m :int-field))
            (.set "booleanField" (get m :boolean-field))
            (.set "enumField" (GenericData$EnumSymbol. EnumField (csk/->SCREAMING_SNAKE_CASE_STRING (get m :enum-field)))))))
```

These coders will be compiled only once at the first time, and reused
for any subsequent invocation.

# Known bugs

See [GitHub issues](https://github.com/piotr-yuxuan/slava/issues).

# References

For a more complete Clojure API around Kafka, see
[FundingCircle/jackdaw](https://github.com/FundingCircle/jackdaw).
