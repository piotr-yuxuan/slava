(ns com.slava.generic-avro-serde-test
  "I prefer no abstraction than a bad abstraction: this explains the
  boilerplate code. On the bright side, you're likely to focus on some
  test and not to read this file from beginning to the end, so the
  specific part you are going to read will be clear to you and will
  require less cognitive overhead.

  All these tests are partly for my own exploration of Avro and its
  schema Java API, partly in order to proove by the example it works
  well on multiple use cases and integrates properly with underlying
  Confluent Serde -- which does the actual job."
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [com.slava.specs :refer :all])
  (:import (org.apache.avro SchemaBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler Schema AvroMissingFieldException SchemaBuilder$ArrayDefault Schema$EnumSchema SchemaBuilder$MapDefault Schema$FixedSchema SchemaBuilder$UnionAccumulator Schema$Field)
           (io.confluent.kafka.schemaregistry.client MockSchemaRegistryClient)
           (io.confluent.kafka.streams.serdes.avro GenericAvroSerde)
           (com.slava NativeAvroSerde)
           (org.apache.avro.generic GenericRecordBuilder GenericData$Record GenericData$StringType)
           (org.apache.kafka.common.errors SerializationException)
           (org.apache.avro.mojo SchemaMojo AbstractAvroMojo)
           (java.lang.reflect Field)
           (java.nio ByteBuffer)))

(defn failed-test-with-specific-schema-generation []
  ;; I should better use https://github.com/TimMoore/mojo-executor
  (spit
    (io/as-file "test-resources/slava.avsc")
    (-> (SchemaBuilder/builder)
        (.record "Empty")
        ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
        ^SchemaBuilder$FieldAssembler .fields
        ^Schema .endRecord
        .toString))
  (let [mojo (SchemaMojo.)]
    (doto ^Field (.getDeclaredField ^Class AbstractAvroMojo "sourceDirectory")
      (.setAccessible true)
      (.set mojo (io/as-file "test-resources"))
      (.setAccessible false))
    (doto ^Field (.getDeclaredField ^Class AbstractAvroMojo "outputDirectory")
      (.setAccessible true)
      (.set mojo (io/as-file "target/classes"))
      (.setAccessible false))
    (.execute mojo)))

(defn- generic-data-record->map
  [^GenericData$Record record]
  (reduce (fn [m field] (assoc m (.name field) (.get record ^String (.name field))))
          {}
          (.getFields (.getSchema record))))

