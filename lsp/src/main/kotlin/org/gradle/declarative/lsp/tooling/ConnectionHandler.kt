package org.gradle.declarative.lsp.tooling

import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.io.File

class ConnectionHandler(val projectRoot: File) {
    fun getDeclarativeModel(): DeclarativeSchemaModel {
        var connection: ProjectConnection? = null
        try {
            connection = GradleConnector
                .newConnector()
                .forProjectDirectory(projectRoot)
                .connect()
            return connection
                .model(DeclarativeSchemaModel::class.java)
                .get()
        } finally {
            connection?.close()
        }
    }
}