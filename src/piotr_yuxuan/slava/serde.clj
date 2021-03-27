(ns piotr-yuxuan.slava.serde
  "FIXME add cljdoc"
  (:require [piotr-yuxuan.slava.encode :refer [encode]]
            [piotr-yuxuan.slava.decode :refer [decode]]
            [piotr-yuxuan.slava.config :as config])
  (:import (io.confluent.kafka.schemaregistry.avro AvroSchema)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroSerializer KafkaAvroSerializerConfig KafkaAvroDeserializer)
           (io.confluent.kafka.serializers.subject.strategy SubjectNameStrategy)
           (java.util Map)
           (org.apache.avro Schema)
           (org.apache.kafka.common.serialization Serializer Deserializer Serdes Serde)
           (clojure.lang Atom)
           (org.apache.avro.generic GenericContainer)))

(defn subject-name
  "FIXME add cljdoc"
  [{:keys [config]} ^String topic]
  (let [{:keys [key? ^SubjectNameStrategy subject-name-strategy]} @config]
    (.subjectName subject-name-strategy topic key? nil)))

(defn resolve-subject-name
  "FIXME add cljdoc"
  [this ^String topic m]
  (if (contains? (meta m) :piotr-yuxuan.slava/subject-name)
    (get (meta m) :piotr-yuxuan.slava/subject-name)
    (subject-name this topic)))

(defn schema-id!
  "FIXME add cljdoc"
  [{:keys [^SchemaRegistryClient inner-client]} ^String subject-name]
  (.getId (.getLatestSchemaMetadata inner-client subject-name)))

(defn resolve-schema-id
  "FIXME add cljdoc"
  [this ^Map m ^String subject-name]
  (if (contains? (meta m) :piotr-yuxuan.slava/schema-id)
    (get (meta m) :piotr-yuxuan.slava/schema-id)
    (schema-id! this subject-name)))

(defn resolve-schema
  "FIXME add cljdoc"
  ^Schema [{:keys [inner-client] :as this} ^String topic ^Map m]
  (cond (contains? (meta m) :piotr-yuxuan.slava/writer-schema) (get (meta m) :piotr-yuxuan.slava/writer-schema)
        (contains? (meta m) :piotr-yuxuan.slava/reader-schema) (get (meta m) :piotr-yuxuan.slava/reader-schema)
        :else (->> (resolve-schema-id this m (resolve-subject-name this topic m))
                   ^AvroSchema (.getSchemaById inner-client)
                   (.rawSchema))))

(defn subject-name-strategy
  "FIXME add cljdoc"
  [inner-config key?]
  (let [inner-config-obj (KafkaAvroSerializerConfig. inner-config)]
    (if key?
      (.keySubjectNameStrategy inner-config-obj)
      (.valueSubjectNameStrategy inner-config-obj))))

(defn configure!
  "FIXME add cljdoc"
  [{:keys [config inner]} value key?]
  (let [inner-config (->> value
                          (remove (comp config/slava-key? key))
                          (into {}))]
    (reset! config (assoc value
                     :key? key?
                     :subject-name-strategy (subject-name-strategy inner-config key?)))
    (.configure inner inner-config key?)))

(defrecord ClojureSerializer [^Atom config
                              ^KafkaAvroSerializer inner
                              ^SchemaRegistryClient inner-client]
  Serializer
  (configure [this value key?] (configure! this value key?))
  (serialize [this topic m]
    (->> (resolve-schema this topic m)
         (encode config m)
         (.serialize inner topic)))
  (close [_] (.close inner)))

(defrecord ClojureDeserializer [^Atom config
                                ^KafkaAvroDeserializer inner
                                ^SchemaRegistryClient inner-client]
  Deserializer
  (configure [this value key?] (configure! this value key?))
  (deserialize [this topic data]
    (let [^GenericContainer generic-container (.deserialize inner topic data)
          reader-schema (.getSchema generic-container)
          m (decode @config generic-container reader-schema)
          subject-name (resolve-subject-name this topic m)]
      (vary-meta m assoc
                 :piotr-yuxuan.slava/reader-schema reader-schema
                 :piotr-yuxuan.slava/subject-name subject-name
                 :piotr-yuxuan.slava/schema-id (resolve-schema-id this m subject-name))))
  (close [_] (.close inner)))

(defn ^ClojureSerializer serializer
  ([inner-client]
   (ClojureSerializer. (atom nil) (KafkaAvroSerializer. inner-client) inner-client))
  ([inner-client config key?]
   (doto (serializer inner-client)
     (.configure config key?))))

(defn ^ClojureDeserializer deserializer
  ([inner-client]
   (ClojureDeserializer. (atom nil) (KafkaAvroDeserializer. inner-client) inner-client))
  ([inner-client config key?]
   (doto (deserializer inner-client)
     (.configure config key?))))

(defn ^Serde ->Serde
  ([inner-client]
   (Serdes/serdeFrom (serializer inner-client)
                     (deserializer inner-client)))
  ([inner-client config key?]
   (doto (->Serde inner-client)
     (.configure config key?))))
