(ns piotr-yuxuan.slava.decode
  "FIXME add cljdoc"
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str]
            [potemkin :refer [def-map-type]])
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

(defn avro-record-get
  [data {::keys [field-decoders]} k default-value]
  (when (instance? GenericData$Record data)
    (if-let [field-getter (get field-decoders k)]
      (field-getter data)
      default-value)))

(def-map-type AvroRecord [generic-record mta]
  (get [_ k default-value] (avro-record-get generic-record mta k default-value))
  (assoc [_ k v] (AvroRecord. (.put ^GenericData$Record generic-record ^String k v) mta))
  (dissoc [_ k] (throw (ex-info "NotImplementedException" {:error "It is not possible to dissoc the field of an GenericData$Record. Try to set the value at `nil`?"})))
  (keys [_] (-> mta ::field-decoders keys))
  (meta [_] mta)
  (with-meta [_ new-mta] (AvroRecord. generic-record (merge mta new-mta))))

(defn field-decoders
  "Return a map. The keys are the field names in the Clojure
  convention, the value are function that accept one argument `data`
  and return the field value, converted if need be."
  [{:keys [record-key-fn] :or {record-key-fn identity} :as config} ^Schema$RecordSchema schema]
  (reduce (fn [acc ^Schema$Field field]
            (let [record-key (record-key-fn config schema)
                  field-name (.name field)
                  field-getter (if-let [value-decoder (-decoder-fn (assoc config :field-name field-name) (.schema field))]
                                 (fn [^GenericData$Record data] (value-decoder (.get data field-name)))
                                 (fn [^GenericData$Record data] (.get data field-name)))]
              (assoc acc (record-key field-name) field-getter)))
          {}
          (.getFields schema)))

(defn ^AvroRecord avro-record
  "FIXME add cljdoc"
  [{:keys [record-key-fn] :or {record-key-fn identity} :as config} ^Schema$RecordSchema schema]
  (fn [^GenericData$Record generic-record]
    (->> (field-decoders config schema)
         (assoc (meta generic-record) ::field-decoders)
         (AvroRecord. generic-record))))

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
