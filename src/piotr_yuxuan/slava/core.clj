(ns piotr-yuxuan.slava.core
  "FIXME add cljdoc"
  (:require [piotr-yuxuan.slava.decode :refer [decode]]
            [piotr-yuxuan.slava.encode :refer [encode]])
  (:import (org.apache.avro Schema)
           (org.apache.avro.generic GenericContainer)
           (java.util Map)))

(defn deserialize
  "FIXME add cljdoc"
  ^Map [config ^Schema reader-schema ^GenericContainer data]
  (decode config reader-schema data))

(defn serialize
  "FIXME add cljdoc"
  ^Map [config ^Schema writer-schema ^Map m]
  (encode config writer-schema m))
