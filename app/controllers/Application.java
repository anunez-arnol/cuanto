package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.Logger;
import play.Play;
import play.db.DB;
import play.libs.Json;
import play.mvc.*;

import views.html.*;

import java.io.*;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import static play.libs.F.Promise;

public class Application extends Controller {

    public static Result index() throws FileNotFoundException {
        return ok(index.render("Your new application is ready."));
    }

    public static Result prepare() {
        Connection connection = null;
        try {
            connection = DB.getDataSource().getConnection();

            try {
                /*
                 * {
                 * String sql = "DROP TABLE IF EXISTS expenses";
                 * final PreparedStatement stm = connection.prepareStatement(sql);
                 * stm.execute();
                 * }
                 * {
                 * String sql =
                 * "CREATE TABLE expenses (date timestamp, place varchar, payment varchar, reference varchar, amount varchar, source varchar)"
                 * ;
                 * final PreparedStatement stm = connection.prepareStatement(sql);
                 * stm.execute();
                 * }
                 */
                {
                    String sql = "DELETE FROM expenses";
                    final PreparedStatement stm = connection.prepareStatement(sql);
                    stm.execute();
                }
            } catch (Exception e) {
                Logger.error("error creating table", e);
                e.printStackTrace();
            }
        } catch (Exception e) {
            Logger.error("error getting connection: {}", e.getMessage());
            e.printStackTrace();
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    Logger.error("error disconnecting db");
                }
            }
        }

        return ok("done");
    }

    public static Promise<Result> process() {
        return new Trace<Promise<Result>>(Application.class, "process").trace(trace -> {
            File shFile = Play.application().getFile("external/process.sh");
            File dataFile = Play.application().getFile(
                    "data/" + (new SimpleDateFormat("yyyy-MM-dd-HH").format(Calendar.getInstance().getTime())) + "_data.json");

            try {
                String line;
                Process p = Runtime.getRuntime().exec("sh " + shFile.getAbsolutePath() + " " + dataFile.getAbsolutePath());
                BufferedReader bri = new BufferedReader(new InputStreamReader(p.getInputStream()));
                BufferedReader bre = new BufferedReader(new InputStreamReader(p.getErrorStream()));
                while ((line = bri.readLine()) != null) {
                    Logger.debug("{}", line);
                }
                bri.close();
                while ((line = bre.readLine()) != null) {
                    Logger.debug("{}", line);
                }
                bre.close();
                p.waitFor();
                System.out.println("Done.");
            } catch (Exception err) {
                err.printStackTrace();
            }

            JsonNode node;
            try {
                node = Json.parse(new FileInputStream(dataFile));
            } catch (FileNotFoundException e) {
                Logger.error("cannot open file", e);
                return Promise.pure(internalServerError());
            }

            return Promise.promise(() -> {
                Connection connection = null;
                try {
                    connection = DB.getDataSource().getConnection();

                    for (JsonNode item : node) {
                        /*if ("pending".equals(item.path("status").asText())) {
                            Logger.debug("skipping pending node {}", item);
                            continue;
                        }*/

                        Long itemId = getItemId(connection, item);

                        if (itemId == null) {
                            insert(connection, item);
                        } else {
                            update(connection, itemId, item);
                        }
                    }
                } catch (Exception e) {
                    Logger.error("error getting connection: {}", e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (connection != null) {
                        try {
                            connection.close();
                        } catch (SQLException e) {
                            Logger.error("error disconnecting db");
                        }
                    }
                }

                Logger.debug("done");
                return ok();
            });

        });
    }

    private static Long getItemId(Connection connection, JsonNode item) throws SQLException, ParseException {
        String sql = "SELECT * FROM expenses WHERE amount = ?";
        final PreparedStatement stm = connection.prepareStatement(sql);

        stm.setString(1, item.path("amount").asText());

        ResultSet resultSet = stm.executeQuery();

        while (resultSet.next()) {
            ObjectNode expenseNode = getExpenseNode(resultSet);

            long itemMillis = getMillis(item.path("date").asText());
            Logger.debug("itemMillis:{}", itemMillis);
            long dbMillis = expenseNode.path("date").asLong();
            Logger.debug("dbMillis:{}", dbMillis);

            if(itemMillis != dbMillis) {
                continue;
            }

            return expenseNode.get("id").asLong();
        }

        return null;
    }

    private static boolean insert(Connection connection, JsonNode item) {
        Logger.debug("inserting node {}", item);
        try {
            String sql = "INSERT INTO expenses (date, place, payment, reference, amount, source, status) VALUES (?,?,?,?,?,?,?)";
            final PreparedStatement stm = connection.prepareStatement(sql);

            long millis = getMillis(item.path("date").asText());
            stm.setDate(1, new Date(millis));
            stm.setString(2, item.path("place").asText());
            stm.setString(3, item.path("payment").asText());
            stm.setString(4, item.path("reference").asText());
            stm.setString(5, item.path("amount").asText());
            stm.setString(6, item.path("source").asText());
            stm.setString(7, item.path("status").asText());
            stm.execute();

            return true;
        } catch (Exception e) {
            Logger.error("error inserting node: {}, error: {}", item, e.getMessage());
            return false;
        }
    }

    private static boolean update(Connection connection, long itemId, JsonNode item) {
        Logger.debug("updating node {}", item);
        try {
            String sql = "UPDATE expenses SET date=?, place=?, payment=?, reference=?, amount=?, source=?, status=? WHERE id=?";
            final PreparedStatement stm = connection.prepareStatement(sql);

            long millis = getMillis(item.path("date").asText());
            stm.setDate(1, new Date(millis));
            stm.setString(2, item.path("place").asText());
            stm.setString(3, item.path("payment").asText());
            stm.setString(4, item.path("reference").asText());
            stm.setString(5, item.path("amount").asText());
            stm.setString(6, item.path("source").asText());
            stm.setString(7, item.path("status").asText());
            stm.setLong(8, itemId);
            stm.execute();

            return true;
        } catch (Exception e) {
            Logger.error("error updating node: {}, error: {}", item, e.getMessage());
            return false;
        }
    }

    private static long getMillis(String date) throws ParseException {
        return new SimpleDateFormat("yyyy-MM-dd").parse(date).getTime();
    }

    public static Result expenses() {
        return new Trace<Result>(Application.class, "expenses").trace(trace -> {
            ArrayNode items = JsonNodeFactory.instance.arrayNode();

            Connection connection = null;
            try {
                connection = DB.getDataSource().getConnection();
                try {
                    String sql = "SELECT * FROM expenses";
                    final PreparedStatement stm = connection.prepareStatement(sql);

                    ResultSet query = stm.executeQuery();

                    while (query.next()) {
                        items.add(getExpenseNode(query));
                    }

                    return ok(items);
                } catch (Exception e) {
                    Logger.error("error selecting expenses");
                    e.printStackTrace();
                }
            } catch (Exception e) {
                Logger.error("error getting connection: {}", e.getMessage());
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        Logger.error("error disconnecting db");
                    }
                }
            }

            return ok(items);
        });
    }

    private static ObjectNode getExpenseNode(ResultSet query) throws SQLException {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("id", query.getLong("id"));
        node.put("date", query.getDate("date").getTime());
        node.put("place", query.getString("place"));
        node.put("payment", query.getString("payment"));
        node.put("reference", query.getString("reference"));
        node.put("amount", query.getString("amount"));
        node.put("source", query.getString("source"));
        return node;
    }
}
