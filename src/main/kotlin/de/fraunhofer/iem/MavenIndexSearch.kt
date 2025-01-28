package de.fraunhofer.iem

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
data class LuceneArtifact(
    val u: String,
    val i: String,
    val m: String,
    val n: String,
    val d: String,
    val score: Float,
)

@Serializable
data class QueryResult(
    val artifact: LuceneArtifact,
    val numberOfFindings: Int,
)

@Serializable
data class Input(
    @SerialName("_id")
    val name: String
)

fun main(args: Array<String>) {

    if (args.size != 3) {
        exitProcess(1)
    }
    val indexPathRaw = args[0]
    val inputFileRaw = args[1]
    val outFileRaw = args[2]
    println("starting analysis for index path $indexPathRaw and input file $inputFileRaw and out file $outFileRaw")

    val outFile = Paths.get(outFileRaw).toFile()
    val inputFile = Paths.get(inputFileRaw).toFile()

    val inputRaw = inputFile.readText()
    val input = Json.decodeFromString<Array<Input>>(inputRaw)


    val analyzer = StandardAnalyzer()
    val indexPath: Path = Path.of(indexPathRaw)
    val dir = FSDirectory.open(indexPath)

    val ireader = DirectoryReader.open(dir)
    val isearcher = IndexSearcher(ireader)


    val parser = QueryParser("u", analyzer)

    val results = ArrayList<QueryResult>(input.size)

    var counter = 0

    for (i in input) {
        val queryString = "/.*\\|${i.name}\\|.*/"
        println("Query string: $queryString")
        val query = parser.parse(queryString)

        val hits = isearcher.search(query, 10).scoreDocs
        println("Results ${hits.size}")

        val storedFields = isearcher.storedFields()
        if (hits.isEmpty()) {
            continue
        }
        val hit = hits.first()

        val score = hit.score
        val doc = storedFields.document(hit.doc)
        var u = ""
        var i = ""
        var m = ""
        var n = ""
        var d = ""

        doc.fields.forEach { field ->
            when (field.name()) {
                "u" -> u = field.storedValue().stringValue
                "i" -> i = field.storedValue().stringValue
                "m" -> m = field.storedValue().stringValue
                "n" -> n = field.storedValue().stringValue
                "d" -> d = field.storedValue().stringValue
            }
        }
        val artifact = LuceneArtifact(
            u = u,
            i = i,
            m = m,
            n = n,
            d = d,
            score = score
        )
        val qr = QueryResult(
            artifact = artifact,
            numberOfFindings = hits.size
        )

        println(qr)
        results.add(qr)
        counter += 1
        if (counter % 100 == 0) {
            println("Processed $counter / ${input.size} files")
        }
    }
    ireader.close()
    dir.close()

    println("Found ${results.size} results for ${input.size} artifacts")

    val out = Json.encodeToString(results)
    outFile.writeText(out)
    println("results saved")
}
