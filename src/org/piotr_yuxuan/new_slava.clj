(ns org.piotr-yuxuan.new-slava
  (:require [clojure.data.json :as json]
            [camel-snake-kebab.core :as csk])
  (:import (org.apache.avro Schema Schema$RecordSchema SchemaBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler SchemaBuilder$FieldDefault Schema$Field Schema$Type)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder)
           (java.lang.reflect Method)))

(def ^Schema$RecordSchema simple-schema
  (-> (SchemaBuilder/builder)
      (.record "Nested")
      ^SchemaBuilder$RecordBuilder (.namespace "org.piotr-yuxuan.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "simpleField") .type .stringType (.stringDefault "default value")
      (.name "otherField") .type .stringType .noDefault
      ^SchemaBuilder$FieldDefault .endRecord))

(clojure.walk/keywordize-keys (json/read-str (str simple-schema)))

(def ^Schema$RecordSchema wrapping-schema
  (-> (SchemaBuilder/builder)
      (.record "Record")
      ^SchemaBuilder$RecordBuilder (.namespace "org.piotr-yuxuan.test")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "intField") .type .intType .noDefault
      (.name "doubleField") .type .doubleType (.doubleDefault (double 12))
      (.name "nestedField") (.type simple-schema) .noDefault
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
                        clj-name# (if-let [clj-name (->> record-schema-name
                                                         (get-in macro-registry [:clj-name])
                                                         (get-in macro-registry [(.getType ^Schema (.schema field)) :clj-name])
                                                         (get-in macro-registry [(keyword (.toLowerCase (str (.getType ^Schema (.schema field))))) :clj-name])
                                                         (get-in macro-registry [record-schema-name :clj-name])
                                                         (get-in macro-registry [(keyword record-schema-name) :clj-name])
                                                         (get-in macro-registry [(keyword record-schema-name (.name field)) :clj-name])
                                                         (get-in macro-registry [record-schema-name (.name field) :clj-name])
                                                         (get-in macro-registry [(keyword record-schema-name) (.name field) :clj-name]))]
                                    ((eval clj-name) (.name field))
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
                        value->avro-fn (->> default-value->avro
                                            (get-in macro-registry [(.getType ^Schema (.schema field))])
                                            (get-in macro-registry [(keyword (.toLowerCase (str (.getType ^Schema (.schema field)))))])
                                            (get-in macro-registry [record-schema-name])
                                            (get-in macro-registry [(keyword record-schema-name)])
                                            (get-in macro-registry [(keyword record-schema-name (.name field))])
                                            (get-in macro-registry [record-schema-name (.name field)])
                                            (get-in macro-registry [(keyword record-schema-name) (.name field)]))
                        ?value->avro (when value->avro-fn
                                       (condp #(contains? %2 %1) (arities value->avro-fn)
                                         :variadic (value->avro-fn macro-registry macro-schema)
                                         2 (value->avro-fn macro-registry macro-schema) ;; standard case
                                         1 (value->avro-fn macro-schema)
                                         0 (value->avro-fn)
                                         (throw (ex-info "value->avro-fn doesn't have compatible arity." {:value->avro-fn value->avro-fn
                                                                                                          :arities (arities value->avro-fn)}))))]
                    (cond
                      ;; schema type: record
                      (and record-field? nil-as-absent?) ;; no value->avro overload for records, so nested builder built at compile time. Would it simplify to put that in the registry?
                      `(when-let [~value (get ~m ~clj-name#)]
                         (.set ~builder ~avro-name# ((compile-clj->avro ~registry (.schema (.getField ^Schema ~schema ~(.name field))))
                                                     ~value)))

                      (and record-field? (not nil-as-absent?))
                      `(.set ~builder ~avro-name# ((compile-clj->avro ~registry (.schema (.getField ^Schema ~schema ~(.name field))))
                                                   (get ~m ~clj-name#)))

                      ;; schema type: not record
                      (and nil-as-absent? ?value->avro)
                      `(when-let [~value (get ~m ~clj-name#)]
                         (.set ~builder ~avro-name# (~?value->avro ~value)))

                      (and nil-as-absent? (not ?value->avro))
                      `(when-let [~value (get ~m ~clj-name#)]
                         (.set ~builder ~avro-name# ~value))

                      (and (not nil-as-absent?) (not ?value->avro))
                      `(.set ~builder ~avro-name# (get ~m ~clj-name#)))))
                (.getFields ^Schema macro-schema))
         (.build ~builder)))))

(def clj->avro-registry
  {:record (fn [registry schema] (println :record) nil)
   :enum (fn [registry schema] (println :enum) nil)
   :array (fn [registry schema] (println :array) nil)
   :map (fn [registry schema] (println :map) nil)
   :union (fn [registry schema] (println :union) nil)
   :fixed (fn [registry schema] (println :fixed) nil)
   :string (fn [registry schema] (println :string) nil)
   :bytes (fn [registry schema] (println :bytes) nil)
   :int (fn [registry schema] (println :int) nil)
   :long (fn [registry schema] (println :long) nil)
   :float (fn [registry schema] (println :float) nil)
   :double (fn [registry schema] (println :double) nil)
   :boolean (fn [registry schema] (println :boolean) nil)
   :null (fn [registry schema] (println :null) nil)
   ;; Custom configuration keys
   :clj-name csk/->kebab-case-keyword
   :nil-as-absent? true
   :org.piotr-yuxuan.test.Nested/otherField (fn [] (println :org.piotr-yuxuan.test.Nested/otherField) (fn [value] value))})
(macroexpand '(compile-clj->avro clj->avro-registry wrapping-schema))
(def compiled (compile-clj->avro clj->avro-registry wrapping-schema))
(compiled
  {:int-field (int 1)
   :double-field (double 1)
   :nested-field {:simple-field "value for simple-field"
                  :other-field "value for other-field"}})
