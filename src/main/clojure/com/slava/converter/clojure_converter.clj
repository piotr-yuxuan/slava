(ns com.slava.converter.clojure-converter
  (:import (org.apache.avro Schema Schema$RecordSchema)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder))
  (:gen-class :name com.slava.converter.ClojureConversionStrategy
              :implements [com.slava.ConversionStrategy]
              :prefix "impl-"))

(defn generic-data-record->map
  [^GenericData$Record record]
  (let [m! (transient {})]
    (doseq [^String field-name (->> (.getSchema record)
                                    (.getFields)
                                    (map #(.name %)))]
      (assoc! m! field-name (.get record field-name)))
    (persistent! m!)))

(defn map->generic-data-record
  [^Schema schema m]
  (let [builder (new GenericRecordBuilder schema)]
    (doseq [^String field-name (->> (.getFields schema)
                                    (map #(.name %))
                                    (filter #(contains? m %)))]
      (.set builder field-name (get m field-name)))
    (.build builder)))

(defprotocol Conversion
  (->clj-type [^Schema schema data])
  (->avro-type [^Schema schema data]))

(extend-protocol Conversion
  Schema$RecordSchema
  (->clj-type [_ data] (generic-data-record->map data))
  (->avro-type [schema data] (map->generic-data-record schema data))

  nil
  (->clj-type [_ data] data)
  (->avro-type [_ data] data))

(defn impl-toNativeType [_ schema object] (->clj-type schema object))
(defn impl-toAvroType [_ schema object] (->avro-type schema object))