(defn map->generic-data-record
  [^Schema schema m]
  (let [builder (new GenericRecordBuilder schema)]
    (doseq [^String field-name (->> (.getFields schema)
                                    (map #(.name %))
                                    (filter #(contains? m %)))]
      (.set builder field-name (get m field-name)))
    (.build builder)))

(deftest field-logic-test
  (let [topic "simple-string"
        client (MockSchemaRegistryClient.)
        serde-config {"schema.registry.url" "mock://"}
        actual-serde (doto (NativeAvroSerde. client)
                       (.configure serde-config (boolean (not :key))))
        control-serde (doto (GenericAvroSerde. client)
                        (.configure serde-config (boolean (not :key))))]
    (testing "empty schema"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Empty")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       .endRecord)]
        (testing "empty record"
          (let [datum-map {}
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> (.build (GenericRecordBuilder. ^Schema schema))
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> (.build (GenericRecordBuilder. ^Schema schema))
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization)
                   actual-with-native-deserialization))))
        (testing "populated record"
          (let [datum-map {"someField" "some value"}
                actual (->> (assoc datum-map "org.apache.avro.Schema" schema)
                            (.serialize (.serializer actual-serde) topic)
                            (.deserialize (.deserializer control-serde) topic))
                control (->> (.build (GenericRecordBuilder. ^Schema schema))
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual))))))
    (testing "no-default field"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Record")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "noDefault") .type .stringType .noDefault
                       .endRecord)]
        (testing "missing field"
          (try (let [datum-map {}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch AvroMissingFieldException e
                 (is (= "Field noDefault type:STRING pos:0 not set and has no default value" (.getMessage e))))))
        (testing "field present"
          (let [datum-map {"noDefault" ""}
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> (map->generic-data-record schema datum-map)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization)
                   actual-with-native-deserialization))))
        (testing "bad type"
          (try (let [datum-map {"noDefault" 1}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))
        (testing "forbidden nil value"
          (try (let [datum-map {"noDefault" 1}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))))
    (testing "default field"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Record")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "stringDefault") .type .stringType (.stringDefault "default value")
                       .endRecord)]
        (testing "missing field"
          (let [datum-map {}
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> (map->generic-data-record schema datum-map)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization)
                   actual-with-native-deserialization))))
        (testing "field present"
          (let [datum-map {"stringDefault" "some value"}
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> (map->generic-data-record schema datum-map)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization)
                   actual-with-native-deserialization))))
        (testing "bad type"
          (try (let [datum-map {"stringDefault" 1}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))
        (testing "forbidden nil value"
          (try (let [datum-map {"stringDefault" 1}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))))
    (testing "nullable, no-default field"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Record")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "stringNullableNoDefault") .type .nullable .stringType .noDefault
                       .endRecord)]
        (testing "missing field"
          (try (let [datum-map {}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch AvroMissingFieldException e
                 (is (= "Field stringNullableNoDefault type:UNION pos:0 not set and has no default value" (.getMessage e))))))
        (testing "field present"
          (let [datum-map {"stringNullableNoDefault" "some value"}
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> (map->generic-data-record schema datum-map)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization)
                   actual-with-native-deserialization)))
          (let [datum-map {"stringNullableNoDefault" nil}
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> (map->generic-data-record schema datum-map)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization)
                   actual-with-native-deserialization))))
        (testing "bad type"
          (try (let [datum-map {"stringNullableNoDefault" 1}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))))
    (testing "UTF-8 magic"
      (let [utf-8-avro-name "_çœ_ɵθɤɣʃʄ_ˈʕ_cA_sعربيe_æAe_胡雨軒_Петр"
            utf-8-avro-string "_çœ_ɵθɤɣʃʄ_ˈʕ_cA_sعربيe_æAe_胡雨軒_Петр👍🚀"
            schema (-> (SchemaBuilder/builder)
                       (.record utf-8-avro-name)
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name utf-8-avro-name) .type .stringType .noDefault
                       .endRecord)]
        (let [datum-map (assoc {} utf-8-avro-name utf-8-avro-string)
              actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                    (.serialize (.serializer actual-serde) topic)
                                                    (.deserialize (.deserializer control-serde) topic))
              actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                      (.serialize (.serializer control-serde) topic)
                                                      (.deserialize (.deserializer actual-serde) topic))
              control (->> (map->generic-data-record schema datum-map)
                           (.serialize (.serializer control-serde) topic)
                           (.deserialize (.deserializer control-serde) topic))]
          (is (= control actual-with-native-serialization))
          (is (= (generic-data-record->map actual-with-native-serialization)
                 actual-with-native-deserialization)))))))

