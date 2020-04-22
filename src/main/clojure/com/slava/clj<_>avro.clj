(ns com.slava.clj<->avro
  "TODO: bigger, very simple and detailed documentation.

  I acknowledge and pead guilty for the current ugliness of code.

  Dispatch on schema name: takes precedence on all other dispatches so
  that you can introduce this library in some new code without
  changing too much. After all, it's newcomer' job to get used to its
  surroundings."
  (:require [clojure.string :as str]
            [camel-snake-kebab.core :as csk])
  (:import (org.apache.avro Schema Schema$Type Schema$FixedSchema Schema$UnionSchema Schema$MapSchema Schema$ArraySchema Schema$EnumSchema Schema$RecordSchema Schema$Field Conversions$DecimalConversion Conversions$UUIDConversion Conversion LogicalType SchemaBuilder)
           (java.util Collections Map List Collection)
           (org.apache.avro.generic GenericRecord GenericRecordBuilder GenericFixed GenericData$Fixed GenericData GenericData$EnumSymbol GenericData$Record)
           (java.nio ByteBuffer)
           (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimeMicrosConversion TimeConversions$TimeMillisConversion TimeConversions$TimestampMicrosConversion TimeConversions$TimestampMillisConversion)
           (java.time Period)
           (com.slava CljAvroSerdeConfig CljAvroTransformer)
           (clojure.lang Named)
           (org.apache.avro.util Utf8))
  (:gen-class :name com.slava.CljAvroTransformer
              :implements [com.slava.ICljAvroTransformer]
              :constructors {[] [], [java.util.Map] []}
              :init init
              :state config
              :prefix "impl-"))

(defn impl-init
  ([] [[] (atom {})])
  ;; Used for tests
  ([m] [[] (atom m)]))

(defn config->map [^CljAvroSerdeConfig config]
  (merge
    {:field-name (keyword (.getString config CljAvroSerdeConfig/COM_SLAVA_FIELD_NAME_CONVERSION_CONFIG))
     :map-key (keyword (.getString config CljAvroSerdeConfig/COM_SLAVA_MAP_KEY_CONVERSION_CONFIG))
     :enum-type (keyword (.getString config CljAvroSerdeConfig/COM_SLAVA_ENUM_CONVERSION_CONFIG))}
    (when (.getBoolean config CljAvroSerdeConfig/COM_SLAVA_INCLUDE_SCHEMA_IN_MAP_CONFIG)
      {:schema-key (keyword (.getString config CljAvroSerdeConfig/ORG_APACHE_AVRO_SCHEMA_KEY_CONFIG))})))

(defn impl-configure
  [^CljAvroTransformer config ^CljAvroSerdeConfig serde-config]
  (reset! (.config config) (config->map serde-config)))

(declare avro->clj clj->avro)

