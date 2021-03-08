(ns piotr-yuxuan.slava.config
  "FIXME add cljdoc"
  (:require [piotr-yuxuan.slava.schema-registry :as schema-registry]
            [piotr-yuxuan.slava.decode :as decode]
            [piotr-yuxuan.slava.encode :as encode]
            [camel-snake-kebab.core :as csk])
  (:import (org.apache.avro.generic GenericData$EnumSymbol GenericData$Record GenericData)
           (org.apache.avro.util Utf8)
           (java.nio ByteBuffer)
           (java.util Collection List Map)
           (org.apache.avro Schema Conversions$DecimalConversion Conversions$UUIDConversion)
           (org.apache.avro.data TimeConversions$DateConversion TimeConversions$TimestampMillisConversion TimeConversions$TimeMicrosConversion TimeConversions$TimestampMicrosConversion TimeConversions$LocalTimestampMillisConversion TimeConversions$LocalTimestampMicrosConversion)))

(defn domain
  "FIXME add cljdoc"
  [k]
  (if (schema-registry/config-keys k)
    :schema-registry
    :slava))

(defn split-domains
  "FIXME add cljdoc"
  [configs]
  (reduce-kv (fn [acc k v] (update acc (domain k) assoc k v)) {} configs))

(def avro-decoders
  "FIXME add cljdoc"
  #:decoder{:avro-record decode/avro-record
            :avro-enum nil
            :avro-array decode/avro-array
            :map-key-fn (constantly nil)
            :avro-map decode/avro-map
            :avro-union decode/avro-union
            :avro-fixed nil
            :avro-fixed-decimal (fn [_ ^Schema reader-schema]
                                  (let [logical-type (.getLogicalType reader-schema)]
                                    (when-let [^Conversions$DecimalConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                      #(.fromFixed conversion % reader-schema logical-type))))
            :avro-fixed-duration (fn [_ ^Schema reader-schema]
                                   (let [logical-type (.getLogicalType reader-schema)]
                                     (when-let [conversion (.getConversionFor (GenericData/get) logical-type)]
                                       #(.fromFixed conversion % reader-schema logical-type))))
            :avro-string nil
            :avro-string-uuid (fn [_ ^Schema reader-schema]
                                (let [logical-type (.getLogicalType reader-schema)]
                                  (when-let [^Conversions$UUIDConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                    #(.fromCharSequence conversion % reader-schema logical-type))))
            :avro-bytes nil
            :avro-bytes-decimal (fn [_ ^Schema reader-schema]
                                  (let [logical-type (.getLogicalType reader-schema)]
                                    (when-let [^Conversions$DecimalConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                      #(.fromBytes conversion % reader-schema logical-type))))
            :avro-int nil
            :avro-int-date (fn [_ ^Schema reader-schema]
                             (let [logical-type (.getLogicalType reader-schema)]
                               (when-let [^TimeConversions$DateConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                 #(.fromInt conversion % reader-schema logical-type))))
            :avro-int-time-millis (fn [_ ^Schema reader-schema]
                                    (let [logical-type (.getLogicalType reader-schema)]
                                      (when-let [^TimeConversions$TimestampMillisConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                        #(.fromInt conversion % reader-schema logical-type))))
            :avro-long nil
            :avro-long-time-micros (fn [_ ^Schema reader-schema]
                                     (let [logical-type (.getLogicalType reader-schema)]
                                       (when-let [^TimeConversions$TimeMicrosConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                         #(.fromLong conversion % reader-schema logical-type))))
            :avro-long-timestamp-millis (fn [_ ^Schema reader-schema]
                                          (let [logical-type (.getLogicalType reader-schema)]
                                            (when-let [^TimeConversions$TimestampMillisConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                              #(.fromLong conversion % reader-schema logical-type))))
            :avro-long-timestamp-micros (fn [_ ^Schema reader-schema]
                                          (let [logical-type (.getLogicalType reader-schema)]
                                            (when-let [^TimeConversions$TimestampMicrosConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                              #(.fromLong conversion % reader-schema logical-type))))
            :avro-long-local-timestamp-millis (fn [_ ^Schema reader-schema]
                                                (let [logical-type (.getLogicalType reader-schema)]
                                                  (when-let [^TimeConversions$LocalTimestampMillisConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                                    #(.fromLong conversion % reader-schema logical-type))))
            :avro-long-local-timestamp-micros (fn [_ ^Schema reader-schema]
                                                (let [logical-type (.getLogicalType reader-schema)]
                                                  (when-let [^TimeConversions$LocalTimestampMicrosConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                                    #(.fromLong conversion % reader-schema logical-type))))
            :avro-float nil
            :avro-double nil
            :avro-boolean nil
            :avro-null nil})

(def java-types
  "Concrete types returned by GenericAvroSerde. You probably don't need to change them if you're getting started."
  ;; It's important to have all of them to resolve unions and further decode any nested datum when there is a need.
  #:decoder{:avro-record #(instance? GenericData$Record %)
            :avro-enum #(instance? GenericData$EnumSymbol %)
            :avro-array #(or (instance? Collection %) (instance? List %))
            :avro-map #(instance? Map %)
            :avro-union (constantly false) ; Union type can't be a concrete type.
            :avro-fixed #(instance? ByteBuffer %)
            :avro-string #(or (string? %) (instance? Utf8 %))
            :avro-bytes #(instance? ByteBuffer %)
            :avro-int int?
            :avro-long #(instance? Long %)
            :avro-float float?
            :avro-double double?
            :avro-boolean boolean?
            :avro-null nil?})

(def avro-encoders
  "FIXME add cljdoc"
  #:encoder{:avro-record encode/avro-record
            :avro-enum nil
            :avro-array encode/avro-array
            :map-key-fn (constantly nil)
            :avro-map encode/avro-map
            :avro-union encode/avro-union
            :avro-fixed nil
            :avro-fixed-decimal (fn [_ ^Schema reader-schema]
                                  (let [logical-type (.getLogicalType reader-schema)]
                                    (when-let [^Conversions$DecimalConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                      #(.toFixed conversion % reader-schema logical-type))))
            :avro-fixed-duration (fn [_ ^Schema reader-schema]
                                   (let [logical-type (.getLogicalType reader-schema)]
                                     (when-let [conversion (.getConversionFor (GenericData/get) logical-type)]
                                       #(.toFixed conversion % reader-schema logical-type))))
            :avro-string nil
            :avro-string-uuid (fn [_ ^Schema reader-schema]
                                (let [logical-type (.getLogicalType reader-schema)]
                                  (when-let [^Conversions$UUIDConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                    #(.toCharSequence conversion % reader-schema logical-type))))
            :avro-bytes nil
            :avro-bytes-decimal (fn [_ ^Schema reader-schema]
                                  (let [logical-type (.getLogicalType reader-schema)]
                                    (when-let [^Conversions$DecimalConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                      #(.toBytes conversion % reader-schema logical-type))))
            :avro-int nil
            :avro-int-date (fn [_ ^Schema reader-schema]
                             (let [logical-type (.getLogicalType reader-schema)]
                               (when-let [^TimeConversions$DateConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                 #(.toInt conversion % reader-schema logical-type))))
            :avro-int-time-millis (fn [_ ^Schema reader-schema]
                                    (let [logical-type (.getLogicalType reader-schema)]
                                      (when-let [^TimeConversions$TimestampMillisConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                        #(.toInt conversion % reader-schema logical-type))))
            :avro-long nil
            :avro-long-time-micros (fn [_ ^Schema reader-schema]
                                     (let [logical-type (.getLogicalType reader-schema)]
                                       (when-let [^TimeConversions$TimeMicrosConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                         #(.toLong conversion % reader-schema logical-type))))
            :avro-long-timestamp-millis (fn [_ ^Schema reader-schema]
                                          (let [logical-type (.getLogicalType reader-schema)]
                                            (when-let [^TimeConversions$TimestampMillisConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                              #(.toLong conversion % reader-schema logical-type))))
            :avro-long-timestamp-micros (fn [_ ^Schema reader-schema]
                                          (let [logical-type (.getLogicalType reader-schema)]
                                            (when-let [^TimeConversions$TimestampMicrosConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                              #(.toLong conversion % reader-schema logical-type))))
            :avro-long-local-timestamp-millis (fn [_ ^Schema reader-schema]
                                                (let [logical-type (.getLogicalType reader-schema)]
                                                  (when-let [^TimeConversions$LocalTimestampMillisConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                                    #(.toLong conversion % reader-schema logical-type))))
            :avro-long-local-timestamp-micros (fn [_ ^Schema reader-schema]
                                                (let [logical-type (.getLogicalType reader-schema)]
                                                  (when-let [^TimeConversions$LocalTimestampMicrosConversion conversion (.getConversionFor (GenericData/get) logical-type)]
                                                    #(.toLong conversion % reader-schema logical-type))))
            :avro-float nil
            :avro-double nil
            :avro-boolean nil
            :avro-null nil})

(def clojure-types
  "Used by the encoder. Must match types returned by the decoder."
  ;; It's important to have all of them to resolve unions and further encode any nested datum when there is a need.
  #:encoder{:avro-record map?
            :avro-enum #(instance? GenericData$EnumSymbol %)
            :avro-array sequential?
            :avro-map map?
            :avro-union (constantly false) ; Union type can't be a concrete type.
            :avro-fixed #(instance? ByteBuffer %)
            :avro-string #(or (string? %) (instance? Utf8 %))
            :avro-bytes #(instance? ByteBuffer %)
            :avro-int int?
            :avro-long #(instance? Long %)
            :avro-float float?
            :avro-double double?
            :avro-boolean boolean?
            :avro-null nil?})

(def default
  "FIXME add cljdoc"
  (merge
    {:record-key-fn (constantly nil)
     :clojure-types clojure-types
     :java-types java-types}
    avro-decoders
    avro-encoders))

(def opinionated
  "FIXME add cljdoc"
  (assoc default
    :record-key-fn (constantly csk/->kebab-case-keyword)

    :decoder/avro-enum (constantly csk/->kebab-case-keyword)
    :encoder/avro-enum (constantly csk/->SCREAMING_SNAKE_CASE_STRING)

    :decoder/map-key-fn (constantly keyword)
    :encoder/map-key-fn (constantly name)

    ;; avoid Utf8
    :decoder/avro-string (constantly str)))
