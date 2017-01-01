## About the Project

Lo\*HTTP stands for Lower, or Local HTTP — small, robust and observable implementation
of HTTP protocol parse and response server. «Is it not too strange to create own HTTP
server in 2016s?» — one may ask. The answer is that it's not a server. It's not like
a full-scale Apache HTTP daemon, or effective event-driven NGINX, or Java Servlet
container like Tomcat, or it's Java EE superset. When I needed I was not able to find
something like Lo\*HTTP implemented in Java of that quality. Let's see the details…


### Java with No Dependencies

The server and the interfaces are written on Java and may be compiled for 1.5 version.
The tests are of 1.8 version, but the demo application for Android is of 1.7 one.

The are no dependencies on any libraries, only pure Java, namely `java.*` subset.
HTTP parsers are hand-written state machines.


### Observable, Small, and Simple

To write small and simple server program — is not a big deal in our days. With a few
lines on NodeJS you may create almost everything. (Consider this statement a joke!)
But these lines transitively borrow millions of lines of dependent libraries.

For strict secutiry reasons, when you have to validate the program on the backdoors,
every additional library imposes a risk to eliminate. When you run Java program, you
may not avoid Java SE library or it's analogues, such as for Android. These libraries
are considered to be safe. With Lo\*HTTP, you check only `net.java.lohttp` package,
and this is all.


### Performance in Mind

With it's tiny size, Lo\*HTTP is considerable effective in the memory usage. It takes
streams constructed of 512 byte buffers from simple weak pool. It never allocates huge
continuous arrays of bytes. It's able to write UTF-8 encoded strings in small chunks.
This utility stuff is in `Support` class.

When parsing the HTTP request the server copies into the chuncked memory only the
request line and the headers, but never the data. Then it creates strings for each
URL parameter and the headers. The parsers work with memory buffers, not strings.


### Configurable

Every noticeable aspect of the server is configurable. Check `Setup` class for the
options. There are common adapter address and TCP port, but also server socket backlog,
socket timeout, HTTP header limit, and threads pool as abstract `Executor`.

Call to `Setup.setPool()` allows to create standard `ThreadPoolExecutor`. The default
is a pool of two threads: one for the server socket, and one worker. With a small
number of workers, you still able to serve multiple concurrent requests by increasing
the requests queue length giving it the same size as of server socket backlong — the
wait queue of TCP socket.


### Raw Enough

The library defines own interfaces for the request and the response. All supporting
interfaces are effectively functional, but not marked with `@FunctionalInterface`
to be compatible with Java 1.5.

There is no predefined request execution scheme. You need to assign to the setup
single invocation point: `Callback` taking three arguments: `Request`, `Response`,
and `Socket`. The request object is filled for you, but you are not obligated to
write to the response, not to the socket. This makes it raw as iron.

Still, the implementation is kind enouch to decode URL encoded body of a POST
request into the parameters, using `Post.decode()` — clear and simple thing
that Servlets do not provide.

And, there are no HTTP sessions, at all! As this are a malicious nasty things that
tend to unexpected memory leaks. Provide your own headers for this.


### Embed It!

The primary goal of this server is to embed it into applications running on small
devices, such as Android phones. This project comes with a sample where a `WebView`
requests local server for static files of index HTML page, jQuery and AngularJS.

This targets the same goal as for Embeddy project of mine. But Embeddy is for complex
local server applications as it's build on top of OSGi, Jetty, Spring frameworks,
and server-side JavaScripting on Nashorn engine.


Lo\*HTTP is still competitive here. Modern web applications use concept of a single
page with static web client resources communicating with the server by sending and
receiving JSON documents. Lo\*HTTP is reach enougn for that.


### And extend!

The design of the project classes is so to make it possible and simple to extend or
rewrite every point of the implementation. Try your own features! But not come so far
to build second Tomcat in the result! Keep in mind the cornerstone aspects.


### Android Build

Lo\*HTTP is not provided as JAR library. You need to include it on the sources level.
But there is pre-build `lohttp-debug.apk` application available to download.

This application is a copy of `TestWeb` class with Android specific changes. Static
files are saved as assets, not as Java resources.

When main activity starts it creates the server and requests the embedded web server
`WebView` to open `127.0.0.1` on a port starting from `8080`. When the activity is
passivated, the server is destroyed. This makes no overwhelm as the server starts
almost instantly on a hot threads pool (that is not destroyed).

Type `tester@gmail.com` as the login and `password` to see the welcome sentence.
The web implementation is based on Anger library for AngularJS that is a separated
concept project of mine to come in the close future.


### The Tests

The project is shipped with three tests. There are not of JUnit, but direct entry
points of Java.

`TestStreams` takes two classes from `Support` wrapper: chunked `BytesStream`, and
`CharBytes` input to write a string to UTF-8 bytes not creating continuous bytes
array with `String.getBytes('UTF-8')`.

`TestLowHat` stands for the HTTP server of the project. It's primary method is
massive concurrent requests.

`TestWeb` simply runs the local server on `8080` port to serve the same sample
web application as of Android demo.


**Thanks for your considerations, Baukin Anton.**