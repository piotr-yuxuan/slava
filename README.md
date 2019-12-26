# 🇷🇺 [slava](https://clojars.org/slava)

[![Clojars Project](https://img.shields.io/clojars/v/com.slava.svg)](https://clojars.org/com.slava)

[![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)

![слава советскому народу](resources/слава-советскому-народу.jpg)

This poster reads: « Glory to Soviet People, creator of a powerful
aviation ». Created in 1954 year it is a perfect symbol of the
powerful soviet militarism stream of that time. Avro was a British
aircraft manufacturer, but as the propaganda goes, _Soviet aircrafts
are the best aircrafts_.

# What does it aim at?

Present Kafka messages as primitive map-like data structures, which
are more friendly than Avro specific / generic record objects. Achieve
it by providing a custom Avro Kafka Serde heavily relying on upstream
Avro and Confluent code.

- Implement a custom `org.apache.kafka.common.serialization.Serde`
- Works on the JVM, useful for Clojure or any other language
- Rely as much as possible on upstream Avro and Confluent codes
- Well-integrated with Confluent schema registry

# Test example with Kafka Streams

``` clojure
```

Further documentation in available in [![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)


# Related projects

Here are other projects. They are quite awesome. Perhaps they would be
more useful to you than slava.

- https://github.com/damballa/abracad
- https://github.com/komolovf/kfk-avro-bridge
- https://github.com/ovotech/kafka-avro-confluent
- https://github.com/deercreeklabs/lancaster
