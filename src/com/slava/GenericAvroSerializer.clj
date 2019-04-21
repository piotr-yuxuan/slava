(ns com.slava.GenericAvroSerializer
  (:gen-class
    :name com.slava.GenericAvroSerializer
    :state inner
    :init init
    :constructors {[] []
                   [io.confluent.kafka.schemaregistry.client.SchemaRegistryClient] []}
    :implements [org.apache.kafka.common.serialization.Serializer])
  (:import (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroSerializer)
           (java.util HashMap)
           (org.apache.avro.generic GenericRecord)))

(defn -configure
  [this deserializerConfig isSerializerForRecordKeys]
  (let [configuration (HashMap. (assoc (into {} deserializerConfig) "specific.avro.reader" false))]
    (.configure ^KafkaAvroSerializer (.inner this) configuration isSerializerForRecordKeys)))

(defn -serialize
  (^GenericRecord [this topic headers record]
   (.serialize ^KafkaAvroSerializer (.inner this) topic headers record))
  (^GenericRecord [this topic record]
   (.serialize ^KafkaAvroSerializer (.inner this) topic record)))

(defn -init
  ([] [[] (KafkaAvroSerializer.)])
  ([^SchemaRegistryClient client] [[] (KafkaAvroSerializer. client)]))

(defn -close
  [this]
  (.close ^KafkaAvroSerializer (.inner this)))
