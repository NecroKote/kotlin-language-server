package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.javacs.kt.database.DatabaseService
import org.javacs.kt.util.AsyncExecutor
import org.javacs.kt.util.parseURI
import org.javacs.kt.resolve.resolveMain
import org.javacs.kt.position.offset
import org.javacs.kt.overridemembers.listOverridableMembers
import java.util.concurrent.CompletableFuture
import java.nio.file.Paths

class KotlinProtocolExtensionService(
    private val uriContentProvider: URIContentProvider,
    private val cp: CompilerClassPath,
    private val sp: SourcePath,
    private val databaseService: DatabaseService,
) : KotlinProtocolExtensions, LanguageClientAware {
    private val async = AsyncExecutor()

    private var languageClient: LanguageClient? = null

    override fun connect(client: LanguageClient): Unit {
        languageClient = client
    }

    override fun jarClassContents(textDocument: TextDocumentIdentifier): CompletableFuture<String?> = async.compute {
        uriContentProvider.contentOf(parseURI(textDocument.uri))
    }

    override fun buildOutputLocation(): CompletableFuture<String?> = async.compute {
        cp.outputDirectory.absolutePath
    }

    override fun mainClass(textDocument: TextDocumentIdentifier): CompletableFuture<Map<String, Any?>> = async.compute {
        val fileUri = parseURI(textDocument.uri)
        val filePath = Paths.get(fileUri)

        // we find the longest one in case both the root and submodule are included
        val workspacePath = cp.workspaceRoots.filter {
            filePath.startsWith(it)
        }.map {
            it.toString()
        }.maxByOrNull(String::length) ?: ""

        val compiledFile = sp.currentVersion(fileUri)

        resolveMain(compiledFile) + mapOf(
            "projectRoot" to workspacePath
        )
    }

    override fun overrideMember(position: TextDocumentPositionParams): CompletableFuture<List<CodeAction>> = async.compute {
        val fileUri = parseURI(position.textDocument.uri)
        val compiledFile = sp.currentVersion(fileUri)
        val cursorOffset = offset(compiledFile.content, position.position)

        listOverridableMembers(compiledFile, cursorOffset)
    }

    override fun cleanWorkspaceDb(): CompletableFuture<String?> = async.compute {
        databaseService.clear(true)
        languageClient?.showMessage(MessageParams(MessageType.Info, "Database cleared!"))
        ""
    }
}
