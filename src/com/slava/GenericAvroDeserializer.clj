(ns com.slava.GenericAvroDeserializer
  (:gen-class
    :name com.slava.GenericAvroDeserializer
    :state inner
    :init init
    :constructors {[] []
                   [io.confluent.kafka.schemaregistry.client.SchemaRegistryClient] []}
    :implements [org.apache.kafka.common.serialization.Deserializer])
  (:import (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroDeserializer)
           (java.util HashMap)
           (org.apache.avro.generic GenericRecord)))

(defn -configure
  [this deserializerConfig isDeserializerForRecordKeys]
  (let [configuration (HashMap. (assoc (into {} deserializerConfig) "generic.avro.reader" true))]
    (.configure ^KafkaAvroDeserializer (.inner this) configuration isDeserializerForRecordKeys)))

(defn -deserialize
  (^GenericRecord [this topic headers record]
   (.deserialize ^KafkaAvroDeserializer (.inner this) topic headers record))
  (^GenericRecord [this topic record]
   (.deserialize ^KafkaAvroDeserializer (.inner this) topic record)))

(defn -init
  ([] [[] (KafkaAvroDeserializer.)])
  ([^SchemaRegistryClient client] [[] (KafkaAvroDeserializer. client)]))

(defn -close
  [this]
  (.close ^KafkaAvroDeserializer (.inner this)))
