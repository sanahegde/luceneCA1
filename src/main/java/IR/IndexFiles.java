package IR;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

public class IndexFiles {

    private IndexFiles() {}

    public static void main(String[] args) {
        String usage = "java IR.IndexFiles [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n" +
                "This indexes the documents in DOCS_PATH, creating a Lucene index " +
                "in INDEX_PATH that can be searched.";
        String indexPath = "index";
        String docsPath = null;
        String indexPaath = null;
        String queriesPaath = null;
        String scoreMe = null;
        String argsPaath = null;
        boolean create = true;

        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i + 1];
                indexPaath = indexPath;
                i++;
            } else if ("-docs".equals(args[i])) {
                docsPath = args[i + 1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            } else if ("-queries".equals(args[i])) {
                queriesPaath = args[i + 1];
                i++;
            } else if ("-score".equals(args[i])) {
                scoreMe = args[i + 1];
                i++;
            } else if ("-args_path".equals(args[i])) {
                argsPaath = args[i + 1];
                i++;
            }
        }

        if (docsPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        final Path docDir = Paths.get(docsPath);
        if (!Files.isReadable(docDir)) {
            System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
            System.exit(1);
        }

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexDocs(writer, docDir);
            writer.close();

            Searcher search = new Searcher();
            search.SearchMe(indexPaath, queriesPaath, scoreMe, argsPaath);

            System.out.println("\nIndexing for all 1400 cran documents was successfully done at the directory " + indexPaath);
            System.out.println("Searching was successfully performed and 'outputs.txt' was created at " + argsPaath);
            System.out.println("You can now use the Trec_Eval to evaluate from the 'outputs.txt'.\n");

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");

        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void indexDocs(final IndexWriter writer, Path path) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
                    } catch (IOException ignore) {
                        // don't index files that can't be read
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
        }
    }

    static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
        try (InputStream stream = Files.newInputStream(file)) {
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String currentLine = inputReader.readLine();
            Document document = null;
            String docType = "";

            while (currentLine != null) {
                System.out.print(currentLine);
                if (currentLine.contains(".I")) {
                    document = new Document();
                    Field pathField = new StringField("path", currentLine, Field.Store.YES);
                    document.add(pathField);
                    currentLine = inputReader.readLine();

                    while (currentLine != null && !currentLine.startsWith(".I")) {
                        if (currentLine.startsWith(".T")) {
                            docType = "Title";
                        } else if (currentLine.startsWith(".A")) {
                            docType = "Author";
                        } else if (currentLine.startsWith(".W")) {
                            docType = "Words";
                        } else if (currentLine.startsWith(".B")) {
                            docType = "Bibliography";
                        }
                        currentLine = inputReader.readLine();

                        if (currentLine != null && document != null && !currentLine.startsWith(".I")) {
                            document.add(new TextField(docType, currentLine, Field.Store.YES));
                        }
                    }

                    if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                        System.out.println("Adding document: " + file);
                        writer.addDocument(document);
                    } else {
                        System.out.println("Updating document: " + file);
                        writer.updateDocument(new Term("path", file.toString()), document);
                    }
                }
                currentLine = inputReader.readLine();
            }
        }
    }
}
