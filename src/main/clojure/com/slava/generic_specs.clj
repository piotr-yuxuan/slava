(ns com.slava.generic-specs
  "Specification of datastructures used by generic avro schemas"
  (:require [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as test.g])
  (:import (org.apache.avro Schema Schema$EnumSchema)
           (org.apache.avro.generic GenericData$EnumSymbol GenericData$StringType)
           (org.apache.avro.util Utf8)
           (org.apache.avro.generic GenericData$StringType GenericData$Fixed GenericRecord)
           (org.apache.avro Schema Schema$EnumSchema Schema$FixedSchema Schema$RecordSchema Schema$ArraySchema Schema$MapSchema Schema$StringSchema Schema$BytesSchema Schema$IntSchema Schema$LongSchema Schema$FloatSchema Schema$DoubleSchema Schema$BooleanSchema Schema$NullSchema)
           (java.nio ByteBuffer)
           (java.time Instant LocalTime LocalDate)
           (java.time.temporal ChronoField)
           (java.util UUID)))

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
  "Avro array complex type. Uses a sorted map to preserve order."
  (memoize
    (fn [value-avro-type]
      (s/with-gen
        (s/map-of string? value-avro-type)
        #(test.g/fmap (fn [m] (into (sorted-map) m))
                      (test.g/map (s/gen string?)
                                  (s/gen value-avro-type)))))))

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

(def avro-uuid?
  "The uuid logical type represents a random generated universally
  unique identifier (UUID).

  A uuid logical type annotates an Avro string. The string has to
  conform with RFC-4122"
  (s/with-gen
    #(instance? UUID %)
    #(test.g/fmap (fn [_] (UUID/randomUUID)) ;; v4
                  ;; cheat
                  test.g/byte)))

(defn round-to-order
  "Round the nanosecond so that it represents a millisecond"
  [magnitude number]
  (let [most-precise-order (int (Math/pow 10 magnitude))]
    (->> most-precise-order
         (/ number)
         double Math/round
         (* most-precise-order)
         (cast (type number)))))

(def seconds-expressed-in-nanoseconds (partial round-to-order 9))
(def millis-expressed-in-nanoseconds (partial round-to-order 6))
(def micros-expressed-in-nanoseconds (partial round-to-order 3))

(defn ->avro-decimal?
  "The decimal logical type represents an arbitrary-precision signed
  decimal number of the form unscaled × 10-scale.

  A decimal logical type annotates Avro bytes or fixed types. The byte
  array must contain the two's-complement representation of the
  unscaled integer value in big-endian byte order. The scale is fixed,
  and is specified using an attribute.

  The following attributes are supported:

  - scale, a JSON integer representing the scale (optional). If not
    specified the scale is 0.
  - precision, a JSON integer representing the (maximum) precision of
    decimals stored in this type (required)."
  [precision scale]
  (assert (<= scale precision) (format "Invalid decimal scale: %s (greater than precision: %s)" scale precision))
  (s/with-gen
    #(instance? BigDecimal %)
    (fn []
      (test.g/fmap (fn [[integer-part decimal-part]] (BigDecimal/valueOf (+ (* integer-part (Math/pow 10 (- precision scale))) decimal-part) scale))
                   (test.g/tuple (test.g/large-integer* {:min 0
                                                         :max (dec (Math/pow 10 (- precision scale)))})
                                 (test.g/large-integer* {:min 0
                                                         :max (dec (Math/pow 10 scale))}))))))

(def avro-date?
  "The date logical type represents a date within the calendar, with
  no reference to a particular time zone or time of day.

  A date logical type annotates an Avro int, where the int stores the
  number of days from the unix epoch, 1 January 1970 (ISO calendar)."
  (s/with-gen
    #(instance? LocalDate %)
    (fn []
      (test.g/fmap
        (fn [[^Integer year ^Integer month ^Integer day-of-month]] (LocalDate/of year month day-of-month))
        (test.g/tuple (test.g/large-integer* {:min 1903 ;; Backward and forward https://en.wikipedia.org/wiki/Year_2038_problem
                                              :max 2037})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/MONTH_OF_YEAR))
                                              :max (.getMaximum (.range ChronoField/MONTH_OF_YEAR))})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/DAY_OF_MONTH))
                                              :max (.getMaximum (.range ChronoField/DAY_OF_MONTH))}))))))

