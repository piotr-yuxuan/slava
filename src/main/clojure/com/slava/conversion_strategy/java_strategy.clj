(ns com.slava.conversion-strategy.java-strategy
  (:require [com.slava.generic-specs :refer [schema-spec]]
            [clojure.spec.alpha :as s])
  (:import (org.apache.avro Schema$RecordSchema Schema$StringSchema Schema$MapSchema Schema$ArraySchema Schema$Field Schema$FixedSchema Schema$EnumSchema Schema Schema$UnionSchema)
           (org.apache.avro.generic GenericRecordBuilder GenericFixed GenericData$EnumSymbol GenericData$Fixed GenericRecord)
           (java.util Collections HashMap ArrayList LinkedHashMap)
           (java.nio ByteBuffer))
  (:gen-class :name com.slava.conversion_strategy.JavaStrategy
              :implements [com.slava.ConversionStrategy]
              :prefix "impl-"))

(defprotocol JavaStrategy
  (to-java [^Schema schema data])
  (to-avro [^Schema schema data]))

(extend-protocol JavaStrategy
  Schema$RecordSchema
  (to-java [_ data]
    (let [m! (HashMap.)]
      (doseq [^Schema$Field field (.getFields (.getSchema data))]
        (.put m! (.name field) (to-java (.schema field) (.get data (.name field)))))
      (Collections/unmodifiableMap m!)))
  (to-avro [schema data]
    (let [builder (new GenericRecordBuilder schema)]
      (doseq [^Schema$Field field (filter #(contains? data (.name %)) (.getFields schema))]
        (.set builder (.name field) (to-avro (.schema field) (get data (.name field)))))
      (.build builder)))

  Schema$StringSchema
  (to-java [_ data] (str data))
  (to-avro [_ data] data)

  Schema$MapSchema
  (to-java [schema data]
    (let [m! (LinkedHashMap.)]
      (doseq [[k v] data]
        (.put m! (str k) (to-java (.getValueType schema) v)))
      (Collections/unmodifiableMap m!)))
  (to-avro [_ data] data)

  Schema$ArraySchema
  (to-java [schema data]
    (let [l! (ArrayList.)]
      (doseq [v data]
        (.add l! (to-java (.getElementType schema) v)))
      (Collections/unmodifiableList l!)))
  (to-avro [_ data] data)

  Schema$FixedSchema
  (to-java [schema data] (doto (ByteBuffer/allocate (.getFixedSize schema)) (.put (.bytes ^GenericFixed data)) (.rewind)))
  (to-avro [schema data] (GenericData$Fixed. schema (.array ^ByteBuffer data)))

  Schema$EnumSchema
  (to-java [_ data] (str data))
  (to-avro [schema data] (GenericData$EnumSymbol. schema data))

  Schema$UnionSchema
  (to-java [schema data]
    (let [data-schema (some (fn [inner-schema]
                              (when (s/valid? (schema-spec inner-schema) data)
                                inner-schema))
                            (.getTypes schema))]
      (to-java data-schema data)))
  (to-avro [_ data] data)

  Object
  (to-java [_ data] data)
  (to-avro [_ data] data)

  nil
  (to-java [_ _] nil)
  (to-avro [_ _] nil))

(defn impl-toConvertedType [_ schema object] (to-java schema object))
(defn impl-toAvroType [_ schema object] (to-avro schema object))
