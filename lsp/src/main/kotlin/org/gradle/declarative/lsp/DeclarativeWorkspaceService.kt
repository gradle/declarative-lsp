/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.declarative.lsp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.WorkspaceService
import org.gradle.declarative.lsp.build.model.DeclarativeResourcesModel
import org.gradle.declarative.lsp.service.MutationRegistry
import org.gradle.declarative.lsp.service.VersionedDocumentStore
import org.gradle.internal.declarativedsl.dom.mutation.ModelMutationStepResult
import org.gradle.internal.declarativedsl.dom.mutation.MutationArgumentContainer
import org.gradle.internal.declarativedsl.dom.mutation.MutationAsTextRunner
import org.gradle.internal.declarativedsl.dom.mutation.MutationDefinition
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameter
import org.gradle.internal.declarativedsl.dom.mutation.MutationParameterKind
import org.gradle.internal.declarativedsl.dom.mutation.TextMutationApplicationTarget
import org.gradle.internal.declarativedsl.evaluator.main.SimpleAnalysisEvaluator
import org.gradle.internal.declarativedsl.evaluator.runner.stepResultOrPartialResult
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.CompletableFuture

class DeclarativeWorkspaceService : WorkspaceService {
    private lateinit var client: LanguageClient
    private lateinit var documentStore: VersionedDocumentStore
    private lateinit var mutationRegistry: MutationRegistry
    private lateinit var declarativeResources: DeclarativeResourcesModel
    private lateinit var schemaAnalysisEvaluator: SimpleAnalysisEvaluator

    fun initialize(
        client: LanguageClient,
        documentStore: VersionedDocumentStore,
        mutationRegistry: MutationRegistry,
        declarativeResources: DeclarativeResourcesModel
    ) {
        this.client = client
        this.documentStore = documentStore
        this.mutationRegistry = mutationRegistry
        this.declarativeResources = declarativeResources
        this.schemaAnalysisEvaluator = SimpleAnalysisEvaluator.withSchema(
            declarativeResources.settingsInterpretationSequence,
            declarativeResources.projectInterpretationSequence
        )
    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {
        LOGGER.info("Changed configuration: ${params?.settings}")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
        LOGGER.info("Changed watched files: ${params?.changes}")
    }

    override fun executeCommand(params: ExecuteCommandParams?): CompletableFuture<Any> {
        params?.let {
            when (it.command) {
                "gradle-dcl.executeMutation" -> {
                    val arguments = it.arguments[0] as JsonObject
                    executeMutationCommand(arguments)
                }
            }
        }
        return CompletableFuture.completedFuture(null)
    }

    private fun executeMutationCommand(args: JsonObject) {
        val documentUri = URI(args["documentUri"].asString)
        val fileName = documentUri.path.substringAfterLast('/')

        documentStore[documentUri]?.let { (document, dom) ->
            val mutationDefinition = mutationRegistry.getMutationByName(args["mutationId"].asString)!!
            val mutationParamsContainer = JsonBackedArgumentContainer(
                mutationDefinition,
                args["mutationParameters"].asJsonArray
            )

            val evaluationSchema = schemaAnalysisEvaluator.evaluate(fileName, document)
                .stepResults
                .values
                .single()
                .stepResultOrPartialResult
                .evaluationSchema
            val target = TextMutationApplicationTarget(dom.result, evaluationSchema)
            val mutationRunResult = MutationAsTextRunner()
                .runMutation(mutationDefinition, mutationParamsContainer, target)
                .stepResults
                .last()
            when (mutationRunResult) {
                is ModelMutationStepResult.ModelMutationFailed -> {
                    client.showMessage(
                        MessageParams(
                            MessageType.Error,
                            "Mutation failed: ${mutationDefinition.name}"
                        )
                    )
                }

                is ModelMutationStepResult.ModelMutationStepApplied -> {
                    client.applyEdit(
                        ApplyWorkspaceEditParams(
                            WorkspaceEdit(
                                mapOf(
                                    documentUri.toString() to listOf(
                                        TextEdit(
                                            Range(
                                                Position(0, 0),
                                                Position(Int.MAX_VALUE, Int.MAX_VALUE)
                                            ),
                                            mutationRunResult.newDocumentText
                                        )
                                    )
                                )
                            )
                        )
                    )
                }
            }

        }

    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(DeclarativeWorkspaceService::class.java)
    }
}

class JsonBackedArgumentContainer(
    private val definition: MutationDefinition,
    private val args: JsonArray
) : MutationArgumentContainer {

    override val allArguments: Map<MutationParameter<*>, Any>
        get() = definition.parameters.associateWith { definitionParam ->
            args.first { resolvedParam ->
                resolvedParam.asJsonObject["name"].asString == definitionParam.name
            }.let {
                when (definitionParam.kind) {
                    MutationParameterKind.StringParameter -> it.asJsonObject["value"].asString
                    MutationParameterKind.IntParameter -> it.asJsonObject["value"].asInt
                    MutationParameterKind.BooleanParameter -> it.asJsonObject["value"].asBoolean
                }
            }
        }

    override fun <T : Any> get(parameter: MutationParameter<T>): T {
        @Suppress("UNCHECKED_CAST")
        return allArguments[parameter] as T
    }

}