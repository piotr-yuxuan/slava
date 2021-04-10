(ns piotr-yuxuan.slava.config
  "FIXME add cljdoc"
  (:require [piotr-yuxuan.slava.decode :as decode]
            [piotr-yuxuan.slava.encode :as encode]
            [camel-snake-kebab.core :as csk])
  (:import (org.apache.avro.generic GenericData$EnumSymbol GenericData$Record)
           (org.apache.avro.util Utf8)
           (java.nio ByteBuffer)
           (java.util Collection List Map)
           (org.apache.avro Schema$EnumSchema)))

(defn slava-key?
  [k]
  (not (string? k)))

(def avro-decoders
  "FIXME add cljdoc"
  #:decoder{:avro-record decode/avro-record
            :avro-enum nil
            :avro-array decode/avro-array
            :map-key-fn (constantly nil)
            :avro-map decode/avro-map
            :avro-union decode/avro-union
            :avro-fixed nil
            :avro-string nil
            :avro-bytes nil
            :avro-int nil
            :avro-long nil
            :avro-float nil
            :avro-double nil
            :avro-boolean nil
            :avro-null nil})

(def generic-concrete-types
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
            :avro-string nil
            :avro-bytes nil
            :avro-int nil
            :avro-long nil
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
     :generic-concrete-types generic-concrete-types
     :subject-name->id :default}
    avro-decoders
    avro-encoders))

(def opinionated
  "FIXME add cljdoc"
  (assoc default
    :record-key-fn (constantly csk/->kebab-case-keyword)

    ;; Explicit example of a decoder
    :decoder/avro-enum (fn enum-decoder-compiler [_config ^Schema$EnumSchema _reader-schema]
                         (fn enum-decoder [^GenericData$EnumSymbol data]
                           (csk/->kebab-case-keyword (.toString data))))

    ;; Explicit example of an encoder
    :encoder/avro-enum (fn enum-encoder-compiler [_config ^Schema$EnumSchema writer-schema]
                         (fn enum-encoder [data]
                           (GenericData$EnumSymbol.
                             writer-schema
                             (csk/->SCREAMING_SNAKE_CASE_STRING data))))

    :decoder/map-key-fn (fn [{:keys [field-name]} _] (partial keyword field-name))
    :encoder/map-key-fn (constantly name)

    ;; avoid Utf8
    :decoder/avro-string (constantly str)))
