(ns com.slava.generic-specs
  "Specification of datastructures used by generic avro schemas"
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as test.g])
  (:import (org.apache.avro Schema Schema$EnumSchema)
           (org.apache.avro.generic GenericData$EnumSymbol GenericData$StringType)
           (org.apache.avro.util Utf8)
           (org.apache.avro.generic GenericData$StringType GenericData$Fixed GenericRecord)
           (org.apache.avro Schema Schema$EnumSchema Schema$FixedSchema Schema$RecordSchema Schema$ArraySchema Schema$MapSchema Schema$StringSchema Schema$BytesSchema Schema$IntSchema Schema$LongSchema Schema$FloatSchema Schema$DoubleSchema Schema$BooleanSchema Schema$NullSchema Schema$Field SchemaBuilder$MapDefault SchemaBuilder SchemaBuilder$FieldAssembler SchemaBuilder$RecordBuilder)
           (java.nio ByteBuffer)))

(def avro-null?
  "null: no value"
  nil?)

(def avro-boolean?
  "boolean: a binary value"
  boolean?)

(def avro-int?
  "Int: 32-bit signed two's complement integer"
  (s/with-gen int? #(test.g/fmap int (s/gen int?))))

(def avro-long?
  "Long: 64-bit signed integer"
  (s/with-gen
    #(instance? Long %)
    #(test.g/fmap
       long
       (test.g/double* {:infinite? false
                        :NaN? false
                        :min Long/MIN_VALUE
                        :max Long/MAX_VALUE}))))

(def avro-float?
  "Imprecise. Single precision (32-bit) IEEE 754 floating-point number"
  (s/with-gen
    (s/and #(instance? Float %)
           #(not (Float/isNaN %))
           #(Float/isFinite %))
    #(test.g/fmap
       float
       (test.g/double* {:infinite? false
                        :NaN? false
                        :min Float/MIN_VALUE
                        :max Float/MAX_VALUE}))))

(def avro-double?
  "Imprecise. Double precision (64-bit) IEEE 754 floating-point number"
  (s/with-gen
    (s/and #(instance? Double %)
           #(not (Double/isNaN %))
           #(Double/isFinite %))
    #(test.g/fmap
       double
       (test.g/double* {:infinite? false
                        :NaN? false
                        :min Double/MIN_VALUE
                        :max Double/MAX_VALUE}))))

(def ->avro-fixed?
  (memoize
    (fn [^Schema$FixedSchema fixed-schema]
      (s/with-gen
        (fn [fixed]
          (and (instance? GenericData$Fixed fixed)
               (= fixed-schema (.getSchema fixed))))
        #(test.g/fmap (comp (fn [^bytes b] (GenericData$Fixed. fixed-schema b))
                            byte-array)
                      (test.g/vector test.g/byte (.getFixedSize fixed-schema)))))))

(def to-avro-bytes?
  "Sequence of 8-bit unsigned bytes."
  (memoize
    (fn []
      (let [capacity (rand-int 100)] ;; FIXME magic, should be changed
        (s/with-gen
          (s/and #(instance? ByteBuffer %)
                 #(.capacity ^ByteBuffer %))
          #(test.g/fmap (comp (fn [^bytes b] (doto (ByteBuffer/allocate capacity) (.put b) .rewind)) byte-array)
                        (test.g/vector test.g/byte capacity)))))))

(def to-avro-string?
  "Sequence of unicode characters"
  (memoize
    (fn to-avro-string?
      ([] (to-avro-string? GenericData$StringType/Utf8))
      ([^GenericData$StringType stringType]
       (if (= GenericData$StringType/String stringType)
         string?
         (s/with-gen
           #(instance? Utf8 %)
           (fn [] (test.g/fmap
                    #(Utf8. ^String %)
                    (s/gen string?)))))))))

(def ->avro-enum?
  "Avro enum complex type"
  (memoize
    (fn [^Schema$EnumSchema enum-schema]
      (s/with-gen
        #(and (instance? GenericData$EnumSymbol %)
              (.hasEnumSymbol enum-schema (.toString ^GenericData$EnumSymbol %)))
        (fn [] (test.g/fmap #(GenericData$EnumSymbol. ^Schema enum-schema %)
                            (s/gen (set (.getEnumSymbols enum-schema)))))))))

(def ->avro-array?
  "Avro array complex type"
  (memoize
    (fn [item-avro-type]
      (s/with-gen
        (s/coll-of item-avro-type :kind list?)
        #(test.g/list (s/gen item-avro-type))))))

(def ->avro-map?
  "Avro array complex type"
  (memoize
    (fn [value-avro-type]
      (s/with-gen
        (s/map-of string? value-avro-type)
        #(test.g/map (s/gen string?)
                     (s/gen value-avro-type))))))

;; spec-alpha2 would be much better, sorry for that.  Also, this
;; doesn't allow for memoization, which is to say there isn't a
;; singleton for each union.
(defmacro ->avro-union?
  "Avro union complex type. When specified, the default value must match
  the first element of the union. Thus, `null` is usually listed
  first. Unions may not contain more than one schema with the same
  type, except for the named types record, fixed and enum; two types
  with different names are permitted."
  [& avro-united-types]
  `(s/or ~@(mapcat (juxt (comp keyword str) identity) `~avro-united-types)))

(declare ->avro-record?)

(defprotocol SchemaSpec (schema-spec [schema]))

(extend-protocol SchemaSpec
  Schema$StringSchema (schema-spec [schema]
                        (if-let [s (.getObjectProp schema "avro.java.string")]
                          (to-avro-string? (Enum/valueOf GenericData$StringType s))
                          (to-avro-string?)))
  Schema$BytesSchema (schema-spec [_] (to-avro-bytes?))
  Schema$IntSchema (schema-spec [_] avro-int?)
  Schema$LongSchema (schema-spec [_] avro-long?)
  Schema$FloatSchema (schema-spec [_] avro-float?)
  Schema$DoubleSchema (schema-spec [_] avro-double?)
  Schema$BooleanSchema (schema-spec [_] avro-boolean?)
  Schema$NullSchema (schema-spec [_] avro-null?)
  Schema$EnumSchema (schema-spec [schema] (->avro-enum? schema))
  Schema$ArraySchema (schema-spec [schema] (->avro-array? (.getElementType schema)))
  Schema$MapSchema (schema-spec [schema] (->avro-map? (.getValueType schema)))
  Schema$FixedSchema (schema-spec [schema] (->avro-fixed? schema))
  Schema$RecordSchema (schema-spec [schema] (->avro-record? schema)))

(def ->avro-record?
  ;; If you want to generate records, don't loose time with macro subtleties, use clojure-spec2 instead.
  (memoize
    (fn [schema]
      (s/and #(instance? GenericRecord %)
             (fn [record]
               (every? (fn [field]
                         (s/valid? (schema-spec (.schema field)) (.get record (.name field))))
                       (.getFields schema)))))))
