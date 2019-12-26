(ns com.slava.conversion-native
  "TODO: bigger, very simple and detailed documentation.

  Dispatch on schema name: takes precedence on all other dispatches so
  that you can introduce this library in some new code without
  changing too much. After all, it's newcomer' job to get used to its
  surroundings."
  (:import (org.apache.avro Schema Schema$Type Schema$FixedSchema Schema$UnionSchema Schema$MapSchema Schema$ArraySchema Schema$EnumSchema Schema$RecordSchema Schema$Field Conversions$DecimalConversion Conversions$UUIDConversion Conversion LogicalType SchemaBuilder)
           (com.slava Conversion$Dispatch)
           (java.util Collections HashMap LinkedHashMap ArrayList)
           (org.apache.avro.generic GenericRecord GenericRecordBuilder GenericFixed GenericData$Fixed GenericData)
           (java.nio ByteBuffer)
           (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimeMicrosConversion TimeConversions$TimeMillisConversion TimeConversions$TimestampMicrosConversion TimeConversions$TimestampMillisConversion)
           (java.time Period))
  (:gen-class :name com.slava.ConversionNative
              :implements [com.slava.Conversion]
              :prefix "impl-"))

(defmulti from-avro-schema-type (fn [^Schema schema data] (Conversion$Dispatch/schemaType schema data)))
(defmulti from-avro-logical-type (fn [^Schema schema data] (Conversion$Dispatch/logicalType schema data)))
(defmulti from-avro-schema-name (fn [^Schema schema data] (Conversion$Dispatch/schemaName schema data)))

(defmulti to-avro-schema-type (fn [^Schema schema data] (Conversion$Dispatch/schemaType schema data)))
(defmulti to-avro-logical-type (fn [^Schema schema data] (Conversion$Dispatch/logicalType schema data)))
(defmulti to-avro-schema-name (fn [^Schema schema data] (Conversion$Dispatch/schemaName schema data)))

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
  ;; This will raise if no Enum is present
  (Enum/valueOf (Class/forName (.getFullName schema)) (str data)))

(defmethod from-avro-schema-type Schema$Type/ARRAY [^Schema$ArraySchema schema data]
  (let [l! (ArrayList.)]
    (doseq [v data]
      (.add l! (from-avro (.getElementType schema) v)))
    (Collections/unmodifiableList l!)))

(defmethod from-avro-schema-type Schema$Type/MAP [^Schema$MapSchema schema data]
  (let [m! (LinkedHashMap.)]
    (doseq [[k v] data]
      (.put m! (str k) (from-avro (.getValueType schema) v)))
    (Collections/unmodifiableMap m!)))

(defmethod from-avro-schema-type Schema$Type/UNION [^Schema$UnionSchema schema data]
  (from-avro (nth (.getTypes schema) (.resolveUnion (GenericData/get) schema data)) data))
(defmethod to-avro-schema-type Schema$Type/UNION [^Schema$UnionSchema schema data]
  (to-avro (nth (.getTypes schema) (.resolveUnion (GenericData/get) schema data)) data))

(defmethod from-avro-schema-type Schema$Type/FIXED [^Schema$FixedSchema schema data]
  (doto (ByteBuffer/allocate (.getFixedSize schema)) (.put (.bytes ^GenericFixed data)) (.rewind)))
(defmethod to-avro-schema-type Schema$Type/FIXED [^Schema$FixedSchema schema data]
  (GenericData$Fixed. schema (.array ^ByteBuffer data)))

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
     (defmethod from-avro-logical-type ~'logical-type-name ~'[schema data]
       (case (.ordinal (.getType ~'schema))
         ~(.ordinal Schema$Type/FIXED) (.fromFixed @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/STRING) (.fromCharSequence @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/BYTES) (.fromBytes @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/INT) (.fromInt @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/LONG) (.fromLong @~'conversion ~'data ~'schema (.getLogicalType ~'schema))))
     (defmethod to-avro-logical-type ~'logical-type-name ~'[schema data]
       (case (.ordinal (.getType ~'schema))
         ~(.ordinal Schema$Type/FIXED) (.toFixed @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/STRING) (.toCharSequence @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/BYTES) (.toBytes @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/INT) (.toInt @~'conversion ~'data ~'schema (.getLogicalType ~'schema))
         ~(.ordinal Schema$Type/LONG) (.toLong @~'conversion ~'data ~'schema (.getLogicalType ~'schema))))))

(logical-type-implementation! logical-type-conversions)
