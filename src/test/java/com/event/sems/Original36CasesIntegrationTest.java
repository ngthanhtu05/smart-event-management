package com.event.sems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.event.sems.common.TimeUtil;
import com.event.sems.config.DashboardSocketHandler;
import com.event.sems.entity.Venue;
import com.event.sems.repository.*;
import com.event.sems.service.AccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/** Backend checks mapped to the 36 resource cases; does not certify browser/camera behavior. */
@SpringBootTest(
    properties = {
      "spring.config.import=",
      "app.ticket-secret=SEMS_TEST_ONLY_KEY_FOR_ORIGINAL_36_CASES",
      "spring.datasource.url=jdbc:h2:mem:original36;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "logging.level.root=WARN",
      "logging.level.org.hibernate.SQL=WARN",
      "logging.level.org.springframework=WARN"
    })
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class Original36CasesIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired RoleRepository roles;
  @Autowired UserAccountRepository users;
  @Autowired PasswordEncoder passwords;
  @Autowired VenueRepository venues;
  @Autowired TicketRepository tickets;
  @Autowired AccountService accounts;
  @Autowired DataSource dataSource;
  @Autowired DashboardSocketHandler socket;
  static final String COMMENT = "review tiếng Việt, \"rõ ràng\"\nDòng thứ hai";
  int nextGuest;
  final Map<String, MockHttpSession> sessions = new HashMap<>();

  static Stream<String> cases() {
    List<String> ids = new ArrayList<>();
    for (int[] group :
        new int[][] {{13, 4}, {20, 6}, {23, 3}, {29, 8}, {31, 3}, {35, 3}, {42, 4}, {44, 4}})
      for (int i = 1; i <= group[1]; i++) ids.add("UC" + group[0] + "-" + String.format("%02d", i));
    ids.add("INT-01");
    return ids.stream();
  }

  @BeforeAll
  static void matchOriginalDocument() throws Exception {
    try (var in = Files.newInputStream(Path.of("resource/Bang_Test_Case_SEMS.docx"));
        var doc = new XWPFDocument(in)) {
      List<String> documentIds = new ArrayList<>();
      for (var table : doc.getTables())
        for (var row : table.getRows()) {
          if (row.getTableCells().size() < 3) continue;
          String id = row.getCell(2).getText().trim();
          if (id.matches("(?:UC[0-9]{2}|INT)-[0-9]{2}")) documentIds.add(id);
        }
      assertThat(documentIds)
          .hasSize(36)
          .doesNotHaveDuplicates()
          .containsExactlyElementsOf(cases().toList());
    }
  }

  @BeforeEach
  void isolatedData() throws Exception {
    // Refuse cleanup if this test ever points at another database.
    try (var connection = dataSource.getConnection()) {
      assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:original36");
    }
    // H2 only: never use or reset the user's MySQL database.
    for (String t :
        List.of(
            "feedback",
            "checkin_logs",
            "tickets",
            "attendees",
            "ticket_types",
            "agenda_items",
            "speakers",
            "event_staff",
            "event_reviews",
            "events",
            "venues",
            "account_tokens",
            "pending_email_actions",
            "user_roles",
            "users",
            "roles",
            "mail_settings")) db.update("DELETE FROM " + t);
    SampleAccounts.seed(roles, users, passwords);
    nextGuest = 0;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("cases")
  void originalCaseBackend(String id) throws Exception {
    assumeTrue(
        !Set.of("UC23-03", "UC29-07", "UC29-08").contains(id),
        "Requires real SMTP/inbox or phone HTTPS/camera permissions; not simulated as a pass.");
    switch (id) {
      case "UC13-01" -> {
        long v = venue("organizer");
        Map<String, Object> in = input(20);
        in.remove("onlineUrl");
        in.put("venueId", v);
        in.put("description", "Mô tả sự kiện kiểm thử");
        long e = created(in);
        JsonNode detail = getOk("/events/" + e, "organizer");
        assertThat(detail.get("approvalStatus").asText()).isEqualTo("DRAFT");
        assertThat(detail.get("venueId").asLong()).isEqualTo(v);
        for (String key : List.of("title", "description"))
          assertThat(detail.get(key).asText()).as(key).isEqualTo(in.get(key).toString());
        for (String key : List.of("startsAt", "endsAt"))
          assertThat(LocalDateTime.parse(detail.get(key).asText()))
              .as(key)
              .isEqualTo(LocalDateTime.parse(in.get(key).toString()));
        assertThat(detail.get("capacity").asInt()).isEqualTo(20);
        assertThat(
                db.queryForObject(
                    "select quota from ticket_types where event_id=?", Integer.class, e))
            .isEqualTo(20);
        assertThat(
                db.queryForObject(
                    "select count(*) from ticket_types where event_id=? and price=0",
                    Integer.class,
                    e))
            .isEqualTo(1);
        assertThat(httpGet("/public/events/" + e, null).getResponse().getStatus()).isEqualTo(404);
      }
      case "UC13-02" -> {
        Map<String, Object> in = input(20);
        in.put(
            "endsAt",
            LocalDateTime.parse(in.get("startsAt").toString()).plusMinutes(29).toString());
        failureMessage(expectPost("/events", in, "organizer", 400), "ít nhất 30 phút");
        assertThat(db.queryForObject("select count(*) from events", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from ticket_types", Integer.class)).isZero();
      }
      case "UC13-03" -> {
        long v = venue("organizer2");
        Map<String, Object> in = input(20);
        in.remove("onlineUrl");
        in.put("venueId", v);
        var response = expectPost("/events", in, "organizer", 404);
        failureMessage(response, "Không tìm thấy địa điểm");
        assertThat(response.getResponse().getContentAsString()).doesNotContain("QA address");
        assertThat(db.queryForObject("select count(*) from ticket_types", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from events", Integer.class)).isZero();
      }
      case "UC13-04" -> {
        long e = created(input(20));
        JsonNode detail = getOk("/events/" + e, "organizer");
        assertThat(detail.get("approvalStatus").asText()).isEqualTo("DRAFT");
        assertThat(detail.get("onlineUrl").asText()).isEqualTo("https://example.org/test-room");
        assertThat(detail.get("venueId").isNull()).isTrue();
      }
      case "UC20-01" -> {
        long e = event(20);
        String code = register(e, "visitor", "0912345678");
        JsonNode ticket = getOk("/tickets/" + code, "visitor");
        assertThat(ticket.get("status").asText()).isEqualTo("ISSUED");
        assertThat(ticket.get("eventId").asLong()).isEqualTo(e);
        assertThat(ticket.get("attendeeName").asText()).isEqualTo("Guest");
        assertThat(getOk("/tickets/mine", "visitor").get(0).get("code").asText()).isEqualTo(code);
        assertThat(db.queryForObject("select user_id from tickets where code=?", Long.class, code))
            .isEqualTo(users.findByUsernameIgnoreCase("visitor").orElseThrow().getId());
        assertThat(registrationCounts()).containsExactly(1L, 1L);
        assertThat(ticket.get("attendeeEmail").asText()).isEqualTo("visitor@sems.example");
        assertThat(getOk("/tickets/mine", "visitor").size()).isEqualTo(1);
        registered(e, 1);
      }
      case "UC20-02", "UC20-03", "UC20-04" -> {
        long e = event(id.equals("UC20-04") ? 1 : 20);
        register(e, "visitor", "0912345678");
        String who = id.equals("UC20-02") ? "visitor" : "visitor2";
        String phone = id.equals("UC20-03") ? "0912345678" : "0912345679";
        List<Long> before = registrationCounts();
        var result = expectPost("/events/" + e + "/registrations", registration(phone), who, 409);
        String reason =
            switch (id) {
              case "UC20-02" -> "Tài khoản này đã đăng ký";
              case "UC20-03" -> "Email hoặc số điện thoại đã đăng ký";
              default -> "đã hết vé";
            };
        failureMessage(result, reason);
        assertThat(registrationCounts()).isEqualTo(before);
        registered(e, 1);
        if (!who.equals("visitor")) assertThat(getOk("/tickets/mine", who).isEmpty()).isTrue();
        assertThat(db.queryForObject("select count(*) from tickets", Integer.class)).isEqualTo(1);
      }
      case "UC20-05" -> {
        Map<String, Object> in = input(20);
        in.put("status", "CLOSED");
        long e = created(in);
        approve(e);
        failureMessage(
            expectPost(
                "/events/" + e + "/registrations", registration("0912345678"), "visitor", 409),
            "đã đóng đăng ký");
        assertThat(registrationCounts()).containsExactly(0L, 0L);
        registered(e, 0);
      }
      case "UC20-06" -> {
        long e = event(20);
        var a = users.findByUsernameIgnoreCase("visitor").orElseThrow();
        a.setEmailVerified(false);
        users.saveAndFlush(a);
        failureMessage(
            expectPost(
                "/events/" + e + "/registrations", registration("0912345678"), "visitor", 409),
            "xác minh email");
        assertThat(registrationCounts()).containsExactly(0L, 0L);
        registered(e, 0);
      }
      case "UC23-01" -> {
        long e = event(20);
        String code = register(e, "visitor", "0912345678");
        Map<String, Object> before = ticketSnapshot(code);
        for (int i = 0; i < 2; i++) {
          assertThat(getOk("/tickets/" + code, "visitor").get("code").asText()).isEqualTo(code);
          var qr = httpGet("/tickets/" + code + "/qr", "visitor").getResponse();
          assertThat(qr.getStatus()).isEqualTo(200);
          assertThat(qr.getContentType()).startsWith("image/png");
          var image = ImageIO.read(new ByteArrayInputStream(qr.getContentAsByteArray()));
          assertThat(image).isNotNull();
          String decoded =
              new MultiFormatReader()
                  .decode(
                      new BinaryBitmap(
                          new HybridBinarizer(new BufferedImageLuminanceSource(image))))
                  .getText();
          assertThat(decoded).isEqualTo(token(code));
          assertThat(getOk("/tickets/" + code, "visitor").get("eventId").asLong()).isEqualTo(e);
          assertThat(getOk("/tickets/" + code, "visitor").get("attendeeName").asText())
              .isEqualTo("Guest");
        }
        assertThat(ticketSnapshot(code)).isEqualTo(before);
        assertThat(registrationCounts()).containsExactly(1L, 1L);
        registered(e, 1);
      }
      case "UC23-02" -> {
        long e = event(20);
        String code = register(e, "visitor", "0912345678");
        Map<String, Object> before = ticketSnapshot(code);
        for (String suffix : List.of("", "/qr")) {
          var response = httpGet("/tickets/" + code + suffix, "visitor2").getResponse();
          assertThat(response.getStatus()).isEqualTo(404);
          assertThat(response.getContentType()).doesNotStartWith("image/");
          assertThat(response.getContentAsString())
              .doesNotContain(code, "visitor@sems.example", "qrToken");
        }
        assertThat(ticketSnapshot(code)).isEqualTo(before);
      }
      case "UC29-01", "UC29-02", "UC29-03", "UC29-04", "UC29-05", "UC29-06" -> {
        long e = event(20);
        String code = register(e, "visitor", "0912345678");
        String token = token(code);
        String staff = "staff";
        int expected = 200;
        if (id.equals("UC29-02")) {
          check(e, code, "QR", 200);
          expected = 409;
        }
        Long otherEvent = null;
        String otherCode = null;
        if (id.equals("UC29-03")) {
          otherEvent = event(20);
          otherCode = register(otherEvent, "visitor2", "0912345679");
          token = token(otherCode);
          expected = 400;
        }
        if (id.equals("UC29-04")) {
          token = token.substring(0, 5) + (token.charAt(5) == 'A' ? 'B' : 'A') + token.substring(6);
          expected = 400;
        }
        if (id.equals("UC29-05")) {
          staff = "staff2";
          expected = 403;
        }
        if (id.equals("UC29-06")) {
          var start = TimeUtil.now().plusDays(2);
          db.update(
              "update events set starts_at=?,ends_at=? where id=?", start, start.plusHours(2), e);
          expected = 409;
        }
        Map<String, Object> before = ticketSnapshot(code);
        Map<String, Object> otherBefore = otherCode == null ? null : ticketSnapshot(otherCode);
        var response =
            expectPost(
                "/events/" + e + "/check-ins",
                Map.of("method", "QR", "qrToken", token),
                staff,
                expected);
        if (expected == 200) assertCheckinResponse(response, code);
        else {
          String reason =
              switch (id) {
                case "UC29-02" -> "đã check-in lúc";
                case "UC29-03" -> "không thuộc sự kiện";
                case "UC29-04" -> "không hợp lệ hoặc đã bị sửa đổi";
                case "UC29-05" -> "chưa được phân công";
                default -> "khung giờ check-in";
              };
          failureMessage(response, reason);
          assertThat(ticketSnapshot(code)).isEqualTo(before);
          if (id.equals("UC29-02")) {
            var oldTime =
                db.queryForObject(
                        "select checked_in_at from tickets where code=?",
                        java.sql.Timestamp.class,
                        code)
                    .toLocalDateTime();
            failureMessage(
                response, oldTime.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")));
          }
        }
        if (otherEvent != null) {
          assertThat(ticketSnapshot(otherCode)).isEqualTo(otherBefore);
          assertThat(logCount(otherEvent)).isZero();
          assertThat(dashboard(otherEvent).get("checkedIn").asInt()).isZero();
        }
        boolean used = expected == 200 || id.equals("UC29-02");
        assertThat(getOk("/tickets/" + code, "visitor").get("status").asText())
            .isEqualTo(used ? "CHECKED_IN" : "ISSUED");
        assertThat(logCount(e)).isEqualTo(used ? 1 : 0);
        assertThat(dashboard(e).get("checkedIn").asInt()).isEqualTo(used ? 1 : 0);
      }
      case "UC31-01" -> {
        long e = event(20);
        String code = register(e, "visitor", "0912345678");
        long other = event(20);
        register(other, "visitor2", "0912345679");
        Map<String, Object> before = ticketSnapshot(code);
        for (String term : List.of("Guest", "visitor@sems.example")) {
          JsonNode result = getOk("/events/" + e + "/check-ins/search?query=" + term, "staff");
          assertThat(result.get("total").asInt()).isEqualTo(1);
          assertThat(result.get("items").get(0).get("ticketCode").asText()).isEqualTo(code);
          assertThat(result.get("items").get(0).get("attendeeEmail").asText())
              .isEqualTo("visitor@sems.example");
          assertThat(result.get("items").get(0).get("status").asText()).isEqualTo("ISSUED");
        }
        assertThat(
                getOk("/events/" + e + "/check-ins/search?query=not-present", "staff")
                    .get("total")
                    .asInt())
            .isZero();
        assertThat(ticketSnapshot(code)).isEqualTo(before);
        assertThat(logCount(e)).isZero();
      }
      case "UC31-02" -> {
        long e = event(20);
        String code = register(e, "visitor", "0912345678");
        assertCheckinResponse(check(e, code, "MANUAL", 200), code);
        assertThat(getOk("/tickets/" + code, "visitor").get("status").asText())
            .isEqualTo("CHECKED_IN");
        assertThat(logCount(e)).isEqualTo(1);
        assertThat(
                db.queryForObject(
                    "select method from checkin_logs where event_id=?", String.class, e))
            .isEqualTo("MANUAL");
        assertThat(
                db.queryForObject(
                    "select operator_name from checkin_logs where event_id=?", String.class, e))
            .isEqualTo("staff");
        assertThat(dashboard(e).get("checkedIn").asInt()).isEqualTo(1);
      }
      case "UC31-03" -> {
        long e = event(20);
        failureMessage(check(e, "does-not-exist", "MANUAL", 400), "Không tìm thấy mã vé");
        assertThat(registrationCounts()).containsExactly(0L, 0L);
        assertThat(logCount(e)).isZero();
        assertThat(dashboard(e).get("checkedIn").asInt()).isZero();
        registered(e, 0);
      }
      case "UC35-01" -> {
        long e = event(20);
        for (int i = 0; i < 10; i++) {
          String code = guest(e);
          if (i < 4) check(e, code, "MANUAL", 200);
        }
        JsonNode d = dashboard(e);
        assertThat(d.get("registered").asInt()).isEqualTo(10);
        assertThat(d.get("checkedIn").asInt()).isEqualTo(4);
        assertThat(d.get("attendanceRate").asDouble()).isEqualTo(40);
        assertThat(d.get("occupancyRate").asDouble()).isEqualTo(20);
        assertThat(d.get("registered").asInt() - d.get("checkedIn").asInt()).isEqualTo(6);
      }
      case "UC35-02" -> {
        long e = event(20);
        String c = guest(e);
        assertThat(dashboard(e).get("checkedIn").asInt()).isZero();
        WebSocketSession connection = mock(WebSocketSession.class);
        when(connection.getUri()).thenReturn(URI.create("ws://localhost/ws/dashboard/" + e));
        when(connection.getPrincipal())
            .thenReturn(
                new UsernamePasswordAuthenticationToken(
                    "organizer", "", List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
        when(connection.isOpen()).thenReturn(true);
        socket.afterConnectionEstablished(connection);
        try {
          check(e, c, "QR", 200);
          ArgumentCaptor<TextMessage> notification = ArgumentCaptor.forClass(TextMessage.class);
          verify(connection, times(1)).sendMessage(notification.capture());
          JsonNode pushed = json.readTree(notification.getValue().getPayload());
          assertThat(pushed.get("eventId").asLong()).isEqualTo(e);
          assertThat(pushed.get("checkedIn").asInt()).isEqualTo(1);
          for (int i = 0; i < 2; i++)
            assertThat(dashboard(e).get("checkedIn").asInt()).isEqualTo(1);
          verify(connection, times(1)).sendMessage(any(TextMessage.class));
        } finally {
          socket.afterConnectionClosed(
              connection, org.springframework.web.socket.CloseStatus.NORMAL);
        }
      }
      case "UC35-03" -> {
        long e = event(20);
        for (int i = 1; i <= 19; i++) {
          check(e, guest(e), "MANUAL", 200);
          if (i == 16 || i == 17 || i == 19) {
            JsonNode d = dashboard(e);
            assertThat(d.get("occupancyRate").asDouble()).isEqualTo(i * 5.0);
            assertThat(d.get("occupancyAlert").asText())
                .isEqualTo(i == 16 ? "NORMAL" : i == 17 ? "YELLOW" : "RED");
          }
        }
      }
      case "UC42-01", "UC42-02", "UC42-03", "UC42-04" -> {
        long e = event(20);
        String c = register(e, "visitor", "0912345678");
        if (!id.equals("UC42-02")) check(e, c, "MANUAL", 200);
        if (id.equals("UC42-03")) feedback(c, "visitor", 4, 201);
        var beforeFeedback = db.queryForList("select * from feedback order by id");
        var beforeTicket = ticketSnapshot(c);
        if (id.equals("UC42-04")) {
          failureMessage(feedback(c, "visitor", 0, 400), "rating");
          assertThat(db.queryForList("select * from feedback order by id"))
              .isEqualTo(beforeFeedback);
          failureMessage(feedback(c, "visitor", 6, 400), "rating");
        } else {
          var response = feedback(c, "visitor", 4, id.equals("UC42-01") ? 201 : 409);
          if (id.equals("UC42-02")) failureMessage(response, "Chỉ khách đã check-in");
          if (id.equals("UC42-03")) failureMessage(response, "đã gửi đánh giá");
          if (id.equals("UC42-01")) {
            assertThat(parse(response).get("comment").asText()).isEqualTo(COMMENT);
            assertThat(parse(response).get("rating").asInt()).isEqualTo(4);
          }
        }
        if (!id.equals("UC42-01"))
          assertThat(db.queryForList("select * from feedback order by id"))
              .isEqualTo(beforeFeedback);
        assertThat(ticketSnapshot(c)).isEqualTo(beforeTicket);
        int total = id.equals("UC42-01") || id.equals("UC42-03") ? 1 : 0;
        JsonNode s = getOk("/events/" + e + "/feedback", "organizer");
        assertThat(s.get("count").asInt()).isEqualTo(total);
        if (total == 1) assertThat(s.get("averageRating").asDouble()).isEqualTo(4);
        if (id.equals("UC42-01")) {
          assertThat(getOk("/tickets/" + c, "visitor").get("feedbackSent").asBoolean()).isTrue();
          assertThat(getOk("/feedback/mine", "visitor").size()).isEqualTo(1);
          assertThat(getOk("/tickets/" + c, "visitor").get("status").asText())
              .isEqualTo("CHECKED_IN");
        }
      }
      case "UC44-01" -> {
        long e = event(20);
        for (int score : new int[] {5, 4, 3, 4}) ratedGuest(e, score);
        JsonNode s = getOk("/events/" + e + "/feedback", "organizer");
        assertThat(s.get("count").asInt()).isEqualTo(4);
        assertThat(s.get("averageRating").asDouble()).isEqualTo(4);
        assertThat(s.get("ratings").size()).isEqualTo(5);
        int[] expected = {0, 0, 1, 2, 1};
        for (JsonNode r : s.get("ratings")) {
          int n = expected[r.get("rating").asInt() - 1];
          assertThat(r.get("count").asInt()).isEqualTo(n);
          assertThat(r.get("percentage").asDouble()).isEqualTo(n * 25.0);
        }
      }
      case "UC44-02" -> {
        long e = event(20);
        JsonNode s = getOk("/events/" + e + "/feedback", "organizer");
        assertThat(s.get("count").asInt()).isZero();
        assertThat(s.get("averageRating").asDouble()).isZero();
        assertThat(s.get("trend").isEmpty()).isTrue();
        assertThat(items(e, "", 0, 0).get("items").isEmpty()).isTrue();
        assertThat(s.get("ratings").size()).isEqualTo(5);
        for (JsonNode r : s.get("ratings")) {
          assertThat(r.get("count").asInt()).isZero();
          assertThat(r.get("percentage").asDouble()).isZero();
        }
      }
      case "UC44-03" -> {
        long e = event(30);
        for (int i = 0; i < 21; i++) ratedGuest(e, i % 5 + 1);
        JsonNode a = items(e, "", 0, 0), b = items(e, "", 0, 1);
        assertThat(a.get("items").size()).isEqualTo(20);
        assertThat(b.get("items").size()).isEqualTo(1);
        assertThat(a.get("total").asInt()).isEqualTo(21);
        assertThat(b.get("total").asInt()).isEqualTo(21);
        Set<String> names = new HashSet<>();
        for (JsonNode r : a.get("items")) names.add(r.get("attendeeName").asText());
        assertThat(names).hasSize(20);
        assertThat(names).doesNotContain(b.get("items").get(0).get("attendeeName").asText());
        JsonNode filtered = items(e, "review", 5, 0);
        assertThat(filtered.get("total").asInt()).isEqualTo(4);
        for (JsonNode r : filtered.get("items")) assertThat(r.get("rating").asInt()).isEqualTo(5);
        assertThat(items(e, "Guest 21", 0, 0).get("items").get(0).get("attendeeName").asText())
            .isEqualTo("Guest 21");
        assertThat(items(e, "not-present", 0, 0).get("total").asInt()).isZero();
      }
      case "UC44-04" -> {
        long e = event(30);
        for (int i = 0; i < 21; i++) ratedGuest(e, i % 5 + 1);
        items(e, "review", 5, 0);
        reports(e, 21);
      }
      case "INT-01" -> {
        long e = event(20);
        String c = register(e, "visitor", "0912345678");
        assertThat(httpGet("/tickets/" + c + "/qr", "visitor").getResponse().getStatus())
            .isEqualTo(200);
        check(e, c, "QR", 200);
        feedback(c, "visitor", 4, 201);
        registered(e, 1);
        assertThat(logCount(e)).isEqualTo(1);
        assertThat(dashboard(e).get("checkedIn").asInt()).isEqualTo(1);
        assertThat(getOk("/events/" + e + "/feedback", "organizer").get("count").asInt())
            .isEqualTo(1);
        reports(e, 1);
      }
      default -> throw new AssertionError("Unmapped original case: " + id);
    }
  }

  Map<String, Object> input(int capacity) {
    var start = TimeUtil.now().plusMinutes(30).withSecond(0).withNano(0);
    return new HashMap<>(
        Map.of(
            "title",
            "Original 36 QA",
            "onlineUrl",
            "https://example.org/test-room",
            "startsAt",
            start.toString(),
            "endsAt",
            start.plusHours(3).toString(),
            "capacity",
            capacity,
            "status",
            "OPEN"));
  }

  long venue(String owner) {
    Venue v = new Venue();
    v.setOrganizer(users.findByUsernameIgnoreCase(owner).orElseThrow());
    v.setName("QA hall");
    v.setAddress("QA address");
    v.setCapacity(20);
    return venues.saveAndFlush(v).getId();
  }

  long created(Map<String, Object> in) throws Exception {
    return parse(expectPost("/events", in, "organizer", 201)).get("id").asLong();
  }

  long event(int cap) throws Exception {
    long e = created(input(cap));
    approve(e);
    return e;
  }

  void approve(long e) throws Exception {
    expectPost("/events/" + e + "/submit", Map.of(), "organizer", 200);
    expectPost("/admin/events/" + e + "/review", Map.of("decision", "APPROVED"), "admin", 200);
    long s = users.findByUsernameIgnoreCase("staff").orElseThrow().getId();
    expectPost("/admin/events/" + e + "/staff/" + s, Map.of(), "admin", 200);
  }

  Map<String, Object> registration(String phone) {
    return Map.of("fullName", "Guest", "phone", phone);
  }

  String register(long e, String who, String phone) throws Exception {
    return parse(expectPost("/events/" + e + "/registrations", registration(phone), who, 201))
        .get("code")
        .asText();
  }

  String guest(long e) throws Exception {
    int n = ++nextGuest;
    String name = "qa" + n;
    accounts.createAttendee(name, "Guest " + n, name + "@example.org", "QaGuest@123456");
    return parse(
            expectPost(
                "/events/" + e + "/registrations",
                Map.of("fullName", "Guest " + n, "phone", String.format("09%08d", n)),
                name,
                201))
        .get("code")
        .asText();
  }

  void ratedGuest(long e, int score) throws Exception {
    String c = guest(e);
    check(e, c, "MANUAL", 200);
    feedback(c, "qa" + nextGuest, score, 201);
  }

  String token(String c) {
    return tickets.findByCode(c).orElseThrow().getQrToken();
  }

  MvcResult check(long e, String c, String method, int expected) throws Exception {
    return expectPost(
        "/events/" + e + "/check-ins",
        method.equals("QR")
            ? Map.of("method", method, "qrToken", token(c))
            : Map.of("method", method, "ticketCode", c),
        "staff",
        expected);
  }

  MvcResult feedback(String c, String who, int rating, int expected) throws Exception {
    return expectPost(
        "/tickets/" + c + "/feedback", Map.of("rating", rating, "comment", COMMENT), who, expected);
  }

  JsonNode dashboard(long e) throws Exception {
    return getOk("/events/" + e + "/dashboard", "organizer");
  }

  void registered(long e, int n) throws Exception {
    assertThat(dashboard(e).get("registered").asInt()).isEqualTo(n);
  }

  int logCount(long e) {
    return db.queryForObject(
        "select count(*) from checkin_logs where event_id=?", Integer.class, e);
  }

  JsonNode items(long e, String query, int rating, int page) throws Exception {
    return getOk(
        "/events/" + e + "/feedback/items?query=" + query + "&rating=" + rating + "&page=" + page,
        "organizer");
  }

  void reports(long e, int count) throws Exception {
    var expected =
        db.queryForList(
            "select a.full_name,a.email,t.code,f.rating,f.comment_text,f.created_at from feedback f"
                + " join tickets t on t.id=f.ticket_id join attendees a on a.id=t.attendee_id where"
                + " f.event_id=? order by f.id desc",
            e);
    assertThat(expected).hasSize(count);
    var csv = httpGet("/events/" + e + "/reports/feedback", "organizer").getResponse();
    assertThat(csv.getStatus()).isEqualTo(200);
    assertThat(csv.getHeader("Content-Disposition")).contains("attachment");
    String text = csv.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(text).startsWith("\uFEFF");
    List<List<String>> csvRows = parseCsv(text.substring(1));
    assertThat(csvRows).hasSize(count + 1);
    assertThat(csvRows.get(0))
        .containsExactly(
            "Sự kiện", "Họ tên", "Email", "Mã vé", "Điểm", "Nhận xét", "Ngày đánh giá");
    var response = httpGet("/events/" + e + "/reports/feedback.xlsx", "organizer").getResponse();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).contains("spreadsheetml.sheet");
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
      assertThat(book.getNumberOfSheets()).isEqualTo(2);
      var overview = book.getSheet("Tổng quan");
      var details = book.getSheet("Chi tiết");
      assertThat(overview).isNotNull();
      assertThat(details).isNotNull();
      assertThat(details.getLastRowNum()).isEqualTo(count);
      assertThat(overview.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(count);
      double score = expected.stream().mapToInt(r -> ((Number) r.get("RATING")).intValue()).sum();
      assertThat(overview.getRow(4).getCell(1).getNumericCellValue())
          .isEqualTo(Math.round(score / count * 10) / 10.0);
      String title = getOk("/events/" + e, "organizer").get("title").asText();
      assertThat(overview.getRow(2).getCell(1).getStringCellValue()).isEqualTo(title);
      for (int i = 0; i < count; i++) {
        var record = expected.get(i);
        var detail = details.getRow(i + 1);
        var time = ((java.sql.Timestamp) record.get("CREATED_AT")).toLocalDateTime();
        int rating = ((Number) record.get("RATING")).intValue();
        assertThat(csvRows.get(i + 1))
            .containsExactly(
                title,
                record.get("FULL_NAME").toString(),
                record.get("EMAIL").toString(),
                record.get("CODE").toString(),
                Integer.toString(rating),
                record.get("COMMENT_TEXT").toString(),
                time.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        assertThat(detail.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
        assertThat(detail.getCell(0).getLocalDateTimeCellValue())
            .isCloseTo(time, within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(detail.getCell(1).getStringCellValue()).isEqualTo(record.get("FULL_NAME"));
        assertThat(detail.getCell(2).getNumericCellValue()).isEqualTo(rating);
        assertThat(detail.getCell(3).getCellType()).isEqualTo(CellType.STRING);
        assertThat(detail.getCell(3).getStringCellValue()).isEqualTo(record.get("COMMENT_TEXT"));
      }
    }
  }

  // Independent RFC 4180 reader: quoted commas, escaped quotes and embedded newlines.
  List<List<String>> parseCsv(String text) {
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '"') {
        if (quoted && i + 1 < text.length() && text.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else quoted = !quoted;
      } else if (c == ',' && !quoted) {
        row.add(field.toString());
        field.setLength(0);
      } else if ((c == '\r' || c == '\n') && !quoted) {
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
        row.add(field.toString());
        field.setLength(0);
        rows.add(List.copyOf(row));
        row.clear();
      } else field.append(c);
    }
    assertThat(quoted).as("CSV quotes must close").isFalse();
    if (!row.isEmpty() || !field.isEmpty()) {
      row.add(field.toString());
      rows.add(row);
    }
    return rows;
  }

  List<Long> registrationCounts() {
    return List.of(
        db.queryForObject("select count(*) from tickets", Long.class),
        db.queryForObject("select count(*) from attendees", Long.class));
  }

  Map<String, Object> ticketSnapshot(String code) {
    return db.queryForMap(
        "select status,checked_in_at,qr_token,event_id,user_id,attendee_id from tickets where"
            + " code=?",
        code);
  }

  void failureMessage(MvcResult result, String fragment) throws Exception {
    assertThat(parse(result).get("message").asText()).contains(fragment);
  }

  void assertCheckinResponse(MvcResult result, String code) throws Exception {
    JsonNode response = parse(result);
    assertThat(response.get("ticketCode").asText()).isEqualTo(code);
    assertThat(response.get("attendeeName").asText()).isNotBlank();
    assertThat(response.get("checkedInAt").asText()).isNotBlank();
    var ticketTime =
        db.queryForObject(
                "select checked_in_at from tickets where code=?", java.sql.Timestamp.class, code)
            .toLocalDateTime();
    var logTime =
        db.queryForObject(
                "select l.checked_in_at from checkin_logs l join tickets t on t.id=l.ticket_id"
                    + " where t.code=?",
                java.sql.Timestamp.class,
                code)
            .toLocalDateTime();
    assertThat(LocalDateTime.parse(response.get("checkedInAt").asText()))
        .isCloseTo(ticketTime, within(1, java.time.temporal.ChronoUnit.MILLIS));
    assertThat(logTime).isEqualTo(ticketTime);
  }

  MockHttpSession sessionFor(String who) throws Exception {
    if (sessions.containsKey(who)) return sessions.get(who);
    String password =
        switch (who) {
          case "admin" -> "Admin@123456";
          case "organizer" -> "Organizer@123456";
          case "organizer2" -> "Organizer2@123456";
          case "staff" -> "Staff@123456";
          case "staff2" -> "Staff2@123456";
          case "visitor" -> "Visitor@123456";
          case "visitor2" -> "Visitor2@123456";
          default -> "QaGuest@123456";
        };
    var result =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(csrf())
                    .param("username", who)
                    .param("password", password))
            .andReturn();
    assertThat(result.getResponse().getStatus()).as("Login %s", who).isEqualTo(200);
    var session = (MockHttpSession) result.getRequest().getSession(false);
    assertThat(session).isNotNull();
    sessions.put(who, session);
    return session;
  }

  MvcResult httpGet(String path, String who) throws Exception {
    var req = get("/api/v1" + path);
    if (who != null) req.session(sessionFor(who));
    return mvc.perform(req).andReturn();
  }

  JsonNode getOk(String path, String who) throws Exception {
    var r = httpGet(path, who);
    assertThat(r.getResponse().getStatus()).as(path).isEqualTo(200);
    return parse(r);
  }

  MvcResult expectPost(String path, Object data, String who, int expected) throws Exception {
    var r =
        mvc.perform(
                post("/api/v1" + path)
                    .with(csrf())
                    .session(sessionFor(who))
                    .contentType("application/json")
                    .content(json.writeValueAsBytes(data)))
            .andReturn();
    assertThat(r.getResponse().getStatus())
        .as(path + " " + r.getResponse().getContentAsString())
        .isEqualTo(expected);
    return r;
  }

  JsonNode parse(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsByteArray());
  }
}
