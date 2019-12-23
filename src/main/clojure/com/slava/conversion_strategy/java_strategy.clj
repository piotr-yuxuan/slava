(ns com.slava.conversion-strategy.java-strategy
  "TODO: bigger, very simple and detailed documentation."
  (:require [clojure.spec.alpha :as s]
            [com.slava.generic-specs :refer [schema-spec seconds-expressed-in-nanoseconds micros-expressed-in-nanoseconds]])
  (:import (org.apache.avro Schema Schema$Type Schema$BooleanSchema Schema$DoubleSchema Schema$FloatSchema Schema$LongSchema Schema$IntSchema Schema$BytesSchema Schema$StringSchema Schema$FixedSchema Schema$UnionSchema Schema$MapSchema Schema$ArraySchema Schema$EnumSchema Schema$RecordSchema Schema$NullSchema Schema$Field LogicalType LogicalTypes$Decimal)
           (com.slava ConversionStrategy$Dispatch)
           (java.util Collections HashMap LinkedHashMap ArrayList UUID)
           (org.apache.avro.generic GenericRecord GenericRecordBuilder GenericFixed GenericData$Fixed GenericData$EnumSymbol)
           (java.nio ByteBuffer)
           (java.time LocalDate LocalTime Instant))
  (:gen-class :name com.slava.conversion_strategy.JavaStrategy
              :implements [com.slava.ConversionStrategy]
              :prefix "impl-"))

(defmulti from-avro-schema-type (fn [^Schema schema data] (ConversionStrategy$Dispatch/schemaType schema data)))
(defmulti from-avro-logical-type (fn [^Schema schema data] (ConversionStrategy$Dispatch/logicalType schema data)))
(defmulti from-avro-schema-name (fn [^Schema schema data] (ConversionStrategy$Dispatch/schemaName schema data)))

(defmulti to-avro-schema-type (fn [^Schema schema data] (ConversionStrategy$Dispatch/schemaType schema data)))
(defmulti to-avro-logical-type (fn [^Schema schema data] (ConversionStrategy$Dispatch/logicalType schema data)))
(defmulti to-avro-schema-name (fn [^Schema schema data] (ConversionStrategy$Dispatch/schemaName schema data)))

(defmethod from-avro-schema-type :default [_ data] nil)
(defmethod from-avro-logical-type :default [_ data] nil)
(defmethod from-avro-schema-name :default [_ data] nil)
(defmethod to-avro-schema-type :default [_ data] nil)
(defmethod to-avro-logical-type :default [_ data] nil)
(defmethod to-avro-schema-name :default [_ data] nil)

(defn from-avro [^Schema schema data] (or (from-avro-schema-name schema data)
                                          (from-avro-logical-type schema data)
                                          (from-avro-schema-type schema data)
                                          data))
(defn to-avro [^Schema schema data] (or (to-avro-schema-name schema data)
                                        (to-avro-logical-type schema data)
                                        (to-avro-schema-type schema data)
                                        data))

(defn impl-fromAvro [_ schema object] (from-avro schema object))
(defn impl-toAvro [_ schema object] (to-avro schema object))

;;;
;;; Implementation of dispatch on schema types
;;;

(defmethod from-avro-schema-type Schema$Type/RECORD [^Schema$RecordSchema schema ^GenericRecord data]
  (let [m! (HashMap.)]
    (doseq [^Schema$Field field (.getFields (.getSchema data))]
      (.put m! (.name field) (from-avro (.schema field) (.get data (.name field)))))
    (Collections/unmodifiableMap m!)))
