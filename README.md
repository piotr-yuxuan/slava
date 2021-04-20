Kafka Avro Serde for Clojure.

![слава советскому народу](dev-resources/слава-советскому-народу.jpg)

# Installation

[![](https://img.shields.io/clojars/v/piotr-yuxuan/slava.svg)](https://clojars.org/piotr-yuxuan/slava)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/slava)](https://cljdoc.org/d/piotr-yuxuan/slava/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/slava)](https://github.com/piotr-yuxuan/slava/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/slava)](https://github.com/piotr-yuxuan/slava/issues)

Contrarily to the deplorable state of this README.md, as well as that
of the documentation, I would say that the underlying ideas of this
project have reached some stability.

FIXME FIXME FIXME write a better README.

- Lazily convert
- Expose a map interface around the actual (mutable)
  GenericData$Record.
- Some love should be given to the interface to create records the
  same (efficient) way.
- Rewrite tree traversal with `clojure.walk/walk` for more clarity;
  inner and outer are powerful enough concepts.

# Known bugs

See [GitHub issues](https://github.com/piotr-yuxuan/slava/issues).

# References

For a more complete Clojure API around Kafka, see
[FundingCircle/jackdaw](https://github.com/FundingCircle/jackdaw).
