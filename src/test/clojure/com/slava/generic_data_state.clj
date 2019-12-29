(ns com.slava.generic-data-state
  "Heavily depends on internal implementation and current JVM internals. Should not be used except for tests."
  (:require [clojure.test :refer :all]
            [com.slava.conversion-native :as conversion-native])
  (:import (org.apache.avro.generic GenericData)
           (java.lang.reflect Modifier Field)
           (java.util HashMap IdentityHashMap)
           (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimeMillisConversion TimeConversions$TimeMicrosConversion TimeConversions$TimestampMillisConversion TimeConversions$TimestampMicrosConversion)
           (org.apache.avro Conversions$DecimalConversion Conversions$UUIDConversion)))

(defn unlock-access!
  [^Field field ^Field modifiers]
  (.setAccessible field true)
  (doto modifiers
    (.setAccessible true)
    (.setInt field (bit-and (.getModifiers field) (bit-not Modifier/FINAL)))))

(defn lock-access!
  [^Field field ^Field modifiers]
  (doto modifiers
    (.setInt field (bit-and (.getModifiers field) Modifier/FINAL))
    (.setAccessible false))
  (.setAccessible field false))

(defn reset-field-value!
  [object field-name new-value]
  (let [conversion (.getDeclaredField (class object) field-name)
        conversion-modifiers (.getDeclaredField (class conversion) "modifiers")]
    (unlock-access! conversion conversion-modifiers)
    (.set conversion object new-value)
    (lock-access! conversion conversion-modifiers)))

(defn brittle-reset-all-conversions!
  []
  (reset-field-value! (GenericData/get) "conversions" (HashMap.))
  (reset-field-value! (GenericData/get) "conversionsByClass" (IdentityHashMap.)))

(defn add-all-conversions!
  []
  (doto (GenericData/get)
    (.addLogicalTypeConversion (Conversions$DecimalConversion.))
    (.addLogicalTypeConversion (Conversions$UUIDConversion.))
    (.addLogicalTypeConversion (TimeConversions$DateConversion.))
    (.addLogicalTypeConversion (TimeConversions$TimeMillisConversion.))
    (.addLogicalTypeConversion (TimeConversions$TimeMicrosConversion.))
    (.addLogicalTypeConversion (TimeConversions$TimestampMillisConversion.))
    (.addLogicalTypeConversion (TimeConversions$TimestampMicrosConversion.))
    (.addLogicalTypeConversion (conversion-native/duration-conversion))))

(defmacro with-all-conversions
  [& body]
  `(do (add-all-conversions!)
       (try
         ~@body
         (finally
           (brittle-reset-all-conversions!)))))