(deftest primitive-types-test
  "https://avro.apache.org/docs/1.9.1/spec.html#schema_primitive"
  (let [topic "simple-string"
        client (MockSchemaRegistryClient.)
        serde-config {"schema.registry.url" "mock://"}
        actual-serde (doto (NativeAvroSerde. client)
                       (.configure serde-config (boolean (not :key))))
        control-serde (doto (GenericAvroSerde. client)
                        (.configure serde-config (boolean (not :key))))]
    (testing "avro null, no value"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Null")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .nullType .noDefault
                       .endRecord)
            datum-map {"field" (gen/generate (s/gen avro-null?))}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))))
    (testing "avro boolean, a binary value"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Boolean")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .booleanType .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen avro-boolean?))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))))
    (testing "avro int, 32-bit signed integer"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Int")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .intType .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen avro-int?))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))
        (is (= (type (get (generic-data-record->map actual-with-native-serialization) "field"))
               (type (get actual-with-native-deserialization "field"))))))
    (testing "avro long, 64-bit signed integer"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Long")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .longType .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen avro-long?))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))
        (is (= (type (get (generic-data-record->map actual-with-native-serialization) "field"))
               (type (get actual-with-native-deserialization "field"))))))
    (testing "avro float, single precision (32-bit) IEEE 754 floating-point number"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Float")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .floatType .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen avro-float?))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))
        (is (= (type (get (generic-data-record->map actual-with-native-serialization) "field"))
               (type (get actual-with-native-deserialization "field"))))))
    (testing "avro double, double precision (64-bit) IEEE 754 floating-point number"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Double")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .doubleType .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen avro-double?))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))
        (is (= (type (get (generic-data-record->map actual-with-native-serialization) "field"))
               (type (get actual-with-native-deserialization "field"))))))
    (testing "avro bytes, sequence of 8-bit unsigned bytes"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Bytes")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .bytesType .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen (->avro-bytes?)))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))
        (is (= (type (get (generic-data-record->map actual-with-native-serialization) "field"))
               (type (get actual-with-native-deserialization "field"))))
        (is (= (.asCharBuffer ^ByteBuffer (get (generic-data-record->map actual-with-native-serialization) "field"))
               (.asCharBuffer ^ByteBuffer (get actual-with-native-deserialization "field"))))))
    (testing "avro string, unicode character sequence"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "String")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "stringTypeDefault") .type .stringType .noDefault ;; default stringType
                       (.name "stringTypeUtf8") .type .stringBuilder (.prop "avro.java.string" "Utf8") .endString .noDefault
                       (.name "stringTypeCharSequence") .type .stringBuilder (.prop "avro.java.string" "CharSequence") .endString .noDefault
                       (.name "stringTypeString") .type .stringBuilder (.prop "avro.java.string" "String") .endString .noDefault
                       .endRecord)
            string-type-default (gen/generate (s/gen (->avro-string?)))
            string-type-utf8 (gen/generate (s/gen (->avro-string? GenericData$StringType/Utf8)))
            string-type-char-sequence (gen/generate (s/gen (->avro-string? GenericData$StringType/CharSequence)))
            string-type-string (gen/generate (s/gen (->avro-string? GenericData$StringType/String)))
            datum-map {"stringTypeDefault" string-type-default
                       "stringTypeUtf8" string-type-utf8
                       "stringTypeCharSequence" string-type-char-sequence
                       "stringTypeString" string-type-string}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= actual-with-native-deserialization
               {"stringTypeDefault" string-type-default
                "stringTypeUtf8" string-type-utf8
                "stringTypeCharSequence" string-type-char-sequence
                "stringTypeString" string-type-string}))))))

