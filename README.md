# 🇷🇺 [slava](https://clojars.org/slava)

- [![Clojars Project](https://img.shields.io/clojars/v/com.slava.svg)](https://clojars.org/com.slava)
- [![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)

# What does it do?

It provides facilities for converting Avro data between several
representations:

- Instance of `org.apache.avro.specific.SpecificRecord`;
- Instance of `org.apache.avro.specific.GenericRecord`;
- Clojure map with `:avro.schema/name` key;

To fulfill this goal it comes with wrappers around
 `io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde` and
 `io.confluent.kafka.streams.serdes.avro.GenericAvroSerde`
 schema-registry compatible Avro implementations of
 `org.apache.kafka.common.serialization.Serde<T>`. It also provides
 ancillary functions to use in Clojure code.

# What can I use it for?

General avro data handling. Allows simpler Kafka Streams Clojure code.

![слава советскому народу](resources/слава-советскому-народу.jpg)

This poster reads: « Glory to Soviet People, creator of a powerful
aviation ». Created in 1954 year it is a perfect symbol of the
powerful soviet militarism stream of that time.

Avro was a British aircraft manufacturer, but as the propaganda goes,
_Soviet aircrafts are the best aircrafts_.

# How to use it

# Further parameters

# Troubleshoot

# Related projects

```
$ lein clean && mvn clean && lein compile && mvn compile
```
