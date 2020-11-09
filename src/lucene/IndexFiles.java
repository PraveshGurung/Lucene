/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package src.lucene;


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.function.Function;

/** Index all text files under a directory.
 */
public class IndexFiles {

    private IndexFiles() {}

    /** Index all text files under a directory. */
    public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException {
        String usage = "java org.apache.lucene.demo.IndexFiles"
                + " [-index INDEX_PATH] [-xml XML_FILE_PATH] [-update]\n\n"
                + "This indexes the xml file in XML_FILE_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with SearchFiles";
        String indexPath = "index";
        String xmlPath = null;
        boolean create = true;
        for(int i=0;i<args.length;i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i+1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            } else if ("-xml".equals(args[i])){
                xmlPath=args[i+1];
                i++;
            }
        }

        if (xmlPath == null) {
            System.err.println("Usage: " + usage);
            System.exit(1);
        }

        Path xmlFile=null;
        if (xmlPath!=null){
            xmlFile = Paths.get(xmlPath);
            if (!Files.isReadable(xmlFile)) {
                System.out.println("xml file '" +xmlFile.toAbsolutePath()+ "' does not exist or is not readable, please check the file");
                System.exit(1);
            }
        }

        Date start = new Date();
        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            IndexWriter writer = new IndexWriter(dir, iwc);
            indexXml(writer,xmlPath);

            writer.close();

            Date end = new Date();
            System.out.println(end.getTime() - start.getTime() + " total milliseconds");

        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() +
                    "\n with message: " + e.getMessage());
        }

    }

    static void indexXml(final IndexWriter writer,String xmlPath) throws IOException, SAXException, ParserConfigurationException {
        File file = new File(xmlPath);

        DocumentBuilder dBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

        org.w3c.dom.Document xmldoc = dBuilder.parse(file);

        NodeList list=xmldoc.getElementsByTagName("row");
        // nodeList is not iterable, so we are using for loop
        if (list.getLength()==0){
            System.out.println("cannot retrieve data, is this xml dump from stackExchange/stackOverflow ?");
        }
        else{

            Function<Node,String> tmp= node ->{if (node!=null) return node.toString(); return "";};
            for (int itr = 0; itr < list.getLength(); itr++) {
                Node post = list.item(itr);

                String questionOrAnswer=tmp.apply(post.getAttributes().getNamedItem("PostTypeId"));
                if (!questionOrAnswer.equals("PostTypeId=\"1\"")){
                    continue;
                }
                String body= tmp.apply(post.getAttributes().getNamedItem("Body"));
                String title=tmp.apply( post.getAttributes().getNamedItem("Title"));




                Document doc = new Document();

                // Add the path of the file as a field named "title".  Use a
                // field that is indexed (i.e. searchable), but don't tokenize
                // the field into separate words and do index term frequency and positional information
                Field pathField = new TextField("title", title, Field.Store.YES);
                doc.add(pathField);

                // Add the contents of the file to a field named "contents".  Specify a Reader,
                // so that the text of the file is tokenized and indexed, but not stored.
                doc.add(new TextField("contents", body, Field.Store.YES));

                if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                    // New index, so we just add the document (no old document can be there):
                    System.out.println("adding " + title);
                    writer.addDocument(doc);
                } else {
                    // Existing index (an old copy of this document may have been indexed) so
                    // we use updateDocument instead to replace the old one matching the exact
                    // path, if present:
                    System.out.println("updating " + title);
                    writer.updateDocument(new Term("title", file.toString()), doc);
                }
            }
        }
    }
}
