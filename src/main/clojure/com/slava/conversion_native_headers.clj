(ns com.slava.conversion-native-headers
  "TODO: bigger, very simple and detailed documentation.

  I acknowledge and pead guilty for the current ugliness of code.

  Dispatch on schema name: takes precedence on all other dispatches so
  that you can introduce this library in some new code without
  changing too much. After all, it's newcomer' job to get used to its
  surroundings."
  (:import (org.apache.avro Schema Schema$RecordSchema Schema$Field)
           (org.apache.avro.generic GenericData)))

(defn dispatch-schema-name [^Schema schema] (.getFullName schema))

(defmulti from-avro-schema-name
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [this ^Schema schema ^Object data] (dispatch-schema-name schema)))

(defmulti to-avro-schema-name
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [this ^Schema schema ^Object data] (dispatch-schema-name schema)))

(defn dispatch-logical-type [^Schema schema]
  (when-let [logical-type (.getLogicalType schema)]
    (when-not (.getConversionFor (GenericData/get) logical-type)
      (.getName logical-type))))

(defmulti from-avro-logical-type
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [this ^Schema schema ^Object data] (dispatch-logical-type schema)))

(defmulti to-avro-logical-type
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [this ^Schema schema ^Object data] (dispatch-logical-type schema)))

(defn dispatch-schema-type [^Schema schema] (.getType schema))

(defmulti from-avro-schema-type
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [this ^Schema schema ^Object data] (dispatch-schema-type schema)))

(defmulti to-avro-schema-type
  ""
  {:arglists '([^ConversionNative this ^Schema schema ^Object data])}
  (fn [this ^Schema schema ^Object data] (dispatch-schema-type schema)))

(defmulti from-avro-field-name
  "" ;; TODO remove this
  {:arglists '([^ConversionNative this ^Schema$RecordSchema schema ^Schema$Field field])}
  (fn [this ^Schema$RecordSchema schema ^Schema$Field field] (:field-name @(.config this))))

(defmulti from-avro-map-key
  ""
  {:arglists '([^ConversionNative this ^Schema$RecordSchema schema ^Schema$Field field ^String map-key])}
  (fn [this ^Schema$RecordSchema schema ^Schema$Field field ^String map-key] (:map-key @(.config this))))

(defmulti from-avro-enum-type
  ""
  {:arglists '([^ConversionNative this ^Schema$RecordSchema schema data])}
  (fn [this ^Schema$RecordSchema schema data] (:enum-type @(.config this))))

(defmulti to-avro-enum-type
  ""
  {:arglists '([^ConversionNative this ^Schema$RecordSchema schema data])}
  (fn [this ^Schema$RecordSchema schema data] (:enum-type @(.config this))))

(defmethod from-avro-schema-name :default [this ^Schema schema ^Object data] nil)
(defmethod from-avro-logical-type :default [this ^Schema schema ^Object data] nil)
(defmethod from-avro-schema-type :default [this ^Schema schema ^Object data] nil)

(defmethod to-avro-schema-name :default [this ^Schema schema ^Object data] nil)
(defmethod to-avro-logical-type :default [this ^Schema schema ^Object data] nil)
(defmethod to-avro-schema-type :default [this ^Schema schema ^Object data] nil)

(defmethod from-avro-field-name :default [this schema field] (.name field))
(defmethod from-avro-field-name :keyword [this schema field] (keyword (.name field)))
(defmethod from-avro-field-name :namespaced-keyword [this schema field] (keyword (.getFullName schema) (.name field)))

(defmethod from-avro-map-key :default [this schema field map-key] (str map-key))
(defmethod from-avro-map-key :keyword [this schema field map-key] (keyword map-key))
(defmethod from-avro-map-key :namespaced-keyword [this schema field map-key] (keyword (str (.getFullName schema) "." (.name field)) map-key))
(defmethod from-avro-map-key :record-namespaced-keyword [this schema field map-key] (keyword (.getFullName schema) (str (.name field) "." map-key)))

(defmethod from-avro-enum-type :default [this ^Schema$RecordSchema schema data] (str data))
(defmethod from-avro-enum-type :keyword [this ^Schema$RecordSchema schema data] (keyword (str data)))
(defmethod from-avro-enum-type :namespaced-keyword [this ^Schema$RecordSchema schema data] (keyword (.getFullName schema) (str data)))
(defmethod from-avro-enum-type :enum [this ^Schema$RecordSchema schema data] (Enum/valueOf (Class/forName (.getFullName schema)) (str data))) ;; This will raise if no Enum is present

(defmethod to-avro-enum-type :default [this ^Schema$RecordSchema schema data] (str data))
(defmethod to-avro-enum-type :keyword [this ^Schema$RecordSchema schema data] (name data))
(defmethod to-avro-enum-type :namespaced-keyword [this ^Schema$RecordSchema schema data] (name data))
(defmethod to-avro-enum-type :enum [this ^Schema$RecordSchema schema data] (str data))
