package org.gradle.declarative.lsp.tooling

import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.tooling.GradleConnector
import java.io.File

class ConnectionHandler(val projectRoot: File) {
    fun getDeclarativeModel(): DeclarativeSchemaModel {
        val connection = GradleConnector
            .newConnector()
            .forProjectDirectory(projectRoot)
            .connect()
        val model = connection
            .model(DeclarativeSchemaModel::class.java)
            .get()
        connection.close()
        return model
    }
}