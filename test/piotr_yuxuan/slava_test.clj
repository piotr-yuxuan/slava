(ns piotr-yuxuan.slava-test
  (:require [clojure.test :refer [deftest testing is]]
            [piotr-yuxuan.slava :as slava]
            [piotr-yuxuan.slava.config :as config])
  (:import (io.confluent.kafka.serializers AbstractKafkaSchemaSerDeConfig KafkaAvroSerializerConfig)
           (io.confluent.kafka.streams.serdes.avro GenericAvroSerde)
           (io.confluent.kafka.schemaregistry.client MockSchemaRegistryClient)
           (org.apache.avro SchemaBuilder SchemaBuilder$NamespacedBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler Schema)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder)
           (io.confluent.kafka.schemaregistry.avro AvroSchema)))

(def topic "topic-name")

(def avro-config
  {AbstractKafkaSchemaSerDeConfig/SCHEMA_REGISTRY_URL_CONFIG "mock://"})

(deftest subject-name-test
  (is (= (slava/subject-name
           {:key? true
            :subject-name-strategy (.keySubjectNameStrategy (KafkaAvroSerializerConfig. avro-config))}
           topic)
         "topic-name-key"))
  (is (= (slava/subject-name
           {:key? false
            :subject-name-strategy (.valueSubjectNameStrategy (KafkaAvroSerializerConfig. avro-config))}
           topic)
         "topic-name-value")))

(deftest resolve-subject-name-test
  (is (= (slava/resolve-subject-name
           {:key? true
            :subject-name-strategy (.keySubjectNameStrategy (KafkaAvroSerializerConfig. avro-config))}
           topic
           {})
         "topic-name-key"))
  (is (= (slava/resolve-subject-name
           {:key? false
            :subject-name-strategy (.keySubjectNameStrategy (KafkaAvroSerializerConfig. avro-config))}
           topic
           {})
         "topic-name-value"))
  (is (= (slava/resolve-subject-name
           {:key? true
            :subject-name-strategy (.keySubjectNameStrategy (KafkaAvroSerializerConfig. avro-config))}
           topic
           (with-meta {} {:piotr-yuxuan.slava/subject-name "custom-subject-name"}))
         "custom-subject-name")))

(def ^Schema previous-schema
  (-> (SchemaBuilder/builder)
      ^SchemaBuilder$NamespacedBuilder (.record "PreviousRecord")
      ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "field") .type .intType .noDefault
      ^GenericData$Record .endRecord))

(def ^Schema schema
  (-> (SchemaBuilder/builder)
      ^SchemaBuilder$NamespacedBuilder (.record "Record")
      ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "field") .type .intType .noDefault
      ^GenericData$Record .endRecord))

(def ^Integer version-id
  (rand-int 100))

(def ^Integer schema-id
  (rand-int 100))

(def ^Integer writer-version-id
  (rand-int 100))

(def ^Integer reader-version-id
  (rand-int 100))

(def ^Integer writer-schema-id
  (rand-int 100))

(def ^Integer reader-schema-id
  (rand-int 100))

(deftest resolve-schema-id-test
  (let [another-schema-id 101]
    (is (= another-schema-id
           (slava/resolve-schema-id
             (doto (MockSchemaRegistryClient.)
               (.register "subject-name" (AvroSchema. schema) version-id schema-id))
             (with-meta {} {:piotr-yuxuan.slava/schema-id another-schema-id})
             "subject-name"))))
  (is (= schema-id
         (slava/resolve-schema-id
           (doto (MockSchemaRegistryClient.)
             (.register "subject-name" (AvroSchema. schema) version-id schema-id))
           {}
           "subject-name"))))

(def ^Schema writer-schema
  (-> (SchemaBuilder/builder)
      ^SchemaBuilder$NamespacedBuilder (.record "WriterRecord")
      ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "field") .type .intType .noDefault
      ^GenericData$Record .endRecord))

(def ^Schema reader-schema
  (-> (SchemaBuilder/builder)
      ^SchemaBuilder$NamespacedBuilder (.record "ReaderRecord")
      ^SchemaBuilder$RecordBuilder (.namespace "piotr-yuxuan.slava.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "field") .type .intType .noDefault
      ^GenericData$Record .endRecord))

(deftest resolve-schema-test
  (is (= schema (slava/resolve-schema
                  config/default
                  (doto (MockSchemaRegistryClient.)
                    (.register "subject-name" (AvroSchema. schema) version-id schema-id))
                  topic
                  {})))
  (is (= writer-schema
         (slava/resolve-schema (MockSchemaRegistryClient.)
                               (with-meta {} {:piotr-yuxuan.slava/writer-schema writer-schema
                                              :piotr-yuxuan.slava/reader-schema reader-schema})
                               reader-schema-id)))
  (is (= schema
         (slava/resolve-schema (MockSchemaRegistryClient.)
                               (with-meta {} {:piotr-yuxuan.slava/reader-schema schema})
                               reader-schema-id)))
  (is (= reader-schema
         (slava/resolve-schema (doto (MockSchemaRegistryClient.)
                                 (.register "subject-name" (AvroSchema. writer-schema) writer-version-id writer-schema-id)
                                 (.register "subject-name" (AvroSchema. reader-schema) reader-version-id reader-schema-id))
                               {}
                               reader-schema-id))))

(deftest serde-test
  (let [key? false
        inner-client (doto (MockSchemaRegistryClient.)
                       (.register "topic-name-value" (AvroSchema. schema) version-id schema-id))
        avro-serde (doto (GenericAvroSerde. inner-client)
                     (.configure avro-config key?))
        clojure-serde (doto (slava/clojure-serde inner-client)
                        (.configure (merge config/default avro-config) key?))]
    (is (= (.build (.set (^GenericRecordBuilder GenericRecordBuilder. schema) "field" ^Object (int 1)))
           (->> {"field" (int 1)}
                (.serialize (.serializer clojure-serde) topic)
                (.deserialize (.deserializer avro-serde) topic))))
    (is (= {"field" (int 1)}
           (->> (.build (.set ^GenericRecordBuilder (GenericRecordBuilder. schema) "field" ^Object (int 1)))
                (.serialize (.serializer avro-serde) topic)
                (.deserialize (.deserializer clojure-serde) topic))))
    (is (= {"field" (int 1)}
           (->> {"field" (int 1)}
                (.serialize (.serializer clojure-serde) topic)
                (.deserialize (.deserializer clojure-serde) topic)))))
  (let [key? false
        inner-client (doto (MockSchemaRegistryClient.)
                       (.register "topic-name-value" (AvroSchema. schema) version-id schema-id))
        avro-serde (doto (GenericAvroSerde. inner-client)
                     (.configure avro-config key?))
        clojure-serde (doto (slava/clojure-serde inner-client)
                        (.configure (merge config/opinionated avro-config) key?))]
    (is (= (.build (.set (GenericRecordBuilder. schema) "field" ^Object (int 1)))
           (->> {:field (int 1)}
                (.serialize (.serializer clojure-serde) topic)
                (.deserialize (.deserializer avro-serde) topic))))
    (is (= {:field (int 1)}
           (->> (.build ^GenericRecordBuilder (.set (GenericRecordBuilder. schema) "field" ^Object (int 1)))
                (.serialize (.serializer avro-serde) topic)
                (.deserialize (.deserializer clojure-serde) topic))))
    (is (= {:field (int 1)}
           (->> {:field (int 1)}
                (.serialize (.serializer clojure-serde) topic)
                (.deserialize (.deserializer clojure-serde) topic))))))
