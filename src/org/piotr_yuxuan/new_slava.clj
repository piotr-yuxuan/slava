(ns org.piotr-yuxuan.new-slava
  (:require [clojure.data.json :as json]
            [camel-snake-kebab.core :as csk])
  (:import (org.apache.avro Schema Schema$RecordSchema SchemaBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler SchemaBuilder$FieldDefault Schema$Field Schema$Type SchemaBuilder$MapDefault SchemaBuilder$GenericDefault Schema$StringSchema SchemaBuilder$StringBldr)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder GenericData$EnumSymbol)
           (java.lang.reflect Method)))

(def ^Schema$RecordSchema nested-schema
  (-> (SchemaBuilder/builder)
      (.record "Nested")
      ^SchemaBuilder$RecordBuilder (.namespace "org.piotr-yuxuan.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "simpleField") .type .stringType (.stringDefault "default value")
      (.name "otherField") .type .stringType .noDefault
      ^SchemaBuilder$FieldDefault .endRecord))

(clojure.walk/keywordize-keys (json/read-str (str nested-schema)))

(def ^Schema$RecordSchema wrapping-schema
  (-> (SchemaBuilder/builder)
      (.record "Record")
      ^SchemaBuilder$RecordBuilder (.namespace "org.piotr-yuxuan.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "utf8Field") .type .stringBuilder ^SchemaBuilder$StringBldr (.prop "avro.java.string" "Utf8") ^SchemaBuilder$FieldDefault .endString .noDefault
      (.name "intField") .type .intType .noDefault
      (.name "doubleField") .type .doubleType (.doubleDefault (double 12))
      (.name "mapField") .type .map .values ^SchemaBuilder$MapDefault .stringType (.mapDefault {"default singleton map key" "🚀"})
      (.name "nestedField") ^SchemaBuilder$GenericDefault (.type nested-schema) .noDefault
      .endRecord))

(clojure.walk/keywordize-keys (json/read-str (str wrapping-schema)))

(defn arities
  "Returns the arities of:
    - anonymous functions like `#()` and `(fn [])`.
    - defined functions like `map` or `+`.
    - macros, by passing a var like `#'->`.

  Returns `:variadic` if the function/macro is variadic.

  Inspired from https://stackoverflow.com/a/47861069"
  [f]
  (let [func (if (var? f) @f f)
        methods (->> func
                     class
                     .getDeclaredMethods
                     (map (juxt #(.getName ^Method %)
                                #(count (.getParameterTypes ^Method %)))))
        var-args? (some #(-> % first #{"getRequiredArity"})
                        methods)]
    (set (if var-args?
           [:variadic]
           (let [arities (->> methods
                              (filter (comp #{"invoke"} first))
                              (map second))]
             (if (and (var? f) (-> f meta :macro))
               (map #(- % 2) arities) ;; substract implicit &form and &env arguments
               arities))))))

(defmacro compile-clj->avro
  "https://fs.blog/2020/03/chestertons-fence/"
  [registry ^Schema schema]
  (let [[builder m value compiler] (map gensym ["builder" "m" "value" "compiler"])
        macro-registry (eval registry)
        ^Schema macro-schema (eval schema)]
    (cond
      (= Schema$Type/RECORD (.getType macro-schema))
      `(fn ^GenericData$Record [~m]
         (let [~builder (GenericRecordBuilder. ^Schema ~schema)]
           ~@(map (fn [^Schema$Field field]
                    (let [record-schema-name (.getFullName macro-schema)
                          ;; Enforce with malli the registry keep simple. No subtle keys hiding others.
                          field-name# (if-let [field-name (->> record-schema-name
                                                               (get-in macro-registry [:field-name])
                                                               (get-in macro-registry [(.getType ^Schema (.schema field)) :field-name])
                                                               (get-in macro-registry [(keyword (.toLowerCase (str (.getType ^Schema (.schema field))))) :field-name])
                                                               (get-in macro-registry [record-schema-name :field-name])
                                                               (get-in macro-registry [(keyword record-schema-name) :field-name])
                                                               (get-in macro-registry [(keyword record-schema-name (.name field)) :field-name])
                                                               (get-in macro-registry [record-schema-name (.name field) :field-name])
                                                               (get-in macro-registry [(keyword record-schema-name) (.name field) :field-name]))]
                                        ((eval field-name) (.name field))
                                        (.name field))
                          avro-name# (.name ^Schema$Field field)
                          nil-as-absent? (->> record-schema-name
                                              (get-in macro-registry [:nil-as-absent?])
                                              (get-in macro-registry [(.getType ^Schema (.schema field)) :nil-as-absent?])
                                              (get-in macro-registry [(keyword (.toLowerCase (str (.getType ^Schema (.schema field))))) :nil-as-absent?])
                                              (get-in macro-registry [record-schema-name :nil-as-absent?])
                                              (get-in macro-registry [(keyword record-schema-name) :nil-as-absent?])
                                              (get-in macro-registry [(keyword record-schema-name (.name field)) :nil-as-absent?])
                                              (get-in macro-registry [record-schema-name (.name field) :nil-as-absent?])
                                              (get-in macro-registry [(keyword record-schema-name) (.name field) :nil-as-absent?]))
                          schema->avro (let [f (->> ::not-found
                                                    (get-in macro-registry [(.getType ^Schema (.schema field))])
                                                    (get-in macro-registry [(keyword (.toLowerCase (str (.getType ^Schema (.schema field)))))]))]
                                         (when-not (= ::not-found f) (f macro-registry macro-schema)))
                          custom->avro (let [f (->> ::not-found
                                                    (get-in macro-registry [record-schema-name])
                                                    (get-in macro-registry [(keyword record-schema-name)])
                                                    (get-in macro-registry [(keyword record-schema-name (.name field))])
                                                    (get-in macro-registry [record-schema-name (.name field)])
                                                    (get-in macro-registry [(keyword record-schema-name) (.name field)]))]
                                         (when-not (= ::not-found f) (f macro-registry macro-schema)))
                          ?value->avro# (cond (and schema->avro (nil? custom->avro)) schema->avro
                                              (and (nil? schema->avro) custom->avro) custom->avro
                                              (and schema->avro custom->avro) `(comp ~schema->avro ~custom->avro))
                          value# (if ?value->avro# `(~?value->avro# (get ~m ~field-name#)) `(get ~m ~field-name#))]
                      (cond
                        ;; schema type: record
                        (and (= Schema$Type/RECORD (.getType (.schema field))) nil-as-absent?)
                        `(when-let [~value ~value#]
                           (let [~compiler (compile-clj->avro ~registry (.schema (.getField ^Schema ~schema ~(.name field))))]
                             (.set ~builder ~avro-name# (~compiler ~value))))

                        (and (= Schema$Type/RECORD (.getType (.schema field))) (not nil-as-absent?))
                        `(let [~compiler (compile-clj->avro ~registry (.schema (.getField ^Schema ~schema ~(.name field))))]
                           (.set ~builder ~avro-name# (~compiler ~value#)))

                        ;; schema type: map
                        (and (= Schema$Type/MAP (.getType (.schema field))) nil-as-absent?)
                        `(when-let [~value ~value#]
                           (if-let [~compiler (compile-clj->avro ~registry (.getValueType (.schema (.getField ^Schema ~schema ~(.name field)))))]
                             (.set ~builder ~avro-name# (zipmap (keys ~value)
                                                                (map ~compiler (vals ~value))))
                             (.set ~builder ~avro-name# ~value)))

                        (and (= Schema$Type/MAP (.getType (.schema field))) (not nil-as-absent?))
                        `(if-let [~compiler (compile-clj->avro ~registry (.getValueType (.schema (.getField ^Schema ~schema ~(.name field)))))]
                           (.set ~builder ~avro-name# (zipmap (keys ~value#)
                                                              (map ~compiler (vals ~value#))))
                           (.set ~builder ~avro-name# ~value#))

                        ;; schema type: array
                        (and (= Schema$Type/ARRAY (.getType (.schema field))) nil-as-absent?)
                        `(when-let [~value ~value#]
                           (if-let [~compiler (compile-clj->avro ~registry (.getElementType (.schema (.getField ^Schema ~schema ~(.name field)))))]
                             (.set ~builder ~avro-name# (map ~compiler ~value))
                             (.set ~builder ~avro-name# ~value)))

                        (and (= Schema$Type/ARRAY (.getType (.schema field))) (not nil-as-absent?))
                        `(if-let [~compiler (compile-clj->avro ~registry (.getElementType (.schema (.getField ^Schema ~schema ~(.name field)))))]
                           (.set ~builder ~avro-name# (map ~compiler ~value#))
                           (.set ~builder ~avro-name# ~value#))

                        ;; schema type: not record
                        (and nil-as-absent? ?value->avro#)
                        `(when-let [~value ~value#]
                           (.set ~builder ~avro-name# ~value))

                        (and (not nil-as-absent?))
                        `(.set ~builder ~avro-name# ~value#))))
                  (.getFields macro-schema))
           (.build ~builder)))

      :else
      (do (println :else)
          (let [f (->> ::not-found
                       (get-in macro-registry [(.getType ^Schema macro-schema)])
                       (get-in macro-registry [(keyword (.toLowerCase (str (.getType ^Schema macro-schema))))]))]
            (when-not (= ::not-found f)
              (f macro-registry macro-schema)))))))

(def clj->avro-registry
  {;; Base serialisers. Always applied.
   :enum (fn [{:keys [enum-name]} ^Schema schema] (println :enum)
           (if enum-name
             (fn [value] (GenericData$EnumSymbol. schema (enum-name value)))
             (fn [value] (GenericData$EnumSymbol. schema value))))
   :union (fn [registry ^Schema schema] (println :union) nil)
   :fixed (fn [registry ^Schema schema] (println :fixed) nil)
   :string (fn [registry ^Schema schema] (println :string) (fn [value] (str value)))
   :bytes (fn [registry ^Schema schema] (println :bytes) nil)
   :int (fn [registry ^Schema schema] (println :int) (fn [value] (int value)))
   :long (fn [registry ^Schema schema] (println :long) (fn [value] (long value)))
   :float (fn [registry ^Schema schema] (println :float) (fn [value] (float value)))
   :double (fn [registry ^Schema schema] (println :double) (fn [value] (double value)))
   :boolean (fn [registry ^Schema schema] (println :boolean) (fn [value] (boolean value)))
   :null (fn [registry ^Schema schema]
           ;; no transformation, schema builder will throw an exception if value is not nil.
           nil)
   ;; Custom configuration keys
   :enum-name csk/->SCREAMING_SNAKE_CASE_STRING
   :field-name csk/->kebab-case-keyword
   :nil-as-absent? false
   ;; Custom serialisers. Applied before base serialisers.
   :org.piotr-yuxuan.test.Record/intField (fn [registry ^Schema schema]
                                            (fn [value]
                                              (* 2 value)))
   :org.piotr-yuxuan.test.Record/mapField (fn [registry ^Schema schema]
                                            (fn [value]
                                              (assoc value :c "I'm c.")))})
(macroexpand '(compile-clj->avro clj->avro-registry wrapping-schema))
(def compiled (compile-clj->avro clj->avro-registry wrapping-schema))
(println "post-compilation")
(compiled
  {:int-field (int 1)
   :utf-8-field "I am a string."
   :double-field (double 1)
   :map-field {1 :a, "b" 1}
   :nested-field {:simple-field "value for simple-field"
                  :other-field "value for other-field"}})

(macroexpand '(compile-clj->avro clj->avro-registry nested-schema))
(def nested-compiled (compile-clj->avro clj->avro-registry nested-schema))
(println "post-compilation for second")
(nested-compiled
  {:simple-field "value for simple-field"
   :other-field "value for other-field"})
