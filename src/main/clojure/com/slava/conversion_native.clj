(ns com.slava.conversion-native
  "TODO: bigger, very simple and detailed documentation.

  I acknowledge and pead guilty for the current ugliness of code.

  Dispatch on schema name: takes precedence on all other dispatches so
  that you can introduce this library in some new code without
  changing too much. After all, it's newcomer' job to get used to its
  surroundings."
  (:require [com.slava.conversion-native-headers :refer :all])
  (:import (org.apache.avro Schema Schema$Type Schema$FixedSchema Schema$UnionSchema Schema$MapSchema Schema$ArraySchema Schema$EnumSchema Schema$RecordSchema Schema$Field Conversions$DecimalConversion Conversions$UUIDConversion Conversion LogicalType SchemaBuilder UnresolvedUnionException)
           (java.util Collections LinkedHashMap)
           (org.apache.avro.generic GenericRecord GenericRecordBuilder GenericFixed GenericData$Fixed)
           (java.nio ByteBuffer)
           (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimeMicrosConversion TimeConversions$TimeMillisConversion TimeConversions$TimestampMicrosConversion TimeConversions$TimestampMillisConversion)
           (java.time Period)
           (com.slava NativeAvroSerdeConfig))
  (:gen-class :name com.slava.ConversionNative
              :implements [com.slava.Conversion]
              :constructors {[] [], [java.util.Map] []}
              :init init
              :state config
              :prefix "impl-"))

(defn impl-init
  ([] [[] (atom {})])
  ;; Used for tests
  ([m] [[] (atom m)]))

(defn config->map [^NativeAvroSerdeConfig config]
  (merge
    {:field-name (keyword (.getString config NativeAvroSerdeConfig/COM_SLAVA_FIELD_NAME_CONVERSION_CONFIG))
     :map-key (keyword (.getString config NativeAvroSerdeConfig/COM_SLAVA_MAP_KEY_CONVERSION_CONFIG))
     :enum-type (keyword (.getString config NativeAvroSerdeConfig/COM_SLAVA_ENUM_CONVERSION_CONFIG))}
    (when (.getBoolean config NativeAvroSerdeConfig/COM_SLAVA_INCLUDE_SCHEMA_IN_MAP_CONFIG)
      {:schema-key (keyword (.getString config NativeAvroSerdeConfig/ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG))})))

(defn impl-configure
  [this ^NativeAvroSerdeConfig config]
  (reset! (.config this) (config->map config)))

(defn from-avro
  [this ^Schema schema data]
  (or (from-avro-schema-name this schema data)
      (from-avro-logical-type this schema data)
      (from-avro-schema-type this schema data)
      data))

(defn to-avro
  [this ^Schema schema data]
  (or (to-avro-schema-name this schema data)
      (to-avro-logical-type this schema data)
      (to-avro-schema-type this schema data)
      data))

(defn impl-fromAvro [this schema object] (from-avro this schema object))
(defn impl-toAvro [this schema object] (to-avro this schema object))

;;;
;;; Implementation of dispatch on schema types
;;;

(defmethod from-avro-schema-type Schema$Type/RECORD [this ^Schema$RecordSchema schema ^GenericRecord data]
  (let [m! (transient {})]
    (doseq [^Schema$Field field (.getFields (.getSchema data))]
      (assoc! m! (from-avro-field-name this schema field) (from-avro this (.schema field) (.get data (.name field)))))
    (when-let [schema-key (:schema-key @(.config this))]
      (assoc! m! schema-key schema))
    (persistent! m!)))
