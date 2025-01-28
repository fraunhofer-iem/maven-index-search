package de.fraunhofer.iem

import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

@Serializable
data class LuceneArtifact(val u: String, val i: String, val m: String, val n: String, val d: String, val score: Float)

@Serializable
data class Input(@SerialName("_id") val name: String)

const val QUERY_FIELD = "u"
const val QUERY_PATTERN = "/.*\\|%s\\|.*/"
const val PROCESS_LOG_INTERVAL = 100

fun main(args: Array<String>) = runBlocking {
    if (args.size != 3) {
        System.err.println("Usage: <indexPath> <inputFile> <outFile>")
        exitProcess(1)
    }

    val (indexPathRaw, inputFileRaw, outFileRaw) = args
    println("Starting analysis for index path $indexPathRaw and input file $inputFileRaw and out file $outFileRaw")

    val outFile = Paths.get(outFileRaw).toFile()
    val inputFile = Paths.get(inputFileRaw).toFile()

    val inputRaw = inputFile.readText()
    val input = Json.decodeFromString<Array<Input>>(inputRaw)

    val analyzer = StandardAnalyzer()
    val indexPath: Path = Path.of(indexPathRaw)
    val dir = FSDirectory.open(indexPath)

    val results = mutableListOf<LuceneArtifact>()

    DirectoryReader.open(dir).use { ireader ->
        val isearcher = IndexSearcher(ireader)

        // Use a dispatcher with limited parallelism
        val dispatcher = Dispatchers.Default.limitedParallelism(Runtime.getRuntime().availableProcessors())

        withContext(dispatcher) {
            input.mapIndexed { idx, i ->
                async {
                    val parser = QueryParser(QUERY_FIELD, analyzer)
                    val query = parser.parse(String.format(QUERY_PATTERN, i.name))
                    println("Query string: ${query.toString()}")

                    val hits = isearcher.search(query, 1).scoreDocs
                    val res = hits.firstOrNull()?.let { hit ->
                        val doc = isearcher.storedFields().document(hit.doc)
                        LuceneArtifact(
                            u = doc.get("u") ?: "",
                            i = doc.get("i") ?: "",
                            m = doc.get("m") ?: "",
                            n = doc.get("n") ?: "",
                            d = doc.get("d") ?: "",
                            score = hit.score
                        )
                    }

                    if (idx % PROCESS_LOG_INTERVAL == 0) {
                        println("------------------------ PROCESSED $idx documents ------------------")
                    }
                    res
                }
            }.awaitAll().filterNotNull().let { results.addAll(it) }
        }
    }

    println("Found ${results.size} results for ${input.size} artifacts")

    val out = Json.encodeToString(results)
    outFile.writeText(out)
    println("Results saved")
}