(deftest complex-types-test
  "https://avro.apache.org/docs/current/spec.html#schema_complex"
  (let [topic "simple-string"
        client (MockSchemaRegistryClient.)
        serde-config {"schema.registry.url" "mock://"}
        actual-serde (doto (NativeAvroSerde. client)
                       (.configure serde-config (boolean (not :key))))
        control-serde (doto (GenericAvroSerde. client)
                        (.configure serde-config (boolean (not :key))))]
    (testing "avro records"
      (let [;; Pragmatic. However it would be better to defined "Nested" schema only one.
            nested-schema (-> (SchemaBuilder/builder)
                              (.record "Nested")
                              ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                              ^SchemaBuilder$FieldAssembler .fields
                              (.name "nestedField") .type .stringType (.stringDefault "default value")
                              .endRecord)
            schema (-> (SchemaBuilder/builder)
                       (.record "Record")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type
                       #_() #_() (.record "Nested")
                       #_() #_() ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       #_() #_() ^SchemaBuilder$FieldAssembler .fields
                       #_() #_() (.name "nestedField") .type .stringType (.stringDefault "default value")
                       #_() #_() .endRecord
                       .noDefault
                       .endRecord)
            field-value (.build (GenericRecordBuilder. ^Schema nested-schema))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))))
    (testing "avro enums"
      (let [enum-values ["SPADES" "HEARTS" "DIAMONDS" "CLUBS"]
            ;; Pragmatic. However it would be better to defined "Nested" schema only one.
            enum-schema (-> (SchemaBuilder/builder)
                            (.enumeration "Suit")
                            (.symbols (into-array String enum-values)))
            schema (-> (SchemaBuilder/builder)
                       (.record "Enum")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type (.enumeration "Suit") (.symbols (into-array String enum-values)) .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen (->avro-enum? enum-schema)))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))))
    (testing "avro arrays"
      (let [;; Pragmatic. However it would be better to defined "Nested" schema only one.
            enum-schema (-> (SchemaBuilder/builder)
                            (.enumeration "Suit")
                            (.symbols (into-array String ["SPADES" "HEARTS" "DIAMONDS" "CLUBS"])))
            schema (-> (SchemaBuilder/builder)
                       (.record "Array")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .array ^SchemaBuilder$ArrayDefault (.items enum-schema) ;; explore avro capabilities
                       (.arrayDefault (take 1 (gen/generate (s/gen (->avro-array? (->avro-enum? enum-schema))))))
                       .endRecord)
            field-value (gen/generate (s/gen (->avro-array? (->avro-enum? enum-schema))))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))))
    (testing "avro maps"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Map")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .map .values ^SchemaBuilder$MapDefault .stringType ;; explore avro capabilities
                       (.mapDefault {"default singleton map key" "🚀"})
                       .endRecord)
            field-value (gen/generate (s/gen (->avro-map? (->avro-string?))))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))))
    (testing "avro union (in array)" ;; explore avro capabilities
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Union")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type .array
                       #_() .items .unionOf
                       #_() #_() .nullType
                       #_() #_() .and (.fixed "IPv4") ^SchemaBuilder$UnionAccumulator (.size 4)
                       #_() #_() .and (.fixed "IPv6") ^SchemaBuilder$UnionAccumulator (.size 16)
                       #_() #_() .and ^SchemaBuilder$UnionAccumulator .stringType
                       .endUnion
                       .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen (->avro-array? (->avro-union?
                                                              avro-null?
                                                              (->avro-fixed? (-> (SchemaBuilder/builder) (.fixed "IPv4") (.namespace "com.slava.test") (.size 4)))
                                                              (->avro-fixed? (-> (SchemaBuilder/builder) (.fixed "IPv6") (.namespace "com.slava.test") (.size 16)))
                                                              (->avro-string?)))))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))))
    (testing "avro fixed"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Fixed")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.test")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "field") .type (.fixed "IPv6") (.size 16) .noDefault
                       .endRecord)
            field-value (gen/generate (s/gen (->avro-fixed? (-> (SchemaBuilder/builder) (.fixed "IPv6") (.size 16)))))
            datum-map {"field" field-value}
            actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                  (.serialize (.serializer actual-serde) topic)
                                                  (.deserialize (.deserializer control-serde) topic))
            actual-with-native-deserialization (->> (map->generic-data-record schema datum-map)
                                                    (.serialize (.serializer control-serde) topic)
                                                    (.deserialize (.deserializer actual-serde) topic))
            control (->> (map->generic-data-record schema datum-map)
                         (.serialize (.serializer control-serde) topic)
                         (.deserialize (.deserializer control-serde) topic))]
        (is (= control actual-with-native-serialization))
        (is (= (generic-data-record->map actual-with-native-serialization)
               actual-with-native-deserialization))))))
