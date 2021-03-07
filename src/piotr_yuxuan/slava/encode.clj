(ns piotr-yuxuan.slava.encode
  "FIXME add cljdoc"
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as str])
  (:import (org.apache.avro Schema Schema$MapSchema Schema$UnionSchema Schema$ArraySchema Schema$Field Schema$RecordSchema)
           (java.util Map)
           (org.apache.avro.generic GenericRecordBuilder)))

(defn encoder-name
  "FIXME add cljdoc"
  [^Schema avro-schema]
  (let [schema-type (-> avro-schema (.getType) str csk/->kebab-case-string)
        logical-type (some-> avro-schema (.getLogicalType) (.getName))]
    (->> ["avro" schema-type logical-type]
         (remove nil?)
         (str/join "-")
         (keyword "encoder"))))

(declare -encoder-fn encode)

(defn avro-record
  "FIXME add cljdoc"
  [config ^Schema$RecordSchema writer-schema]
  (let [{:keys [record-key-fn]} config
        record-key (record-key-fn config writer-schema)
        field-encoders (map (fn [^Schema$Field field]
                              (let [value-encoder (-encoder-fn config (.schema field))
                                    field-name (.name field)]
                                (cond (and value-encoder record-key) (fn [^GenericRecordBuilder record-builder ^Map m] (.set record-builder field-name (value-encoder (get m (record-key field-name)))))
                                      (and value-encoder) (fn [^GenericRecordBuilder record-builder ^Map m] (.set record-builder field-name (value-encoder (get m field-name))))
                                      (and record-key) (fn [^GenericRecordBuilder record-builder ^Map m] (.set record-builder field-name (get m (record-key field-name))))
                                      :else (fn [^GenericRecordBuilder record-builder ^Map m] (.set record-builder field-name (get m field-name))))))
                            (.getFields writer-schema))]
    (fn [data]
      (let [record-builder (GenericRecordBuilder. writer-schema)]
        (doseq [encoder! field-encoders]
          (encoder! record-builder data))
        (.build record-builder)))))

(defn avro-array
  "FIXME add cljdoc"
  [config ^Schema$ArraySchema writer-schema]
  (when-let [value-encoder (-encoder-fn config (.getElementType writer-schema))]
    (fn [data] (map value-encoder data))))

(defn avro-map
  "FIXME add cljdoc"
  [config ^Schema$MapSchema writer-schema]
  (let [{:encoder/keys [map-key-fn]} config
        map-key (map-key-fn config writer-schema)
        value-encoder (-encoder-fn config (.getValueType writer-schema))]
    (cond (and map-key value-encoder) #(->> % (map (juxt (comp map-key key) (comp value-encoder val))) (into {}))
          (and value-encoder) #(->> % (map (juxt key (comp value-encoder val))) (into {}))
          (and map-key) #(->> % (map (juxt (comp map-key key) val)) (into {}))
          :else nil)))

(defn avro-union
  "FIXME add cljdoc"
  [{:keys [clojure-types] :as config} ^Schema$UnionSchema writer-schema]
  (let [possible-encoders (->> (.getTypes writer-schema)
                               (map (juxt encoder-name (partial -encoder-fn config)))
                               (remove (comp nil? second))
                               (into {}))
        encoded-types (select-keys clojure-types (keys possible-encoders))]
    ;; If no types in the union need a encode, no need to find some.
    (when (seq encoded-types)
      (fn [data]
        (condp (fn trololo [t _] t) nil
          (some data [:piotr-yuxuan.slava/writer-schema
                      :piotr-yuxuan.slava/reader-schema
                      :piotr-yuxuan.slava/schema])
          :>> #(encode config % data)

          (some->> (some :piotr-yuxuan.slava/type [(meta data) data])
                   name
                   (keyword "encoder")
                   (get possible-encoders))
          :>> #(% data)

          ;; BEWARE Opinionated choice, but that can be challenged. If
          ;; the above heuristics don't work, return the first
          ;; possible encoder. Some undesirable behaviours can't be
          ;; avoided: for example it's not possible just by looking at
          ;; `{:my-field 1}` to tell whether it is a map with one
          ;; entry, or a record with one field. If you want certainty
          ;; to break a tie in a predictable way, see explicit
          ;; encoders above.
          (some (fn first-possible [[avro-type pred]] (when (pred data) avro-type))
                encoded-types)
          :>> #(% data)

          ;; If the concrete type doesn't need to be encoded, return
          ;; datum as is.
          :else data)))))

(defn -encoder-fn
  "FIXME add cljdoc"
  [config ^Schema writer-schema]
  (when-let [encoder (get config (encoder-name writer-schema))]
    (encoder config writer-schema)))

(def ^{:arglists '([config ^org.apache.avro.Schema writer-schema])
       :doc "FIXME add cljdoc"}
  ;; The assumption is that we won't see a lot of schemas here, so we can build a encoder only once.
  encoder-fn
  (memoize -encoder-fn))

(defn encode
  "FIXME add cljdoc"
  [config ^Schema writer-schema data]
  ((encoder-fn config writer-schema) data))
