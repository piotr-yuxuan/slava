(ns piotr-yuxuan.slava.serde
  "FIXME add cljdoc"
  (:require [piotr-yuxuan.slava.deserializer]
            [piotr-yuxuan.slava.serializer])
  (:import (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.streams.serdes.avro ClojureSerde ClojureDeserializer ClojureSerializer)
           (java.util Map)
           (org.apache.kafka.common.serialization Deserializer Serializer))
  (:gen-class
    :name io.confluent.kafka.streams.serdes.avro.ClojureSerde
    :implements [org.apache.kafka.common.serialization.Serde]
    :constructors {[] [], [io.confluent.kafka.schemaregistry.client.SchemaRegistryClient] []}
    :state state
    :init init
    :prefix "-"))

(defrecord State
  [^ClojureSerializer serializer
   ^ClojureDeserializer deserializer])

(defn -init
  "FIXME add cljdoc"
  ([]
   [[] (State.
         (ClojureSerializer.)
         (ClojureDeserializer.))])
  ;; For testing purpose only
  ([^SchemaRegistryClient client]
   [[] (State.
         (ClojureSerializer. client)
         (ClojureDeserializer. client))]))

(defn -configure
  "FIXME add cljdoc"
  [^ClojureSerde this ^Map configs isKey]
  (.configure ^ClojureSerializer (:serializer (.-state this))
              configs
              isKey)
  (.configure ^ClojureDeserializer (:deserializer (.-state this))
              configs
              isKey))

(defn -close
  "FIXME add cljdoc"
  [^ClojureSerde this]
  (.close ^ClojureSerializer (:serializer (.-state this)))
  (.close ^ClojureDeserializer (:deserializer (.-state this))))

(defn -serializer
  "FIXME add cljdoc"
  ^Serializer [^ClojureSerde this]
  (:serializer (.-state this)))

(defn -deserializer
  "FIXME add cljdoc"
  ^Deserializer [^ClojureSerde this]
  (:deserializer (.-state this)))
