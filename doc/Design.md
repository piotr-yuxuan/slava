# Design

## Functional core, imperative shell

This library is organised with the principle of _functional core,
imperative shell_. The namespace `piotr-yuxuan.slava` is the main
entrypoint, or imperative shell which contains the mutable state, and
reference to stateful Java objects.

Other nested namespaces `piotr-yuxuan.slava.*` remain reachable from
user code, and might be useful under some circumstances.

## Schema registry and Java interop

Much of the Java interop is complicated by the inability to access to
the schema registry client. I see it as additional complexity brought
by the way the library `io.confluent/kafka-avro-serializer` has been
designed. One may choose to break object-oriented privacy and access
this private field anyway, but I am of the opinion that it would make
the code brittle, and prone to break on dependency upgrade. As a
result, we rely on a lesser-used constructor for the inner serialiser
and deserializer and pass the schema registry client as an
argument. That way we stay good citizens.

## Constructors

This library has been designed to be used from Clojure code. As a
result Clojure records are used to extand `Serializer` and
`Deserializer`, which makes it not possible to rely on the default,
empty public object constructors for the serialiser and the
deserialiser. You always need to explicitly pass the schema registry
client, or this library won't work. Convenience constructor functions
are available, but you can also exert more complete control on these
records and use their idiomatic Clojure constructors, for example
`->ClojureDeserializer`.

## Schema resolution

As this library intends to provides idiomatic Clojure data structures
out of Avro records, it uses Clojure object meta data to store the
original schema used on read, as well as the subject name and registry
schema id. These data are used as hints on serialisation and encoding
to ease schema resolution. As such, the user retains full control on
the schema resolution, either by explicit declaration, or relying on
the schema registry. As the same client is used in the Clojure ->
GenericRecord and GenericRecord -> bytes serializers, by design
mismatches are reduced to the minimum.

Whereas the schema resolution should cover the vast majority of use
cases, in some edge cases it might not be possible to properly elicit
the expected schema in a union. For instance it is not possible to
distinguish between an Avro map `{:field "value"}` and an Avro record
`{:field "value"}`. In such rare circumstances, the abovementionned
schema hints will prove useful.

## Open, not closed maps

On encoding the fields of an Avro record are iterated upon and then
read values are read from the Clojure map to be encoded. This means
that any map with the required keys will be successfully encoded into
an Avro record. If you think that you need some safeguard, open an
issue.

## Generic and specific records

No need has be felt for supporting specific records as this library
merely exposes maps. If you need to support such records, please open
an issue describing a use case.

## Coder compilation

Similarly to the transformers of the excellent library
https://github.com/metosin/malli, slava uses coders which can be
encoders or decoders. On the first time to see a schema, a schema
coder is compiled. If the schema represents a type which contains
nested values, then the nested coders will be compiled. This is a
one-off, and further coding of values from/to this schema will use the
same memoized coder.

Let's look at an example. Here is a example config:

``` clojure
(def custom
  (assoc default
    :decoder/avro-int nil
    :encoder/avro-int nil

    :decoder/avro-boolean (fn [{:keys [mess-with-boolean?] :as _config} ^Schema$BooleanSchema _reader-schema]
                            (when mess-with-boolean?
                              (constantly not)))
    :encoder/avro-boolean (fn [{:keys [mess-with-boolean?] :as _config} ^Schema$BooleanSchema _reader-schema]
                            (when mess-with-boolean?
                              (fn [data]
                                (if (< 1/2 (rand))
                                  (not data)
                                  data))))

    :decoder/avro-enum (fn enum-decoder-compiler [_config ^Schema$EnumSchema _reader-schema]
                         (fn enum-decoder [^GenericData$EnumSymbol data]
                           (csk/->kebab-case-keyword (.toString data))))
    :encoder/avro-enum (fn enum-encoder-compiler [_config ^Schema$EnumSchema writer-schema]
                         (fn enum-encoder [data]
                           (GenericData$EnumSymbol.
                             writer-schema
                             (csk/->SCREAMING_SNAKE_CASE_STRING data))))))
```

Here is an example schema:

``` clojure
(def EnumField
  (-> (SchemaBuilder/builder) (.enumeration "enum") (.symbols (into-array String ["A" "B" "C"]))))

(def MyRecord
  (-> (SchemaBuilder/builder)
      ^SchemaBuilder$NamespacedBuilder (.record "RecordSchema")
      ^SchemaBuilder$FieldAssembler .fields
      (.name "intField") .type .intType .noDefault
      (.name "booleanField") .type .booleanType .noDefault
      (.name "enumField") (.type EnumField) .noDefault
      ^GenericData$Record .endRecord))
```

When first decoding a record, a decoder will be compiled first, then
used on this call and any subsequent calls. We first look at the first
field, which contains an integer: the value for `:decoder/avro-int` is
retrieved from the config but it is nil, so nothing will be done and
the value will be passed as is from the `GenericData$Record` to an
idiomatic Clojure map. Then we look up into the config for the value
of `:decoder/avro-boolean`. It is a compiler function. If it returns
`nil` nothing will be done at decode time and the value will be passed
as is. However if a coder function is returned, then it will be
applied at decode time on each value. The same mecanism is applied for
`:decoder/avro-enum`. In such case an Avro enum will be decoded as
Clojure keyword.

Once compiled, a coder is very close to hand-written code. If our
config contains a true-like value for `mess-with-boolean?`, then our
coders will be equivalent to:

``` clojure
(defn ^Map my-record-decoder
  [^GenericData$Record record]
  {"intField" (.get record "intField")
   "booleanField" (not (.get record "booleanField"))
   "enumField" (csk/->kebab-case-keyword (.toString (.get record "enumField")))})

(defn ^GenericData$Record my-record-encoder
  [^Map m]
  (.build (doto (GenericRecordBuilder. MyRecord)
            (.set "intField" (get m "intField"))
            (.set "booleanField" (let [v (get m "booleanField")]
                                   (if (< 1/2 (rand)) (not v) v)))
            (.set "enumField" (GenericData$EnumSymbol. EnumField (csk/->SCREAMING_SNAKE_CASE_STRING (get m "enumField")))))))
```

The coders are memoized at runtime. The assumption is that we will
only deal with a low, finite number of schemas. Most Kafka topics only
have one single schema. If you would prefer to use a LRU cache instead
(the same strategy as the standard cached schema registry client) feel
free to open an issue.
