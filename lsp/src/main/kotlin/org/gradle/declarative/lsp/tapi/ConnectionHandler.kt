package org.gradle.declarative.lsp.tapi

import org.gradle.declarative.lsp.build.action.GetDeclarativeResourcesModel
import org.gradle.declarative.lsp.build.model.ResolvedDeclarativeResourcesModel
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.io.File

class ConnectionHandler(val projectRoot: File) {
    fun getDomPrequisites(): ResolvedDeclarativeResourcesModel {
        var connection: ProjectConnection? = null
        try {
            connection = GradleConnector
                .newConnector()
                .forProjectDirectory(projectRoot)
                .connect()
            return connection.action(GetDeclarativeResourcesModel()).run()
        } finally {
            connection?.close()
        }
    }
}
