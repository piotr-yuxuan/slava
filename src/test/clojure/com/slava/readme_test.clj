(ns com.slava.readme-test
  (:require [clojure.test :refer :all]
            [com.slava.conversion-native-headers :as serde]
            [clojure.network.ip :as ip])
  (:import (org.apache.avro SchemaBuilder Schema)
           (com.slava NativeAvroSerde NativeAvroSerdeConfig)
           (org.apache.avro.generic GenericData$Fixed)
           (java.util Properties UUID)
           (org.apache.kafka.clients.producer ProducerRecord)
           (org.apache.kafka.streams.kstream ValueMapper Predicate)
           (org.apache.kafka.streams StreamsBuilder TopologyTestDriver StreamsConfig)
           (clojure.network.ip IPAddress)
           (io.confluent.kafka.serializers AbstractKafkaAvroSerDeConfig)
           (io.confluent.kafka.schemaregistry.client CachedSchemaRegistryClient)
           (org.apache.kafka.streams.test ConsumerRecordFactory)
           (org.apache.kafka.common.serialization Serdes$UUIDSerde)
           (com.bakdata.schemaregistrymock SchemaRegistryMock)
           (java.nio ByteBuffer)))

(def ^Schema ip-v4-schema (-> (SchemaBuilder/builder (str *ns*)) (.fixed "IPv4") (.size 4)))

(defmethod serde/from-avro-schema-name (.getFullName ip-v4-schema)
  [this schema ^GenericData$Fixed data]
  (ip/make-ip-address (.array (serde/from-avro-schema-type this schema data))))

(defmethod serde/to-avro-schema-name (.getFullName ip-v4-schema)
  [_ schema ^IPAddress ip-adress]
  (GenericData$Fixed. schema (.array (doto (ByteBuffer/allocate 4) (.putInt (.numeric_value ip-adress)) (.rewind)))))

(def ^Schema ip-v6-schema (-> (SchemaBuilder/builder (str *ns*)) (.fixed "IPv6") (.size 16)))

(defmethod serde/from-avro-schema-name (.getFullName ip-v6-schema)
  [this schema ^GenericData$Fixed data]
  (ip/make-ip-address (.array (serde/from-avro-schema-type this schema data))))

(defmethod serde/to-avro-schema-name (.getFullName ip-v6-schema)
  [_ schema ^IPAddress ip-adress]
  (GenericData$Fixed. schema (.array (doto (ByteBuffer/allocate 16) (.putDouble (.numeric_value ip-adress)) (.rewind)))))

(def ^String ip-array-input-topic "ip-array-input-topic")
(def ^String ip-v4-output-topic "ip-v4-output-topic")

(def ^Schema ip-array-input-schema
  (-> (SchemaBuilder/builder (str *ns*))
      (.record "Input")
      .fields
      (.name "array") .type .array .items .unionOf (.type ip-v4-schema) .and (.type ip-v6-schema) .endUnion .noDefault
      .endRecord))

(def ^Schema ip-v4-output-schema
  (-> (SchemaBuilder/builder (str *ns*))
      (.record "Output")
      .fields
      (.name "address") (.type ip-v4-schema) .noDefault
      .endRecord))

(defonce schema-registry
  (doto (SchemaRegistryMock.)
    (.start)
    (.registerValueSchema ip-array-input-topic ip-array-input-schema)
    (.registerValueSchema ip-v4-output-topic ip-v4-output-schema)))

(def topology
  (let [builder (StreamsBuilder.)]
    (-> (.stream builder ip-array-input-topic)
        (.flatMapValues (reify ValueMapper (apply [_ record] (map #(do {::address %}) (record :com.slava.readme-test.Input/array)))))
        (.filter (reify Predicate (test [_ uuid record] (->> ^IPAddress (record ::address) (.version) (= 4)))))
        (.mapValues (reify ValueMapper (apply [_ record] (clojure.set/rename-keys record {::address :com.slava.readme-test.Output/address}))))
        (.to ip-v4-output-topic))
    (.build builder)))

(def properties
  (doto (Properties.)
    (.put AbstractKafkaAvroSerDeConfig/SCHEMA_REGISTRY_URL_CONFIG (.getUrl schema-registry))
    (.put StreamsConfig/DEFAULT_KEY_SERDE_CLASS_CONFIG (.getName Serdes$UUIDSerde))
    (.put StreamsConfig/DEFAULT_VALUE_SERDE_CLASS_CONFIG (.getName NativeAvroSerde))
    (.put StreamsConfig/PROCESSING_GUARANTEE_CONFIG StreamsConfig/EXACTLY_ONCE)
    (.put StreamsConfig/BOOTSTRAP_SERVERS_CONFIG "kafka-broker-uri://")
    (.put StreamsConfig/APPLICATION_ID_CONFIG "app-id")
    (.put NativeAvroSerdeConfig/COM_SLAVA_FIELD_NAME_CONVERSION_CONFIG (name :namespaced-keyword))))

(def schema-registry-client (CachedSchemaRegistryClient. (.getUrl schema-registry) (int 1e2)))
(def key-avro-serde (doto (Serdes$UUIDSerde.) (.configure properties (boolean :key))))
(def value-avro-serde (doto (NativeAvroSerde. schema-registry-client) (.configure properties (boolean (not :key)))))
(def consumer-record-factory (ConsumerRecordFactory. (.serializer key-avro-serde) (.serializer value-avro-serde)))

(deftest kafka-streams-integration-test
  (with-open [^TopologyTestDriver test-driver (TopologyTestDriver. topology properties)]
    (doseq [record (list {:com.slava.readme-test.Input/array [(ip/make-ip-address "192.168.1.1")
                                                              (ip/make-ip-address "1::1")]}
                         {:com.slava.readme-test.Input/array [(ip/make-ip-address "1::2")
                                                              (ip/make-ip-address "1::3")]}
                         {:com.slava.readme-test.Input/array [(ip/make-ip-address "192.168.1.2")
                                                              (ip/make-ip-address "192.168.1.3")]})]
      (.pipeInput test-driver [(.create consumer-record-factory ip-array-input-topic (UUID/randomUUID) record)]))
    (is (= (for [^ProducerRecord record (take-while some? (repeatedly #(.readOutput test-driver ip-v4-output-topic)))]
             (.deserialize (.deserializer value-avro-serde) ip-v4-output-topic (.value ^ProducerRecord record)))
           (list {:com.slava.readme-test.Output/address (ip/make-ip-address "192.168.1.1")}
                 {:com.slava.readme-test.Output/address (ip/make-ip-address "192.168.1.2")}
                 {:com.slava.readme-test.Output/address (ip/make-ip-address "192.168.1.3")})))))
