(ns com.slava.serde-test
  "Reference: https://avro.apache.org/docs/1.8.2/spec.html.
  Any difference should be considered a bug."
  (:require [clojure.test :refer :all])
  (:import (com.slava PrimitiveTypes SpecificAvroSerde)
           (java.nio ByteBuffer)
           (org.apache.kafka.streams StreamsBuilder TopologyTestDriver StreamsConfig)
           (io.confluent.kafka.serializers KafkaAvroDeserializerConfig AbstractKafkaAvroSerDeConfig)
           (java.util Properties)
           (org.apache.kafka.common.serialization Serializer Deserializer Serde)
           (org.apache.kafka.streams.test ConsumerRecordFactory)
           (io.confluent.kafka.schemaregistry.client CachedSchemaRegistryClient)
           (org.apache.kafka.streams.kstream ForeachAction)))

(def ^Properties properties
  (doto (Properties.)
    (.put KafkaAvroDeserializerConfig/SPECIFIC_AVRO_READER_CONFIG "true")
    (.put AbstractKafkaAvroSerDeConfig/SCHEMA_REGISTRY_URL_CONFIG "http://localhost:8081")
    (.put StreamsConfig/BOOTSTRAP_SERVERS_CONFIG "localhost:9092")
    (.put StreamsConfig/APPLICATION_ID_CONFIG "test-app-id")
    (.put StreamsConfig/DEFAULT_KEY_SERDE_CLASS_CONFIG (.getName SpecificAvroSerde))
    (.put StreamsConfig/DEFAULT_VALUE_SERDE_CLASS_CONFIG (.getName SpecificAvroSerde))))

(defn new-schema-registry-client [props]
  (new CachedSchemaRegistryClient (get props AbstractKafkaAvroSerDeConfig/SCHEMA_REGISTRY_URL_CONFIG) 1000))

(deftest specific-serde-test
  (let [avro-object (.build (doto (PrimitiveTypes/newBuilder)
                              (.setANull nil)
                              (.setABoolean true)
                              (.setAInt (int 1))
                              (.setALong (long 1))
                              (.setAFloat (float 1))
                              (.setADouble (double 1))
                              (.setABytes (ByteBuffer/wrap (.getBytes "")))
                              (.setAString "")))
        partition-key avro-object
        input "input"
        output "output"]
    (doseq [^Serde serde [(SpecificAvroSerde.)
                          (SpecificAvroSerde. (new-schema-registry-client properties))]]
      (let [value-serde (doto serde (.configure properties false))
            key-serde (doto serde (.configure properties true))
            consumer-record-factory (new ConsumerRecordFactory
                                         (.serializer key-serde)
                                         (.serializer value-serde))]
        (testing "round-trip"
          (is (->> avro-object
                   (.serialize ^Serializer (.serializer value-serde) input)
                   (.deserialize ^Deserializer (.deserializer value-serde) input)
                   (= avro-object))))
        (testing "minimal kafka streams topology"
          (with-open [^TopologyTestDriver test-driver (TopologyTestDriver.
                                                        (let [builder (StreamsBuilder.)]
                                                          (doto (.stream builder input)
                                                            (.foreach
                                                              (reify ForeachAction
                                                                (apply [_ k v]
                                                                  (is (= avro-object k))
                                                                  (is (= avro-object v)))))
                                                            (.to output))
                                                          (.build builder))
                                                        properties)]
            (.pipeInput test-driver (.create consumer-record-factory input partition-key avro-object))))))))
