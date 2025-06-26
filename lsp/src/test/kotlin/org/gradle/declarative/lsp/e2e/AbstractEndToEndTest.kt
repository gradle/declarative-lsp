/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.declarative.lsp.e2e

import org.gradle.declarative.lsp.startDeclarativeLanguageServer
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Future

abstract class AbstractEndToEndTest {

    // Streams from the test's/client's perspective
    /** Use this stream in your test to READ what the server sends. */
    protected lateinit var serverToClientStream: InputStream
    /** Use this stream in your test to WRITE to the server. */
    protected lateinit var clientToServerStream: OutputStream

    private var serverFuture: Future<Void>? = null

    protected fun testLspServerStart() {
        // This is the pipe for communication FROM the client TO the server
        val serverInputStream = PipedInputStream()
        clientToServerStream = PipedOutputStream(serverInputStream) // Test writes here, server reads from serverInputStream

        // This is the pipe for communication FROM the server TO the client
        serverToClientStream = PipedInputStream()
        val serverOutputStream = PipedOutputStream(serverToClientStream as PipedInputStream) // Server writes here, test reads from serverToClientStream

        // Start the server with its ends of the pipes
        serverFuture = startDeclarativeLanguageServer(serverInputStream, serverOutputStream)
    }

    protected fun testLspServerStop() {
        // Close the stream that the test writes to. This signals "end of input" to the server.
        clientToServerStream.close()
        serverToClientStream.close()

        // Wait for the server to shut down gracefully
        serverFuture?.get()
        serverFuture = null
    }

    protected fun testLspServerInitialize() {
        if (serverFuture == null) {
            throw IllegalStateException("Server must be started before calling lspInitialize()")
        } else {
            // Write initialization parameters to the server
            val initParams = """
                {
                    "jsonrpc": "2.0",
                    "id": 1,
                    "method": "initialize",
                    "params": {
                        "processId": 12345,
                        "rootUri": "file:///path/to/project",
                        "capabilities": {
                            "textDocument": {
                                "declarative": {
                                    "mutations": true
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            clientToServerStream.write(initParams.toByteArray())
            clientToServerStream.flush()
        }
    }
}