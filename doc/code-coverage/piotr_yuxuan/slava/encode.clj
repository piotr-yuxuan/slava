✔ (ns piotr-yuxuan.slava.encode
?   "FIXME add cljdoc"
?   (:require [camel-snake-kebab.core :as csk]
?             [clojure.string :as str]
?             [piotr_yuxuan.slava.decode])
?   (:import (piotr_yuxuan.slava.slava_record SlavaRecord)
?            (org.apache.avro Schema Schema$MapSchema Schema$UnionSchema Schema$ArraySchema Schema$Field Schema$RecordSchema)
?            (java.util Map)
?            (org.apache.avro.generic GenericRecordBuilder)))
  
✔ (defn encoder-name
?   "FIXME add cljdoc"
?   [^Schema avro-schema]
✔   (->> avro-schema
✔        (.getType)
✔        str
✔        csk/->kebab-case-string
✔        (conj ["avro"])
✔        (str/join "-")
✔        (keyword "encoder")))
  
✔ (declare -encoder-fn)
  
✔ (defn avro-record
?   "FIXME add cljdoc"
?   [config ^Schema$RecordSchema writer-schema]
✔   (let [{:keys [record-key-fn]} config
✔         record-key (record-key-fn config writer-schema)
✔         field-encoders (map (fn [^Schema$Field field]
✔                               (let [field-name (.name field)
~                                     map-key-name (if record-key (record-key field-name) field-name)]
✔                                 (if-let [value-encoder (-encoder-fn config (.schema field))]
✔                                   (fn [^GenericRecordBuilder record-builder ^Map m] (.set record-builder field-name (value-encoder (get m map-key-name))))
✔                                   (fn [^GenericRecordBuilder record-builder ^Map m] (.set record-builder field-name (get m map-key-name))))))
✔                             (.getFields writer-schema))]
✔     (fn [data]
✔       (if (instance? SlavaRecord data)
✔         (.unwrap data)
✔         (let [record-builder (GenericRecordBuilder. writer-schema)]
~           (doseq [encoder! field-encoders]
~             (encoder! record-builder data))
✔           (.build record-builder))))))
  
✔ (defn avro-array
?   "FIXME add cljdoc"
?   [config ^Schema$ArraySchema writer-schema]
✔   (when-let [value-encoder (-encoder-fn config (.getElementType writer-schema))]
✔     (fn avro-array-youp [data]
✔       (map value-encoder data))))
  
✔ (defn avro-map
?   "FIXME add cljdoc"
?   [config ^Schema$MapSchema writer-schema]
✔   (let [{:encoder/keys [map-key-fn]} config
✔         map-key (map-key-fn config writer-schema)
✔         value-encoder (-encoder-fn config (.getValueType writer-schema))]
~     (cond (and map-key value-encoder) #(->> % (map (juxt (comp map-key key) (comp value-encoder val))) (into {}))
✔           value-encoder #(->> % (map (juxt key (comp value-encoder val))) (into {}))
~           map-key #(->> % (map (juxt (comp map-key key) val)) (into {}))
?           :else nil)))
  
✔ (declare encoder-fn)
  
✔ (defn avro-union
?   "FIXME add cljdoc"
?   [{:keys [clojure-types] :as config} ^Schema$UnionSchema writer-schema]
✔   (let [possible-encoders (->> (.getTypes writer-schema)
✔                                (map (juxt encoder-name (partial -encoder-fn config)))
✔                                (remove (comp nil? second)))
✔         encoded-types (select-keys clojure-types (map first possible-encoders))
✔         possible-encoders (into {} possible-encoders)]
?     ;; If no types in the union need a encode, no need to find some.
✔     (when (seq encoded-types)
✔       (fn [data]
~         (condp (fn trololo [t _] t) nil
✔           (:piotr-yuxuan.slava/schema data)
✘           :>> #((-encoder-fn config %) data)
  
✔           (when-let [m (meta data)]
✔             (some m [:piotr-yuxuan.slava/writer-schema
?                      :piotr-yuxuan.slava/reader-schema]))
?           ;; memoize?
✘           :>> #((encoder-fn config %) data)
  
✔           (some->> (some :piotr-yuxuan.slava/type [(meta data) data])
✔                    name
✔                    (keyword "encoder")
✔                    (get possible-encoders))
✔           :>> #(% data)
  
?           ;; BEWARE Opinionated choice, but that can be challenged. If
?           ;; the above heuristics don't work, return the first
?           ;; possible encoder. Some undesirable behaviours can't be
?           ;; avoided: for example it's not possible just by looking at
?           ;; `{:my-field 1}` to tell whether it is a map with one
?           ;; entry, or a record with one field. If you want certainty
?           ;; to break a tie in a predictable way, see explicit
?           ;; encoders above.
?           ;;
?           ;; We could use malli to observe what is the first matching
?           ;; type in the union. That would solve the map/record tie in
?           ;; the most common ways.
✔           (some (fn first-possible [[avro-type pred]] (when (pred data) avro-type))
✔                 encoded-types)
✔           :>> #((get possible-encoders %) data)
  
?           ;; If the concrete type doesn't need to be encoded, return
?           ;; datum as is.
✔           :else data)))))
  
✔ (defn -encoder-fn
?   "FIXME add cljdoc"
?   [config ^Schema writer-schema]
✔   (when-let [encoder-fn-fn (get config (encoder-name writer-schema))]
✔     (encoder-fn-fn config writer-schema)))
  
✔ (def ^{:arglists '([config ^org.apache.avro.Schema writer-schema])
?        :doc "FIXME add cljdoc"}
?   ;; The assumption is that we won't see a lot of schemas here, so we can build a encoder only once.
?   encoder-fn
✔   (memoize -encoder-fn))
  
✔ (defn encode
?   "FIXME add cljdoc"
?   [config data ^Schema writer-schema]
✔   ((encoder-fn config writer-schema) data))
