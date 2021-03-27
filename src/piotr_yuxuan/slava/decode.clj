(ns piotr-yuxuan.slava.decode
  "FIXME add cljdoc"
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str])
  (:import (org.apache.avro Schema$Field Schema Schema$MapSchema Schema$RecordSchema Schema$ArraySchema Schema$UnionSchema)
           (org.apache.avro.generic GenericData$Record GenericData$Array)))

(defn decoder-name
  "FIXME add cljdoc"
  [^Schema avro-schema]
  (->> avro-schema
       (.getType)
       str
       csk/->kebab-case-string
       (conj ["avro"])
       (str/join "-")
       (keyword "decoder")))

(declare -decoder-fn)

(defn avro-record
  "FIXME add cljdoc"
  [config ^Schema$RecordSchema reader-schema]
  (let [{:keys [record-key-fn]} config
        record-key (record-key-fn config reader-schema)
        field-decoders (map (fn [^Schema$Field field]
                              (let [field-name (.name field)
                                    value-decoder (-decoder-fn (assoc config :field-name field-name)
                                                               (.schema field))]
                                (cond (and value-decoder record-key) (fn [m ^GenericData$Record data] (assoc! m (record-key field-name) (value-decoder (.get data field-name))))
                                      value-decoder (fn [m ^GenericData$Record data] (assoc! m field-name (value-decoder (.get data field-name))))
                                      record-key (fn [m ^GenericData$Record data] (assoc! m (record-key field-name) (.get data field-name)))
                                      :else (fn [m ^GenericData$Record data] (assoc! m field-name (.get data field-name))))))
                            (.getFields reader-schema))]
    (fn [data]
      (let [m (transient {})]
        (doseq [decoder! field-decoders]
          (decoder! m data))
        (vary-meta
          (persistent! m)
          assoc
          :piotr-yuxuan.slava/type :avro-record
          :piotr-yuxuan.slava/reader-schema reader-schema)))))

(defn avro-array
  "FIXME add cljdoc"
  [config ^Schema$ArraySchema reader-schema]
  (when-let [value-decoder (-decoder-fn config (.getElementType reader-schema))]
    (fn [^GenericData$Array data]
      (vary-meta
        (map value-decoder data)
        assoc
        :piotr-yuxuan.slava/type :avro-array
        :piotr-yuxuan.slava/reader-schema reader-schema))))

(defn avro-map
  "FIXME add cljdoc"
  [config ^Schema$MapSchema reader-schema]
  (let [{:decoder/keys [map-key-fn]} config
        map-key (map-key-fn config reader-schema)
        value-decoder (-decoder-fn
                        ;; Don't proprate field-name any deeper
                        (dissoc config :field-name)
                        (.getValueType reader-schema))
        meta-wrapper #(vary-meta %
                                 assoc
                                 :piotr-yuxuan.slava/type :avro-map
                                 :piotr-yuxuan.slava/reader-schema reader-schema)]
    (cond (and map-key value-decoder) (comp meta-wrapper #(->> % (map (juxt (comp map-key key) (comp value-decoder val))) (into {})))
          value-decoder (comp meta-wrapper #(->> % (map (juxt key (comp value-decoder val))) (into {})))
          map-key (comp meta-wrapper #(->> % (map (juxt (comp map-key key) val)) (into {})))
          :else meta-wrapper)))

(defn avro-union
  "FIXME add cljdoc"
  [{:keys [generic-concrete-types] :as config} ^Schema$UnionSchema reader-schema]
  (let [possible-decoders (->> (.getTypes reader-schema)
                               (map (juxt decoder-name (partial -decoder-fn config)))
                               (remove (comp nil? second))
                               (into {}))
        decoded-types (select-keys generic-concrete-types (keys possible-decoders))]
    ;; If no types in the union need a decode, no need to find some.
    (when (seq decoded-types)
      (fn [data]
        (if-let [found-decoder (->> decoded-types
                                    (some (fn [[avro-type pred]] (when (pred data) avro-type)))
                                    (get possible-decoders))]
          (found-decoder data)
          ;; If the concrete type doesn't need to be decoded, return datum as is.
          data)))))

(defn -decoder-fn
  "FIXME add cljdoc"
  [config ^Schema reader-schema]
  (when-let [decoder-fn-fn (get config (decoder-name reader-schema))]
    (decoder-fn-fn config reader-schema)))

(def ^{:arglists '([config ^org.apache.avro.Schema reader-schema])
       :doc "FIXME add cljdoc"}
  ;; The assumption is that we won't see a lot of schemas here, so we can build a encoder only once.
  decoder-fn
  (memoize -decoder-fn))

(defn decode
  "FIXME add cljdoc"
  [config data ^Schema reader-schema]
  ((decoder-fn config reader-schema) data))
