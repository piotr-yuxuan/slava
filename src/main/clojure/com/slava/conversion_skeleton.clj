(ns com.slava.conversion-skeleton
  (:require [com.slava.generic-specs :refer [schema-spec]])
  (:import (org.apache.avro Schema Schema$Type Schema$BooleanSchema Schema$DoubleSchema Schema$FloatSchema Schema$LongSchema Schema$IntSchema Schema$BytesSchema Schema$StringSchema Schema$FixedSchema Schema$UnionSchema Schema$MapSchema Schema$ArraySchema Schema$EnumSchema Schema$RecordSchema Schema$NullSchema Schema$Field)
           (com.slava Conversion$Dispatch)
           (org.apache.avro.generic GenericRecord))
  (:gen-class :name com.slava.conversion_strategy.SkeletonStrategy
              :implements [com.slava.Conversion]
              :prefix "impl-"))

(defmulti from-avro-schema-type (fn [^Schema schema data] (Conversion$Dispatch/schemaType schema data)))
(defmulti from-avro-logical-type (fn [^Schema schema data] (Conversion$Dispatch/logicalType schema data)))
(defmulti from-avro-schema-name (fn [^Schema schema data] (Conversion$Dispatch/schemaName schema data)))

(defmulti to-avro-schema-type (fn [^Schema schema data] (Conversion$Dispatch/schemaType schema data)))
(defmulti to-avro-logical-type (fn [^Schema schema data] (Conversion$Dispatch/logicalType schema data)))
(defmulti to-avro-schema-name (fn [^Schema schema data] (Conversion$Dispatch/schemaName schema data)))

(defmethod from-avro-schema-type :default [_ data] data)
(defmethod from-avro-logical-type :default [_ data] data)
(defmethod from-avro-schema-name :default [_ data] data)
(defmethod to-avro-schema-type :default [_ data] data)
(defmethod to-avro-logical-type :default [_ data] data)
(defmethod to-avro-schema-name :default [_ data] data)

(defn from-avro [^Schema schema data] (some->> data (from-avro-schema-name schema) (from-avro-logical-type schema) (from-avro-schema-type schema)))
(defn to-avro [^Schema schema data] (some->> data (to-avro-schema-name schema) (to-avro-logical-type schema) (to-avro-schema-type schema)))

(defn impl-fromAvro [_ schema object] (from-avro schema object))
(defn impl-toAvro [_ schema object] (to-avro schema object))

;;;
;;; Implementation of dispatch on schema types
;;;

(defmethod from-avro-schema-type Schema$Type/RECORD [^Schema$RecordSchema schema ^GenericRecord data] data)
(defmethod to-avro-schema-type Schema$Type/RECORD [^Schema$RecordSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/ENUM [^Schema$EnumSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/ENUM [^Schema$EnumSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/ARRAY [^Schema$ArraySchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/ARRAY [^Schema$ArraySchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/MAP [^Schema$MapSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/MAP [^Schema$MapSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/UNION [^Schema$UnionSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/UNION [^Schema$UnionSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/FIXED [^Schema$FixedSchema schema data] data)
(defmethod to-avro-schema-type Schema$Type/FIXED [^Schema$FixedSchema schema data] data)

(defmethod from-avro-schema-type Schema$Type/STRING [^Schema$StringSchema schema data] data)
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

(defmethod from-avro-logical-type "date" [schema data] data)
(defmethod to-avro-logical-type "date" [schema data] data)

(defmethod from-avro-logical-type "time-millis" [schema data] data)
(defmethod to-avro-logical-type "time-millis" [schema data] data)

(defmethod from-avro-logical-type "time-micros" [schema data] data)
(defmethod to-avro-logical-type "time-micros" [schema data] data)

(defmethod from-avro-logical-type "timestamp-millis" [schema data] data)
(defmethod to-avro-logical-type "timestamp-millis" [schema data] data)

(defmethod from-avro-logical-type "timestamp-micros" [schema data] data)
(defmethod to-avro-logical-type "timestamp-micros" [schema data] data)

(defmethod from-avro-logical-type "duration" [schema data] data)
(defmethod to-avro-logical-type "duration" [schema data] data)

;;;
;;; Implementation of dispatch on schema names
;;;
