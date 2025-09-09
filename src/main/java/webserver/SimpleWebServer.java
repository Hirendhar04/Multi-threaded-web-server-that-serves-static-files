package webserver;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * A simple multi-threaded web server that can serve static files, handle login requests, 
 * and process file uploads.
 */
public class SimpleWebServer {
    private final int port;
    private final File publicDirectory;
    private final ExecutorService threadPool;


    /**
     * Constructs a SimpleWebServer.
     * @param port The port number on which the server will listen.
     * @param publicDir The directory from which files will be served.
     */

    public SimpleWebServer(int port, String publicDir) {
        this.port = port;
        this.publicDirectory = new File(publicDir);
        this.threadPool = Executors.newFixedThreadPool(10);
    }

    /**
     * Starts the web server, accepting client connections and handling requests.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port);
            System.out.println("Serving files from: " + publicDirectory.getAbsolutePath());

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Handles an incoming client request.
     * @param clientSocket The socket connected to the client.
     */
    private void handleClient(Socket clientSocket) {
        try {
            // Use a PushbackInputStream to allow low-level reading
            InputStream rawIn = clientSocket.getInputStream();
            PushbackInputStream pbis = new PushbackInputStream(rawIn, 8192);
            OutputStream out = clientSocket.getOutputStream();
            
            // Read the request line manually (in ISO-8859-1 to preserve raw bytes)
            String requestLine = readLine(pbis);
            if (requestLine == null || requestLine.isEmpty()) return;
            
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            
            String method = parts[0];
            String filePath = parts[1];

            if (method.equals("GET")) {
                System.out.println("Received GET request: " + filePath);
                if (filePath.equals("/")) {
                    filePath = "/index.html";
                }
                handleGetRequest(out, filePath);
            } else if (method.equals("POST")) {
                if (filePath.equals("/login")) {
                    // For simple text-based login, wrap the stream in a reader.
                    BufferedReader in = new BufferedReader(new InputStreamReader(pbis, "ISO-8859-1"));
                    handleLoginRequest(in, out);
                } else if (filePath.equals("/upload")) {
                    // For file uploads, use the binary-safe method.
                    handleFileUploadRequest(pbis, out);
                }
            }
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ex) {
                // Ignore close exception
            }
        }
    }

    /**
     * Reads a single line from the input stream.
     *
     * @param in The input stream.
     * @return The line read as a String.
     * @throws IOException If an I/O error occurs.
     */
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int c;
        boolean seenCR = false;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                seenCR = true;
            } else if (c == '\n' && seenCR) {
                break;
            } else {
                if (seenCR) {
                    baos.write('\r');
                    seenCR = false;
                }
                baos.write(c);
            }
        }
        if (baos.size() == 0 && c == -1) {
            return null;
        }
        return baos.toString("ISO-8859-1");
    }
    
    /**
     * Handles a GET request and sends the requested file.
     *
     * @param out      The output stream to write the response.
     * @param filePath The requested file path.
     * @throws IOException If an I/O error occurs.
     */
    private void handleGetRequest(OutputStream out, String filePath) throws IOException {
        filePath = filePath.replace("..", ""); // Prevent directory traversal
        File requestedFile = new File(publicDirectory, filePath);

        if (!requestedFile.exists() || !requestedFile.getCanonicalPath().startsWith(publicDirectory.getCanonicalPath())) {
            sendResponse(out, "HTTP/1.1 404 Not Found\r\n\r\n", null);
            return;
        }

        String contentType = getContentType(requestedFile);
        sendResponse(out, "HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\n\r\n", requestedFile);
    }

    /**
     * Handles a login request by reading and validating credentials.
     *
     * @param in  The input reader containing request data.
     * @param out The output stream to write the response.
     * @throws IOException If an I/O error occurs.
     */
    private void handleLoginRequest(BufferedReader in, OutputStream out) throws IOException {
        String body = readRequestBody(in);
        System.out.println("Received POST body: " + body); // Debugging

        Map<String, String> formData = parseFormData(body);

        if (!formData.containsKey("username") || !formData.containsKey("password")) {
            sendResponse(out, "HTTP/1.1 400 Bad Request\r\n\r\nMissing username or password", null);
            return;
        }

        String username = formData.get("username");
        String password = formData.get("password");

        if (validateLogin(username, password)) {
            sendResponse(out, "HTTP/1.1 302 Found\r\nLocation: /upload.html\r\n\r\n", null);
        } else {
            sendResponse(out, "HTTP/1.1 401 Unauthorized\r\n\r\nInvalid Credentials", null);
        }
    }

    /**
     * Validates a username and password by reading them from a file.
     *
     * @param username The username to check.
     * @param password The password to check.
     * @return True if credentials match, false otherwise.
     */
    private boolean validateLogin(String username, String password) {
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(publicDirectory, "login.txt")))) {
            String line;
            String fileUsername = null;
            String filePassword = null;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    if (parts[0].trim().equals("username")) {
                        fileUsername = parts[1].trim();
                    } else if (parts[0].trim().equals("password")) {
                        filePassword = parts[1].trim();
                    }
                }
            }
            return fileUsername != null && filePassword != null && fileUsername.equals(username) && filePassword.equals(password);
        } catch (IOException e) {
            System.err.println("Error reading login file: " + e.getMessage());
        }
        return false;
    }

    /**
     * Handles a file upload request.
     *
     * @param in  The input stream containing file data.
     * @param out The output stream to write the response.
     * @throws IOException If an I/O error occurs.
     */
    private void handleFileUploadRequest(InputStream in, OutputStream out) throws IOException {
        String boundary = null;
        int contentLength = -1;
        String line;
    
        // Read headers to get boundary and content length
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Type: multipart/form-data;")) {
                int idx = line.indexOf("boundary=");
                if (idx != -1) {
                    boundary = line.substring(idx + "boundary=".length()).trim();
                    if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                        boundary = boundary.substring(1, boundary.length() - 1);
                    }
                }
            } else if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
        }
    
        if (boundary == null || contentLength <= 0) {
            sendResponse(out, "HTTP/1.1 400 Bad Request\r\n\r\nInvalid request", null);
            return;
        }
    
        byte[] body = new byte[contentLength];
        int totalRead = 0;
        while (totalRead < contentLength) {
            int read = in.read(body, totalRead, contentLength - totalRead);
            if (read == -1) break;
            totalRead += read;
        }
    
        String bodyStr = new String(body, "ISO-8859-1");
        String boundaryMarker = "--" + boundary;
        String[] parts = bodyStr.split(boundaryMarker);
        String filename = null;
        byte[] fileData = null;
    
        for (String part : parts) {
            if (part.contains("name=\"filename\"")) {
                int headerEnd = part.indexOf("\r\n\r\n");
                if (headerEnd != -1) {
                    filename = part.substring(headerEnd + 4).trim();
                }
            } else if (part.contains("name=\"file\"") && part.contains("filename=")) {
                int headerEndIndex = part.indexOf("\r\n\r\n");
                if (headerEndIndex != -1) {
                    String fileBody = part.substring(headerEndIndex + 4);
                    fileData = fileBody.getBytes("ISO-8859-1");
                }
            }
        }
    
        if (filename == null || fileData == null) {
            sendResponse(out, "HTTP/1.1 400 Bad Request\r\n\r\nFailed to extract file or filename", null);
            return;
        }
    
        filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    
        File uploadDir = new File(publicDirectory, "uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    
        File uploadedFile = new File(uploadDir, filename +".png");
        try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
            fos.write(fileData);
        }
    
        sendResponse(out, "HTTP/1.1 200 OK\r\n\r\nFile Uploaded Successfully as " + filename +".png", null);
    }
    
    /**
     * Reads the request body.
     *
     * @param in The input reader.
     * @return The request body as a String.
     * @throws IOException If an I/O error occurs.
     */
    private String readRequestBody(BufferedReader in) throws IOException {
        int contentLength = 0;
        String line;
        // Read headers to find Content-Length
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            if (line.startsWith("Content-Length:")) {
                contentLength = Integer.parseInt(line.split(":")[1].trim());
            }
        }
        char[] buffer = new char[contentLength];
        int bytesRead = in.read(buffer, 0, contentLength);
        return new String(buffer, 0, bytesRead).trim();
    }

    /**
     * Parses form data from a request body.
     *
     * @param body The request body.
     * @return A map of key-value pairs representing form data.
     */
    private Map<String, String> parseFormData(String body) {
        Map<String, String> formData = new HashMap<>();
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                formData.put(keyValue[0], keyValue[1]);
            }
        }
        return formData;
    }

    /**
     * Sends an HTTP response.
     *
     * @param out    The output stream.
     * @param header The response header.
     * @param file   The file to be sent (if applicable).
     * @throws IOException If an I/O error occurs.
     */
    private void sendResponse(OutputStream out, String header, File file) throws IOException {
        out.write(header.getBytes());
        if (file != null) {
            try (FileInputStream fileInput = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fileInput.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        }
        out.flush();
    }

    /**
     * Determines the content type of a file.
     *
     * @param file The file.
     * @return The MIME type.
     */
    private String getContentType(File file) {
        if (file.getName().endsWith(".html")) return "text/html";
        if (file.getName().endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
