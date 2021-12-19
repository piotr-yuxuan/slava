✔ (ns piotr-yuxuan.slava.duration
?   "FIXME add cljdoc"
?   (:require [byte-streams])
?   (:import (org.apache.avro LogicalType Schema Conversion)
?            (org.apache.avro.generic GenericData$Fixed)
?            (java.nio ByteBuffer ByteOrder)
?            (java.util Map)))
  
✔ (def ^LogicalType logical-type
?   "FIXME add cljdoc"
✔   (LogicalType. "duration"))
  
✔ (def ^Schema schema
?   "FIXME add cljdoc"
✔   (.addToSchema logical-type (Schema/createFixed "duration" nil nil 12)))
  
✔ (defn from-byte-array
?   "FIXME add cljdoc"
?   [ba]
✔   (let [byte-buffer (doto ^ByteBuffer (byte-streams/convert ba ByteBuffer)
✔                       (.order ByteOrder/LITTLE_ENDIAN))]
✔     {:months (.getInt byte-buffer)
✔      :days (.getInt byte-buffer)
✔      :milliseconds (.getInt byte-buffer)}))
  
✔ (defn to-byte-array
?   "FIXME add cljdoc"
?   [{:keys [months days milliseconds]}]
✔   (byte-streams/to-byte-array
✔     (doto (ByteBuffer/allocate 12)
✔       (.order ByteOrder/LITTLE_ENDIAN)
✔       (.putInt (int months))
✔       (.putInt (int days))
✔       (.putInt (int milliseconds))
✔       (.rewind))))
  
✔ (def ^Conversion conversion
?   "Not implemented in upstream avro Conversions."
✔   (proxy [Conversion] []
✘     (getConvertedType [] Map)
✘     (getRecommendedSchema [] schema)
✘     (getLogicalTypeName [] (.getName logical-type))
✘     (fromFixed [^GenericData$Fixed data _ _] (from-byte-array (.bytes data)))
✘     (toFixed [m schema _] (GenericData$Fixed. schema (to-byte-array m)))))
