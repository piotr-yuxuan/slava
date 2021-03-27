(ns piotr-yuxuan.slava.serde-test
  (:require [clojure.test :refer :all]
            [piotr-yuxuan.slava.serde :as slava]
            [piotr-yuxuan.slava.duration :as logical-types])
  (:import (io.confluent.kafka.serializers AbstractKafkaSchemaSerDeConfig KafkaAvroSerializerConfig)
           (io.confluent.kafka.streams.serdes.avro GenericAvroSerde)
           (io.confluent.kafka.schemaregistry.client MockSchemaRegistryClient SchemaRegistryClient)
           (io.confluent.kafka.serializers.subject.strategy SubjectNameStrategy)
           (org.apache.kafka.common.serialization Serializer Deserializer)
           (org.apache.avro SchemaBuilder SchemaBuilder$NamespacedBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder)))

(def topic "topic-name")

(def ^SchemaRegistryClient schema-registry
  (MockSchemaRegistryClient.))

(def ^GenericAvroSerde generic-avro-serde
  (doto (GenericAvroSerde. schema-registry)
    (.configure {AbstractKafkaSchemaSerDeConfig/SCHEMA_REGISTRY_URL_CONFIG "mock://"}
                (boolean (not :key)))))

(def ^GenericAvroSerde slava-serde
  (doto (slava/->Serde schema-registry)
    (.configure {AbstractKafkaSchemaSerDeConfig/SCHEMA_REGISTRY_URL_CONFIG "mock://"}
                (boolean (not :key)))))

(defn serde-round-trip [serde data]
  (->> data
       (.serialize ^Serializer (.serializer serde) topic)
       (.deserialize ^Deserializer (.deserializer serde) topic)))

(serde-round-trip
  generic-avro-serde
  (let [array-schema (-> (SchemaBuilder/builder)
                         .array
                         (.items (-> (SchemaBuilder/builder)
                                     .array
                                     (.items logical-types/schema))))
        record-schema (-> (SchemaBuilder/builder)
                          ^SchemaBuilder$NamespacedBuilder (.record "Record")
                          ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.test")
                          ^SchemaBuilder$FieldAssembler .fields
                          (.name "field") .type .intType .noDefault
                          (.name "arraySchema") (.type array-schema) .noDefault
                          ^GenericData$Record .endRecord)]
    (.build (doto (GenericRecordBuilder. record-schema)
              (.set "field" (int 1))
              (.set "arraySchema" [[(.toFixed logical-types/conversion {:months 1 :days 2 :milliseconds 3} array-schema logical-types/logical-type) (.toFixed logical-types/conversion {:months 1 :days 2 :milliseconds 3} array-schema logical-types/logical-type)]
                                   [(.toFixed logical-types/conversion {:months 1 :days 2 :milliseconds 3} array-schema logical-types/logical-type)]
                                   []])))))
