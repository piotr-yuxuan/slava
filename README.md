# 🇷🇺 [slava](https://clojars.org/slava)

[![Clojars Project](https://img.shields.io/clojars/v/org.piotr-yuxuan.svg)](https://clojars.org/com.slava)

[![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)

If you are here because you precisely know what you're looking for,
just read the next section and see the following example code. If
you've got no idea how you ended up on this page, you might be
interested in the « explain me like I'm five » section at the bottom
of this page.

# TL;DR What does it aim at?

Present Kafka messages as primitive map-like data structures, which
are more friendly than Avro specific / generic record objects. Achieve
it by providing an opinionated Avro Kafka Serde heavily relying on
upstream Avro and Confluent code.

- Very easy customisation: create your own tailored Serde
- Implement a custom `org.apache.kafka.common.serialization.Serde`
- Work on the JVM, useful for Clojure or any other language
- Rely as much as possible on upstream Avro and Confluent codes
- Well-integrated with Confluent schema registry

![слава советскому народу](resources/слава-советскому-народу.jpg)

This poster reads: « Glory to Soviet People, creator of a powerful
aviation ». Created in 1954 year it is a perfect symbol of the
powerful soviet militarism stream of that time. Avro was a British
aircraft manufacturer, but as the propaganda goes, _Soviet aircrafts
are the best (class-less) aircrafts_.

# Test example with Kafka Streams

See full [test
file](https://github.com/piotr-yuxuan/slava/blob/master/src/test/clojure/com/slava/readme_test.clj)
for details such as import and explicit vars not defined in this
snippet. The full test actually passes 🤗

The following snippet how to serialise from and to Avro a new custom
data type: IP address. It also demonstrates how to seamlessly
manipulate Clojure datatypes within Kafka Streams and how to integrate
custom Serde in your test code.

``` clojure
(ns org.piotr-yuxuan.readme-test
  (:require [clojure.test :refer :all]
            [org.piotr-yuxuan.conversion-native :as serde]
            [clojure.network.ip :as ip]))

(declare properties schema-registry schema-registry-client ip-array-input-topic ip-v4-output-topic)

(def ^Schema ip-v4-schema (-> (SchemaBuilder/builder (str *ns*)) (.fixed "IPv4") (.size 4)))
(defmethod serde/from-avro-schema-name (.getFullName ip-v4-schema) [this schema ^GenericData$Fixed data] …)
(defmethod serde/to-avro-schema-name (.getFullName ip-v4-schema) [_ schema ^IPAddress ip-adress] …)

(def ^Schema ip-v6-schema (-> (SchemaBuilder/builder (str *ns*)) (.fixed "IPv6") (.size 16)))
(defmethod serde/from-avro-schema-name (.getFullName ip-v6-schema) [this schema ^GenericData$Fixed data] …)
(defmethod serde/to-avro-schema-name (.getFullName ip-v6-schema) [_ schema ^IPAddress ip-adress] …)

(def ^Schema ip-array-input-schema
  (-> (SchemaBuilder/builder (str *ns*))
      (.record "Input")
      .fields
      (.name "array") .type .array .items .unionOf (.type ip-v4-schema) .and (.type ip-v6-schema) .endUnion .noDefault
      .endRecord))

(def ^Schema ip-v4-output-schema
  (-> (SchemaBuilder/builder (str *ns*))
      (.record "Output")
      .fields
      (.name "address") (.type ip-v4-schema) .noDefault
      .endRecord))

(def topology
  (let [builder (StreamsBuilder.)]
    (-> (.stream builder ip-array-input-topic)
        (.flatMapValues (reify ValueMapper
                          (apply [_ record]
                            (map #(do {::address %})
                                 (record :org.piotr-yuxuan.readme-test.Input/array)))))
        (.filter (reify Predicate
                   (test [_ uuid record]
                     (->> ^IPAddress (record ::address)
                          (.version)
                          (= 4)))))
        (.mapValues (reify ValueMapper
                      (apply [_ record]
                        (clojure.set/rename-keys record
                                                 {::address :org.piotr-yuxuan.readme-test.Output/address}))))
        (.to ip-v4-output-topic))
    (.build builder)))

(deftest kafka-streams-integration-test
  (with-open [^TopologyTestDriver test-driver (TopologyTestDriver. topology properties)]
    (doseq [record (list {:org.piotr-yuxuan.readme-test.Input/array [(ip/make-ip-address "192.168.1.1")
                                                              (ip/make-ip-address "1::1")]}
                         {:org.piotr-yuxuan.readme-test.Input/array [(ip/make-ip-address "1::2")
                                                              (ip/make-ip-address "1::3")]}
                         {:org.piotr-yuxuan.readme-test.Input/array [(ip/make-ip-address "192.168.1.2")
                                                              (ip/make-ip-address "192.168.1.3")]})]
      (.pipeInput test-driver [(.create consumer-record-factory ip-array-input-topic (UUID/randomUUID) record)]))
 ✅ (is (= (for [^ProducerRecord record (take-while some? (repeatedly #(.readOutput test-driver ip-v4-output-topic)))]
             (.deserialize (.deserializer value-avro-serde) ip-v4-output-topic (.value ^ProducerRecord record)))
           (list {:org.piotr-yuxuan.readme-test.Output/address (ip/make-ip-address "192.168.1.1")}
                 {:org.piotr-yuxuan.readme-test.Output/address (ip/make-ip-address "192.168.1.2")}
                 {:org.piotr-yuxuan.readme-test.Output/address (ip/make-ip-address "192.168.1.3")})))))


```

Further documentation in available in [![cljdoc badge](https://cljdoc.org/badge/com.slava/com.slava)](https://cljdoc.org/d/com.slava/com.slava)

# Related projects

Here are other projects. They are quite awesome. Perhaps they would be
more useful to you than Slava.

- https://github.com/damballa/abracad
- https://github.com/komolovf/kfk-avro-bridge
- https://github.com/ovotech/kafka-avro-confluent
- https://github.com/deercreeklabs/lancaster
- https://github.com/FundingCircle/jackdaw

# Explain me like I'm five

There is a super cool platform called Kafka which is quite handy when
it comes to record a stream of events, store it in a robust manner,
and allow different readers to to replay it as they wish à la time
travel.

As the 'big' in _big data_ actually means quite big, Kafka has to
store big data in a smart, compactful way. Avro is a way to represent
a lot of data while using only very few storage space. It also
enforces some constraints so that you know for sure they will always
respect some precise shape. For instance when you store the details of
a person, it must always have a surname and a first name, and the age
must be a number – but sometimes can be null if unknown.

To put it in a nutshell, you need the equivalent of both a vacuum pump
and bicycle pump:

- A vacuum pump reduces the data size so you can easily move and store
  them (technical word for it: serialisation);
- A bicycle pump inflates data back to their original, useful form
  (technical word for it: deserialisation). You can seamlessly uses
  them in your favourite programming language.

Of course, Kafka already provides such two pumps for a very well
established programming language: Java. Programming languages, just
like human languages, have different strategies to express ideas. For
a various set of reasons, I prefer to express programs in Clojure
which I do find more terse, more precise, and which allows to think
less than Java to express the same ideas as more straightforward.

However, after some research, I didn't find such two-pump tool for
Clojure the same way it exists for Java. Of course some similar tools
exist but for quite petty details I find none of them completely
satisfaying. _Slava_ is yet another attempt to create such tool. In
writing it, I've tried my best to rely on other's code: the less code
I write, the less bug I create. Furthermore I've been willing to stay
focused – do only one thing, but do it well – and to be a good citizen
– use standard tools, make this code easily reusable and adaptable to
yours.

The technical word for two-pump system is a `Serde`, wich is portmanteau
for serialisation and deserialisation.

# Design strategy

As an Avro `Serde` is available for Java, I felt I could chain to with
another `Serde` – composition over inheritance. However Java type
system defines a `Serde` as from something to byte array, so it felt
short. I didn't want to reinvent the wheel by creating my own
serialiser and deserialiser from scratch so I chose to extend the Java
`Serde` provided by Confluent. I just wrap around it a Clojure
<-> Java conversion system. `Serde` being an interface, this custom
implementation should play nice with other tools.

Schema resolution relies on schema registry but an Avro map can
contain a key to point to specific schema. When relying on schema
registry only latest registered schema is considered so no unintended
modification should happen.

This conversion wrapper should represent Avro message as native
immutable Clojure datastructures so you don't even notice it comes
from Avro. It should support Avro logical types. Custom conversion
mechanism based on schema name is also available. Dispatch on logical
type name and schema name makes protocol-based dispatch less relevant
so the main library namespace hence exposes six multimethods on three
levels:

- The highest priority goes to `{from,to}-avro-schema-name` so that
  user's override takes precedence;
- Then comes `{from,to}-avro-logical-type` if stateful Avro
  `GenericData` conversion mechanism is not used
- Finally `{from,to}-avro-schema-type` relies on schema type

Dispatch on Avro schema name allows Avro messages to be directly
mapped onto arbitrary types such as IP addresses Let's call this
arbitrary types _proprietary business types_.

*Note about protocol v. multimethod*

The lowest level from above fits very nicely with protocol as schema
`.getType` function is strongly linked to the actual schema class. The
middle level about logical types doesn't hinder either protocol-based
dispatch, provided the logical type class is used instead of logical
type names. However the highest level sounds more difficult as no
schema class can be used (no SpecificRecord). What about depending on
the mapped business types, for example `IpAddress`?

This implies one business type is mapped onto more than one Avro
schema (example continued: either `IPv4/fixed(4)` or `IPv6/fixed(16)`)
which depends on the inner conversion logic. I find it more
obscure. Moreover this sounds great from Clojure to Avro, but which
efficient, stateless way can we use from Avro to Clojure? We would
need a register, just like jackdaw does. I'm open to this option, but
I still need to be convinced.

*Note about [methodical](https://github.com/camsaul/methodical)*

I'm a bit reluctant to introduce such a powerful dependency into a
library code. Sure this is a great library, but any code using it
becomes less standard, less easy to understand, much more funny to
write and read – but is it made any simple?














