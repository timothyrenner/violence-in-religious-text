# A Quantitative Look at Violence in Religious Texts

This is the code used for the analysis done for my [blog post](http://timothyrenner.github.io/datascience/2015/12/02/violence-in-religious-text.html) on violence in religious text.

### Requirements

You'll need [Leiningen](http://leiningen.org/) to build the project and run the notebook, and [wget](https://www.gnu.org/software/wget/) to obtain the data.

This project also uses the [gg4clj](https://github.com/JonyEpsilon/gg4clj) library, which requires an installation of [R](https://www.r-project.org/), along with the [ggplot2](http://ggplot2.org/) package.

### Running the Notebook

As step one, you need a landing point for the images.

```shell
mkdir images
```

Next get the data.
There's a shell script in `resources/` that handles this for you.
From project root:

```shell
cd resources/
chmod u+x get_data.sh
./get_data.sh
```

It takes a few minutes.
Once it's done you can launch the notebook with Leiningen.

```shell
lein gorilla
```

From the GorillaREPL you can open the notebook file and run it.

The `main` function converts the notebook to markdown.
Run it with

```shell
lein run
```

It'll overwrite the file `violence-in-religious-text.md` in the root directory of the project.

There are also unit tests for the markdown conversion.

```shell
lein test
```

will run them.