(defmethod to-avro-schema-type Schema$Type/RECORD [this ^Schema$RecordSchema schema data]
  (let [builder (new GenericRecordBuilder schema)]
    (doseq [^Schema$Field field (filter #(contains? data (from-avro-field-name this schema %)) (.getFields schema))]
      (.set builder (.name field) (to-avro this (.schema field) (get data (from-avro-field-name this schema field)))))
    (.build builder)))

(defmethod from-avro-schema-type Schema$Type/ENUM [this ^Schema$EnumSchema schema data] (from-avro-enum-type this schema data))
(defmethod to-avro-schema-type Schema$Type/ENUM [this ^Schema$EnumSchema schema data] (to-avro-enum-type this schema data))

(defmethod from-avro-schema-type Schema$Type/ARRAY [this ^Schema$ArraySchema schema data]
  (let [l! (transient [])]
    (doseq [v data]
      (conj! l! (from-avro this (.getElementType schema) v)))
    (persistent! l!)))
(defmethod to-avro-schema-type Schema$Type/ARRAY [this ^Schema$ArraySchema schema data]
  (let [element-type (.getElementType schema)]
    (vec (map #(to-avro this element-type %) data))))

(defmethod from-avro-schema-type Schema$Type/MAP [this ^Schema$MapSchema schema data]
  (let [m! (transient {})]
    (doseq [[k v] data]
      (assoc! m! (str k) (from-avro this (.getValueType schema) v)))
    (persistent! m!)))
(defmethod to-avro-schema-type Schema$Type/MAP [this ^Schema$MapSchema schema data]
  (let [m! (LinkedHashMap.)]
    (doseq [[k v] data]
      (.put m! (str k) (to-avro this (.getValueType schema) v)))
    (Collections/unmodifiableMap m!)))

(defmethod from-avro-schema-type Schema$Type/UNION [this ^Schema$UnionSchema schema data]
  (some (fn first-matching-type [schema]
          (try (from-avro this schema data) (catch Exception _)))
        (condp = (.getType schema)
          Schema$Type/UNION (.getTypes schema)
          Schema$Type/ARRAY (.getTypes (.getElementType schema)))))
(defmethod to-avro-schema-type Schema$Type/UNION [this ^Schema$UnionSchema schema data]
  (some (fn first-matching-type [schema]
          (try (to-avro this schema data) (catch Exception _)))
        (condp = (.getType schema)
          Schema$Type/UNION (.getTypes schema)
          Schema$Type/ARRAY (.getTypes (.getElementType schema)))))

(defmethod from-avro-schema-type Schema$Type/FIXED [this ^Schema$FixedSchema schema data]
  (doto (ByteBuffer/allocate (.getFixedSize schema)) (.put (.bytes ^GenericFixed data)) (.rewind)))
(defmethod to-avro-schema-type Schema$Type/FIXED [this ^Schema$FixedSchema schema data]
  (GenericData$Fixed. schema (.array ^ByteBuffer data)))

(defmethod from-avro-schema-type Schema$Type/STRING [this ^Schema$FixedSchema schema data] (str data))

;;;
;;; Implementation of dispatch on logical types
;;;

(def duration-logical-type (LogicalType. "duration"))
(def duration-schema
  (.addToSchema duration-logical-type
                (-> (SchemaBuilder/builder)
                    (.fixed "duration")
                    (.size 12))))

(defn duration-conversion []
  "Not implemented in upstream avro Conversions." ;; TODO
  (proxy [Conversion] []
    (getConvertedType [] Period)
    (getRecommendedSchema [] duration-schema)
    (getLogicalTypeName [] (.getName duration-logical-type))
    (fromFixed [value schema type] value)
    (toFixed [value schema type] value)))

(def logical-type-conversions
  #{{:logical-type-name "decimal" :conversion (delay (Conversions$DecimalConversion.))}
    {:logical-type-name "uuid" :conversion (delay (Conversions$UUIDConversion.))}
    {:logical-type-name "date" :conversion (delay (TimeConversions$DateConversion.))}
    {:logical-type-name "time-millis" :conversion (delay (TimeConversions$TimeMillisConversion.))}
    {:logical-type-name "time-micros" :conversion (delay (TimeConversions$TimeMicrosConversion.))}
    {:logical-type-name "timestamp-millis" :conversion (delay (TimeConversions$TimestampMillisConversion.))}
    {:logical-type-name "timestamp-micros" :conversion (delay (TimeConversions$TimestampMicrosConversion.))}
    {:logical-type-name "duration" :conversion (delay (duration-conversion))}})

;; I've got mixed feelings about this macro. On one hand it allows
;; explicit case dispatch values on Enum, which is good. On the other
;; hand, the best macro is the one which doesn't get written.
(defmacro logical-type-implementation!
  [logical-type-conversions]
  `(doseq [{:keys [~'logical-type-name ~'conversion]} logical-type-conversions]
     (defmethod from-avro-logical-type ~'logical-type-name ~'[_ schema data]
       (case (.ordinal (.getType ~'schema))
         ~(.ordinal Schema$Type/FIXED) (.fromFixed @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/STRING) (.fromCharSequence @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/BYTES) (.fromBytes @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/INT) (.fromInt @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/LONG) (.fromLong @~'conversion ~'data ~'schema (.getLogicalType ~'schema))))
     (defmethod to-avro-logical-type ~'logical-type-name ~'[_ schema data]
       (case (.ordinal (.getType ~'schema))
         ~(.ordinal Schema$Type/FIXED) (.toFixed @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/STRING) (.toCharSequence @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/BYTES) (.toBytes @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/INT) (.toInt @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/LONG) (.toLong @~'conversion ~'data ~'schema (.getLogicalType ~'schema))))))

(logical-type-implementation! logical-type-conversions)

;;;
;;; Implementation of dispatch on schema types
;;;

