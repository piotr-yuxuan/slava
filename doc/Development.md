# Development

Invoking the function provided by this library from the command-line:

FIXME add cljdoc

Also, see
[./test/piotr-yuxuan/slava_test.clj](./test/piotr_yuxuan/slava_test.clj).

This project was created with:

``` zsh
clojure -X:project/new :name piotr-yuxuan/slava
```

Compile java classes from within a repl with:
``` clojure
(compile 'piotr-yuxuan.slava.deserializer)
(compile 'piotr-yuxuan.slava.serializer)
(compile 'piotr-yuxuan.slava.serde)
```

Alternatively, you can achieve the same from the CLI with:
``` clojure
clj -M -e "(compile 'piotr-yuxuan.slava.deserializer)"
clj -M -e "(compile 'piotr-yuxuan.slava.serializer)"
clj -M -e "(compile 'piotr-yuxuan.slava.serde)"
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
clojure -X:deploy
```

Deploy it to GitHub packages with [this
guide](https://docs.github.com/en/packages/guides/configuring-apache-maven-for-use-with-github-packages)
and:

``` zsh
mvn deploy -DaltDeploymentRepository=github::default::https://maven.pkg.github.com/piotr-yuxuan/slava
```

# Notes on pom.xml

If you don't plan to install/deploy the library, you can remove the
`pom.xml` file but you will also need to remove `:sync-pom true` from
the `deps.edn` file (in the `:exec-args` for `depstar`).

As of now it is suggested to run `lein pom` to update the pom before
installing a jar or deploying a new version, so that the file `pom.xml`
is correctly updated by Leiningen (especially the scm revision), which I
don't know yet how to do with `deps.edn` tooling.
