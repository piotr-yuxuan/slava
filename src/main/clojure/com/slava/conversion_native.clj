(ns com.slava.conversion-native
  "TODO: bigger, very simple and detailed documentation.

  I acknowledge and pead guilty for the current ugliness of code.

  Dispatch on schema name: takes precedence on all other dispatches so
  that you can introduce this library in some new code without
  changing too much. After all, it's newcomer' job to get used to its
  surroundings."
  (:import (org.apache.avro Schema Schema$Type Schema$FixedSchema Schema$UnionSchema Schema$MapSchema Schema$ArraySchema Schema$EnumSchema Schema$RecordSchema Schema$Field Conversions$DecimalConversion Conversions$UUIDConversion Conversion LogicalType SchemaBuilder UnresolvedUnionException)
           (java.util Collections HashMap LinkedHashMap ArrayList)
           (org.apache.avro.generic GenericRecord GenericRecordBuilder GenericFixed GenericData$Fixed GenericData)
           (java.nio ByteBuffer)
           (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimeMicrosConversion TimeConversions$TimeMillisConversion TimeConversions$TimestampMicrosConversion TimeConversions$TimestampMillisConversion)
           (java.time Period)
           (com.slava NativeAvroSerdeConfig ConversionNative))
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

(declare from-avro to-avro)

;; simplify signature
(defn dispatch-schema-name [^Schema schema] (.getFullName schema))
(defmulti from-avro-schema-name
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [^ConversionNative this ^Schema schema ^Object data] (dispatch-schema-name schema)))
(defmethod from-avro-schema-name :default [^ConversionNative this ^Schema schema ^Object data] nil)
(defmulti to-avro-schema-name
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [^ConversionNative this ^Schema schema ^Object data] (dispatch-schema-name schema)))
(defmethod to-avro-schema-name :default [^ConversionNative this ^Schema schema ^Object data] nil)

(defn dispatch-logical-type [^Schema schema]
  (when-let [logical-type (.getLogicalType schema)]
    (when-not (.getConversionFor (GenericData/get) logical-type)
      (.getName logical-type))))
(defmulti from-avro-logical-type
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [^ConversionNative this ^Schema schema ^Object data] (dispatch-logical-type schema)))
(defmethod from-avro-logical-type :default [^ConversionNative this ^Schema schema ^Object data] nil)
(defmulti to-avro-logical-type
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [^ConversionNative this ^Schema schema ^Object data] (dispatch-logical-type schema)))
(defmethod to-avro-logical-type :default [^ConversionNative this ^Schema schema ^Object data] nil)

(defn dispatch-schema-type [^Schema schema] (.getType schema))
(defmulti from-avro-schema-type
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [^ConversionNative this ^Schema schema ^Object data] (dispatch-schema-type schema)))
(defmethod from-avro-schema-type :default [^ConversionNative this ^Schema schema ^Object data] nil)
(defmulti to-avro-schema-type
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [^ConversionNative this ^Schema schema ^Object data] (dispatch-schema-type schema)))
(defmethod to-avro-schema-type :default [^ConversionNative this ^Schema schema ^Object data] nil)

(defonce debug (atom nil))
(comment (reset! debug nil))

(defn from-avro
  [this ^Schema schema data]
  (swap! debug update :from-avro (fnil conj []) [schema data])
  (or (from-avro-schema-name this schema data)
      (from-avro-logical-type this schema data)
      (from-avro-schema-type this schema data)
      data))

(defn to-avro
  [this ^Schema schema data]
  (swap! debug update :to-avro (fnil conj []) [schema data])
  (or (to-avro-schema-name this schema data)
      (to-avro-logical-type this schema data)
      (to-avro-schema-type this schema data)
      data))

(defn impl-fromAvro [this schema object] (from-avro this schema object))
(defn impl-toAvro [this schema object] (to-avro this schema object))

(defmulti from-avro-field-name
  "" ;; TODO remove this
  {:arglists '([^ConversionNative this ^Schema$RecordSchema schema ^Schema$Field field])}
  (fn [^ConversionNative this ^Schema$RecordSchema schema ^Schema$Field field] (:field-name @(.config this))))
(defmethod from-avro-field-name :default [this schema field] (.name field))
(defmethod from-avro-field-name :keyword [this schema field] (keyword (.name field)))
(defmethod from-avro-field-name :namespaced-keyword [this schema field] (keyword (.getFullName schema) (.name field)))

(defmulti from-avro-map-key
  ""
  {:arglists '([^ConversionNative this ^Schema$RecordSchema schema ^Schema$Field field ^String map-key])}
  (fn [^ConversionNative this ^Schema$RecordSchema schema ^Schema$Field field ^String map-key] (:map-key @(.config this))))
(defmethod from-avro-map-key :default [this schema field map-key] (str map-key))
(defmethod from-avro-map-key :keyword [this schema field map-key] (keyword map-key))
(defmethod from-avro-map-key :namespaced-keyword [this schema field map-key] (keyword (str (.getFullName schema) "." (.name field)) map-key))
(defmethod from-avro-map-key :record-namespaced-keyword [this schema field map-key] (keyword (.getFullName schema) (str (.name field) "." map-key)))

(defmulti from-avro-enum-type
  ""
  {:arglists '([^ConversionNative this ^Schema$RecordSchema schema data])}
  (fn [^ConversionNative this ^Schema$RecordSchema schema data] (:enum-type @(.config this))))
(defmulti to-avro-enum-type
  ""
  {:arglists '([^ConversionNative this ^Schema$RecordSchema schema data])}
  (fn [^ConversionNative this ^Schema$RecordSchema schema data] (:enum-type @(.config this))))
(defmethod from-avro-enum-type :default [this ^Schema$RecordSchema schema data] (str data))
(defmethod to-avro-enum-type :default [this ^Schema$RecordSchema schema data] (str data))
(defmethod from-avro-enum-type :keyword [this ^Schema$RecordSchema schema data] (keyword (str data)))
(defmethod to-avro-enum-type :keyword [this ^Schema$RecordSchema schema data] (name data))
(defmethod from-avro-enum-type :namespaced-keyword [this ^Schema$RecordSchema schema data] (keyword (.getFullName schema) (str data)))
(defmethod to-avro-enum-type :namespaced-keyword [this ^Schema$RecordSchema schema data] (name data))
(defmethod from-avro-enum-type :enum [this ^Schema$RecordSchema schema data] (Enum/valueOf (Class/forName (.getFullName schema)) (str data))) ;; This will raise if no Enum is present
(defmethod to-avro-enum-type :enum [this ^Schema$RecordSchema schema data] (str data))

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

