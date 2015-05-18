package novajoy.crawler;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.logging.Logger;

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.ParsingFeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import novajoy.util.db.JdbcManager;
import novajoy.util.logger.Loggers;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HTTP;

public class Crawler extends Thread {
    private final JdbcManager dbManager;
    private final long sleepMillis;
    private static Logger log = Logger.getLogger(Crawler.class.getName());//new Loggers().getCrawlerLogger();

    public Crawler(String name, int sleepmin, JdbcManager dbManager) {
        super(name);
        this.sleepMillis = sleepmin * 60 * 1000;
        this.dbManager = dbManager;
    }

    public void run() {
        log.info("Starting crawl thread: " + Thread.currentThread().getName());
        try {
            while (true) {
                process_reqsts();
                log.info("Process: " + Thread.currentThread().getName()
                        + " went to sleep.");
                sleep(sleepMillis);
            }
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            log.warning(e.getMessage());
        }
    }

    private void process_reqsts() {
        try {
            String query = "Select * FROM novajoy_rssfeed WHERE spoiled <> 1";
            String pquery = "UPDATE novajoy_rssfeed  SET pubDate = ? WHERE id = ?";

            Statement stmt = dbManager.createStatement();
            PreparedStatement ps = dbManager.createPreparedStatement(pquery);

            String squery = "UPDATE novajoy_rssfeed  SET spoiled = 1 WHERE id = ?";
            PreparedStatement spoiled_statement = dbManager
                    .createPreparedStatement(squery);

            ResultSet rs = stmt.executeQuery(query);
            String addr;
            int items_count = 0;
            while (rs.next()) {
                addr = rs.getString(2);
                log.info("Crawling from: " + addr);

                SyndFeed feed = null;
                try {
                    feed = readfeed(addr);
                } catch (ParsingFeedException e1) {
                    log.warning(e1.getMessage());
                    log.warning("Rssfeed with (id = " + rs.getString(1)
                            + ") is spoiled");
                    spoiled_statement.setLong(1,
                            Long.parseLong(rs.getString(1)));
                    spoiled_statement.executeUpdate();
                    continue;
                }

                for (Iterator i = feed.getEntries().iterator(); i.hasNext(); ) {
                    if (insert_item((SyndEntry) i.next(),
                            Long.parseLong(rs.getString(1))))
                        ++items_count;
                }

                Date time = feed.getPublishedDate();
                if (time == null)
                    time = Calendar.getInstance().getTime();

                ps.setTimestamp(1, new java.sql.Timestamp(time.getTime()));
                ps.setLong(2, Long.parseLong(rs.getString(1)));
                ps.executeUpdate();
                log.info(items_count
                        + " items added.\nCrawling and updating finished on: "
                        + addr);
                items_count = 0;
            }
        } catch (SQLException e) {
            log.warning(e.getMessage());
            // Could not find the database driver
        } catch (Exception e) {
            log.warning(e.getMessage());
            // Could not connect to the database
        }
    }

    private String sendPost(String url) throws Exception {

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        String urlParameters = "sn=C02G8416DRJM&cn=&locale=&caller=&num=12345";

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_16));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        return response.toString();

    }

    public String f(String url) {
        try {
            HttpClient client = HttpClientBuilder.create().build();
            client.getParams().setParameter("http.protocol.version", HttpVersion.HTTP_1_1);
            client.getParams().setParameter("http.protocol.content-charset", "UTF-8");
            HttpGet request = new HttpGet(url);

            // add request header
//        request.addHeader("User-Agent", USER_AGENT);
            HttpResponse response = client.execute(request);

            System.out.println("Response Code : "
                    + response.getStatusLine().getStatusCode());

            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent(),"UTF-8"));

            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = rd.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private SyndFeed readfeed(String addr) throws Exception {
        XmlReader reader = null;
        SyndFeed feed1 = null;
        try {

            String s = f(addr);
            System.out.println(s);
            InputStream stream = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
            log.info(s);
            reader = new XmlReader(stream);
            feed1 = new SyndFeedInput().build(reader);
        } finally {
            if (reader != null)
                reader.close();
        }
        return feed1;
    }

    private boolean insert_item(SyndEntry entry, long feedid)
            throws SQLException {

        if (check_already_in(entry.getLink())) {
            // log.info("Entry with link: " + entry.getLink() +
            // " already exists.");
            return false;
        }

        String pquery = "INSERT INTO novajoy_rssitem (rssfeed_id, title, description, link, author, pubDate) VALUES(?,?,?,?,?,?)";
        PreparedStatement ps = dbManager.createPreparedStatement(pquery);

        ps.setLong(1, feedid);
        ps.setString(2, entry.getTitle());
        ps.setString(3, entry.getDescription().getValue());
        ps.setString(4, entry.getLink());
        ps.setString(5, entry.getAuthor());
        ps.setTimestamp(6, new java.sql.Timestamp(entry.getPublishedDate()
                .getTime()));
        ps.executeUpdate();
        return true;
    }

    private boolean check_already_in(String link) throws SQLException {
        String pquery = "Select * FROM novajoy_rssitem WHERE link = ?";
        PreparedStatement ps = dbManager.createPreparedStatement(pquery);
        ps.setString(1, link);
        return ps.executeQuery().next();
    }
}
