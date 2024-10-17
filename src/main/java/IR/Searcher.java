package IR;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;

public class Searcher {

    public Searcher() {}

    public void SearchMe(String indexPaath, String queriesPaath, String scoreMe, String argsPaath) throws Exception {
        String usage = "Usage:\tjava IR.Searcher [-index dir] [-field f] [-repeat n] [-queries file] " +
                "[-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/ for details." +
                "[-args_path] for writing directory [-score] for score";

        String index = indexPaath;
        String queries = queriesPaath;
        int setScore = Integer.parseInt(scoreMe);

        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
        IndexSearcher searcher = new IndexSearcher(reader);

        // Traditional switch-case statement for compatibility with Java 8
        switch (setScore) {
            case 0:
                searcher.setSimilarity(new ClassicSimilarity());
                break;
            case 1:
                searcher.setSimilarity(new BM25Similarity());
                break;
            case 2:
                searcher.setSimilarity(new BooleanSimilarity());
                break;
            case 3:
                searcher.setSimilarity(new LMDirichletSimilarity());
                break;
            case 4:
                searcher.setSimilarity(new LMJelinekMercerSimilarity(0.7f)); // optimal lambda value
                break;
            default:
                throw new IllegalArgumentException("Unknown score value: " + setScore);
        }

        Analyzer analyzer = new StandardAnalyzer();
        BufferedReader in = Files.newBufferedReader(Paths.get(queries), StandardCharsets.UTF_8);

        // Multi-field search with boosted scores
        HashMap<String, Float> boostedScores = new HashMap<>();
        boostedScores.put("Title", 0.65f);
        boostedScores.put("Author", 0.04f);
        boostedScores.put("Bibliography", 0.02f);
        boostedScores.put("Words", 0.35f);
        MultiFieldQueryParser parser = new MultiFieldQueryParser(
                new String[]{"Title", "Author", "Bibliography", "Words"},
                analyzer, boostedScores);

        PrintWriter writer = new PrintWriter(argsPaath + "outputs.txt", "UTF-8");

        System.out.println("Please Wait, Querying....");

        String line = in.readLine();
        int queryNumber = 1;

        while (line != null) {
            if (line.startsWith(".I")) {
                line = in.readLine(); // Move to next line
                if (line != null && line.equals(".W")) {
                    line = in.readLine();
                }

                // Read full query text
                StringBuilder queryText = new StringBuilder();
                while (line != null && !line.startsWith(".I")) {
                    queryText.append(line).append(" ");
                    line = in.readLine();
                }

                Query query = parser.parse(QueryParser.escape(queryText.toString().trim()));
                doPagingSearch(queryNumber, searcher, query, 10, writer); // Assume 10 hits per page
                queryNumber++;
            }
        }

        writer.close();
        reader.close();
    }

    public static void doPagingSearch(int queryNumber, IndexSearcher searcher, Query query,
                                      int hitsPerPage, PrintWriter writer) throws IOException {
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        int numTotalHits = Math.toIntExact(results.totalHits.value);
        ScoreDoc[] hits = results.scoreDocs;

        int start = 0;
        int end = Math.min(numTotalHits, hitsPerPage);

        while (start < numTotalHits) {
            end = Math.min(hits.length, start + hitsPerPage);
            for (int i = start; i < end; i++) {
                Document doc = searcher.doc(hits[i].doc);
                String path = doc.get("path");
                if (path != null) {
                    writer.println(queryNumber + " 0 " + path.replace(".I ", "") + " " + (i + 1) + " " + hits[i].score + " Any");
                }
            }
            start += hitsPerPage;
        }
    }
}
