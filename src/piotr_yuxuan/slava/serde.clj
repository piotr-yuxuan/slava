(ns piotr-yuxuan.slava.serde
  "FIXME add cljdoc"
  (:require [piotr-yuxuan.slava.encode :refer [encode]]
            [piotr-yuxuan.slava.decode :refer [decode]])
  (:import (io.confluent.kafka.schemaregistry.avro AvroSchema)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroSerializer KafkaAvroSerializerConfig KafkaAvroDeserializer)
           (io.confluent.kafka.serializers.subject.strategy SubjectNameStrategy)
           (java.util Map)
           (org.apache.avro Schema)
           (org.apache.kafka.common.serialization Serializer Deserializer Serdes)
           (clojure.lang Atom)
           (org.apache.avro.generic GenericContainer)))

(defn subject-name
  "FIXME add cljdoc"
  [{:keys [config]} ^String topic m]
  (if-let [subject-name (get (meta m) :piotr-yuxuan.slava/subject-name)]
    subject-name
    (let [{:keys [key? ^SubjectNameStrategy subject-name-strategy]} @config]
      (.subjectName subject-name-strategy topic key? nil))))

(defn resolve-schema-id
  "FIXME add cljdoc"
  [^SchemaRegistryClient inner-client ^String topic ^Map m]
  (if-let [schema-id (get (meta m) :piotr-yuxuan.slava/schema-id)]
    schema-id
    (->> (subject-name inner-client topic m)
         (.getLatestSchemaMetadata inner-client)
         (.getId))))

(defn resolve-schema
  "FIXME add cljdoc"
  ^Schema [{:keys [inner-client] :as this} ^String topic ^Map m]
  (cond (contains? (meta m) :piotr-yuxuan.slava/writer-schema) (get (meta m) :piotr-yuxuan.slava/writer-schema)
        (contains? (meta m) :piotr-yuxuan.slava/reader-schema) (get (meta m) :piotr-yuxuan.slava/reader-schema)
        :else (->> (resolve-schema-id this topic m)
                   ^AvroSchema (.getSchemaById inner-client)
                   (.rawSchema))))

(defn subject-name-strategy
  [inner-config key?]
  (let [inner-config-obj (KafkaAvroSerializerConfig. inner-config)]
    (if key?
      (.keySubjectNameStrategy inner-config-obj)
      (.valueSubjectNameStrategy inner-config-obj))))

(defn -configure
  [{:keys [config inner]} value key?]
  (let [inner-config (->> value (filter (comp string? key)) (into {}))]
    (reset! config (assoc value
                     :key? key?
                     :subject-name-strategy (subject-name-strategy inner-config key?)))
    (.configure inner inner-config key?)))

(defrecord ClojureSerializer [^Atom config
                              ^KafkaAvroSerializer inner
                              ^SchemaRegistryClient inner-client]
  Serializer
  (configure [this value key?] (-configure this value key?))
  (serialize [this topic m]
    (->> m
         (encode config (resolve-schema this topic m))
         (.serialize inner topic)))
  (close [_] (.close inner)))

(defrecord ClojureDeserializer [^Atom config
                                ^KafkaAvroDeserializer inner
                                ^SchemaRegistryClient inner-client]
  Deserializer
  (configure [this value key?] (-configure this value key?))
  (deserialize [_ topic data]
    (let [^GenericContainer generic-container (.deserialize inner topic data)
          reader-schema (.getSchema generic-container)
          m (decode @config reader-schema generic-container)]
      (vary-meta m assoc :piotr-yuxuan.slava/reader-schema reader-schema)))
  (close [_] (.close inner)))

(defn serde
  [inner-client config key?]
  (Serdes/serdeFrom ^Serializer (doto (ClojureSerializer. nil (KafkaAvroSerializer. inner-client) inner-client)
                                  (.configure config key?))
                    ^Deserializer (doto (ClojureDeserializer. nil (KafkaAvroDeserializer. inner-client) inner-client)
                                    (.configure config key?))))
