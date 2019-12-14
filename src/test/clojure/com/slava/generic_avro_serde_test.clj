(ns com.slava.generic-avro-serde-test
  "TODO"
  (:require [clojure.test :refer :all])
  (:import (org.apache.avro SchemaBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler Schema AvroMissingFieldException)
           (io.confluent.kafka.schemaregistry.client MockSchemaRegistryClient)
           (io.confluent.kafka.streams.serdes.avro GenericAvroSerde)
           (com.slava NativeGenericAvroSerde)
           (org.apache.avro.generic GenericRecordBuilder)
           (org.apache.kafka.common.errors SerializationException)))

(deftest serialization
  (let [topic "simple-string"
        client (MockSchemaRegistryClient.)
        serde-config {"schema.registry.url" "mock://"}
        actual-serde (doto (NativeGenericAvroSerde. client)
                       (.configure serde-config (boolean (not :key))))
        control-serde (doto (GenericAvroSerde. client)
                        (.configure serde-config (boolean (not :key))))]
    (testing "empty schema"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Empty")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.core")
                       ^SchemaBuilder$FieldAssembler .fields
                       .endRecord)]
        (.register client topic schema)
        (testing "empty record"
          (let [m {}
                control (->> (.build (GenericRecordBuilder. ^Schema schema))
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))
                actual (->> (assoc m "org.apache.avro.Schema" schema)
                            (.serialize (.serializer actual-serde) topic)
                            (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual))))
        (testing "populated record"
          (let [m {"someField" "some value"}
                actual (->> (assoc m "org.apache.avro.Schema" schema)
                            (.serialize (.serializer actual-serde) topic)
                            (.deserialize (.deserializer control-serde) topic))
                control (->> (.build (GenericRecordBuilder. ^Schema schema))
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual))))))
    (testing "no-default field"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Simple")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.core")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "noDefault") .type .stringType .noDefault
                       .endRecord)]
        (.register client topic schema)
        (testing "missing field"
          (try (let [m {}]
                 (->> (assoc m "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch AvroMissingFieldException e
                 (is (= "Field noDefault type:STRING pos:0 not set and has no default value" (.getMessage e))))))
        (testing "field present"
          (let [m {"noDefault" ""}
                actual (->> (assoc m "org.apache.avro.Schema" schema)
                            (.serialize (.serializer actual-serde) topic)
                            (.deserialize (.deserializer control-serde) topic))
                control (->> (-> (GenericRecordBuilder. ^Schema schema) (.set "noDefault" "") .build)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual))))
        (testing "bad type"
          (try (let [m {"noDefault" 1}]
                 (->> (assoc m "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))
        (testing "forbidden nil value"
          (try (let [m {"noDefault" 1}]
                 (->> (assoc m "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))))
    (testing "default field"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Simple")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.core")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "stringDefault") .type .stringType (.stringDefault "default value")
                       .endRecord)]
        (.register client topic schema)
        (testing "missing field"
          (let [m {}
                actual (->> (assoc m "org.apache.avro.Schema" schema)
                            (.serialize (.serializer actual-serde) topic)
                            (.deserialize (.deserializer control-serde) topic))
                control (->> (.build (GenericRecordBuilder. ^Schema schema))
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual))))
        (testing "field present"
          (let [m {"stringDefault" "some value"}
                actual (->> (assoc m "org.apache.avro.Schema" schema)
                            (.serialize (.serializer actual-serde) topic)
                            (.deserialize (.deserializer control-serde) topic))
                control (->> (-> (GenericRecordBuilder. ^Schema schema) (.set "stringDefault" "some value") .build)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual))))
        (testing "bad type"
          (try (let [m {"stringDefault" 1}]
                 (->> (assoc m "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))
        (testing "forbidden nil value"
          (try (let [m {"stringDefault" 1}]
                 (->> (assoc m "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))))
    (testing "nullable, no-default field"
      (let [schema (-> (SchemaBuilder/builder)
                       (.record "Simple")
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.core")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "stringNullableNoDefault") .type .nullable .stringType .noDefault
                       .endRecord)]
        (.register client topic schema)
        (testing "missing field"
          (try (let [m {}]
                 (->> (assoc m "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch AvroMissingFieldException e
                 (is (= "Field stringNullableNoDefault type:UNION pos:0 not set and has no default value" (.getMessage e))))))
        (testing "field present"
          (let [m {"stringNullableNoDefault" "some value"}
                actual (->> (assoc m "org.apache.avro.Schema" schema)
                            (.serialize (.serializer actual-serde) topic)
                            (.deserialize (.deserializer control-serde) topic))
                control (->> (-> (GenericRecordBuilder. ^Schema schema) (.set "stringNullableNoDefault" "some value") .build)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual)))
          (let [m {"stringNullableNoDefault" nil}
                actual (->> (assoc m "org.apache.avro.Schema" schema)
                            (.serialize (.serializer actual-serde) topic)
                            (.deserialize (.deserializer control-serde) topic))
                control (->> (-> (GenericRecordBuilder. ^Schema schema) (.set "stringNullableNoDefault" nil) .build)
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual))))
        (testing "bad type"
          (try (let [m {"stringNullableNoDefault" 1}]
                 (->> (assoc m "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))))))
