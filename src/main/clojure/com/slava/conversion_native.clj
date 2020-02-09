(ns com.slava.conversion-native
  "TODO: bigger, very simple and detailed documentation.

  I acknowledge and pead guilty for the current ugliness of code.

  Dispatch on schema name: takes precedence on all other dispatches so
  that you can introduce this library in some new code without
  changing too much. After all, it's newcomer' job to get used to its
  surroundings."
  (:import (org.apache.avro Schema Schema$Type Schema$FixedSchema Schema$UnionSchema Schema$MapSchema Schema$ArraySchema Schema$EnumSchema Schema$RecordSchema Schema$Field Conversions$DecimalConversion Conversions$UUIDConversion Conversion LogicalType SchemaBuilder)
           (java.util Collections LinkedHashMap)
           (org.apache.avro.generic GenericRecord GenericRecordBuilder GenericFixed GenericData$Fixed GenericData GenericData$EnumSymbol GenericData$Record)
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
  [config ^NativeAvroSerdeConfig config]
  (reset! (.config config) (config->map config)))

(declare from-avro to-avro)

;; simplify signature
(defn dispatch-schema-name [^Schema schema] (.getFullName schema))
(defmulti from-avro-schema-name
  ""
  {:arglists '([^ConversionNative config ^Schema schema ^Object data])}
  (fn [^ConversionNative config ^Schema schema ^Object data] (dispatch-schema-name schema)))
(defmethod from-avro-schema-name :default [^ConversionNative config ^Schema schema ^Object data] nil)
(defmulti to-avro-schema-name
  ""
  {:arglists '([^ConversionNative config ^Schema schema ^Object data])}
  (fn [^ConversionNative config ^Schema schema ^Object data] (dispatch-schema-name schema)))
(defmethod to-avro-schema-name :default [^ConversionNative config ^Schema schema ^Object data] nil)

(defn dispatch-logical-type [^Schema schema]
  (when-let [logical-type (.getLogicalType schema)]
    (when-not (.getConversionFor (GenericData/get) logical-type)
      (.getName logical-type))))
(defmulti from-avro-logical-type
  ""
  {:arglists '([^ConversionNative config ^Schema schema ^Object data])}
  (fn [^ConversionNative config ^Schema schema ^Object data] (dispatch-logical-type schema)))
(defmethod from-avro-logical-type :default [^ConversionNative config ^Schema schema ^Object data] nil)
(defmulti to-avro-logical-type
  ""
  {:arglists '([^ConversionNative config ^Schema schema ^Object data])}
  (fn [^ConversionNative config ^Schema schema ^Object data] (dispatch-logical-type schema)))
(defmethod to-avro-logical-type :default [^ConversionNative config ^Schema schema ^Object data] nil)

(defn dispatch-schema-type [^Schema schema] (.getType schema))
(defmulti from-avro-schema-type
  ""
  {:arglists '([^ConversionNative config ^Schema schema ^Object data])}
  (fn [^ConversionNative config ^Schema schema ^Object data] (dispatch-schema-type schema)))
(defmethod from-avro-schema-type :default [^ConversionNative config ^Schema schema ^Object data] nil)
(defmulti to-avro-schema-type
  ""
  {:arglists '([^ConversionNative config ^Schema schema ^Object data])}
  (fn [^ConversionNative config ^Schema schema ^Object data] (dispatch-schema-type schema)))
(defmethod to-avro-schema-type :default [^ConversionNative config ^Schema schema ^Object data] nil)

(defn from-avro
  [config ^Schema schema data]
  (or (from-avro-schema-name config schema data)
      (from-avro-logical-type config schema data)
      (from-avro-schema-type config schema data)
      data))

(defn to-avro
  [config ^Schema schema data]
  (or (to-avro-schema-name config schema data)
      (to-avro-logical-type config schema data)
      (to-avro-schema-type config schema data)
      data))

(defn impl-fromAvro [config schema object] (from-avro config schema object))
(defn impl-toAvro [config schema object] (to-avro config schema object))

(defmulti from-avro-field-name
  "" ;; TODO remove config
  {:arglists '([^ConversionNative config ^Schema$RecordSchema schema ^Schema$Field field])}
  (fn [^ConversionNative config ^Schema$RecordSchema schema ^Schema$Field field] (:field-name @(.config config))))
(defmethod from-avro-field-name :default [config schema field] (.name field))
(defmethod from-avro-field-name :keyword [config schema field] (keyword (.name field)))
(defmethod from-avro-field-name :namespaced-keyword [config schema field] (keyword (.getFullName schema) (.name field)))
;; TODO add other options to map keys and field names to handle some case conversions more easily.

(defmulti from-avro-map-key
  ""
  {:arglists '([^ConversionNative config ^Schema$RecordSchema schema ^Schema$Field field ^String map-key])}
  (fn [^ConversionNative config ^Schema$RecordSchema schema ^Schema$Field field ^String map-key] (:map-key @(.config config))))
(defmethod from-avro-map-key :default [config schema field map-key] (str map-key))
(defmethod from-avro-map-key :keyword [config schema field map-key] (keyword map-key))
(defmethod from-avro-map-key :namespaced-keyword [config schema field map-key] (keyword (str (.getFullName schema) "." (.name field)) map-key))
(defmethod from-avro-map-key :record-namespaced-keyword [config schema field map-key] (keyword (.getFullName schema) (str (.name field) "." map-key)))

(defmulti from-avro-enum-type
  ""
  {:arglists '([^ConversionNative config ^Schema$RecordSchema schema data])}
  (fn [^ConversionNative config ^Schema$RecordSchema schema data] (:enum-type @(.config config))))
(defmulti to-avro-enum-type
  ""
  {:arglists '([^ConversionNative config ^Schema$RecordSchema schema data])}
  (fn [^ConversionNative config ^Schema$RecordSchema schema data] (:enum-type @(.config config))))
(defmethod from-avro-enum-type :default [config ^Schema$RecordSchema schema data] (str data))
(defmethod to-avro-enum-type :default [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (str data)))
(defmethod from-avro-enum-type :keyword [config ^Schema$RecordSchema schema data] (keyword (str data)))
(defmethod to-avro-enum-type :keyword [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (name data)))
(defmethod from-avro-enum-type :namespaced-keyword [config ^Schema$RecordSchema schema data] (keyword (.getFullName schema) (str data)))
(defmethod to-avro-enum-type :namespaced-keyword [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (name data)))
(defmethod from-avro-enum-type :enum [config ^Schema$RecordSchema schema data] (Enum/valueOf (Class/forName (.getFullName schema)) (str data))) ;; This will raise if no Enum is present
(defmethod to-avro-enum-type :enum [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (str data)))

;;;
;;; Implementation of dispatch on schema types
;;;

(declare record-field)

(defn to-avro-record
  "Given a config and a schema, return a higher-order function which turns Clojure data structures to record of the schema. The optional third parameter is when you need to generate records from a same basis."
  ([config schema] (to-avro-record config schema nil))
  ([config schema default]
   (let [record-base (or (and default ((to-avro-record config schema nil) default)) schema)
         fields (map (fn [^Schema$Field field]
                       {:avro-field-name (.name ^Schema$Field field)
                        :clj-field-name (from-avro-field-name config schema field)
                        :field-schema (.schema ^Schema$Field field)})
                     (.getFields ^Schema schema))
         record-fields (record-field config schema default)]
     (fn record-producer [data]
       (let [;; GenericRecordBuilder is stateful, hence must be created each time
             record-builder (if default
                              (GenericRecordBuilder. ^GenericData$Record record-base) ;; FIXME this forces all fields to be set. GenericRecordBuilder be better. Here is a bug.
                              (GenericRecordBuilder. ^Schema schema))]
         (doseq [{:keys [avro-field-name clj-field-name field-schema]} (filter #(contains? data (% :clj-field-name)) fields)]
           (let [clj-value (get data clj-field-name)
                 avro-value (if-let [process-record-field (record-fields avro-field-name)]
                              (process-record-field clj-value)
                              (to-avro config field-schema clj-value))]
             (.set record-builder
                   ^String avro-field-name
                   avro-value)))
         (.build record-builder))))))

(defn record-field
  [config ^Schema schema default]
  (->> (.getFields schema)
       (filter #(= Schema$Type/RECORD (.getType (.schema ^Schema$Field %))))
       (map (fn [^Schema$Field field] (vector (.name field) (to-avro-record config (.schema field) default))))
       (into {})))

(defmethod from-avro-schema-type Schema$Type/RECORD [config ^Schema$RecordSchema schema ^GenericRecord data]
  (let [m! (transient {})]
    (doseq [^Schema$Field field (.getFields (.getSchema data))]
      (assoc! m! (from-avro-field-name config schema field) (from-avro config (.schema field) (.get data (.name field)))))
    (when-let [schema-key (:schema-key @(.config config))]
      (assoc! m! schema-key schema))
    (persistent! m!)))
(defmethod to-avro-schema-type Schema$Type/RECORD [config ^Schema$RecordSchema schema data]
  (let [builder (new GenericRecordBuilder schema)]
    (doseq [^Schema$Field field (filter #(contains? data (from-avro-field-name config schema %)) (.getFields schema))]
      (.set builder (.name field) (to-avro config (.schema field) (get data (from-avro-field-name config schema field)))))
    (.build builder)))

(defmethod from-avro-schema-type Schema$Type/ENUM [config ^Schema$EnumSchema schema data] (from-avro-enum-type config schema data))
(defmethod to-avro-schema-type Schema$Type/ENUM [config ^Schema$EnumSchema schema data] (to-avro-enum-type config schema data))

(defmethod from-avro-schema-type Schema$Type/ARRAY [config ^Schema$ArraySchema schema data]
  (let [l! (transient [])]
    (doseq [v data]
      (conj! l! (from-avro config (.getElementType schema) v)))
    (persistent! l!)))
(defmethod to-avro-schema-type Schema$Type/ARRAY [config ^Schema$ArraySchema schema data]
  (let [element-type (.getElementType schema)]
    (vec (map #(to-avro config element-type %) data))))

(defmethod from-avro-schema-type Schema$Type/MAP [config ^Schema$MapSchema schema data]
  (let [m! (transient {})]
    (doseq [[k v] data]
      (assoc! m! (str k) (from-avro config (.getValueType schema) v)))
    (persistent! m!)))
(defmethod to-avro-schema-type Schema$Type/MAP [config ^Schema$MapSchema schema data]
  (let [m! (LinkedHashMap.)]
    (doseq [[k v] data]
      (.put m! (str k) (to-avro config (.getValueType schema) v)))
    (Collections/unmodifiableMap m!)))

(defmethod from-avro-schema-type Schema$Type/UNION [config ^Schema$UnionSchema schema data]
  (some (fn first-matching-type [schema]
          (try (from-avro config schema data) (catch Exception _)))
        (condp = (.getType schema)
          Schema$Type/UNION (.getTypes schema)
          Schema$Type/ARRAY (.getTypes (.getElementType schema)))))
(defmethod to-avro-schema-type Schema$Type/UNION [config ^Schema$UnionSchema schema data]
  (some (fn first-matching-type [schema]
          (try (to-avro config schema data) (catch Exception _)))
        (condp = (.getType schema)
          Schema$Type/UNION (.getTypes schema)
          Schema$Type/ARRAY (.getTypes (.getElementType schema)))))

(defmethod from-avro-schema-type Schema$Type/FIXED [config ^Schema$FixedSchema schema data]
  (doto (ByteBuffer/allocate (.getFixedSize schema)) (.put (.bytes ^GenericFixed data)) (.rewind)))
(defmethod to-avro-schema-type Schema$Type/FIXED [config ^Schema$FixedSchema schema data]
  (GenericData$Fixed. schema (.array ^ByteBuffer data)))

(defmethod from-avro-schema-type Schema$Type/STRING [config ^Schema$FixedSchema schema data] (str data))

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

