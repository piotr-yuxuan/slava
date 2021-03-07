(ns piotr-yuxuan.slava.config
  (:require [piotr-yuxuan.slava.schema-registry :as schema-registry]
            [piotr-yuxuan.slava.decode :as decode]
            [piotr-yuxuan.slava.encode :as encode])
  (:import (org.apache.avro.generic GenericData$EnumSymbol GenericData$Record)
           (org.apache.avro.util Utf8)
           (java.nio ByteBuffer)
           (java.util Collection List Map)))

(defn domain
  [k]
  (if (schema-registry/config-keys k)
    :schema-registry
    :slava))

(defn split-domains
  [configs]
  (reduce-kv (fn [acc k v] (update acc (domain k) assoc k v)) {} configs))

(def avro-decoders
  #:decoder{:avro-record decode/avro-record
            :avro-enum nil
            :avro-array decode/avro-array
            :map-key-fn (constantly nil)
            :avro-map decode/avro-map
            :avro-union decode/avro-union
            :avro-fixed nil
            :avro-fixed-decimal nil
            :avro-fixed-duration nil
            :avro-string nil
            :avro-string-uuid nil
            :avro-bytes nil
            :avro-bytes-decimal nil
            :avro-int nil
            :avro-int-date nil
            :avro-int-time-millis nil
            :avro-long nil
            :avro-long-time-micros nil
            :avro-long-timestamp-millis nil
            :avro-long-timestamp-micros nil
            :avro-long-local-timestamp-millis nil
            :avro-long-local-timestamp-micros nil
            :avro-float nil
            :avro-double nil
            :avro-boolean nil
            :avro-null nil})

(def java-types
  "Concrete types returned by GenericAvroSerde."
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
  #:encoder{:avro-record encode/avro-record
            :avro-enum nil
            :avro-array encode/avro-array
            :map-key-fn (constantly nil)
            :avro-map encode/avro-map
            :avro-union encode/avro-union
            :avro-fixed nil
            :avro-fixed-decimal nil
            :avro-fixed-duration nil
            :avro-string nil
            :avro-string-uuid nil
            :avro-bytes nil
            :avro-bytes-decimal nil
            :avro-int nil
            :avro-int-date nil
            :avro-int-time-millis nil
            :avro-long nil
            :avro-long-time-micros nil
            :avro-long-timestamp-millis nil
            :avro-long-timestamp-micros nil
            :avro-long-local-timestamp-millis nil
            :avro-long-local-timestamp-micros nil
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
  (merge
    {:record-key-fn (constantly nil)
     :clojure-types clojure-types
     :java-types java-types}
    avro-decoders
    avro-encoders))
