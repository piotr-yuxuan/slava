(ns com.slava.conversion-strategy.clojure-strategy
  "This is an opinionated way to deal with Avro in Clojure. If you
  disagree, you're more than welcome to create your own strategy
  tailored to your needs. Strategies can be composed."
  (:require [com.slava.conversion-strategy.java-strategy :as java-strategy]
            [camel-snake-kebab.core :refer [->kebab-case-keyword]])
  (:import (org.apache.avro Schema$RecordSchema Schema$Field Schema Schema$EnumSchema)
           (org.apache.avro.generic GenericRecordBuilder GenericFixed))
  (:gen-class :name com.slava.conversion_strategy.ClojureStrategy
              :implements [com.slava.ConversionStrategy]
              :prefix "impl-"))

(defprotocol ClojureStrategy
  (to-clojure [^Schema schema data])
  (to-avro [^Schema schema data]))

(extend-protocol ClojureStrategy
  Schema$RecordSchema
  (to-clojure [_ data]
    (let [m! (transient {})]
      (doseq [^Schema$Field field (.getFields (.getSchema data))]
        (assoc! m! (->kebab-case-keyword (.name field)) (to-clojure (.schema field) (.get data (.name field)))))
      (persistent! m!)))
  (to-avro [schema data]
    (let [builder (new GenericRecordBuilder schema)]
      (doseq [^Schema$Field field (filter #(contains? data (->kebab-case-keyword (.name %))) (.getFields schema))]
        (.set builder (.name field) (to-avro (.schema field) (get data (->kebab-case-keyword (.name field))))))
      (.build builder)))

  Schema$EnumSchema
  ;; Throw an exception if Java Enum doesn't exist.
  ;; See avro-maven-plugin to generate Java class from Avro avdl files.
  (to-clojure [schema data] (Enum/valueOf (Class/forName (.getFullName schema)) (str data)))
  (to-avro [schema data] (java-strategy/to-avro schema (str data)))

  Object
  (to-clojure [schema data] (java-strategy/to-java schema data))
  (to-avro [schema data] (java-strategy/to-avro schema data))

  nil
  (to-clojure [schema data] (java-strategy/to-java schema data))
  (to-avro [schema data] (java-strategy/to-avro schema data)))

(defn impl-toConvertedType [_ schema object] (to-clojure schema object))
(defn impl-toAvroType [_ schema object] (to-avro schema object))
