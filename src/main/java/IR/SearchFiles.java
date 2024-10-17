package IR;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Scanner;

public class SearchFiles {
    public static void main(String[] args) throws Exception {
        // Ensure correct number of arguments
        if (args.length < 4) {
            System.out.println("Usage: SearchFiles <indexDir> <queriesFile> <scoreType> <outputFile>");
            return;
        }

        String indexPath = args[0];  // Path to the index
        String queriesPath = args[1]; // Path to the queries file
        int scoreType = Integer.parseInt(args[2]); // Scoring method
        String outputPath = args[3]; // Path for output

        // Open the index reader
        try (DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexPath)));
             PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {

            IndexSearcher searcher = new IndexSearcher(reader);
            setSimilarity(searcher, scoreType);

            StandardAnalyzer analyzer = new StandardAnalyzer();
            QueryParser parser = new QueryParser("contents", analyzer); // Primary search on the content field

            File queryFile = new File(queriesPath);
            try (Scanner scanner = new Scanner(queryFile)) {
                int queryNumber = 1;

                while (scanner.hasNextLine()) {
                    String queryString = scanner.nextLine().trim();
                    if (queryString.isEmpty()) continue;

                    try {
                        // Use a multi-field query parser if you want to search multiple fields
                        Query titleQuery = new QueryParser("title", analyzer).parse(queryString);
                        Query contentQuery = new QueryParser("contents", analyzer).parse(queryString);

                        // You can combine these queries or choose one based on user needs
                        BooleanQuery combinedQuery = new BooleanQuery.Builder()
                                .add(titleQuery, BooleanClause.Occur.SHOULD)
                                .add(contentQuery, BooleanClause.Occur.SHOULD)
                                .build();

                        ScoreDoc[] hits = searcher.search(combinedQuery, 50).scoreDocs;

                        if (hits.length == 0) {
                            System.out.println("No results found for query: " + queryString);
                            continue; // Skip to the next query
                        }

                        int rank = 1;
                        for (ScoreDoc hit : hits) {
                            Document doc = searcher.doc(hit.doc);
                            String docID = doc.get("documentID");
                            String title = doc.get("title");
                            String author = doc.get("author");

                            // Output format for TREC_eval
                            writer.println(queryNumber + " 0 " + docID + " " + rank + " " + hit.score + " STANDARD");
                            // You can log the document title and author for debugging purposes
                            System.out.println("Found Document: ID=" + docID + ", Title=" + title + ", Author=" + author);
                            rank++;
                        }
                        queryNumber++;
                    } catch (ParseException e) {
                        System.out.println("Error parsing query: " + queryString);
                    }
                }
            }
        }
    }

    private static void setSimilarity(IndexSearcher searcher, int scoreType) {
        switch (scoreType) {
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
                searcher.setSimilarity(new LMJelinekMercerSimilarity(0.7f));
                break;
            default:
                System.out.println("Invalid score type");
                throw new IllegalArgumentException("Invalid score type");
        }
    }
}