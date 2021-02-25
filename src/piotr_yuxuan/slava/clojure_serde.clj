(ns piotr-yuxuan.slava.clojure-serde
  (:require [piotr-yuxuan.slava.clojure-deserializer]
            [piotr-yuxuan.slava.clojure-serializer])
  (:import (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (java.util Map)
           (org.apache.kafka.common.serialization Deserializer Serializer)
           (piotr_yuxuan.slava ClojureDeserializer ClojureSerializer))
  (:gen-class
    :name piotr_yuxuan.slava.ClojureSerde
    :implements [org.apache.kafka.common.serialization.Serde]
    :state state
    :init init ;; as of now, not used
    :prefix "-"))

(defrecord SerdeState
  [^ClojureSerializer serializer
   ^ClojureDeserializer deserializer])

(defn -init
  ([]
   [[] (SerdeState. (ClojureSerializer.)
                    (ClojureDeserializer.))])
  ;; For testing purpose only
  ([^SchemaRegistryClient client]
   [[client]
    (assert client "The schema registry client must be not null.")
    (SerdeState.
      (ClojureSerializer. client)
      (ClojureDeserializer. client))]))

(defn -configure
  [this ^Map configs isKey]
  (.configure (:serializer (.-state this))
              configs
              isKey)
  (.configure (:deserializer (.-state this))
              configs
              isKey))

(defn -close
  [this]
  (.close (:serializer (.-state this)))
  (.close (:deserializer (.-state this))))

(defn -serializer
  ^Serializer [this]
  (:serializer (.-state this)))

(defn -deserializer
  ^Deserializer [this]
  (:deserializer (.-state this)))
