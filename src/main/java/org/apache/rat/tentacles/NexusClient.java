package org.apache.rat.tentacles;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.swizzle.stream.StreamLexer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

public class NexusClient {

    private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(NexusClient.class);

    private final DefaultHttpClient client;

    public NexusClient() {
        this.client = new DefaultHttpClient();
    }

    public File download(URI uri, File file) throws IOException {
        if (file.exists()) {

            long length = getConentLength(uri);

            if (file.length() == length) {
                log.info("Exists " + uri);
                return file;
            } else {
                log.info("Incomplete " + uri);
            }
        }

        log.info("Download " + uri);

        final HttpResponse response = get(uri);

        final InputStream content = response.getEntity().getContent();

        Files.mkparent(file);

        IO.copy(content, file);

        return file;
    }

    private long getConentLength(URI uri) throws IOException {
        HttpResponse head = head(uri);
        Header[] headers = head.getHeaders("Content-Length");

        for (Header header : headers) {
            return new Long(header.getValue());
        }

        return -1;
    }

    private HttpResponse get(URI uri) throws IOException {
        final HttpGet request = new HttpGet(uri);
        request.setHeader("User-Agent", "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.2.13) Gecko/20101206 Ubuntu/10.10 (maverick) Firefox/3.6.13");
        return client.execute(request);
    }

    private HttpResponse head(URI uri) throws IOException {
        final HttpHead request = new HttpHead(uri);
        request.setHeader("User-Agent", "Mozilla/5.0 (X11; U; Linux x86_64; en-US; rv:1.9.2.13) Gecko/20101206 Ubuntu/10.10 (maverick) Firefox/3.6.13");
        return client.execute(request);
    }

    public Set<URI> crawl(URI index) throws IOException {
        log.info("Crawl " + index);
        final Set<URI> resources = new LinkedHashSet<URI>();

        final HttpResponse response = get(index);

        final InputStream content = response.getEntity().getContent();
        final StreamLexer lexer = new StreamLexer(content);

        final Set<URI> crawl = new LinkedHashSet<URI>();

        //<a href="https://repository.apache.org/content/repositories/orgapacheopenejb-094/archetype-catalog.xml">archetype-catalog.xml</a>
        while (lexer.readAndMark("<a ", "/a>")) {

            try {
                final String link = lexer.peek("href=\"", "\"");
                final String name = lexer.peek(">", "<");

                final URI uri = index.resolve(link);

                if (name.equals("../")) continue;
                if (link.equals("../")) continue;

                if (name.endsWith("/")) {
                    crawl.add(uri);
                    continue;
                }

                resources.add(uri);

            } finally {
                lexer.unmark();
            }
        }

        content.close();

        for (URI uri : crawl) {
            resources.addAll(crawl(uri));
        }

        return resources;
    }
}