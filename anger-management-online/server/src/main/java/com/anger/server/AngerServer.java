// file: src/main/java/com/anger/server/AngerServer.java
package com.anger.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AngerServer {

  public static void main(String[] args) throws Exception {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
    server.createContext("/join", new JoinHandler());
    server.createContext("/move", new MoveHandler());
    server.createContext("/action", new ActionHandler());
    server.createContext("/events", new EventsHandler());
    server.setExecutor(null);
    System.out.println("Server started on port " + port);
    server.start();
  }

  // ==== Simple models/state ====
  static class PlayerState {
    String name;
    int score = 0;
    int anger = 50;
    int satisfaction = 25;
    int confidence = 0;
    PlayerState(String n){ name = n; }
  }
  static class Room {
    String id;
    String p1Name = "Player 1";
    String p2Name = "Player 2";
    PlayerState p1 = new PlayerState(p1Name);
    PlayerState p2 = new PlayerState(p2Name);
    String p1Move = "";
    String p2Move = "";
    int round = 1;
    // SSE subscribers
    List<SseClient> clients = Collections.synchronizedList(new ArrayList<SseClient>());
    // Winner allowed to act
    String pendingActionFor = null; // "P1" or "P2" or null
  }
  static class SseClient {
    HttpExchange exchange;
    OutputStream os;
  }

  static Map<String, Room> rooms = new ConcurrentHashMap<>();

  // ==== Utilities ====
  static Room room(String id) {
    return rooms.computeIfAbsent(id, k -> { Room r = new Room(); r.id = id; return r; });
  }

  static void respond(HttpExchange ex, int code, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    ex.sendResponseHeaders(code, bytes.length);
    try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
  }

  static String readBody(HttpExchange ex) throws IOException {
    try (InputStream is = ex.getRequestBody()) {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      byte[] buf = new byte[8192];
      int read;
      while ((read = is.read(buf)) != -1) {
        baos.write(buf, 0, read);
      }
      return new String(baos.toByteArray(), StandardCharsets.UTF_8);
    }
  }

  static void broadcast(Room r, String eventType, String jsonPayload) {
    synchronized (r.clients) {
      Iterator<SseClient> it = r.clients.iterator();
      while (it.hasNext()) {
        SseClient c = it.next();
        try {
          String msg = "event: " + eventType + "\n" + "data: " + jsonPayload + "\n\n";
          c.os.write(msg.getBytes(StandardCharsets.UTF_8));
          c.os.flush();
        } catch (IOException e) {
          try { c.exchange.close(); } catch (Exception ignore) {}
          it.remove();
        }
      }
    }
  }

  static int clamp(int v){ return Math.max(0, Math.min(100, v)); }

  static String determineWinner(String p1, String p2) {
    if (p1.equals(p2)) return "DRAW";
    if ((p1.equals("rock") && p2.equals("scissors")) ||
        (p1.equals("paper") && p2.equals("rock")) ||
        (p1.equals("scissors") && p2.equals("paper"))) return "P1_WIN";
    return "P2_WIN";
  }

  static String toJsonState(Room r, Map<String, Object> extras) {
    StringBuilder sb = new StringBuilder();
    sb.append("{");
    sb.append("\"round\":").append(r.round).append(",");
    sb.append("\"p1\":{\"name\":\"").append(r.p1.name).append("\",\"score\":").append(r.p1.score)
      .append(",\"anger\":").append(r.p1.anger).append(",\"satisfaction\":").append(r.p1.satisfaction)
      .append(",\"confidence\":").append(r.p1.confidence).append("},");
    sb.append("\"p2\":{\"name\":\"").append(r.p2.name).append("\",\"score\":").append(r.p2.score)
      .append(",\"anger\":").append(r.p2.anger).append(",\"satisfaction\":").append(r.p2.satisfaction)
      .append(",\"confidence\":").append(r.p2.confidence).append("}");
    if (extras != null && !extras.isEmpty()) {
      sb.append(",");
      int i=0;
      for (Map.Entry<String,Object> e : extras.entrySet()) {
        if (i++>0) sb.append(",");
        sb.append("\"").append(e.getKey()).append("\":");
        Object v = e.getValue();
        if (v == null) sb.append("null");
        else if (v instanceof Number || v instanceof Boolean) sb.append(v.toString());
        else sb.append("\"").append(v.toString().replace("\"","\\\"")).append("\"");
      }
    }
    sb.append("}");
    return sb.toString();
  }

  // ==== Handlers ====
  static class JoinHandler implements HttpHandler {
    @Override public void handle(HttpExchange ex) throws IOException {
      if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"method\"}"); return; }
      String q = ex.getRequestURI().getQuery();
      Map<String,String> params = parseQuery(q);
      String rid = params.getOrDefault("room", "default");
      String name = params.getOrDefault("name", "");
      Room r = room(rid);
      if ("Player 1".equalsIgnoreCase(name)) r.p1.name = "Player 1";
      else if ("Player 2".equalsIgnoreCase(name)) r.p2.name = "Player 2";
      Map<String,Object> extras = new HashMap<String,Object>();
      extras.put("message", "joined");
      String state = toJsonState(r, extras);
      respond(ex, 200, state);
      broadcast(r, "state", state);
    }
  }

  static class MoveHandler implements HttpHandler {
    @Override public void handle(HttpExchange ex) throws IOException {
      if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"method\"}"); return; }
      String body = readBody(ex);
      Map<String,String> m = parseJson(body);
      String rid = m.get("room"); String player = m.get("player"); String move = m.get("move");
      Room r = room(rid);
      if ("P1".equals(player) && r.p1Move.isEmpty()) r.p1Move = move;
      else if ("P2".equals(player) && r.p2Move.isEmpty()) r.p2Move = move;

      String result = null;
      if (!r.p1Move.isEmpty() && !r.p2Move.isEmpty()) {
        result = determineWinner(r.p1Move, r.p2Move);
        r.pendingActionFor = result.equals("P1_WIN") ? "P1" : result.equals("P2_WIN") ? "P2" : null;
      }

      Map<String,Object> extras = new HashMap<String,Object>();
      extras.put("p1Move", r.p1Move);
      extras.put("p2Move", r.p2Move);
      extras.put("result", result == null ? "" : result);
      extras.put("pendingActionFor", r.pendingActionFor == null ? "" : r.pendingActionFor);

      String payload = toJsonState(r, extras);
      respond(ex, 200, "{\"ok\":true}");
      broadcast(r, "state", payload);
    }
  }

  static class ActionHandler implements HttpHandler {
    @Override public void handle(HttpExchange ex) throws IOException {
      if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"method\"}"); return; }
      String body = readBody(ex);
      Map<String,String> m = parseJson(body);
      String rid = m.get("room"); String player = m.get("player"); String action = m.get("action");
      Room r = room(rid);

      if (r.pendingActionFor == null || !r.pendingActionFor.equals(player)) {
        respond(ex, 400, "{\"error\":\"not-your-turn\"}");
        return;
      }

      PlayerState winner = "P1".equals(player) ? r.p1 : r.p2;
      PlayerState loser  = "P1".equals(player) ? r.p2 : r.p1;

      winner.confidence = clamp(winner.confidence + 10);
      winner.satisfaction = clamp(winner.satisfaction + 5);
      winner.anger = clamp(winner.anger - 10);

      loser.confidence = clamp(loser.confidence - 10);
      loser.satisfaction = clamp(loser.satisfaction - 5);
      loser.anger = clamp(loser.anger + 10);

      int additionalScore = (int) Math.ceil(winner.anger * 0.05)
                          - (int) Math.ceil(winner.satisfaction * 0.025)
                          - (int) Math.ceil(winner.confidence * 0.01);
      int base;
      switch (action) {
        case "Slap":
          winner.satisfaction = clamp(winner.satisfaction + 15);
          loser.confidence = clamp(loser.confidence - 15);
          base = 1 + additionalScore;
          break;
        case "Punch":
          winner.confidence = clamp(winner.confidence + 10);
          loser.anger = clamp(loser.anger + 15);
          loser.satisfaction = clamp(loser.satisfaction - 10);
          base = 2 + additionalScore;
          break;
        case "Kick":
          loser.anger = clamp(loser.anger + 25);
          loser.satisfaction = clamp(loser.satisfaction - 10);
          additionalScore = (int) Math.ceil(winner.anger * 0.035)
                          - (int) Math.ceil(winner.satisfaction * 0.025)
                          - (int) Math.ceil(winner.confidence * 0.01);
          base = 3 + additionalScore;
          break;
        default:
          base = 0;
      }
      winner.score += Math.max(0, base);
      r.round++;
      r.p1Move = ""; r.p2Move = "";
      r.pendingActionFor = null;

      boolean gameOver = (r.p1.score >= 50 || r.p2.score >= 50);
      Map<String,Object> extras = new HashMap<String,Object>();
      extras.put("lastAction", action);
      extras.put("actor", player);
      extras.put("winnerName", winner.name);
      extras.put("gameOver", Boolean.valueOf(gameOver));

      String state = toJsonState(r, extras);
      respond(ex, 200, "{\"ok\":true}");
      broadcast(r, "state", state);
    }
  }

  static class EventsHandler implements HttpHandler {
    @Override public void handle(HttpExchange ex) throws IOException {
      if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { respond(ex, 405, "{\"error\":\"method\"}"); return; }
      Map<String,String> params = parseQuery(ex.getRequestURI().getQuery());
      Room r = room(params.getOrDefault("room", "default"));
      Headers h = ex.getResponseHeaders();
      h.add("Content-Type", "text/event-stream");
      h.add("Cache-Control", "no-cache");
      h.add("Connection", "keep-alive");
      ex.sendResponseHeaders(200, 0); // streaming
      SseClient client = new SseClient();
      client.exchange = ex;
      client.os = ex.getResponseBody();
      r.clients.add(client);

      // Send initial full state
      Map<String,Object> extras = new HashMap<String,Object>();
      extras.put("message", "init");
      String init = toJsonState(r, extras);
      broadcast(r, "state", init);
      // Keep connection open; writes happen via broadcast()
    }
  }

  // ==== Tiny helpers: parsing ====
  static Map<String,String> parseQuery(String q) {
    Map<String,String> out = new HashMap<>();
    if (q == null || q.isEmpty()) return out;
    for (String part : q.split("&")) {
      String[] kv = part.split("=",2);
      out.put(urlDecode(kv[0]), kv.length>1 ? urlDecode(kv[1]) : "");
    }
    return out;
  }
  static String urlDecode(String s) {
    try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e){ return s; }
  }
  static Map<String,String> parseJson(String body){
    // Minimal JSON key:value string parser for {"a":"b","c":"d"} or {"a":"b","n":2}
    Map<String,String> out = new HashMap<>();
    body = body.trim();
    if (body.startsWith("{") && body.endsWith("}")) {
      body = body.substring(1, body.length()-1);
      for (String pair : body.split(",")) {
        String[] kv = pair.split(":",2);
        if (kv.length==2) {
          String k = kv[0].trim().replaceAll("^\"|\"$", "");
          String v = kv[1].trim();
          if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length()-1);
          out.put(k, v);
        }
      }
    }
    return out;
  }
}