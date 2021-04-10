(ns piotr-yuxuan.slava
  "FIXME add cljdoc"
  (:require [piotr-yuxuan.slava.config :as config]
            [piotr-yuxuan.slava.decode :refer [decode]]
            [piotr-yuxuan.slava.encode :refer [encode]])
  (:import (clojure.lang Atom)
           (io.confluent.kafka.schemaregistry.avro AvroSchema)
           (io.confluent.kafka.schemaregistry.client SchemaRegistryClient CachedSchemaRegistryClient)
           (io.confluent.kafka.serializers KafkaAvroSerializer KafkaAvroSerializerConfig KafkaAvroDeserializer)
           (io.confluent.kafka.serializers.subject.strategy SubjectNameStrategy)
           (java.nio ByteBuffer)
           (java.util Map)
           (org.apache.avro Schema)
           (org.apache.avro.generic GenericContainer)
           (org.apache.kafka.common.serialization Serializer Deserializer Serdes Serde)))

(defn ^String subject-name
  "FIXME add cljdoc"
  [{:keys [key? ^SubjectNameStrategy subject-name-strategy]} ^String topic]
  (.subjectName subject-name-strategy topic key? nil))

(defn ^String resolve-subject-name
  "FIXME add cljdoc"
  [config ^String topic m]
  (if (contains? (meta m) ::subject-name)
    (get (meta m) ::subject-name)
    (subject-name config topic)))

(defn cached-schema!
  "FIXME add cljdoc"
  [^CachedSchemaRegistryClient inner-client schema-id]
  (.rawSchema ^AvroSchema (.getSchemaById inner-client schema-id)))

(defn ^Schema resolve-schema
  "FIXME add cljdoc"
  [config ^CachedSchemaRegistryClient inner-client topic ^Map m]
  (cond (contains? (meta m) ::schema) (get (meta m) ::schema) ; User-defined, takes precedence.
        (contains? (meta m) ::writer-schema) (get (meta m) ::writer-schema)
        (contains? (meta m) ::reader-schema) (get (meta m) ::reader-schema)
        (contains? (meta m) ::schema-id) (cached-schema! inner-client (get (meta m) ::schema-id))
        :else (->> (resolve-subject-name config topic m)
                   ((get-in config [:subject-name->id :through]))
                   (cached-schema! inner-client))))

(defn subject-name->id
  [inner-client value]
  (let [found (get value :subject-name->id)]
    (if (= :default found)
      (let [subject-name->id (atom {})]
        {:ref subject-name->id
         :through (fn stub-through [subject-name]
                    (get @subject-name->id subject-name
                         (let [retrieved-id (.getId (.getLatestSchemaMetadata inner-client subject-name))]
                           (swap! subject-name->id assoc subject-name retrieved-id)
                           retrieved-id)))})
      found)))

(defn ^SubjectNameStrategy subject-name-strategy
  "FIXME add cljdoc"
  [inner-config key?]
  (let [inner-config-obj (KafkaAvroSerializerConfig. inner-config)]
    (if key?
      (.keySubjectNameStrategy inner-config-obj)
      (.valueSubjectNameStrategy inner-config-obj))))

(defn configure!
  "FIXME add cljdoc"
  [{:keys [config inner inner-client]} value key?]
  (let [inner-config (->> value
                          (remove (comp config/slava-key? key))
                          (into {}))]
    (reset! config (assoc value
                     :subject-name->id (subject-name->id inner-client value)
                     :key? key?
                     :subject-name-strategy (subject-name-strategy inner-config key?)))
    ;; Reflection warning: either a KafkaAvroSerializer or a KafkaAvroDeserializer.
    (.configure inner inner-config key?)))

(defrecord ClojureSerializer [^Atom config
                              ^KafkaAvroSerializer inner
                              ^SchemaRegistryClient inner-client]
  Serializer
  (configure [this value key?] (configure! this value key?))
  (serialize [_ topic m]
    (->> (resolve-schema config inner-client topic m)
         (encode @config m)
         (.serialize inner topic)))
  (close [_] (.close inner)))

(def int-size
  "In the JVM, an int always uses 4 bytes."
  4)

(defn schema-id
  "Extract the schema id as known in the schema registry."
  [data]
  (.getInt (ByteBuffer/wrap data 0 int-size)))

(defrecord ClojureDeserializer [^Atom config
                                ^KafkaAvroDeserializer inner
                                ^SchemaRegistryClient inner-client]
  Deserializer
  (configure [this value key?] (configure! this value key?))
  (deserialize [_ topic data]
    (let [^GenericContainer generic-container (.deserialize inner topic data)
          reader-schema (.getSchema generic-container)
          m (decode @config generic-container reader-schema)]
      (vary-meta m assoc
                 ::reader-schema reader-schema
                 ::subject-name (subject-name @config topic)
                 ::schema-id (schema-id data))))
  (close [_] (.close inner)))

(defn ^ClojureSerializer serializer
  "FIXME add cljdoc"
  ([inner-client]
   (ClojureSerializer. (atom nil) (KafkaAvroSerializer. inner-client) inner-client))
  ([inner-client config key?]
   (doto (serializer inner-client)
     (.configure config key?))))

(defn ^ClojureDeserializer deserializer
  "FIXME add cljdoc"
  ([inner-client]
   (ClojureDeserializer. (atom nil) (KafkaAvroDeserializer. inner-client) inner-client))
  ([inner-client config key?]
   (doto (deserializer inner-client)
     (.configure config key?))))

(defn ^Serde clojure-serde
  "FIXME add cljdoc"
  ([inner-client]
   (Serdes/serdeFrom (serializer inner-client)
                     (deserializer inner-client)))
  ([inner-client config key?]
   (doto (clojure-serde inner-client)
     (.configure config key?))))
