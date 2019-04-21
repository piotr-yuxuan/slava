(ns com.slava.SpecificAvroDeserializer
  (:gen-class
    :name com.slava.SpecificAvroDeserializer
    :state inner
    :init init
    :constructors {[] []
                   [io.confluent.kafka.schemaregistry.client.SchemaRegistryClient] []}
    :implements [org.apache.kafka.common.serialization.Deserializer])
  (:import (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroDeserializer)
           (java.util HashMap)
           (org.apache.avro.specific SpecificRecord)))

(defn -configure
  [this deserializerConfig isDeserializerForRecordKeys]
  (let [configuration (HashMap. (assoc (into {} deserializerConfig) "specific.avro.reader" true))]
    (.configure ^KafkaAvroDeserializer (.inner this) configuration isDeserializerForRecordKeys)))

(defn -deserialize
  (^SpecificRecord [this topic headers record]
   (.deserialize ^KafkaAvroDeserializer (.inner this) topic headers record))
  (^SpecificRecord [this topic record]
   (.deserialize ^KafkaAvroDeserializer (.inner this) topic record)))

(defn -init
  ([] [[] (KafkaAvroDeserializer.)])
  ([^SchemaRegistryClient client] [[] (KafkaAvroDeserializer. client)]))

(defn -close
  [this]
  (.close ^KafkaAvroDeserializer (.inner this)))
