(ns piotr-yuxuan.slava.clojure-deserializer
  (:require [piotr-yuxuan.slava :as slava]
            [piotr-yuxuan.slava.serde-properties :as serde-properties]
            [byte-streams :as byte-streams])
  (:import (java.nio ByteBuffer)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.schemaregistry.avro AvroSchema)
           (piotr_yuxuan.slava ClojureDeserializer)
           (java.util Map)
           (io.confluent.kafka.schemaregistry ParsedSchema)
           (io.confluent.kafka.serializers KafkaAvroDeserializerConfig))
  (:gen-class
    :name piotr_yuxuan.slava.ClojureDeserializer
    :extends io.confluent.kafka.streams.serdes.avro.GenericAvroDeserializer
    :exposes-methods {deserialize superDeserialize
                      configure superConfigure}
    :state state
    :init init ;; as of now, not used
    :prefix "-"))

(defn -init
  ([]
   [[] (atom {})])
  ;; For testing purpose only
  ([^SchemaRegistryClient client]
   [[client] (atom {:client client})]))

(defn resolve-schema-id
  [^bytes data]
  (.getInt ^ByteBuffer (byte-streams/convert data ByteBuffer)))

(defn resolve-schema
  [this schema-id]
  (let [^SchemaRegistryClient client (:client @(.-state this))
        ^ParsedSchema parsed-schema (.getSchemaById client schema-id)]
    (assert (instance? AvroSchema parsed-schema) "Don't know how to get AvroSchema.")
    (.rawSchema ^AvroSchema parsed-schema)))

(defn -deserialize
  [this ^String topic ^bytes data]
  (assert (:client @(.-state this)) "The schema registry client must be not null.")
  (let [schema-id (resolve-schema-id data)
        writer-schema (resolve-schema this schema-id)]
    ;; FIXME We could probbly retrieve the schema from super. Would be much better
    ;; FIXME Let's demand and make sure that we get a generic record from user config, and raise an exception otherwise.
    (with-meta
      (->> data
           (.superDeserialize this topic)
           (slava/deserialize writer-schema))
      {:piotr-yuxuan.slava/writer-schema writer-schema
       :piotr-yuxuan.slava/schema-id schema-id})))

(defn -configure
  [this ^Map configs isKey]
  (swap! (.-state this) serde-properties/client-properties (KafkaAvroDeserializerConfig. configs) isKey)
  (.superConfigure this configs isKey))
