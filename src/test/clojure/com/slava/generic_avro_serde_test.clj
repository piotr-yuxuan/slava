(ns com.slava.generic-avro-serde-test
  "TODO"
  (:require [clojure.test :refer :all])
  (:import (org.apache.avro SchemaBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler Schema AvroMissingFieldException)
           (io.confluent.kafka.schemaregistry.client MockSchemaRegistryClient)
           (io.confluent.kafka.streams.serdes.avro GenericAvroSerde)
           (com.slava NativeGenericAvroSerde)
           (org.apache.avro.generic GenericRecordBuilder GenericData$Record)
           (org.apache.kafka.common.errors SerializationException)))

(defn generic-data-record->map
  [^GenericData$Record record]
  (reduce (fn [m field] (assoc m (.name field) (.get record ^String (.name field))))
          {}
          (.getFields (.getSchema record))))

(deftest field-logic
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
            (is (= (generic-data-record->map actual-with-native-serialization) actual-with-native-deserialization))))
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
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.core")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "noDefault") .type .stringType .noDefault
                       .endRecord)]
        (.register client topic schema)
        (testing "missing field"
          (try (let [datum-map {}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch AvroMissingFieldException e
                 (is (= "Field noDefault type:STRING pos:0 not set and has no default value" (.getMessage e))))))
        (testing "field present"
          (let [datum-map {"noDefault" ""}
                datum-record (-> (GenericRecordBuilder. ^Schema schema)
                                 (.set "noDefault" "")
                                 .build)
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> datum-record
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> datum-record
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization) actual-with-native-deserialization))))
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
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.core")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "stringDefault") .type .stringType (.stringDefault "default value")
                       .endRecord)]
        (.register client topic schema)
        (testing "missing field"
          (let [datum-map {}
                datum-record (.build (GenericRecordBuilder. ^Schema schema))
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> datum-record
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> datum-record
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization) actual-with-native-deserialization))))
        (testing "field present"
          (let [datum-map {"stringDefault" "some value"}
                datum-record (-> (GenericRecordBuilder. ^Schema schema) (.set "stringDefault" "some value") .build)
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> datum-record
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> datum-record
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization) actual-with-native-deserialization))))
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
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.core")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name "stringNullableNoDefault") .type .nullable .stringType .noDefault
                       .endRecord)]
        (.register client topic schema)
        (testing "missing field"
          (try (let [datum-map {}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch AvroMissingFieldException e
                 (is (= "Field stringNullableNoDefault type:UNION pos:0 not set and has no default value" (.getMessage e))))))
        (testing "field present"
          (let [datum-map {"stringNullableNoDefault" "some value"}
                datum-record (-> (GenericRecordBuilder. ^Schema schema) (.set "stringNullableNoDefault" "some value") .build)
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> datum-record
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> datum-record
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization) actual-with-native-deserialization)))
          (let [datum-map {"stringNullableNoDefault" nil}
                datum-record (-> (GenericRecordBuilder. ^Schema schema) (.set "stringNullableNoDefault" nil) .build)
                actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                      (.serialize (.serializer actual-serde) topic)
                                                      (.deserialize (.deserializer control-serde) topic))
                actual-with-native-deserialization (->> datum-record
                                                        (.serialize (.serializer control-serde) topic)
                                                        (.deserialize (.deserializer actual-serde) topic))
                control (->> datum-record
                             (.serialize (.serializer control-serde) topic)
                             (.deserialize (.deserializer control-serde) topic))]
            (is (= control actual-with-native-serialization))
            (is (= (generic-data-record->map actual-with-native-serialization) actual-with-native-deserialization))))
        (testing "bad type"
          (try (let [datum-map {"stringNullableNoDefault" 1}]
                 (->> (assoc datum-map "org.apache.avro.Schema" schema)
                      (.serialize (.serializer actual-serde) topic)))
               (throw (ex-message "test failed"))
               (catch SerializationException e
                 (is (= "Error serializing Avro message" (.getMessage e))))))))
    (testing "UTF-8 magic"
      (let [utf-8-magic "_çœ_ɵθɤɣʃʄ_ˈʕ_cA_sعربيe_æAe_胡雨軒_Петр"
            schema (-> (SchemaBuilder/builder)
                       (.record utf-8-magic)
                       ^SchemaBuilder$RecordBuilder (.namespace "com.slava.core")
                       ^SchemaBuilder$FieldAssembler .fields
                       (.name utf-8-magic) .type .stringType .noDefault
                       .endRecord)]
        (let [datum-map (assoc {} utf-8-magic utf-8-magic)
              datum-record (-> (GenericRecordBuilder. ^Schema schema) (.set utf-8-magic utf-8-magic) .build)
              actual-with-native-serialization (->> (assoc datum-map "org.apache.avro.Schema" schema)
                                                    (.serialize (.serializer actual-serde) topic)
                                                    (.deserialize (.deserializer control-serde) topic))
              actual-with-native-deserialization (->> datum-record
                                                      (.serialize (.serializer control-serde) topic)
                                                      (.deserialize (.deserializer actual-serde) topic))
              control (->> datum-record
                           (.serialize (.serializer control-serde) topic)
                           (.deserialize (.deserializer control-serde) topic))]
          (is (= control actual-with-native-serialization))
          (is (= (generic-data-record->map actual-with-native-serialization) actual-with-native-deserialization)))))))
