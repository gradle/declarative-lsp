# Declarative Gradle - Language Server

This project implements an [LSP](https://microsoft.github.io/language-server-protocol/) server for [Declarative Gradle](https://declarative.gradle.org/).

## How to use?

The LSP project can be built and packaged by simply running `./gradlew shadowJar`.

The runnable JAR can then be found at [./lsp/build/libs/lsp-all.jar](./lsp/build/libs/lsp-all.jar).

## Integrations

This server can be used in the integrations below:
 - VSCode: https://github.com/gradle/declarative-vscode-extension
 - Buildship (Eclipse): https://github.com/hegyibalint/buildship

Please refer to the respective projects for more information on how to use the LSP server.
