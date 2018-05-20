import java.io.IOException;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;

import javax.servlet.http.HttpServlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class GuestbookApp extends HttpServlet
{
    public static Connection connectToDB() throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:demo.db");
    }

    public static void setupDB() throws Exception {
        Connection connection = connectToDB();
        Statement statement = connection.createStatement();
        statement.executeUpdate(
            "CREATE TABLE IF NOT EXISTS posts (\n"+ 
            "  public_content TEXT,\n" +
            "  private_content TEXT,\n" +
            "  post_time TEXT\n" +
            ")"
        );
        connection.close();
    }

    public static void main( String[] args ) throws Exception
    {
        Server server = new Server(10103);
        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);

        setupDB();

        handler.addServletWithMapping(GuestbookApp.class, "/*");

        server.start();
        server.join();
    }

    private void startHtml(PrintWriter out) {
        out.println("<!DOCTYPE html>");
        out.println("<style type='text/css'>");
        out.println("body { background: white; color: black; }");
        out.println("table { border-collapse: collapse }");
        out.println("table tr td { border-top: 2px solid black; border-bottom: 2px solid black; }");
        out.println("</style>");
    }

    private void showPosts(PrintWriter out, boolean includePrivate) {
        try {
            Connection connection = connectToDB();
            Statement statement = connection.createStatement();
            ResultSet results = statement.executeQuery(
                "SELECT * FROM posts ORDER BY post_time"
            );
            int numPosts = 0;
            out.println("<p>Guestbook posts:");
            out.println("<table>");
            while (results.next()) {
                out.println("<tr><td>At " + results.getString("post_time") + ":</td><td>");
                out.println(results.getString("public_content"));
                if (includePrivate) {
                    out.println("<hr>" + results.getString("private_content"));
                }
                out.println("</td></tr>");
                numPosts += 1;
            }
            out.println("</table>");
            connection.close();
       } catch (SQLException e) {
            out.println("<p>Exception encountered: " + e.getMessage() + "<pre>");
            e.printStackTrace(out);
            out.println("</pre>");
       }
    }

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);
        PrintWriter out = response.getWriter();
        startHtml(out);
        out.println("<form method=post>");
        out.println("Submit a new post:<br><b>Public part:</b><br>");
        out.println("<textarea name='public' rows=10 cols=70></textarea>");
        out.println("<br>");
        out.println("<br><b>Private part:</b><br>");
        out.println("<textarea name='private' rows=10 cols=70></textarea>");
        out.println("<br><input type=submit value=Submit>");
        out.println("</form>");
        out.println("<hr>");
        out.println("<form method=post>");
        out.println("Enter password to view private posts: <input type=password name=password>");
        out.println("<input type=submit value=View>");
        out.println("</form>");
        out.println("<hr>");
        showPosts(out, false);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws ServletException, IOException {
        Map<String, String[]> parameters = request.getParameterMap();
        if (parameters.containsKey("password")) {
            if (parameters.get("password")[0].equals("secret")) {
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();
                startHtml(out);
                out.println("<a href=/>back to normal view</a><br><hr>");
                showPosts(out, true);
            } else {
                response.setContentType("text/html");
                response.setStatus(HttpServletResponse.SC_OK);
                PrintWriter out = response.getWriter();
                startHtml(out);
                out.println("<a href=/>back to normal view</a><br><hr>");
                out.println("<p><strong>Wrong password.</strong></p>");
            }
        } else if (parameters.containsKey("public")) {
            String publicText = parameters.get("public")[0];
            String privateText = parameters.get("private")[0];
            String postTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            try {
                Connection connection = connectToDB();
                Statement statement = connection.createStatement();
                statement.executeUpdate(
                    "INSERT INTO posts (public_content, private_content, post_time) " +
                    "VALUES (\"" + publicText + "\", \"" + privateText +
                    "\", \"" + postTime + "\")"
                );
                connection.close();
                response.sendRedirect("/");
           } catch (SQLException e) {
                response.setContentType("text/html");
                PrintWriter out = response.getWriter();
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                startHtml(out);
                out.println("<p>Exception encountered: " + e.getMessage() + "<pre>");
                e.printStackTrace(out);
                out.println("</pre>");
           }
        } else {
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            PrintWriter out = response.getWriter();
            startHtml(out);
            out.println("<p>unknown request: " + parameters);
        }
    }
}
