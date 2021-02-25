(ns piotr-yuxuan.slava.clojure-serializer
  (:require [piotr-yuxuan.slava :as slava]
            [piotr-yuxuan.slava.serde-properties :as serde-properties])
  (:import (java.util Map)
           (piotr_yuxuan.slava ClojureSerializer)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (org.apache.avro Schema)
           (io.confluent.kafka.serializers KafkaAvroSerializerConfig)
           (io.confluent.kafka.serializers.subject TopicNameStrategy)
           (io.confluent.kafka.serializers.subject.strategy SubjectNameStrategy)
           (io.confluent.kafka.schemaregistry ParsedSchema)
           (io.confluent.kafka.schemaregistry.avro AvroSchema))
  (:gen-class
    :name piotr_yuxuan.slava.ClojureSerializer
    :extends io.confluent.kafka.streams.serdes.avro.GenericAvroSerializer
    :exposes-methods {serialize superSerialize
                      configure superConfigure}
    :state state
    :init init
    :prefix "-"))

(defn -init
  ([]
   [[] (atom {})])
  ;; For testing purpose only
  ([^SchemaRegistryClient client]
   [[client] (atom {:client client})]))

(defn subject-name
  [this ^String topic m]
  (if (contains? (meta m) :piotr-yuxuan.slava/subject-name)
    (get (meta m) :piotr-yuxuan.slava/subject-name)
    (let [{:keys [isKey key-subject-name-strategy value-subject-name-strategy]} @(.-state this)
          ^SubjectNameStrategy subject-name-strategy (if isKey key-subject-name-strategy value-subject-name-strategy)]
      (assert (instance? TopicNameStrategy subject-name-strategy) "Don't know the schema at this point, so don't know how to resolve subject name.")
      (.subjectName subject-name-strategy
                    topic
                    (:isKey @(.-state this))
                    nil))))

(defn resolve-schema-id
  [this ^String topic ^Map m]
  (if (contains? (meta m) :piotr-yuxuan.slava/schema-id)
    (get (meta m) :piotr-yuxuan.slava/schema-id)
    (->> (subject-name this topic m)
         (.getLatestSchemaMetadata (:client @(.-state this)))
         (.getId))))

(defn resolve-schema
  ^Schema [this ^String topic ^Map m]
  (if (contains? (meta m) :piotr-yuxuan.slava/writer-schema)
    (get (meta m) :piotr-yuxuan.slava/writer-schema)
    (do (assert (:client @(.-state this)) "The schema registry client must be not null.")
        (let [schema-id (resolve-schema-id this topic m)
              ^ParsedSchema parsed-schema (.getSchemaById (:client @(.-state this)) schema-id)]
          (assert (instance? AvroSchema parsed-schema) "Don't know how to get AvroSchema")
          (.rawSchema ^AvroSchema parsed-schema)))))

(defn -serialize
  [this ^String topic ^Map m]
  (let [writer-schema (resolve-schema this topic m)]
    (assert (instance? Schema writer-schema) "The schema must be an Avro schema.")
    (->> m
         (slava/serialize writer-schema)
         (.superSerialize this topic))))

(defn -configure
  [this ^Map configs isKey]
  (swap! (.-state this) serde-properties/client-properties (KafkaAvroSerializerConfig. configs) isKey)
  (.superConfigure this configs isKey))
