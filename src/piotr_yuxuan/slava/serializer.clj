(ns piotr-yuxuan.slava.serializer
  "FIXME add cljdoc"
  (:require [piotr-yuxuan.slava.encode :refer [encode]]
            [piotr-yuxuan.slava.config :as config]
            [piotr-yuxuan.slava.schema-registry :as schema-registry])
  (:import (io.confluent.kafka.schemaregistry ParsedSchema)
           (io.confluent.kafka.schemaregistry.avro AvroSchema)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroSerializerConfig KafkaAvroSerializer)
           (io.confluent.kafka.serializers.subject TopicNameStrategy)
           (io.confluent.kafka.serializers.subject.strategy SubjectNameStrategy)
           (io.confluent.kafka.streams.serdes.avro ClojureSerializer)
           (java.util Map)
           (org.apache.avro Schema))
  (:gen-class
    :name io.confluent.kafka.streams.serdes.avro.ClojureSerializer
    :implements [org.apache.kafka.common.serialization.Serializer]
    :constructors {[] [], [io.confluent.kafka.schemaregistry.client.SchemaRegistryClient] []}
    :state state
    :init init
    :prefix "-"))

(defn -init
  "FIXME add cljdoc"
  ([]
   [[] (atom {:kafka-avro-serializer (KafkaAvroSerializer.)
              :schema-registry {:client nil}
              :config nil})])
  ;; For testing purpose only
  ([^SchemaRegistryClient client]
   [[] (atom {:kafka-avro-serializer (KafkaAvroSerializer. client)
              :schema-registry {:client client}
              :config nil})]))

(defn subject-name
  "FIXME add cljdoc"
  [^ClojureSerializer this ^String topic m]
  (if (contains? (meta m) :piotr-yuxuan.slava/subject-name)
    (get (meta m) :piotr-yuxuan.slava/subject-name)
    (let [{:keys [schema-registry] {:keys [isKey]} :schema-registry} @(.-state this)
          ^SubjectNameStrategy subject-name-strategy (get schema-registry (if isKey :key-subject-name-strategy :value-subject-name-strategy))]
      (assert (instance? TopicNameStrategy subject-name-strategy) "Don't know the schema at this point, so don't know how to resolve subject name.")
      (.subjectName subject-name-strategy topic isKey nil))))

(defn resolve-schema-id
  "FIXME add cljdoc"
  [^ClojureSerializer this ^String topic ^Map m]
  (if (contains? (meta m) :piotr-yuxuan.slava/schema-id)
    (get (meta m) :piotr-yuxuan.slava/schema-id)
    (let [{:keys [^SchemaRegistryClient client]} (:schema-registry @(.-state this))]
      (->> (subject-name this topic m)
           (.getLatestSchemaMetadata client)
           (.getId)))))

(defn resolve-schema
  "FIXME add cljdoc"
  ^Schema [^ClojureSerializer this ^String topic ^Map m]
  (if (contains? (meta m) :piotr-yuxuan.slava/writer-schema)
    (get (meta m) :piotr-yuxuan.slava/writer-schema)
    (let [{:keys [^SchemaRegistryClient client]} (:schema-registry @(.-state this))
          _ (assert client "The schema registry client must be not null.")
          schema-id (resolve-schema-id this topic m)
          ^ParsedSchema parsed-schema (.getSchemaById client schema-id)]
      (assert (instance? AvroSchema parsed-schema) "Don't know how to get AvroSchema")
      (.rawSchema ^AvroSchema parsed-schema))))

(defn -serialize
  "FIXME add cljdoc"
  [^ClojureSerializer this ^String topic ^Map m]
  (let [writer-schema (resolve-schema this topic m)
        {:keys [config ^KafkaAvroSerializer kafka-avro-serializer]} @(.-state this)]
    (assert (instance? Schema writer-schema) "The schema must be an Avro schema.")
    (->> m
         (encode config writer-schema)
         (.serialize kafka-avro-serializer topic))))

(defn -configure
  "FIXME add cljdoc"
  [^ClojureSerializer this ^Map configs isKey]
  (let [{:keys [schema-registry slava]} (config/split-domains configs)]
    (doto (.-state this)
      (->> deref :kafka-avro-serializer (#(.configure ^KafkaAvroSerializer % configs isKey))) ;trololo
      (swap! assoc :config slava)
      (swap! update :schema-registry schema-registry/config (KafkaAvroSerializerConfig. schema-registry) isKey))))
