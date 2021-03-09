(ns piotr-yuxuan.slava.logical-types
  "FIXME add cljdoc"
  (:require [byte-streams])
  (:import (org.apache.avro LogicalType Schema Conversion Conversions$DecimalConversion Conversions$UUIDConversion)
           (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimeMillisConversion TimeConversions$TimeMicrosConversion TimeConversions$TimestampMillisConversion TimeConversions$TimestampMicrosConversion TimeConversions$LocalTimestampMillisConversion TimeConversions$LocalTimestampMicrosConversion)
           (org.apache.avro.generic GenericData GenericData$Fixed)
           (java.nio ByteBuffer ByteOrder)
           (java.util Map)))

(def ^LogicalType duration
  "FIXME add cljdoc"
  (LogicalType. "duration"))

(def ^Schema duration-schema
  "FIXME add cljdoc"
  (.addToSchema duration (Schema/createFixed "duration" nil nil 12)))

(defn duration-from-byte-array
  "FIXME add cljdoc"
  [ba]
  (let [byte-buffer (doto ^ByteBuffer (byte-streams/convert ba ByteBuffer)
                      (.order ByteOrder/LITTLE_ENDIAN))]
    {:months (.getInt byte-buffer)
     :days (.getInt byte-buffer)
     :milliseconds (.getInt byte-buffer)}))

(defn duration-to-byte-array
  "FIXME add cljdoc"
  [{:keys [months days milliseconds]}]
  (byte-streams/to-byte-array
    (doto (ByteBuffer/allocate 12)
      (.order ByteOrder/LITTLE_ENDIAN)
      (.putInt (int months))
      (.putInt (int days))
      (.putInt (int milliseconds))
      (.rewind))))

(def ^Conversion duration-conversion
  "Not implemented in upstream avro Conversions."
  (proxy [Conversion] []
    (getConvertedType [] Map)
    (getRecommendedSchema [] duration-schema)
    (getLogicalTypeName [] (.getName duration))
    (fromFixed [^GenericData$Fixed data _ _] (duration-from-byte-array (.bytes data)))
    (toFixed [m schema _] (GenericData$Fixed. schema (duration-to-byte-array m)))))

(defn add-all-conversions
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
    (.addLogicalTypeConversion (TimeConversions$LocalTimestampMillisConversion.))
    (.addLogicalTypeConversion (TimeConversions$LocalTimestampMicrosConversion.))
    (.addLogicalTypeConversion duration-conversion)))
