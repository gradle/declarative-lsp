package org.gradle.declarative.lsp.tooling

import org.gradle.declarative.lsp.action.GetResolvedDomAction
import org.gradle.declarative.lsp.model.ResolvedDomPrerequisites
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.io.File

class ConnectionHandler(val projectRoot: File) {
    fun getDeclarativeModel(): ResolvedDomPrerequisites {
        val action = GetResolvedDomAction()

        var connection: ProjectConnection? = null
        try {
            connection = GradleConnector
                .newConnector()
                .forProjectDirectory(projectRoot)
                .connect()
            val model = connection
                .action(action)
                .run()
            return model
        } finally {
            connection?.close()
        }
    }
}