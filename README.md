Kafka Avro Serde for Clojure.

![слава советскому народу](dev-resources/слава-советскому-народу.jpg)

# Installation

[![](https://img.shields.io/clojars/v/piotr-yuxuan/slava.svg)](https://clojars.org/piotr-yuxuan/slava)
[![cljdoc badge](https://cljdoc.org/badge/piotr-yuxuan/slava)](https://cljdoc.org/d/piotr-yuxuan/slava/CURRENT)
[![GitHub license](https://img.shields.io/github/license/piotr-yuxuan/slava)](https://github.com/piotr-yuxuan/slava/blob/main/LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/piotr-yuxuan/slava)](https://github.com/piotr-yuxuan/slava/issues)

# TL;DR example

FIXME add cljdoc

# Description

FIXME add cljdoc

# References

FIXME add cljdoc

# Usage

Invoking the function provided by this library from the command-line:

FIXME add cljdoc

Also, see
[./test/piotr-yuxuan/slava_test.clj](./test/piotr_yuxuan/slava_test.clj).

This project was created with:

``` zsh
clojure -X:project/new :name piotr-yuxuan/slava
```

Run the project's tests:

``` zsh
clojure -M:test:runner
```

Lint your code with:

``` zsh
clojure -M:lint/idiom
clojure -M:lint/kondo
```

Visualise links between project vars with:

``` zsh
mkdir graphs
clojure -M:graph/vars-svg
```

Build a deployable jar of this library:

``` zsh
lein pom
clojure -X:jar
```

This will update the generated `pom.xml` file to keep the dependencies
synchronized with your `deps.edn` file.

Install it locally:

``` zsh
lein pom
clojure -X:install
```

Create a new version once a jar has been created:
- Make sure all reasonable documentation is here
- Update resources/slava.version
- `lein pom`
- Create a commit with title `Version x.y.z`
- Create a git tag

Deploy it to Clojars -- needs `CLOJARS_USERNAME` and `CLOJARS_PASSWORD`
environment variables (requires the `pom.xml` file):

``` zsh
lein pom
clojure -X:deploy
```

Deploy it to GitHub packages with [this
guide](https://docs.github.com/en/packages/guides/configuring-apache-maven-for-use-with-github-packages)
and:

``` zsh
mvn deploy -DaltDeploymentRepository=github::default::https://maven.pkg.github.com/piotr-yuxuan/slava
```

# Notes on `pom.xml`

If you don't plan to install/deploy the library, you can remove the
`pom.xml` file but you will also need to remove `:sync-pom true` from
the `deps.edn` file (in the `:exec-args` for `depstar`).

As of now it is suggested to run `lein pom` to update the pom before
installing a jar or deploying a new version, so that the file `pom.xml`
is correctly updated by Leiningen (especially the scm revision), which I
don't know yet how to do with `deps.edn` tooling.
