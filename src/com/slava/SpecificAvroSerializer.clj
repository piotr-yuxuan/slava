(ns com.slava.SpecificAvroSerializer
  (:gen-class
    :name com.slava.SpecificAvroSerializer
    :state inner
    :init init
    :constructors {[] []
                   [io.confluent.kafka.schemaregistry.client.SchemaRegistryClient] []}
    :implements [org.apache.kafka.common.serialization.Serializer])
  (:import (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroSerializer)
           (java.util HashMap)
           (org.apache.avro.specific SpecificRecord)))

(defn -configure
  [this deserializerConfig isSerializerForRecordKeys]
  (let [configuration (HashMap. (assoc (into {} deserializerConfig) "specific.avro.reader" true))]
    (.configure ^KafkaAvroSerializer (.inner this) configuration isSerializerForRecordKeys)))

(defn -serialize
  (^SpecificRecord [this topic headers record]
   (.serialize ^KafkaAvroSerializer (.inner this) topic headers record))
  (^SpecificRecord [this topic record]
   (.serialize ^KafkaAvroSerializer (.inner this) topic record)))

(defn -init
  ([] [[] (KafkaAvroSerializer.)])
  ([^SchemaRegistryClient client] [[] (KafkaAvroSerializer. client)]))

(defn -close
  [this]
  (.close ^KafkaAvroSerializer (.inner this)))