(def avro-time-millis?
  "The time-millis logical type represents a time of day, with no
  reference to a particular calendar, time zone or date, with a
  precision of one millisecond.

  A time-millis logical type annotates an Avro int, where the int
  stores the number of milliseconds after midnight, 00:00:00.000."
  (s/with-gen
    #(instance? LocalTime %)
    (fn []
      (test.g/fmap
        (fn [[hour minute second nano-of-second]] (LocalTime/of hour minute second (millis-expressed-in-nanoseconds nano-of-second)))
        (test.g/tuple (test.g/large-integer* {:min (.getMinimum (.range ChronoField/HOUR_OF_DAY))
                                              :max (.getMaximum (.range ChronoField/HOUR_OF_DAY))})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/MINUTE_OF_HOUR))
                                              :max (.getMaximum (.range ChronoField/MINUTE_OF_HOUR))})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/SECOND_OF_MINUTE))
                                              :max (.getMaximum (.range ChronoField/SECOND_OF_MINUTE))})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/NANO_OF_SECOND))
                                              :max (.getMaximum (.range ChronoField/NANO_OF_SECOND))}))))))

(def avro-time-micros?
  "The time-micros logical type represents a time of day, with no
  reference to a particular calendar, time zone or date, with a
  precision of one microsecond.

  A time-micros logical type annotates an Avro long, where the long
  stores the number of microseconds after midnight, 00:00:00.000000."
  (s/with-gen
    #(instance? LocalTime %)
    (fn []
      (test.g/fmap
        (fn [[hour minute second nano-of-second]] (LocalTime/of hour minute second (micros-expressed-in-nanoseconds nano-of-second)))
        (test.g/tuple (test.g/large-integer* {:min (.getMinimum (.range ChronoField/HOUR_OF_DAY))
                                              :max (.getMaximum (.range ChronoField/HOUR_OF_DAY))})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/MINUTE_OF_HOUR))
                                              :max (.getMaximum (.range ChronoField/MINUTE_OF_HOUR))})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/SECOND_OF_MINUTE))
                                              :max (.getMaximum (.range ChronoField/SECOND_OF_MINUTE))})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/NANO_OF_SECOND))
                                              :max (.getMaximum (.range ChronoField/NANO_OF_SECOND))}))))))

(def avro-timestamp-millis?
  "The timestamp-millis logical type represents an instant on the global
  timeline, independent of a particular time zone or calendar, with a
  precision of one millisecond.

  A timestamp-millis logical type annotates an Avro long, where the
  long stores the number of milliseconds from the unix epoch, 1
  January 1970 00:00:00.000 UTC."
  (s/with-gen
    #(instance? Instant %)
    (fn []
      (test.g/fmap
        (fn [[epochSecond nanoAdjustment]] (Instant/ofEpochSecond epochSecond (millis-expressed-in-nanoseconds nanoAdjustment)))
        (test.g/tuple (test.g/large-integer* {:min (.getMinimum (.range ChronoField/INSTANT_SECONDS))
                                              :max (.getMaximum (.range ChronoField/INSTANT_SECONDS))})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/NANO_OF_SECOND))
                                              :max (.getMaximum (.range ChronoField/NANO_OF_SECOND))}))))))

(def avro-timestamp-micros?
  "The timestamp-micros logical type represents an instant on the global
  timeline, independent of a particular time zone or calendar, with a
  precision of one microsecond.

  A timestamp-micros logical type annotates an Avro long, where the
  long stores the number of microseconds from the unix epoch, 1
  January 1970 00:00:00.000000 UTC."
  ;; Implementation notes: sadly
  ;; java.time.Instant/MIN_SECOND != (.getMinimum (.range java.time.temporal.ChronoField/INSTANT_SECONDS))
  ;; java.time.Instant/MAX_SECOND != (.getMaximum (.range java.time.temporal.ChronoField/INSTANT_SECONDS))
  (s/with-gen
    #(instance? Instant %)
    (fn []
      (test.g/fmap
        (fn [[seconds-from-epoch nanosecond-of-second]]
          (Instant/ofEpochSecond seconds-from-epoch (micros-expressed-in-nanoseconds nanosecond-of-second)))
        (test.g/tuple (test.g/large-integer* {:min (.getEpochSecond Instant/MIN)
                                              :max (.getEpochSecond Instant/MAX)})
                      (test.g/large-integer* {:min (.getMinimum (.range ChronoField/NANO_OF_SECOND))
                                              :max (.getMaximum (.range ChronoField/NANO_OF_SECOND))}))))))
