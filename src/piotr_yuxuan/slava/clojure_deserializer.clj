(ns piotr-yuxuan.slava.clojure-deserializer
  (:require [piotr-yuxuan.slava :as slava]
            [piotr-yuxuan.slava.config :as config]
            [byte-streams :as byte-streams])
  (:import (clojure.lang Atom)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroDeserializer)
           (io.confluent.kafka.streams.serdes.avro ClojureDeserializer)
           (java.nio ByteBuffer)
           (java.util Map)
           (org.apache.avro.generic GenericContainer))
  (:gen-class
    :name io.confluent.kafka.streams.serdes.avro.ClojureDeserializer
    :implements [org.apache.kafka.common.serialization.Deserializer]
    :constructors {[] [], [io.confluent.kafka.schemaregistry.client.SchemaRegistryClient] []}
    :state state
    :init init
    :prefix "-"))

(defrecord State
  [^Atom config
   ^KafkaAvroDeserializer kafka-avro-deserializer])

(defn -init
  "FIXME add cljdoc"
  ([]
   [[] (State.
         (atom nil)
         (KafkaAvroDeserializer.))])
  ;; For testing purpose only
  ([^SchemaRegistryClient client]
   [[] (State.
         (atom nil)
         (KafkaAvroDeserializer. client))]))

(defn resolve-schema-id
  "FIXME add cljdoc"
  [^bytes data]
  (.getInt ^ByteBuffer (byte-streams/convert data ByteBuffer)))

(defn -deserialize
  "FIXME add cljdoc"
  ^Map [^ClojureDeserializer this ^String topic ^bytes data]
  (let [{:keys [config ^KafkaAvroDeserializer kafka-avro-deserializer]} (.-state this)
        schema-id (resolve-schema-id data)
        ^GenericContainer avro-data (.deserialize kafka-avro-deserializer topic data)
        writer-schema (.getSchema avro-data)
        ;; FIXME: how to get it?
        reader-schema writer-schema]
    (with-meta
      (slava/deserialize @config reader-schema avro-data)
      {:piotr-yuxuan.slava/writer-schema writer-schema
       :piotr-yuxuan.slava/schema-id schema-id})))

(defn -configure
  "FIXME add cljdoc"
  [^ClojureDeserializer this ^Map configs isKey]
  (let [{:keys [config ^KafkaAvroDeserializer kafka-avro-deserializer]} (.-state this)]
    (reset! config (:slava (config/split-domains configs)))
    (.configure kafka-avro-deserializer configs isKey)))
