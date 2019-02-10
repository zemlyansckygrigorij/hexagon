
import com.hexagonkt.http.httpDate
import com.hexagonkt.http.server.Server
import com.hexagonkt.http.server.jetty.JettyServletAdapter

import java.time.LocalDateTime.now

val server: Server = Server(JettyServletAdapter()) {
    before {
        response.setHeader("Server", "Servlet/3.1")
        response.setHeader("Transfer-Encoding", "chunked")
        response.setHeader("Date", httpDate(now()))
    }

    get("/text") { ok("Hello, World!", "text/plain") }
}

/**
 * Start the service from the command line.
 */
fun main() {
    server.run()
}
