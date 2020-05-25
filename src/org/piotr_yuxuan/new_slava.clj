(ns org.piotr-yuxuan.new-slava
  (:require [clojure.data.json :as json]
            [camel-snake-kebab.core :as csk])
  (:import (org.apache.avro Schema Schema$RecordSchema SchemaBuilder SchemaBuilder$RecordBuilder SchemaBuilder$FieldAssembler SchemaBuilder$FieldDefault Schema$Field Schema$Type)
           (org.apache.avro.generic GenericData$Record GenericRecordBuilder)))

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


(defmacro compile-clj->avro
  "https://fs.blog/2020/03/chestertons-fence/"
  [config ^Schema schema]
  (let [[builder m value] (map gensym ["builder" "m" "value"])
        macro-config (eval config)
        macro-schema (eval schema)]
    `(fn ^GenericData$Record [~m]
       (let [~builder (GenericRecordBuilder. ^Schema ~schema)]
         ~@(map (fn [^Schema$Field field]
                  (let [field-schema-name (.getFullName ^Schema (.schema field))
                        clj-name# (if-let [clj-name (->> field-schema-name
                                                         (get-in macro-config [:clj-name])
                                                         (get-in macro-config [field-schema-name :clj-name])
                                                         (get-in macro-config [field-schema-name (.name field) :clj-name]))]
                                    ((eval clj-name) (.name field))
                                    (.name field))
                        avro-name# (.name ^Schema$Field field)
                        record-field? (= Schema$Type/RECORD (.getType (.schema field)))
                        nil-as-absent? (->> (get-in macro-config [:nil-as-absent?])
                                            (get-in macro-config [(keyword (.toLowerCase (str (.getType ^Schema (.schema field))))) :nil-as-absent?])
                                            (get-in macro-config [field-schema-name :nil-as-absent?])
                                            (get-in macro-config [field-schema-name (.name field) :nil-as-absent?]))]

                    (cond
                      (and record-field? nil-as-absent?)
                      `(when-let [~value (get ~m ~clj-name#)]
                         (.set ~builder ~avro-name# ((compile-clj->avro ~config (.schema (.getField ^Schema ~schema ~(.name field))))
                                                     ~value)))

                      record-field?
                      `(.set ~builder ~avro-name# ((compile-clj->avro ~config (.schema (.getField ^Schema ~schema ~(.name field))))
                                                   (get ~m ~clj-name#)))

                      nil-as-absent?
                      `(when-let [~value (get ~m ~clj-name#)]
                         (.set ~builder ~avro-name# ~value))

                      :else
                      `(.set ~builder ~avro-name# (get ~m ~clj-name#)))))
                (.getFields ^Schema macro-schema))
         (.build ~builder)))))

(def compiler-config {:clj-name csk/->kebab-case-keyword
                      :nil-as-absent? true})
(macroexpand '(compile-clj->avro compiler-config wrapping-schema))
(def compiled (compile-clj->avro compiler-config wrapping-schema))
(compiled
  {:int-field (int 1)
   :double-field (double 1)
   :nested-field {:simple-field "value for simple-field"
                  :other-field "value for other-field"}})
