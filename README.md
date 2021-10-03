# Dummy project generator

This is a very quick-and-dirty [Babashka](https://github.com/babashka/babashka) script to generate dummy [Leiningen](https://leiningen.org/) projects with lots of content.

# How to use

1. Create a target directory for the project
1. `bb.edn` contains a task called `main` which you can invoke
 - `-o` takes the target directory name
 - `-c` takes the number of source files to create
 - This creates files such as `src/a.clj`..`src/z.clj`, `src/a/a.clj`..`src/a/z.clj`..
1. Open the project in your favourite editor

## Example

```shell
mkdir /tmp/project
bb run main -o /tmp/project -c 1000
emacs /tmp/project/src/a.clj
```
