package me.rrama.musicplayer.cast.server;

/*
 * Steal Licence.
 * #%L
 * NanoHttpd-Core
 * %%
 * Copyright (C) 2012 - 2015 nanohttpd
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the nanohttpd nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import android.support.annotation.NonNull;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.SSLException;

import me.rrama.musicplayer.cast.server.NanoHTTPD.Response.IStatus;

public abstract class NanoHTTPD {

    /**
     * Pluggable strategy for asynchronously executing requests.
     */
    public interface AsyncRunner {

        void closeAll();

        void closed(ClientHandler clientHandler);

        void exec(ClientHandler code);
    }

    /**
     * The runnable that will be used for every new client connection.
     */
    public class ClientHandler implements Runnable {

        private final InputStream inputStream;

        private final Socket acceptSocket;

        private ClientHandler(InputStream inputStream, Socket acceptSocket) {
            this.inputStream = inputStream;
            this.acceptSocket = acceptSocket;
        }

        public void close() {
            safeClose(this.inputStream);
            safeClose(this.acceptSocket);
        }

        @Override
        public void run() {
            OutputStream outputStream = null;
            try {
                outputStream = this.acceptSocket.getOutputStream();
                HTTPSession session = new HTTPSession(this.inputStream, outputStream, this.acceptSocket.getInetAddress());
                while (!this.acceptSocket.isClosed()) {
                    session.execute();
                }
            } catch (Exception e) {
                // When the socket is closed by the client,
                // we throw our own SocketException
                // to break the "keep alive" loop above. If
                // the exception was anything other
                // than the expected SocketException OR a
                // SocketTimeoutException, print the
                // stacktrace
                if (!(e instanceof SocketException && "NanoHttpd Shutdown".equals(e.getMessage())) && !(e instanceof SocketTimeoutException)) {
                    NanoHTTPD.LOG.log(Level.SEVERE, "Communication with the client broken, or an bug in the handler code", e);
                }
            } finally {
                safeClose(outputStream);
                safeClose(this.inputStream);
                safeClose(this.acceptSocket);
                NanoHTTPD.this.asyncRunner.closed(this);
            }
        }
    }

    /**
     * Default threading strategy for NanoHTTPD.
     * <p/>
     * <p>
     * By default, the server spawns a new Thread for every incoming request.
     * These are set to <i>daemon</i> status, and named according to the request
     * number. The name is useful when profiling the application.
     * </p>
     */
    public static class DefaultAsyncRunner implements AsyncRunner {

        private long requestCount;

        private final List<ClientHandler> running = Collections.synchronizedList(new ArrayList<NanoHTTPD.ClientHandler>());

        @Override
        public void closeAll() {
            // copy of the list for concurrency
            for (ClientHandler clientHandler : new ArrayList<>(this.running)) {
                clientHandler.close();
            }
        }

        @Override
        public void closed(ClientHandler clientHandler) {
            this.running.remove(clientHandler);
        }

        @Override
        public void exec(ClientHandler clientHandler) {
            ++this.requestCount;
            Thread t = new Thread(clientHandler);
            t.setDaemon(true);
            t.setName("NanoHttpd Request Processor (#" + this.requestCount + ")");
            this.running.add(clientHandler);
            t.start();
        }
    }

    private static final String CONTENT_REGEX = "[ |\t]*([^/^ ^;^,]+/[^ ^;^,]+)";

    private static final Pattern MIME_PATTERN = Pattern.compile(CONTENT_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String CHARSET_REGEX = "[ |\t]*(charset)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?";

    private static final Pattern CHARSET_PATTERN = Pattern.compile(CHARSET_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String BOUNDARY_REGEX = "[ |\t]*(boundary)[ |\t]*=[ |\t]*['|\"]?([^\"^'^;^,]*)['|\"]?";

    private static final Pattern BOUNDARY_PATTERN = Pattern.compile(BOUNDARY_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Creates a normal ServerSocket for TCP connections
     */
    public static class DefaultServerSocketFactory implements ServerSocketFactory {

        @Override
        public ServerSocket create() throws IOException {
            return new ServerSocket();
        }

    }

    private static final String CONTENT_DISPOSITION_REGEX = "([ |\t]*Content-Disposition[ |\t]*:)(.*)";

    private static final Pattern CONTENT_DISPOSITION_PATTERN = Pattern.compile(CONTENT_DISPOSITION_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String CONTENT_TYPE_REGEX = "([ |\t]*content-type[ |\t]*:)(.*)";

    private static final Pattern CONTENT_TYPE_PATTERN = Pattern.compile(CONTENT_TYPE_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String CONTENT_DISPOSITION_ATTRIBUTE_REGEX = "[ |\t]*([a-zA-Z]*)[ |\t]*=[ |\t]*['|\"]([^\"^']*)['|\"]";

    private static final Pattern CONTENT_DISPOSITION_ATTRIBUTE_PATTERN = Pattern.compile(CONTENT_DISPOSITION_ATTRIBUTE_REGEX);

    protected class HTTPSession implements IHTTPSession {

        private static final int REQUEST_BUFFER_LEN = 512;

        private static final int MEMORY_STORE_LIMIT = 1024;

        public static final int BUFSIZE = 8192;

        public static final int MAX_HEADER_SIZE = 1024;

        private final OutputStream outputStream;

        private final BufferedInputStream inputStream;

        private int splitbyte;

        private int rlen;

        private String uri;

        private Method method;

        private Map<String, String> parms;

        private Map<String, String> headers;

        private String queryParameterString;

        private final String remoteIp;

        private String protocolVersion;

        public HTTPSession(InputStream inputStream, OutputStream outputStream, InetAddress inetAddress) {
            this.inputStream = new BufferedInputStream(inputStream, HTTPSession.BUFSIZE);
            this.outputStream = outputStream;
            this.remoteIp = inetAddress.isLoopbackAddress() || inetAddress.isAnyLocalAddress() ? "127.0.0.1" : inetAddress.getHostAddress();
            this.headers = new HashMap<>();
        }

        /**
         * Decodes the sent headers and loads the data into Key/value pairs
         */
        private void decodeHeader(BufferedReader in, Map<String, String> pre, Map<String, String> parms, Map<String, String> headers) throws ResponseException {
            try {
                // Read the request line
                String inLine = in.readLine();
                if (inLine == null) {
                    return;
                }

                StringTokenizer st = new StringTokenizer(inLine);
                if (!st.hasMoreTokens()) {
                    throw new ResponseException(Response.Status.BAD_REQUEST, "BAD REQUEST: Syntax error. Usage: GET /example/file.html");
                }

                pre.put("method", st.nextToken());

                if (!st.hasMoreTokens()) {
                    throw new ResponseException(Response.Status.BAD_REQUEST, "BAD REQUEST: Missing URI. Usage: GET /example/file.html");
                }

                String uri = st.nextToken();

                // Decode parameters from the URI
                int qmi = uri.indexOf('?');
                if (qmi >= 0) {
                    decodeParms(uri.substring(qmi + 1), parms);
                    uri = decodePercent(uri.substring(0, qmi));
                } else {
                    uri = decodePercent(uri);
                }

                // If there's another token, its protocol version,
                // followed by HTTP headers.
                // NOTE: this now forces header names lower case since they are
                // case insensitive and vary by client.
                if (st.hasMoreTokens()) {
                    protocolVersion = st.nextToken();
                } else {
                    protocolVersion = "HTTP/1.1";
                    NanoHTTPD.LOG.log(Level.FINE, "no protocol version specified, strange. Assuming HTTP/1.1.");
                }
                String line = in.readLine();
                while (line != null && !line.trim().isEmpty()) {
                    int p = line.indexOf(':');
                    if (p >= 0) {
                        headers.put(line.substring(0, p).trim().toLowerCase(Locale.US), line.substring(p + 1).trim());
                    }
                    line = in.readLine();
                }

                pre.put("uri", uri);
            } catch (IOException ioe) {
                throw new ResponseException(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage(), ioe);
            }
        }

        /**
         * Decodes the Multipart Body data and put it into Key/Value pairs.
         */
        private void decodeMultipartFormData(String boundary, String encoding, ByteBuffer fbuf, Map<String, String> parms) throws ResponseException {
            try {
                int[] boundary_idxs = getBoundaryPositions(fbuf, boundary.getBytes());
                if (boundary_idxs.length < 2) {
                    throw new ResponseException(Response.Status.BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but contains less than two boundary strings.");
                }

                byte[] part_header_buff = new byte[MAX_HEADER_SIZE];
                for (int bi = 0; bi < boundary_idxs.length - 1; bi++) {
                    fbuf.position(boundary_idxs[bi]);
                    int len = (fbuf.remaining() < MAX_HEADER_SIZE) ? fbuf.remaining() : MAX_HEADER_SIZE;
                    fbuf.get(part_header_buff, 0, len);
                    BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(part_header_buff, 0, len), Charset.forName(encoding)), len);

                    int headerLines = 0;
                    // First line is boundary string
                    String mpline = in.readLine();
                    headerLines++;
                    if (mpline == null || !mpline.contains(boundary)) {
                        throw new ResponseException(Response.Status.BAD_REQUEST, "BAD REQUEST: Content type is multipart/form-data but chunk does not start with boundary.");
                    }

                    String part_name = null, content_type = null;
                    // Parse the reset of the header lines
                    mpline = in.readLine();
                    headerLines++;
                    while (mpline != null && mpline.trim().length() > 0) {
                        Matcher matcher = CONTENT_DISPOSITION_PATTERN.matcher(mpline);
                        if (matcher.matches()) {
                            String attributeString = matcher.group(2);
                            matcher = CONTENT_DISPOSITION_ATTRIBUTE_PATTERN.matcher(attributeString);
                            while (matcher.find()) {
                                String key = matcher.group(1);
                                if ("name".equalsIgnoreCase(key)) {
                                    part_name = matcher.group(2);
                                }
                            }
                        }
                        matcher = CONTENT_TYPE_PATTERN.matcher(mpline);
                        if (matcher.matches()) {
                            content_type = matcher.group(2).trim();
                        }
                        mpline = in.readLine();
                        headerLines++;
                    }
                    int part_header_len = 0;
                    while (headerLines-- > 0) {
                        part_header_len = scipOverNewLine(part_header_buff, part_header_len);
                    }
                    // Read the part data
                    if (part_header_len >= len - 4) {
                        throw new ResponseException(Response.Status.INTERNAL_ERROR, "Multipart header size exceeds MAX_HEADER_SIZE.");
                    }
                    int part_data_start = boundary_idxs[bi] + part_header_len;
                    int part_data_end = boundary_idxs[bi + 1] - 4;

                    fbuf.position(part_data_start);
                    if (content_type == null) {
                        // Read the part into a string
                        byte[] data_bytes = new byte[part_data_end - part_data_start];
                        fbuf.get(data_bytes);
                        parms.put(part_name, new String(data_bytes, encoding));
                    }
                }
            } catch (ResponseException re) {
                throw re;
            } catch (Exception e) {
                throw new ResponseException(Response.Status.INTERNAL_ERROR, e.toString());
            }
        }

        private int scipOverNewLine(byte[] part_header_buff, int index) {
            while (part_header_buff[index] != '\n') {
                index++;
            }
            return ++index;
        }

        /**
         * Decodes parameters in percent-encoded URI-format ( e.g.
         * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
         * Map. NOTE: this doesn't support multiple identical keys due to the
         * simplicity of Map.
         */
        private void decodeParms(String parms, Map<String, String> p) {
            if (parms == null) {
                this.queryParameterString = "";
                return;
            }

            this.queryParameterString = parms;
            StringTokenizer st = new StringTokenizer(parms, "&");
            while (st.hasMoreTokens()) {
                String e = st.nextToken();
                int sep = e.indexOf('=');
                if (sep >= 0) {
                    p.put(decodePercent(e.substring(0, sep)).trim(), decodePercent(e.substring(sep + 1)));
                } else {
                    p.put(decodePercent(e).trim(), "");
                }
            }
        }

        private void execute() throws IOException {
            Response r = null;
            try {
                // Read the first 8192 bytes.
                // The full header should fit in here.
                // Apache's default header limit is 8KB.
                // Do NOT assume that a single read will get the entire header
                // at once!
                byte[] buf = new byte[HTTPSession.BUFSIZE];
                this.splitbyte = 0;
                this.rlen = 0;

                int read = -1;
                this.inputStream.mark(HTTPSession.BUFSIZE);
                try {
                    read = this.inputStream.read(buf, 0, HTTPSession.BUFSIZE);
                } catch (SSLException e) {
                    throw e;
                } catch (IOException e) {
                    safeClose(this.inputStream);
                    safeClose(this.outputStream);
                    throw new SocketException("NanoHttpd Shutdown");
                }
                if (read == -1) {
                    // socket was been closed
                    safeClose(this.inputStream);
                    safeClose(this.outputStream);
                    throw new SocketException("NanoHttpd Shutdown");
                }
                while (read > 0) {
                    this.rlen += read;
                    this.splitbyte = findHeaderEnd(buf, this.rlen);
                    if (this.splitbyte > 0) {
                        break;
                    }
                    read = this.inputStream.read(buf, this.rlen, HTTPSession.BUFSIZE - this.rlen);
                }

                if (this.splitbyte < this.rlen) {
                    this.inputStream.reset();
                    this.inputStream.skip(this.splitbyte);
                }

                this.parms = new HashMap<>();
                if (null == this.headers) {
                    this.headers = new HashMap<>();
                } else {
                    this.headers.clear();
                }

                // Create a BufferedReader for parsing the header.
                BufferedReader hin = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf, 0, this.rlen)));

                // Decode the header into parms and header java properties
                Map<String, String> pre = new HashMap<>();
                decodeHeader(hin, pre, this.parms, this.headers);

                if (null != this.remoteIp) {
                    this.headers.put("remote-addr", this.remoteIp);
                    this.headers.put("http-client-ip", this.remoteIp);
                }

                this.method = Method.lookup(pre.get("method"));
                if (this.method == null) {
                    throw new ResponseException(Response.Status.BAD_REQUEST, "BAD REQUEST: Syntax error.");
                }

                this.uri = pre.get("uri");

                String connection = this.headers.get("connection");
                boolean keepAlive = "HTTP/1.1".equals(protocolVersion) && (connection == null || !connection.matches("(?i).*close.*"));

                // Ok, now do the serve()
                r = serve(this);

                if (r == null) {
                    throw new ResponseException(Response.Status.INTERNAL_ERROR, "SERVER INTERNAL ERROR: Serve() returned a null response.");
                } else {
                    String acceptEncoding = this.headers.get("accept-encoding");
                    r.setRequestMethod(this.method);
                    r.setGzipEncoding(useGzipWhenAccepted(r) && acceptEncoding != null && acceptEncoding.contains("gzip"));
                    r.setKeepAlive(keepAlive);
                    r.send(this.outputStream);
                }
                if (!keepAlive || r.isCloseConnection()) {
                    throw new SocketException("NanoHttpd Shutdown");
                }
            } catch (SocketException | SocketTimeoutException ex) {
                throw ex;
            } catch (IOException ioe) {
                Response resp = newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
                resp.send(this.outputStream);
                safeClose(this.outputStream);
            } catch (ResponseException re) {
                Response resp = newFixedLengthResponse(re.getStatus(), NanoHTTPD.MIME_PLAINTEXT, re.getMessage());
                resp.send(this.outputStream);
                safeClose(this.outputStream);
            } finally {
                safeClose(r);
            }
        }

        /**
         * Find byte index separating header from body. It must be the last byte
         * of the first two sequential new lines.
         */
        private int findHeaderEnd(final byte[] buf, int rlen) {
            int splitbyte = 0;
            while (splitbyte + 1 < rlen) {

                // RFC2616
                if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && splitbyte + 3 < rlen && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n') {
                    return splitbyte + 4;
                }

                // tolerance
                if (buf[splitbyte] == '\n' && buf[splitbyte + 1] == '\n') {
                    return splitbyte + 2;
                }
                splitbyte++;
            }
            return 0;
        }

        /**
         * Find the byte positions where multipart boundaries start. This reads
         * a large block at a time and uses a temporary buffer to optimize
         * (memory mapped) file access.
         */
        private int[] getBoundaryPositions(ByteBuffer b, byte[] boundary) {
            int[] res = new int[0];
            if (b.remaining() < boundary.length) {
                return res;
            }

            int search_window_pos = 0;
            byte[] search_window = new byte[4 * 1024 + boundary.length];

            int first_fill = (b.remaining() < search_window.length) ? b.remaining() : search_window.length;
            b.get(search_window, 0, first_fill);
            int new_bytes = first_fill - boundary.length;

            do {
                // Search the search_window
                for (int j = 0; j < new_bytes; j++) {
                    for (int i = 0; i < boundary.length; i++) {
                        if (search_window[j + i] != boundary[i])
                            break;
                        if (i == boundary.length - 1) {
                            // Match found, add it to results
                            int[] new_res = new int[res.length + 1];
                            System.arraycopy(res, 0, new_res, 0, res.length);
                            new_res[res.length] = search_window_pos + j;
                            res = new_res;
                        }
                    }
                }
                search_window_pos += new_bytes;

                // Copy the end of the buffer to the start
                System.arraycopy(search_window, search_window.length - boundary.length, search_window, 0, boundary.length);

                // Refill search_window
                new_bytes = search_window.length - boundary.length;
                new_bytes = (b.remaining() < new_bytes) ? b.remaining() : new_bytes;
                b.get(search_window, boundary.length, new_bytes);
            } while (new_bytes > 0);
            return res;
        }

        @Override
        public final Map<String, String> getHeaders() {
            return this.headers;
        }

        @Override
        public final Method getMethod() {
            return this.method;
        }

        @Override
        public final Map<String, String> getParms() {
            return this.parms;
        }

        @Override
        public String getQueryParameterString() {
            return this.queryParameterString;
        }

        @Override
        public final String getUri() {
            return this.uri;
        }

        /**
         * Deduce body length in bytes. Either from "content-length" header or
         * read bytes.
         */
        public long getBodySize() {
            if (this.headers.containsKey("content-length")) {
                return Long.parseLong(this.headers.get("content-length"));
            } else if (this.splitbyte < this.rlen) {
                return this.rlen - this.splitbyte;
            }
            return 0;
        }

        @Override
        public void parseBody(Map<String, String> files) throws IOException, ResponseException {
            long size = getBodySize();
            ByteArrayOutputStream baos = null;
            DataOutput request_data_output = null;

            // Store the request in memory or a file, depending on size
            if (size < MEMORY_STORE_LIMIT) {
                baos = new ByteArrayOutputStream();
                request_data_output = new DataOutputStream(baos);
            }

            // Read all the body and write it to request_data_output
            byte[] buf = new byte[REQUEST_BUFFER_LEN];
            while (this.rlen >= 0 && size > 0) {
                this.rlen = this.inputStream.read(buf, 0, (int) Math.min(size, REQUEST_BUFFER_LEN));
                size -= this.rlen;
                if (this.rlen > 0) {
                    request_data_output.write(buf, 0, this.rlen);
                }
            }

            ByteBuffer fbuf = null;
            if (baos != null) {
                fbuf = ByteBuffer.wrap(baos.toByteArray(), 0, baos.size());
            }

            // If the method is POST, there may be parameters
            // in data section, too, read it:
            if (Method.POST.equals(this.method)) {
                String contentType = "";
                String encoding = "UTF-8";
                String contentTypeHeader = this.headers.get("content-type");
                if (contentTypeHeader != null) {
                    contentType = getDetailFromContentHeader(contentTypeHeader, MIME_PATTERN, "", 1);
                    encoding = getDetailFromContentHeader(contentTypeHeader, CHARSET_PATTERN, "US-ASCII", 2);
                }
                if ("multipart/form-data".equalsIgnoreCase(contentType)) {
                    String boundary = getDetailFromContentHeader(contentTypeHeader, BOUNDARY_PATTERN, null, 2);
                    if (boundary == null) {
                        throw new ResponseException(Response.Status.BAD_REQUEST,
                                "BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html");
                    }
                    decodeMultipartFormData(boundary, encoding, fbuf, this.parms);
                } else {
                    byte[] postBytes = new byte[fbuf.remaining()];
                    fbuf.get(postBytes);
                    String postLine = new String(postBytes, encoding).trim();
                    // Handle application/x-www-form-urlencoded
                    if ("application/x-www-form-urlencoded".equalsIgnoreCase(contentType)) {
                        decodeParms(postLine, this.parms);
                    } else if (postLine.length() != 0) {
                        // Special case for raw POST data => create a
                        // special files entry "postData" with raw content
                        // data
                        files.put("postData", postLine);
                    }
                }
            }
        }

        private String getDetailFromContentHeader(String contentTypeHeader, Pattern pattern, String defaultValue, int group) {
            Matcher matcher = pattern.matcher(contentTypeHeader);
            return matcher.find() ? matcher.group(group) : defaultValue;
        }

    }

    /**
     * Handles one session, i.e. parses the HTTP request and returns the
     * response.
     */
    public interface IHTTPSession {

        Map<String, String> getHeaders();

        Method getMethod();

        Map<String, String> getParms();

        String getQueryParameterString();

        /**
         * @return the path part of the URL.
         */
        String getUri();

        /**
         * Adds the files in the request body to the files map.
         *
         * @param files map to modify
         */
        void parseBody(Map<String, String> files) throws IOException, ResponseException;

    }

    /**
     * HTTP Request methods, with the ability to decode a <code>String</code>
     * back to its enum value.
     */
    public enum Method {
        GET,
        POST,
        HEAD,
        OPTIONS,
        TRACE,
        CONNECT,
        PATCH;

        static Method lookup(String method) {
            for (Method m : Method.values()) {
                if (m.toString().equalsIgnoreCase(method)) {
                    return m;
                }
            }
            return null;
        }
    }

    /**
     * HTTP response. Return one of these from serve().
     */
    public static class Response implements Closeable {

        public interface IStatus {

            String getDescription();

        }

        /**
         * Some HTTP response status codes
         */
        public enum Status implements IStatus {
            SWITCH_PROTOCOL(101, "Switching Protocols"),
            OK(200, "OK"),
            CREATED(201, "Created"),
            ACCEPTED(202, "Accepted"),
            NO_CONTENT(204, "No Content"),
            PARTIAL_CONTENT(206, "Partial Content"),
            REDIRECT(301, "Moved Permanently"),
            NOT_MODIFIED(304, "Not Modified"),
            BAD_REQUEST(400, "Bad Request"),
            UNAUTHORIZED(401, "Unauthorized"),
            FORBIDDEN(403, "Forbidden"),
            NOT_FOUND(404, "Not Found"),
            METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
            NOT_ACCEPTABLE(406, "Not Acceptable"),
            REQUEST_TIMEOUT(408, "Request Timeout"),
            CONFLICT(409, "Conflict"),
            RANGE_NOT_SATISFIABLE(416, "Requested Range Not Satisfiable"),
            INTERNAL_ERROR(500, "Internal Server Error"),
            NOT_IMPLEMENTED(501, "Not Implemented"),
            UNSUPPORTED_HTTP_VERSION(505, "HTTP Version Not Supported");

            private final int requestStatus;

            private final String description;

            Status(int requestStatus, String description) {
                this.requestStatus = requestStatus;
                this.description = description;
            }

            @Override
            public String getDescription() {
                return "" + this.requestStatus + " " + this.description;
            }

        }

        /**
         * Output stream that will automatically send every write to the wrapped
         * OutputStream according to chunked transfer:
         * http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1
         */
        private static class ChunkedOutputStream extends FilterOutputStream {

            public ChunkedOutputStream(OutputStream out) {
                super(out);
            }

            @Override
            public void write(int b) throws IOException {
                byte[] data = {
                        (byte) b
                };
                write(data, 0, 1);
            }

            @Override
            public void write(@NonNull byte[] b) throws IOException {
                write(b, 0, b.length);
            }

            @Override
            public void write(@NonNull byte[] b, int off, int len) throws IOException {
                if (len == 0)
                    return;
                out.write(String.format("%x\r\n", len).getBytes());
                out.write(b, off, len);
                out.write("\r\n".getBytes());
            }

            public void finish() throws IOException {
                out.write("0\r\n\r\n".getBytes());
            }

        }

        /**
         * HTTP status code after processing, e.g. "200 OK", Status.OK
         */
        private final IStatus status;

        /**
         * MIME type of content, e.g. "text/html"
         */
        private final String mimeType;

        /**
         * Data of the response, may be null.
         */
        private InputStream data;

        private final long contentLength;

        /**
         * Headers for the HTTP response. Use addHeader() to add lines. the
         * lowercase map is automaticaly kept up to date.
         */
        private final Map<String, String> header = new HashMap<String, String>() {

            public String put(String key, String value) {
                lowerCaseHeader.put(key == null ? null : key.toLowerCase(), value);
                return super.put(key, value);
            }

        };

        /**
         * copy of the header map with all the keys lowercase for faster
         * searching.
         */
        private final Map<String, String> lowerCaseHeader = new HashMap<>();

        /**
         * The request method that spawned this response.
         */
        private Method requestMethod;

        /**
         * Use chunkedTransfer
         */
        private boolean chunkedTransfer;

        private boolean encodeAsGzip;

        private boolean keepAlive;

        /**
         * Creates a fixed length response if totalBytes>=0, otherwise chunked.
         */
        protected Response(IStatus status, String mimeType, InputStream data, long totalBytes) {
            this.status = status;
            this.mimeType = mimeType;
            if (data == null) {
                this.data = new ByteArrayInputStream(new byte[0]);
                this.contentLength = 0L;
            } else {
                this.data = data;
                this.contentLength = totalBytes;
            }
            this.chunkedTransfer = this.contentLength < 0;
            keepAlive = true;
        }

        @Override
        public void close() throws IOException {
            if (this.data != null) {
                this.data.close();
            }
        }

        /**
         * Adds given line to the header.
         */
        public void addHeader(String name, String value) {
            this.header.put(name, value);
        }

        /**
         * @return {@code true} if connection is to be closed after this
         * Response has been sent.
         */
        public boolean isCloseConnection() {
            return "close".equals(getHeader("connection"));
        }

        public InputStream getData() {
            return this.data;
        }

        public String getHeader(String name) {
            return this.lowerCaseHeader.get(name.toLowerCase());
        }

        public String getMimeType() {
            return this.mimeType;
        }

        public void setGzipEncoding(boolean encodeAsGzip) {
            this.encodeAsGzip = encodeAsGzip;
        }

        public void setKeepAlive(boolean useKeepAlive) {
            this.keepAlive = useKeepAlive;
        }

        /**
         * Sends given response to the socket.
         */
        protected void send(OutputStream outputStream) {
            SimpleDateFormat gmtFrmt = new SimpleDateFormat("E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
            gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));

            try {
                if (this.status == null) {
                    throw new Error("sendResponse(): Status can't be null.");
                }
                PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8")), false);
                pw.append("HTTP/1.1 ").append(this.status.getDescription()).append(" \r\n");
                if (this.mimeType != null) {
                    printHeader(pw, "Content-Type", this.mimeType);
                }
                if (getHeader("date") == null) {
                    printHeader(pw, "Date", gmtFrmt.format(new Date()));
                }
                for (Entry<String, String> entry : this.header.entrySet()) {
                    printHeader(pw, entry.getKey(), entry.getValue());
                }
                if (getHeader("connection") == null) {
                    printHeader(pw, "Connection", (this.keepAlive ? "keep-alive" : "close"));
                }
                if (getHeader("content-length") != null) {
                    encodeAsGzip = false;
                }
                if (encodeAsGzip) {
                    printHeader(pw, "Content-Encoding", "gzip");
                    setChunkedTransfer();
                }
                long pending = this.data != null ? this.contentLength : 0;
                if (this.requestMethod != Method.HEAD && this.chunkedTransfer) {
                    printHeader(pw, "Transfer-Encoding", "chunked");
                } else if (!encodeAsGzip) {
                    pending = sendContentLengthHeaderIfNotAlreadyPresent(pw, pending);
                }
                pw.append("\r\n");
                pw.flush();
                sendBodyWithCorrectTransferAndEncoding(outputStream, pending);
                outputStream.flush();
                safeClose(this.data);
            } catch (IOException ioe) {
                NanoHTTPD.LOG.log(Level.SEVERE, "Could not send response to the client", ioe);
            }
        }

        protected void printHeader(PrintWriter pw, String key, String value) {
            pw.append(key).append(": ").append(value).append("\r\n");
        }

        protected long sendContentLengthHeaderIfNotAlreadyPresent(PrintWriter pw, long defaultSize) {
            String contentLengthString = getHeader("content-length");
            long size = defaultSize;
            if (contentLengthString != null) {
                try {
                    size = Long.parseLong(contentLengthString);
                } catch (NumberFormatException ex) {
                    LOG.severe("content-length was no number " + contentLengthString);
                }
            }
            pw.print("Content-Length: " + size + "\r\n");
            return size;
        }

        private void sendBodyWithCorrectTransferAndEncoding(OutputStream outputStream, long pending) throws IOException {
            if (this.requestMethod != Method.HEAD && this.chunkedTransfer) {
                ChunkedOutputStream chunkedOutputStream = new ChunkedOutputStream(outputStream);
                sendBodyWithCorrectEncoding(chunkedOutputStream, -1);
                chunkedOutputStream.finish();
            } else {
                sendBodyWithCorrectEncoding(outputStream, pending);
            }
        }

        private void sendBodyWithCorrectEncoding(OutputStream outputStream, long pending) throws IOException {
            if (encodeAsGzip) {
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
                sendBody(gzipOutputStream, -1);
                gzipOutputStream.finish();
            } else {
                sendBody(outputStream, pending);
            }
        }

        /**
         * Sends the body to the specified OutputStream. The pending parameter
         * limits the maximum amounts of bytes sent unless it is -1, in which
         * case everything is sent.
         *
         * @param outputStream the OutputStream to send data to
         * @param pending      -1 to send everything, otherwise sets a max limit to the
         *                     number of bytes sent
         * @throws IOException if something goes wrong while sending the data.
         */
        private void sendBody(OutputStream outputStream, long pending) throws IOException {
            long BUFFER_SIZE = 16 * 1024;
            byte[] buff = new byte[(int) BUFFER_SIZE];
            boolean sendEverything = pending == -1;
            while (pending > 0 || sendEverything) {
                long bytesToRead = sendEverything ? BUFFER_SIZE : Math.min(pending, BUFFER_SIZE);
                int read = this.data.read(buff, 0, (int) bytesToRead);
                if (read <= 0) {
                    break;
                }
                outputStream.write(buff, 0, read);
                if (!sendEverything) {
                    pending -= read;
                }
            }
        }

        public void setChunkedTransfer() {
            this.chunkedTransfer = true;
        }

        public void setData(InputStream data) {
            this.data = data;
        }

        public void setRequestMethod(Method requestMethod) {
            this.requestMethod = requestMethod;
        }

    }

    public static final class ResponseException extends Exception {

        private static final long serialVersionUID = 6569838532917408380L;

        private final Response.Status status;

        public ResponseException(Response.Status status, String message) {
            super(message);
            this.status = status;
        }

        public ResponseException(Response.Status status, String message, Exception e) {
            super(message, e);
            this.status = status;
        }

        public Response.Status getStatus() {
            return this.status;
        }
    }

    /**
     * The runnable that will be used for the main listening thread.
     */
    public class ServerRunnable implements Runnable {

        private final int timeout;

        private IOException bindException;

        private boolean hasBinded = false;

        private ServerRunnable(int timeout) {
            this.timeout = timeout;
        }

        @Override
        public void run() {
            try {
                myServerSocket.bind(hostname != null ? new InetSocketAddress(hostname, myPort) : new InetSocketAddress(myPort));
                hasBinded = true;
            } catch (IOException e) {
                this.bindException = e;
                return;
            }
            do {
                try {
                    final Socket finalAccept = NanoHTTPD.this.myServerSocket.accept();
                    if (this.timeout > 0) {
                        finalAccept.setSoTimeout(this.timeout);
                    }
                    final InputStream inputStream = finalAccept.getInputStream();
                    NanoHTTPD.this.asyncRunner.exec(createClientHandler(finalAccept, inputStream));
                } catch (IOException e) {
                    NanoHTTPD.LOG.log(Level.FINE, "Communication with the client broken", e);
                }
            } while (!NanoHTTPD.this.myServerSocket.isClosed());
        }
    }

    /**
     * Factory to create ServerSocketFactories.
     */
    public interface ServerSocketFactory {

        ServerSocket create() throws IOException;

    }

    /**
     * Maximum time to wait on Socket.getInputStream().read() (in milliseconds)
     * This is required as the Keep-Alive HTTP connections would otherwise block
     * the socket reading thread forever (or as long the browser is open).
     */
    public static final int SOCKET_READ_TIMEOUT = 5000;

    /**
     * Common MIME type for dynamic content: plain text
     */
    public static final String MIME_PLAINTEXT = "text/plain";

    /**
     * Pseudo-Parameter to use to store the actual query string in the
     * parameters map for later re-processing.
     */
    private static final String QUERY_STRING_PARAMETER = "NanoHttpd.QUERY_STRING";

    /**
     * logger to log to.
     */
    private static final Logger LOG = Logger.getLogger(NanoHTTPD.class.getName());

    /**
     * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
     */
    protected static Map<String, String> MIME_TYPES;

    public static Map<String, String> mimeTypes() {
        if (MIME_TYPES == null) {
            MIME_TYPES = new HashMap<>();
            loadMimeTypes(MIME_TYPES, "META-INF/nanohttpd/default-mimetypes.properties");
            loadMimeTypes(MIME_TYPES, "META-INF/nanohttpd/mimetypes.properties");
            if (MIME_TYPES.isEmpty()) {
                LOG.log(Level.WARNING, "no mime types found in the classpath! please provide mimetypes.properties");
            }
        }
        return MIME_TYPES;
    }

    private static void loadMimeTypes(Map<String, String> result, String resourceName) {
        try {
            Enumeration<URL> resources = NanoHTTPD.class.getClassLoader().getResources(resourceName);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                Properties properties = new Properties();
                InputStream stream = null;
                try {
                    stream = url.openStream();
                    properties.load(url.openStream());
                } catch (IOException e) {
                    LOG.log(Level.SEVERE, "could not load mimetypes from " + url, e);
                } finally {
                    safeClose(stream);
                }
                result.putAll((Map) properties);
            }
        } catch (IOException e) {
            LOG.log(Level.INFO, "no mime types available at " + resourceName);
        }
    }

    /**
     * Get MIME type from file name extension, if possible
     *
     * @param uri the string representing a file
     * @return the connected mime/type
     */
    public static String getMimeTypeForFile(String uri) {
        int dot = uri.lastIndexOf('.');
        String mime = null;
        if (dot >= 0) {
            mime = mimeTypes().get(uri.substring(dot + 1).toLowerCase());
        }
        return mime == null ? "application/octet-stream" : mime;
    }

    private static void safeClose(Object closeable) {
        try {
            if (closeable != null) {
                if (closeable instanceof Closeable) {
                    ((Closeable) closeable).close();
                } else {
                    throw new IllegalArgumentException("Unknown object to close");
                }
            }
        } catch (IOException e) {
            NanoHTTPD.LOG.log(Level.SEVERE, "Could not close", e);
        }
    }

    private final String hostname;

    private final int myPort;

    private volatile ServerSocket myServerSocket;

    private final ServerSocketFactory serverSocketFactory = new DefaultServerSocketFactory();

    private Thread myThread;

    /**
     * Pluggable strategy for asynchronously executing requests.
     */
    protected AsyncRunner asyncRunner;

    // -------------------------------------------------------------------------------
    // //
    //
    // Threading Strategy.
    //
    // -------------------------------------------------------------------------------
    // //

    /**
     * Constructs an HTTP server on given hostname and port.
     */
    public NanoHTTPD(String hostname, int port) {
        this.hostname = hostname;
        this.myPort = port;
        setAsyncRunner(new DefaultAsyncRunner());
    }

    /**
     * create a instance of the client handler, subclasses can return a subclass
     * of the ClientHandler.
     *
     * @param finalAccept the socket the cleint is connected to
     * @param inputStream the input stream
     * @return the client handler
     */
    protected ClientHandler createClientHandler(final Socket finalAccept, final InputStream inputStream) {
        return new ClientHandler(inputStream, finalAccept);
    }

    /**
     * Instantiate the server runnable, can be overwritten by subclasses to
     * provide a subclass of the ServerRunnable.
     *
     * @param timeout the socet timeout to use.
     * @return the server runnable.
     */
    protected ServerRunnable createServerRunnable(final int timeout) {
        return new ServerRunnable(timeout);
    }

    // -------------------------------------------------------------------------------
    // //

    /**
     * Decode percent encoded <code>String</code> values.
     *
     * @param str the percent encoded <code>String</code>
     * @return expanded form of the input, for example "foo%20bar" becomes
     * "foo bar"
     */
    protected static String decodePercent(String str) {
        String decoded = null;
        try {
            decoded = URLDecoder.decode(str, "UTF8");
        } catch (UnsupportedEncodingException ignored) {
            NanoHTTPD.LOG.log(Level.WARNING, "Encoding not supported, ignored", ignored);
        }
        return decoded;
    }

    /**
     * @return true if the gzip compression should be used if the client
     * accespts it. Default this option is on for text content and off
     * for everything. Override this for custom semantics.
     */
    protected boolean useGzipWhenAccepted(Response r) {
        return r.getMimeType() != null && r.getMimeType().toLowerCase().contains("text/");
    }

    public final boolean isDead() {
        return !wasStarted() || this.myServerSocket.isClosed() || !this.myThread.isAlive();
    }

    public ServerSocketFactory getServerSocketFactory() {
        return serverSocketFactory;
    }

    /**
     * Create a response with known length.
     */
    public static Response newFixedLengthResponse(IStatus status, String mimeType, InputStream data, long totalBytes) {
        return new Response(status, mimeType, data, totalBytes);
    }

    /**
     * Create a text response with known length.
     */
    public static Response newFixedLengthResponse(IStatus status, String mimeType, String txt) {
        if (txt == null) {
            return newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(new byte[0]), 0);
        } else {
            byte[] bytes;
            try {
                bytes = txt.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                NanoHTTPD.LOG.log(Level.SEVERE, "encoding problem, responding nothing", e);
                bytes = new byte[0];
            }
            return newFixedLengthResponse(status, mimeType, new ByteArrayInputStream(bytes), bytes.length);
        }
    }

    /**
     * Override this to customize the server.
     * <p>
     * <p>
     * (By default, this returns a 404 "Not Found" plain text error response.)
     *
     * @param session The HTTP session
     * @return HTTP response, see class Response for details
     */
    public Response serve(IHTTPSession session) {
        Map<String, String> files = new HashMap<>();
        Method method = session.getMethod();
        if (Method.POST.equals(method)) {
            try {
                session.parseBody(files);
            } catch (IOException ioe) {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
            } catch (ResponseException re) {
                return newFixedLengthResponse(re.getStatus(), NanoHTTPD.MIME_PLAINTEXT, re.getMessage());
            }
        }

        Map<String, String> parms = session.getParms();
        parms.put(NanoHTTPD.QUERY_STRING_PARAMETER, session.getQueryParameterString());

        // Deprecated
        return newFixedLengthResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Not Found");
    }

    /**
     * Pluggable strategy for asynchronously executing requests.
     *
     * @param asyncRunner new strategy for handling threads.
     */
    public void setAsyncRunner(AsyncRunner asyncRunner) {
        this.asyncRunner = asyncRunner;
    }

    /**
     * Start the server.
     *
     * @throws IOException if the socket is in use.
     */
    public void start() throws IOException {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT);
    }

    /**
     * Start the server.
     *
     * @param timeout timeout to use for socket connections.
     * @throws IOException if the socket is in use.
     */
    public void start(final int timeout) throws IOException {
        this.myServerSocket = this.getServerSocketFactory().create();
        this.myServerSocket.setReuseAddress(true);

        ServerRunnable serverRunnable = createServerRunnable(timeout);
        this.myThread = new Thread(serverRunnable);
        this.myThread.setDaemon(true);
        this.myThread.setName("NanoHttpd Main Listener");
        this.myThread.start();
        while (!serverRunnable.hasBinded && serverRunnable.bindException == null) {
            try {
                Thread.sleep(10L);
            } catch (Throwable e) {
                // on android this may not be allowed, that's why we
                // catch throwable the wait should be very short because we are
                // just waiting for the bind of the socket
            }
        }
        if (serverRunnable.bindException != null) {
            throw serverRunnable.bindException;
        }
    }

    /**
     * Stop the server.
     */
    public void stop() {
        try {
            safeClose(this.myServerSocket);
            this.asyncRunner.closeAll();
            if (this.myThread != null) {
                this.myThread.join();
            }
        } catch (Exception e) {
            NanoHTTPD.LOG.log(Level.SEVERE, "Could not stop all connections", e);
        }
    }

    public final boolean wasStarted() {
        return this.myServerSocket != null && this.myThread != null;
    }
}