(defmethod to-avro-schema-type Schema$Type/RECORD [^Schema$RecordSchema schema data]
  (let [builder (new GenericRecordBuilder schema)]
    (doseq [^Schema$Field field (filter #(contains? data (.name %)) (.getFields schema))]
      (.set builder (.name field) (to-avro (.schema field) (get data (.name field)))))
    (.build builder)))

(defmethod from-avro-schema-type Schema$Type/ENUM [^Schema$EnumSchema schema data]
  (str data))
(defmethod to-avro-schema-type Schema$Type/ENUM [^Schema$EnumSchema schema data]
  (GenericData$EnumSymbol. schema data))

(defmethod from-avro-schema-type Schema$Type/ARRAY [^Schema$ArraySchema schema data]
  (let [l! (ArrayList.)]
    (doseq [v data]
      (.add l! (from-avro (.getElementType schema) v)))
    (Collections/unmodifiableList l!)))
(defmethod to-avro-schema-type Schema$Type/ARRAY [^Schema$ArraySchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/MAP [^Schema$MapSchema schema data]
  (let [m! (LinkedHashMap.)]
    (doseq [[k v] data]
      (.put m! (str k) (from-avro (.getValueType schema) v)))
    (Collections/unmodifiableMap m!)))
(defmethod to-avro-schema-type Schema$Type/MAP [^Schema$MapSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/UNION [^Schema$UnionSchema schema data]
  (let [data-schema (some (fn [inner-schema]
                            (when (s/valid? (schema-spec inner-schema) data)
                              inner-schema))
                          (.getTypes schema))]
    (from-avro data-schema data)))
(defmethod to-avro-schema-type Schema$Type/UNION [^Schema$UnionSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/FIXED [^Schema$FixedSchema schema data]
  (doto (ByteBuffer/allocate (.getFixedSize schema)) (.put (.bytes ^GenericFixed data)) (.rewind)))
(defmethod to-avro-schema-type Schema$Type/FIXED [^Schema$FixedSchema schema data]
  (GenericData$Fixed. schema (.array ^ByteBuffer data)))

(defmethod from-avro-schema-type Schema$Type/STRING [^Schema$StringSchema schema data] (str data))
(defmethod to-avro-schema-type Schema$Type/STRING [^Schema$StringSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/BYTES [^Schema$BytesSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/BYTES [^Schema$BytesSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/INT [^Schema$IntSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/INT [^Schema$IntSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/LONG [^Schema$LongSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/LONG [^Schema$LongSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/FLOAT [^Schema$FloatSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/FLOAT [^Schema$FloatSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/DOUBLE [^Schema$DoubleSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/DOUBLE [^Schema$DoubleSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/BOOLEAN [^Schema$BooleanSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/BOOLEAN [^Schema$BooleanSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/NULL [^Schema$NullSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/NULL [^Schema$NullSchema schema data] data)

;;;
;;; Implementation of dispatch on logical types
;;;

(defmethod from-avro-logical-type "decimal" [schema data] data)
(defmethod to-avro-logical-type "decimal" [schema data] data)

(defmethod from-avro-logical-type "uuid" [schema ^String data] (UUID/fromString data))
(defmethod to-avro-logical-type "uuid" [schema ^UUID data] (str data))

(defmethod from-avro-logical-type "date" [schema ^Integer days-from-epoch] (LocalDate/ofEpochDay days-from-epoch))
(defmethod to-avro-logical-type "date" [schema ^LocalDate data] (int (.toEpochDay data)))

(defmethod from-avro-logical-type "time-millis" [schema ^Integer milliseconds-after-midnight] (LocalTime/ofNanoOfDay (* 1e6 milliseconds-after-midnight)))
(defmethod to-avro-logical-type "time-millis" [schema ^LocalTime local-time] (int (/ (.toNanoOfDay local-time) 1e6)))

(defmethod from-avro-logical-type "time-micros" [schema ^Long data] (LocalTime/ofNanoOfDay (* 1e3 data)))
(defmethod to-avro-logical-type "time-micros" [schema ^LocalTime data] (long (/ (.toNanoOfDay data) 1e3)))

(defmethod from-avro-logical-type "timestamp-millis" [schema ^Long milliseconds-from-epoch] (Instant/ofEpochMilli milliseconds-from-epoch))
(defmethod to-avro-logical-type "timestamp-millis" [schema ^Instant instant] (long (.toEpochMilli instant)))

(defmethod from-avro-logical-type "timestamp-micros" [schema ^Long microseconds-from-epoch]
  (let [nanoseconds-from-epoch (long (* 1e3 microseconds-from-epoch))
        seconds-from-epoch (seconds-expressed-in-nanoseconds nanoseconds-from-epoch)
        nanosecond-of-second (micros-expressed-in-nanoseconds (- nanoseconds-from-epoch seconds-from-epoch))]
    (Instant/ofEpochSecond (/ seconds-from-epoch 1e9) nanosecond-of-second)))
(defmethod to-avro-logical-type "timestamp-micros" [schema ^Instant instant]
  (long (+ (* 1e6 (.getEpochSecond instant))
           (Math/round (double (/ (.getNano instant) 1e3))))))

(defmethod from-avro-logical-type "duration" [^Schema$FixedSchema schema data] data)
(defmethod to-avro-logical-type "duration" [schema data] data)
