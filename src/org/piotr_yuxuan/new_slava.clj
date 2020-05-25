(ns org.piotr-yuxuan.new-slava
  (:require [clojure.data.json :as json]
            [camel-snake-kebab.core :as csk])
  (:import (org.apache.avro Schema Schema$RecordSchema SchemaBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler SchemaBuilder$FieldDefault Schema$Field Schema$Type SchemaBuilder$MapDefault)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder GenericData$EnumSymbol)
           (java.lang.reflect Method)))

(def ^Schema$RecordSchema nested-schema
  (-> (SchemaBuilder/builder)
      (.record "Nested")
      ^SchemaBuilder$RecordBuilder (.namespace "org.piotr-yuxuan.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "simpleField") .type .stringType (.stringDefault "default value")
      (.name "otherField") .type .stringType .noDefault
      (.name "mapField") .type .map .values ^SchemaBuilder$MapDefault .stringType (.mapDefault {"default singleton map key" "🚀"})
      ^SchemaBuilder$FieldDefault .endRecord))

(clojure.walk/keywordize-keys (json/read-str (str nested-schema)))

(def ^Schema$RecordSchema wrapping-schema
  (-> (SchemaBuilder/builder)
      (.record "Record")
      ^SchemaBuilder$RecordBuilder (.namespace "org.piotr-yuxuan.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "intField") .type .intType .noDefault
      (.name "doubleField") .type .doubleType (.doubleDefault (double 12))
      (.name "nestedField") (.type nested-schema) .noDefault
      .endRecord))

(clojure.walk/keywordize-keys (json/read-str (str wrapping-schema)))

(defn default-value->avro
  [registry schema]
  nil)

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
  (let [[builder m value] (map gensym ["builder" "m" "value"])
        macro-registry (eval registry)
        macro-schema (eval schema)]
    `(fn ^GenericData$Record [~m]
       (let [~builder (GenericRecordBuilder. ^Schema ~schema)]
         ~@(map (fn [^Schema$Field field]
                  (let [record-schema-name (.getFullName ^Schema macro-schema)
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
                        record-field? (= Schema$Type/RECORD (.getType (.schema field)))
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
                        ?value->avro (cond (and schema->avro (nil? custom->avro)) schema->avro
                                           (and (nil? schema->avro) custom->avro) custom->avro
                                           (and schema->avro custom->avro) `(comp ~schema->avro ~custom->avro))]
                    (cond
                      ;; schema type: record
                      (and record-field? nil-as-absent?)
                      `(when-let [~value (get ~m ~field-name#)]
                         (.set ~builder ~avro-name# ((compile-clj->avro ~registry (.schema (.getField ^Schema ~schema ~(.name field))))
                                                     ~value)))

                      (and record-field? (not nil-as-absent?))
                      `(.set ~builder ~avro-name# ((compile-clj->avro ~registry (.schema (.getField ^Schema ~schema ~(.name field))))
                                                   (get ~m ~field-name#)))

                      ;; schema type: not record
                      (and nil-as-absent? ?value->avro)
                      `(when-let [~value (get ~m ~field-name#)]
                         (.set ~builder ~avro-name# (~?value->avro ~value)))

                      (and nil-as-absent? (not ?value->avro))
                      `(when-let [~value (get ~m ~field-name#)]
                         (.set ~builder ~avro-name# ~value))

                      (and (not nil-as-absent?) (not ?value->avro))
                      `(.set ~builder ~avro-name# (get ~m ~field-name#)))))
                (.getFields ^Schema macro-schema))
         (.build ~builder)))))

(defmacro get-local-value
  [schema-sym]
  `(do (println (.getFullName ^Schema ~schema-sym))
       1))

(let [a 1]
  (get-local-value wrapping-schema))

clojure.lang.Compiler$LocalBinding

(get-local-value filter-key)
(get-local-value #'time)

(def clj->avro-registry
  {;; Base serialisers. Always applied.
   :record (fn [registry schema]
             ;; Unexpected error (UnsupportedOperationException) macroexpanding compile-clj->avro. Can't eval locals.
             nil)
   :enum (fn [{:keys [enum-name]} ^Schema schema] (println :enum)
           (if enum-name
             (fn [value] (GenericData$EnumSymbol. schema (enum-name value)))
             (fn [value] (GenericData$EnumSymbol. schema value))))
   :array (fn [registry ^Schema schema] (println :array) nil)
   :map (fn [registry ^Schema schema] (println :map)
          (let [compiler `(fn [] (compile-clj->avro ~registry ~schema))]
            (println (type compiler))
            (fn [value] value)))
   :union (fn [registry ^Schema schema] (println :union) nil)
   :fixed (fn [registry ^Schema schema] (println :fixed) nil)
   :string (fn [registry ^Schema schema] (println :string) nil)
   :bytes (fn [registry ^Schema schema] (println :bytes) nil)
   :int (fn [registry ^Schema schema] (println :int) (fn [value]
                                                       (println :base-int)
                                                       (+ 1 value)))
   :long (fn [registry ^Schema schema] (println :long) nil)
   :float (fn [registry ^Schema schema] (println :float) nil)
   :double (fn [registry ^Schema schema] (println :double) nil)
   :boolean (fn [registry ^Schema schema] (println :boolean) nil)
   :null (fn [registry ^Schema schema] (println :null) nil)
   ;; Custom configuration keys
   :enum-name csk/->SCREAMING_SNAKE_CASE_STRING
   :field-name csk/->kebab-case-keyword
   :nil-as-absent? true
   ;; Custom serialisers. Applied before base serialisers.
   :org.piotr-yuxuan.test.Record/intField (fn [registry ^Schema schema]
                                            (println :org.piotr-yuxuan.test.Record/intField)
                                            (fn [value]
                                              (* 2 value)))
   :org.piotr-yuxuan.test.Nested/mapField (fn [registry ^Schema schema]
                                            (println :org.piotr-yuxuan.test.Nested/mapField)
                                            (fn [value]
                                              (assoc value :c "I'm c.")))})
(macroexpand '(compile-clj->avro clj->avro-registry wrapping-schema))
(def compiled (compile-clj->avro clj->avro-registry wrapping-schema))
(compiled
  {:int-field (int 1)
   :double-field (double 1)
   :nested-field {:map-field {1 1
                              "b" 1}
                  :simple-field "value for simple-field"
                  :other-field "value for other-field"}})
