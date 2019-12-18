(ns com.slava.core
  (:require [clojure.test :refer :all])
  (:import (org.apache.avro Schema$Field Schema)
           (org.apache.avro.generic GenericRecordBuilder GenericData$Record)))

(defn map->generic-data-record
  [^Schema schema m]
  (let [builder (new GenericRecordBuilder schema)]
    (doseq [^Schema$Field field (.getFields schema)]
      (.set builder field (get m (.name field))))
    (.build builder)))

(defn generic-data-record->map
  [^GenericData$Record record]
  (reduce (fn [m field] (assoc m (.name field) (.get record ^String (.name field))))
          {}
          (.getFields (.getSchema record))))