;; simplify signature
(defn dispatch-schema-name [^Schema schema] (.getFullName schema))
(defmulti avro->clj-schema-name
  ""
  {:arglists '([config ^Schema schema ^Object data])}
  (fn [config ^Schema schema ^Object data] (dispatch-schema-name schema)))
(defmethod avro->clj-schema-name :default [config ^Schema schema ^Object data] nil)
(defmulti clj->avro-schema-name
  ""
  {:arglists '([config ^Schema schema ^Object data])}
  (fn [config ^Schema schema ^Object data] (dispatch-schema-name schema)))
(defmethod clj->avro-schema-name :default [config ^Schema schema ^Object data] nil)

(defn dispatch-logical-type [^Schema schema]
  (when-let [logical-type (.getLogicalType schema)]
    (when-not (.getConversionFor (GenericData/get) logical-type)
      (.getName logical-type))))
(defmulti avro->clj-logical-type
  ""
  {:arglists '([config ^Schema schema ^Object data])}
  (fn [config ^Schema schema ^Object data] (dispatch-logical-type schema)))
(defmethod avro->clj-logical-type :default [config ^Schema schema ^Object data] nil)
(defmulti clj->avro-logical-type
  ""
  {:arglists '([config ^Schema schema ^Object data])}
  (fn [config ^Schema schema ^Object data] (dispatch-logical-type schema)))
(defmethod clj->avro-logical-type :default [config ^Schema schema ^Object data] nil)

(defn dispatch-schema-type [^Schema schema] (.getType schema))
(defmulti avro->clj-schema-type
  ""
  {:arglists '([config ^Schema schema ^Object data])}
  (fn [config ^Schema schema ^Object data] (dispatch-schema-type schema)))
(defmethod avro->clj-schema-type :default [config ^Schema schema ^Object data] nil)
(defmulti clj->avro-schema-type
  ""
  {:arglists '([config ^Schema schema ^Object data])}
  (fn [config ^Schema schema ^Object data] (dispatch-schema-type schema)))
(defmethod clj->avro-schema-type :default [config ^Schema schema ^Object data] nil)

(defn avro->clj
  [config ^Schema schema data]
  (or (avro->clj-schema-name config schema data)
      (avro->clj-logical-type config schema data)
      (avro->clj-schema-type config schema data)
      data))

(defn clj->avro
  [config ^Schema schema data]
  (or (clj->avro-schema-name config schema data)
      (clj->avro-logical-type config schema data)
      (clj->avro-schema-type config schema data)
      data))

(defn impl-fromAvroToClj [^CljAvroTransformer this schema object] (avro->clj @(.config this) schema object))
(defn impl-fromCljToAvro [^CljAvroTransformer this schema object] (clj->avro @(.config this) schema object))

(defmulti avro->clj-field-name
  "" ;; TODO remove config
  {:arglists '([config ^Schema$RecordSchema schema ^Schema$Field field])}
  (fn [config ^Schema$RecordSchema schema ^Schema$Field field] (:field-name config)))
(defmethod avro->clj-field-name :default [config schema ^Schema$Field field] (.name field))
(defmethod avro->clj-field-name :keyword [config schema ^Schema$Field field] (keyword (.name field)))
(defmethod avro->clj-field-name :kebab-clj-keyword [config schema ^Schema$Field field] (csk/->kebab-case-keyword (.name field)))
(defmethod avro->clj-field-name :namespaced-keyword [config ^Schema schema ^Schema$Field field] (keyword (.getFullName schema) (.name field)))
;; TODO add other options to map keys and field names to handle some case conversions more easily.

(defmulti avro->clj-map-key
  ""
  {:arglists '([config ^Schema$RecordSchema schema ^String map-key])}
  (fn [config ^Schema$RecordSchema schema ^String map-key] (:map-key config)))
(defmethod avro->clj-map-key :default [config ^Schema schema map-key] (str map-key))
(defmethod avro->clj-map-key :keyword [config ^Schema schema map-key] (keyword map-key))

(defmulti clj->avro-map-key
  ""
  {:arglists '([config ^Schema$RecordSchema schema ^String data])}
  (fn [config ^Schema$RecordSchema schema ^String data] (:map-key config)))
(defmethod clj->avro-map-key :default [config ^Schema schema data] (str data))
(defmethod clj->avro-map-key :keyword [config ^Schema schema data] (if (instance? Named data) (name data) (str data)))

(defmulti avro->clj-enum-type
  ""
  {:arglists '([config ^Schema$RecordSchema schema data])}
  (fn [config ^Schema$RecordSchema schema data] (:enum-type config)))
(defmulti clj->avro-enum-type
  ""
  {:arglists '([config ^Schema$RecordSchema schema data])}
  (fn [config ^Schema$RecordSchema schema data] (:enum-type config)))
(defmethod avro->clj-enum-type :default [config ^Schema$RecordSchema schema data] (str data))
(defmethod clj->avro-enum-type :default [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (str data)))
(defmethod avro->clj-enum-type :keyword [config ^Schema$RecordSchema schema data] (keyword (str data)))
(defmethod clj->avro-enum-type :keyword [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (name data)))
(defmethod avro->clj-enum-type :kebab-clj-SCREAMING_SNAKE-avro-keyword [config ^Schema$RecordSchema schema data] (csk/->kebab-case-keyword (str data)))
(defmethod clj->avro-enum-type :kebab-clj-SCREAMING_SNAKE-avro-keyword [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (csk/->SCREAMING_SNAKE_CASE_STRING (name data))))
(defmethod avro->clj-enum-type :namespaced-keyword [config ^Schema$RecordSchema schema data] (keyword (.getFullName schema) (str data)))
(defmethod clj->avro-enum-type :namespaced-keyword [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (name data)))
(defmethod avro->clj-enum-type :enum [config ^Schema$RecordSchema schema data] (Enum/valueOf (Class/forName (.getFullName schema)) (str data))) ;; This will raise if no Enum is present
(defmethod clj->avro-enum-type :enum [config ^Schema$RecordSchema schema data] (GenericData$EnumSymbol. schema (str data)))

;;;
;;; Implementation of dispatch on schema types
;;;

(declare record-field)

(defn clj->avro-record
  "Given a config and a schema, return a higher-order function which turns Clojure data structures to record of the schema. The optional third parameter is when you need to generate records from a same basis."
  ([config schema] (clj->avro-record config schema nil))
  ([config schema default]
   (let [base-record-builder (if default
                               (GenericRecordBuilder. ^GenericData$Record ((clj->avro-record config schema nil) default)) ;; FIXME this forces all fields to be set. GenericRecordBuilder be better. Here is a bug.
                               (GenericRecordBuilder. ^Schema schema))
         fields (map (fn [^Schema$Field field]
                       {:avro-field-name (.name ^Schema$Field field)
                        :clj-field-name (avro->clj-field-name config schema field)
                        :field-schema (.schema ^Schema$Field field)})
                     (.getFields ^Schema schema))
         record-fields (record-field config schema default)]
     (fn record-producer [data]
       (let [;; GenericRecordBuilder is stateful, hence must be created each time
             record-builder (GenericRecordBuilder. ^GenericRecordBuilder base-record-builder)]
         (doseq [{:keys [avro-field-name clj-field-name field-schema]} (filter #(contains? data (% :clj-field-name)) fields)]
           (let [clj-value (get data clj-field-name)
                 avro-value (if-let [process-record-field (record-fields avro-field-name)]
                              (process-record-field clj-value)
                              (clj->avro config field-schema clj-value))]
             (.set record-builder
                   ^String avro-field-name
                   avro-value)))
         (.build record-builder))))))

(defn record-field
  [config ^Schema schema default]
  (->> (.getFields schema)
       (filter #(= Schema$Type/RECORD (.getType (.schema ^Schema$Field %))))
       (map (fn [^Schema$Field field] (vector (.name field) (clj->avro-record config (.schema field) default))))
       (into {})))

(defmethod avro->clj-schema-type Schema$Type/RECORD [config ^Schema$RecordSchema schema ^GenericRecord data]
  (let [m! (transient {})]
    (doseq [^Schema$Field field (.getFields (.getSchema data))]
      (assoc! m! (avro->clj-field-name config schema field) (avro->clj config (.schema field) (.get data (.name field)))))
    (when-let [schema-key (:schema-key config)]
      (assoc! m! schema-key schema))
    (persistent! m!)))
(defmethod clj->avro-schema-type Schema$Type/RECORD [config ^Schema$RecordSchema schema data]
  (let [builder (new GenericRecordBuilder schema)]
    (doseq [^Schema$Field field (filter #(contains? data (avro->clj-field-name config schema %)) (.getFields schema))]
      (.set builder (.name field) (clj->avro config (.schema field) (get data (avro->clj-field-name config schema field)))))
    (.build builder)))

(defmethod avro->clj-schema-type Schema$Type/ENUM [config ^Schema$EnumSchema schema data] (avro->clj-enum-type config schema data))
(defmethod clj->avro-schema-type Schema$Type/ENUM [config ^Schema$EnumSchema schema data] (clj->avro-enum-type config schema data))

(defmethod avro->clj-schema-type Schema$Type/ARRAY [config ^Schema$ArraySchema schema data]
  (let [l! (transient [])]
    (doseq [v data]
      (conj! l! (avro->clj config (.getElementType schema) v)))
    (persistent! l!)))
(defmethod clj->avro-schema-type Schema$Type/ARRAY [config ^Schema$ArraySchema schema data]
  (let [element-type (.getElementType schema)]
    (vec (map #(clj->avro config element-type %) data))))

(defmethod avro->clj-schema-type Schema$Type/MAP [config ^Schema$MapSchema schema data]
  (reduce (fn [acc [k v]]
            (assoc acc (avro->clj-map-key config schema k) (avro->clj config (.getValueType schema) v)))
          {}
          data))
(defmethod clj->avro-schema-type Schema$Type/MAP [config ^Schema$MapSchema schema data]
  (Collections/unmodifiableMap
    (reduce (fn [acc [k v]]
              (assoc acc (clj->avro-map-key config schema k) (clj->avro config (.getValueType schema) v)))
            {}
            data)))

(defmethod avro->clj-schema-type Schema$Type/UNION [config ^Schema$UnionSchema schema data]
  (let [inner-types (condp = (.getType schema)
                      Schema$Type/UNION (.getTypes schema)
                      Schema$Type/ARRAY (.getTypes (.getElementType schema)))
        inferred-types (cond (instance? Map data)
                             (filter #(= Schema$Type/MAP (.getType ^Schema %))
                                     inner-types)

                             ;; https://stackoverflow.com/q/19850730
                             (or (instance? Collection data)
                                 (instance? List data))
                             (filter #(= Schema$Type/ARRAY (.getType ^Schema %)) inner-types)
                             ;; RECORD, ENUM, ARRAY, MAP, UNION, FIXED, STRING, BYTES, INT, LONG, FLOAT, DOUBLE, BOOLEAN, NULL;

                             (instance? GenericData$EnumSymbol data)
                             (filter #(= Schema$Type/ENUM (.getType ^Schema %)) inner-types)

                             (or (string? data) (instance? Utf8 data))
                             (filter #(= Schema$Type/STRING (.getType ^Schema %)) inner-types)

                             (or (number? data))
                             (filter #(contains? #{Schema$Type/INT Schema$Type/LONG Schema$Type/FLOAT Schema$Type/DOUBLE} (.getType ^Schema %)) inner-types)

                             :else nil)]
    (->> (or inferred-types inner-types)
         (concat (filter #(= Schema$Type/UNION (.getType ^Schema %)) inner-types)
                 (filter #(= Schema$Type/RECORD (.getType ^Schema %)) inner-types))
         (some (fn tinder [schema] ;; needs a match desperately
                 (try (avro->clj config schema data) (catch Exception _)))))))
(defmethod clj->avro-schema-type Schema$Type/UNION [config ^Schema$UnionSchema schema data]
  (some (fn first-matching-type [schema]
          (try (clj->avro config schema data) (catch Exception _)))
        (condp = (.getType schema)
          Schema$Type/UNION (.getTypes schema)
          Schema$Type/ARRAY (.getTypes (.getElementType schema)))))

(defmethod avro->clj-schema-type Schema$Type/FIXED [config ^Schema$FixedSchema schema data]
  (doto (ByteBuffer/allocate (.getFixedSize schema)) (.put (.bytes ^GenericFixed data)) (.rewind)))
(defmethod clj->avro-schema-type Schema$Type/FIXED [config ^Schema$FixedSchema schema data]
  (GenericData$Fixed. schema (.array ^ByteBuffer data)))

(defmethod avro->clj-schema-type Schema$Type/STRING [config ^Schema$FixedSchema schema data] (str data))

(defmethod clj->avro-schema-type Schema$Type/INT [config ^Schema$FixedSchema schema data] (int data))
(defmethod clj->avro-schema-type Schema$Type/LONG [config ^Schema$FixedSchema schema data] (long data))
(defmethod clj->avro-schema-type Schema$Type/FLOAT [config ^Schema$FixedSchema schema data] (float data))
(defmethod clj->avro-schema-type Schema$Type/DOUBLE [config ^Schema$FixedSchema schema data] (double data))
(defmethod clj->avro-schema-type Schema$Type/BOOLEAN [config ^Schema$FixedSchema schema data] (boolean data))
(defmethod clj->avro-schema-type Schema$Type/NULL [config ^Schema$FixedSchema schema data] nil)

;;;
;;; Implementation of dispatch on logical types
;;;

(def ^LogicalType duration-logical-type (LogicalType. "duration"))
(def ^Schema duration-schema
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
  #{{:logical-type-name "decimal" :conversion (Conversions$DecimalConversion.)}
    {:logical-type-name "uuid" :conversion (Conversions$UUIDConversion.)}
    {:logical-type-name "date" :conversion (TimeConversions$DateConversion.)}
    {:logical-type-name "time-millis" :conversion (TimeConversions$TimeMillisConversion.)}
    {:logical-type-name "time-micros" :conversion (TimeConversions$TimeMicrosConversion.)}
    {:logical-type-name "timestamp-millis" :conversion (TimeConversions$TimestampMillisConversion.)}
    {:logical-type-name "timestamp-micros" :conversion (TimeConversions$TimestampMicrosConversion.)}
    {:logical-type-name "duration" :conversion (duration-conversion)}})

(doseq [{:keys [logical-type-name conversion]} logical-type-conversions]
  (defmethod avro->clj-logical-type logical-type-name [_ ^Schema schema data]
    (case (int (.ordinal (.getType schema)))
      5 #_Fixed (.fromFixed ^Conversion conversion data schema (.getLogicalType schema))
      6 #_String (.fromCharSequence ^Conversion conversion data schema (.getLogicalType schema))
      7 #_Bytes (.fromBytes ^Conversion conversion data schema (.getLogicalType schema))
      8 #_Int (.fromInt ^Conversion conversion data schema (.getLogicalType schema))
      9 #_Long (.fromLong ^Conversion conversion data schema (.getLogicalType schema))))
  (defmethod clj->avro-logical-type logical-type-name [_ ^Schema schema data]
    (case (int (.ordinal (.getType schema)))
      5 #_Fixed (.toFixed ^Conversion conversion data schema (.getLogicalType schema))
      6 #_String (.toCharSequence ^Conversion conversion data schema (.getLogicalType schema))
      7 #_Bytes (.toBytes ^Conversion conversion data schema (.getLogicalType schema))
      8 #_Int (.toInt ^Conversion conversion data schema (.getLogicalType schema))
      9 #_Long (.toLong ^Conversion conversion data schema (.getLogicalType schema)))))

;;;
;;; Implementation of dispatch on schema types
;;;

