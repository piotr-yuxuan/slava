(ns com.slava.conversion-strategy
  (:import (org.apache.avro Schema)
           (org.apache.avro.generic GenericRecordBuilder GenericData$Record)))

(defprotocol ConversionStrategy
  (->native [^Schema schema data])
  (->avro [^Schema schema data]))

(defn unbox-from-record
  "Naive translation between a record and a map. Only the container type changes."
  [^GenericData$Record record]
  (let [m! (transient {})]
    (doseq [^String field-name (->> (.getSchema record)
                                    (.getFields)
                                    (map #(.name %)))]
      (assoc! m! field-name (.get record field-name)))
    (persistent! m!)))

(defn box-to-record
  "Naive translation between a record and a map. Only the container type changes."
  [^Schema schema m]
  (let [builder (new GenericRecordBuilder schema)]
    (doseq [^String field-name (->> (.getFields schema)
                                    (map #(.name %))
                                    (filter #(contains? m %)))]
      (.set builder field-name (get m field-name)))
    (.build builder)))
