(ns com.slava.conversion-strategy.default
  (:require [com.slava.conversion-strategy :refer [ConversionStrategy ->native ->avro]])
  (:import (org.apache.avro Schema$RecordSchema Schema$StringSchema Schema$MapSchema Schema$ArraySchema Schema$Field Schema$FixedSchema Schema$EnumSchema)
           (org.apache.avro.generic GenericRecordBuilder GenericFixed GenericData$EnumSymbol GenericData$Fixed GenericRecord)
           (java.util Collections HashMap ArrayList)
           (java.nio ByteBuffer))
  (:gen-class :name com.slava.conversion_strategy.Default
              :implements [com.slava.ConversionStrategy]
              :prefix "impl-"))

(extend-protocol ConversionStrategy
  Schema$RecordSchema
  (->native [_ data]
    (let [m! (HashMap.)]
      (doseq [^Schema$Field field (.getFields (.getSchema data))]
        (.put m! (.name field) (->native (.schema field) (.get data (.name field)))))
      (Collections/unmodifiableMap m!)))
  (->avro [schema data]
    (if (instance? GenericRecord data) ;; TODO make sure it's absolutely necessary, it's probably a smell
      data
      (let [builder (new GenericRecordBuilder schema)]
        (doseq [^Schema$Field field (filter #(contains? data (.name %)) (.getFields schema))]
          (.set builder (.name field) (->avro (.schema field) (get data (.name field)))))
        (.build builder))))

  Schema$StringSchema
  (->native [_ data] (str data))
  (->avro [_ data] data)

  Schema$MapSchema
  (->native [schema data]
    (let [m! (HashMap.)]
      (doseq [[k v] data]
        (.put m! (str k) (->native (.getValueType schema) v)))
      (Collections/unmodifiableMap m!)))
  (->avro [_ data] data)

  Schema$ArraySchema
  (->native [schema data]
    (let [l! (ArrayList.)]
      (doseq [v data]
        (.add l! (->native (.getElementType schema) v)))
      (Collections/unmodifiableList l!)))
  (->avro [_ data] data)

  Schema$FixedSchema
  (->native [schema data]
    (doto (ByteBuffer/allocate (.getFixedSize schema)) (.put (.bytes ^GenericFixed data)) (.rewind)))
  (->avro [schema data]
    (if (instance? GenericData$Fixed data) ;; TODO make sure it's absolutely necessary, it's probably a smell
      data
      (GenericData$Fixed. schema (.array ^ByteBuffer data))))

  Schema$EnumSchema
  (->native [_ data] (str data))
  (->avro [schema data] (GenericData$EnumSymbol. schema data))

  Object
  (->native [_ data] data)
  (->avro [_ data] data)

  nil
  (->native [_ _] nil)
  (->avro [_ _] nil))

(defn impl-toNativeType [_ schema object] (->native schema object))
(defn impl-toAvroType [_ schema object] (->avro schema object))
