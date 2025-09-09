1. Overview

This is a simple multi-threaded web server that serves static files, handles login requests, and processes file uploads. The server is implemented in Java and designed to handle multiple clients simultaneously using a thread pool. It listens for incoming connections on a specified port, processes HTTP requests, and responds accordingly based on the type of request received.

2. How the Code Works

How the Code Works
The project consists of two main classes:

Main.java: This is the entry point of the application. It reads command-line arguments to determine the port number and the public directory, then initializes and starts an instance of SimpleWebServer.

SimpleWebServer.java: This class contains the core functionality of the web server. It performs several key tasks:

Listening for Client Connections: The server opens a socket on the specified port and continuously waits for incoming client connections. Each connection is handed off to a worker thread from the thread pool, allowing multiple requests to be handled concurrently.

Parsing HTTP Requests: The server reads and processes HTTP request headers to determine the request type (GET or POST) and the requested resource path.
Serving Static Files: For GET requests, the server retrieves and returns the requested file from the public directory. If the file does not exist, it responds with a "404 Not Found" error.

Handling Login Requests: For POST requests to /login, the server reads user credentials from public/login.txt and verifies them against the submitted username and password. If authentication succeeds, the user is redirected to an upload page. Otherwise, an error message is returned.

Processing File Uploads: When a client uploads a file via a POST request to /upload, the server extracts the file data and saves it to the public/uploads/ directory. The filename is sanitized to prevent security issues, and a response is sent back to confirm the upload.

Multi-Threading for Concurrency: A thread pool ensures that multiple clients can be handled simultaneously without blocking the main server thread. This improves performance and responsiveness under high traffic.


3. How to Compile and Run the Web Server

Step 1: Compile the Code

Open a terminal and navigate to the project root directory. Run the following command to compile the Java source files:

"javac -d bin src/main/java/webserver/*.java"

This will compile the Java files and place the output in the bin/ directory.

Step 2: Run the Server

Once compiled, start the server with:

"java --enable-preview -cp bin webserver.Main 2800 public"

Explanation of Command:

--enable-preview allows using preview features of Java.

-cp bin sets the classpath to the compiled files.

webserver.Main is the entry point of the application.

2800 is the port number the server will run on.

public is the directory from which files will be served.


4. How to Access the Web Server

After starting the server, open a browser and go to:

http://localhost:2800/

This will serve the index.html file from the public directory.

5. Features

Serves static files (HTML, PNG, etc.) from the public directory.

Handles simple login requests (/login).

Supports file uploads (/upload).

Multi-threaded to handle multiple client requests.