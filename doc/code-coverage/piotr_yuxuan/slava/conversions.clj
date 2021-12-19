✔ (ns piotr-yuxuan.slava.conversions
?   "FIXME add cljdoc"
?   (:require [piotr-yuxuan.slava.duration :as duration])
?   (:import (org.apache.avro Conversions$DecimalConversion Conversions$UUIDConversion Schema)
?            (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimeMillisConversion TimeConversions$TimeMicrosConversion TimeConversions$TimestampMillisConversion TimeConversions$TimestampMicrosConversion TimeConversions$LocalTimestampMillisConversion TimeConversions$LocalTimestampMicrosConversion)
?            (org.apache.avro.generic GenericData)))
  
✔ (defn add-all!
?   "FIXME add cljdoc"
?   [^GenericData generic-data-instance]
✘   (doto generic-data-instance
✘     (.addLogicalTypeConversion (Conversions$DecimalConversion.))
✘     (.addLogicalTypeConversion (Conversions$UUIDConversion.))
✘     (.addLogicalTypeConversion (TimeConversions$DateConversion.))
✘     (.addLogicalTypeConversion (TimeConversions$TimeMillisConversion.))
✘     (.addLogicalTypeConversion (TimeConversions$TimeMicrosConversion.))
✘     (.addLogicalTypeConversion (TimeConversions$TimestampMillisConversion.))
✘     (.addLogicalTypeConversion (TimeConversions$TimestampMicrosConversion.))
✘     (.addLogicalTypeConversion (TimeConversions$LocalTimestampMillisConversion.))
✘     (.addLogicalTypeConversion (TimeConversions$LocalTimestampMicrosConversion.))
✘     (.addLogicalTypeConversion duration/conversion)))
  
✔ (defmacro conversion-coder
?   "FIXME add cljdoc"
?   [method]
✘   (let [reader-schema (vary-meta (gensym "reader-schema") assoc :tag `Schema)]
✘     `(fn [_# ~reader-schema]
✘        (let [logical-type# (.getLogicalType ~reader-schema)]
?          (when-let [conversion# (.getConversionFor (GenericData/get) logical-type#)]
?            (fn [data#]
✘              (~(symbol method) conversion# data# ~reader-schema logical-type#)))))))
