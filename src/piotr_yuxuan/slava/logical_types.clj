(ns piotr-yuxuan.slava.logical-types
  "FIXME add cljdoc"
  (:import (org.apache.avro LogicalType Schema SchemaBuilder Conversion Conversions$DecimalConversion Conversions$UUIDConversion)
           (java.time Period)
           (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimeMillisConversion TimeConversions$TimeMicrosConversion TimeConversions$TimestampMillisConversion TimeConversions$TimestampMicrosConversion)
           (org.apache.avro.generic GenericData)))

(def ^LogicalType duration-logical-type
  "FIXME add cljdoc"
  (LogicalType. "duration"))

(def ^Schema duration-schema
  "FIXME add cljdoc"
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

(defn add-all-conversions!
  "FIXME add cljdoc"
  ;; We are good citizens, we want to slip nicely into existing frameworks, whatever the pain on us.
  []
  (doto (GenericData/get)
    (.addLogicalTypeConversion (Conversions$DecimalConversion.))
    (.addLogicalTypeConversion (Conversions$UUIDConversion.))
    (.addLogicalTypeConversion (TimeConversions$DateConversion.))
    (.addLogicalTypeConversion (TimeConversions$TimeMillisConversion.))
    (.addLogicalTypeConversion (TimeConversions$TimeMicrosConversion.))
    (.addLogicalTypeConversion (TimeConversions$TimestampMillisConversion.))
    (.addLogicalTypeConversion (TimeConversions$TimestampMicrosConversion.))
    (.addLogicalTypeConversion (duration-conversion))))
