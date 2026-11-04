package com.event.sems;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.event.sems.common.TimeUtil;
import com.event.sems.entity.Venue;
import com.event.sems.repository.*;
import com.event.sems.service.AccountService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Backend checks mapped to the 36 resource cases; does not certify browser/camera behavior. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:original36;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.root=WARN", "logging.level.org.hibernate.SQL=WARN",
    "logging.level.org.springframework=WARN"
})
@AutoConfigureMockMvc
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
  int nextGuest;

  static Stream<String> cases() {
    List<String> ids = new ArrayList<>();
    for (int[] group : new int[][] {{13,4},{20,6},{23,3},{29,8},{31,3},{35,3},{42,4},{44,4}})
      for (int i=1;i<=group[1];i++) ids.add("UC"+group[0]+"-"+String.format("%02d",i));
    ids.add("INT-01");
    return ids.stream();
  }

  @BeforeEach
  void isolatedData() {
    // H2 only: never use or reset the user's MySQL database.
    for (String t : List.of("feedback","checkin_logs","tickets","attendees","ticket_types",
        "agenda_items","speakers","event_staff","event_reviews","events","venues",
        "account_tokens","pending_email_actions","user_roles","users","roles","mail_settings"))
      db.update("DELETE FROM " + t);
    SampleAccounts.seed(roles,users,passwords);
    nextGuest=0;
  }

  @ParameterizedTest(name="{0}")
  @MethodSource("cases")
  void originalCaseBackend(String id) throws Exception {
    assumeTrue(!Set.of("UC23-03","UC29-07","UC29-08").contains(id),
        "Requires real SMTP/inbox or phone HTTPS/camera permissions; not simulated as a pass.");
    switch (id) {
      case "UC13-01" -> {
        long v=venue("organizer");
        Map<String,Object> in=input(20);in.remove("onlineUrl");in.put("venueId",v);
        long e=created(in);
        JsonNode detail=getOk("/events/"+e,"organizer");
        assertThat(detail.get("approvalStatus").asText()).isEqualTo("DRAFT");
        assertThat(detail.get("venueId").asLong()).isEqualTo(v);
        assertThat(db.queryForObject("select count(*) from ticket_types where event_id=? and price=0",Integer.class,e)).isEqualTo(1);
        assertThat(httpGet("/public/events/"+e,null).getResponse().getStatus()).isEqualTo(404);
      }
      case "UC13-02" -> {
        Map<String,Object> in=input(20);
        in.put("endsAt",LocalDateTime.parse(in.get("startsAt").toString()).plusMinutes(29).toString());
        expectPost("/events",in,"organizer",400);
        assertThat(db.queryForObject("select count(*) from events",Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from ticket_types",Integer.class)).isZero();
      }
      case "UC13-03" -> {
        long v=venue("organizer2");Map<String,Object> in=input(20);in.remove("onlineUrl");in.put("venueId",v);
        expectPost("/events",in,"organizer",404);
        assertThat(db.queryForObject("select count(*) from events",Integer.class)).isZero();
      }
      case "UC13-04" -> {
        long e=created(input(20));JsonNode detail=getOk("/events/"+e,"organizer");
        assertThat(detail.get("approvalStatus").asText()).isEqualTo("DRAFT");
        assertThat(detail.get("onlineUrl").asText()).isEqualTo("https://example.org/test-room");
        assertThat(detail.get("venueId").isNull()).isTrue();
      }
      case "UC20-01" -> {
        long e=event(20);String code=register(e,"visitor","0912345678");
        JsonNode ticket=getOk("/tickets/"+code,"visitor");
        assertThat(ticket.get("status").asText()).isEqualTo("ISSUED");
        assertThat(ticket.get("attendeeEmail").asText()).isEqualTo("visitor@sems.example");
        assertThat(getOk("/tickets/mine","visitor").size()).isEqualTo(1);registered(e,1);
      }
      case "UC20-02", "UC20-03", "UC20-04" -> {
        long e=event(id.equals("UC20-04")?1:20);register(e,"visitor","0912345678");
        String who=id.equals("UC20-02")?"visitor":"visitor2";
        String phone=id.equals("UC20-03")?"0912345678":"0912345679";
        expectPost("/events/"+e+"/registrations",registration(phone),who,409);registered(e,1);
        assertThat(db.queryForObject("select count(*) from tickets",Integer.class)).isEqualTo(1);
      }
      case "UC20-05" -> {
        Map<String,Object> in=input(20);in.put("status","CLOSED");long e=created(in);approve(e);
        expectPost("/events/"+e+"/registrations",registration("0912345678"),"visitor",409);registered(e,0);
      }
      case "UC20-06" -> {
        long e=event(20);var a=users.findByUsernameIgnoreCase("visitor").orElseThrow();a.setEmailVerified(false);users.saveAndFlush(a);
        expectPost("/events/"+e+"/registrations",registration("0912345678"),"visitor",409);registered(e,0);
      }
      case "UC23-01" -> {
        long e=event(20);String code=register(e,"visitor","0912345678");
        for(int i=0;i<2;i++) {
          assertThat(getOk("/tickets/"+code,"visitor").get("code").asText()).isEqualTo(code);
          var qr=httpGet("/tickets/"+code+"/qr","visitor").getResponse();
          assertThat(qr.getStatus()).isEqualTo(200);assertThat(qr.getContentType()).startsWith("image/png");
          assertThat(qr.getContentAsByteArray()).startsWith(new byte[]{(byte)137,80,78,71});
        }
        registered(e,1);
      }
      case "UC23-02" -> {
        long e=event(20);String code=register(e,"visitor","0912345678");
        assertThat(httpGet("/tickets/"+code,"visitor2").getResponse().getStatus()).isEqualTo(404);
        assertThat(httpGet("/tickets/"+code+"/qr","visitor2").getResponse().getStatus()).isEqualTo(404);
      }
      case "UC29-01", "UC29-02", "UC29-03", "UC29-04", "UC29-05", "UC29-06" -> {
        long e=event(20);String code=register(e,"visitor","0912345678");String token=token(code);
        String staff="staff";int expected=200;
        if(id.equals("UC29-02")){check(e,code,"QR",200);expected=409;}
        if(id.equals("UC29-03")){long other=event(20);String c=register(other,"visitor2","0912345679");token=token(c);expected=400;}
        if(id.equals("UC29-04")){token=token.substring(0,5)+(token.charAt(5)=='A'?'B':'A')+token.substring(6);expected=400;}
        if(id.equals("UC29-05")){staff="staff2";expected=403;}
        if(id.equals("UC29-06")){
          var start=TimeUtil.now().plusDays(2);
          db.update("update events set starts_at=?,ends_at=? where id=?",start,start.plusHours(2),e);expected=409;
        }
        expectPost("/events/"+e+"/check-ins",Map.of("method","QR","qrToken",token),staff,expected);
        boolean used=expected==200||id.equals("UC29-02");
        assertThat(getOk("/tickets/"+code,"visitor").get("status").asText()).isEqualTo(used?"CHECKED_IN":"ISSUED");
        assertThat(logCount(e)).isEqualTo(used?1:0);
        assertThat(dashboard(e).get("checkedIn").asInt()).isEqualTo(used?1:0);
      }
      case "UC31-01" -> {
        long e=event(20);String code=register(e,"visitor","0912345678");
        for(String term:List.of("Guest","visitor@sems.example")) {
          JsonNode result=getOk("/events/"+e+"/check-ins/search?query="+term,"staff");
          assertThat(result.get("total").asInt()).isEqualTo(1);
          assertThat(result.get("items").get(0).get("ticketCode").asText()).isEqualTo(code);
        }
        assertThat(getOk("/events/"+e+"/check-ins/search?query=not-present","staff").get("total").asInt()).isZero();
        assertThat(logCount(e)).isZero();
      }
      case "UC31-02" -> {
        long e=event(20);String code=register(e,"visitor","0912345678");check(e,code,"MANUAL",200);
        assertThat(logCount(e)).isEqualTo(1);
        assertThat(db.queryForObject("select method from checkin_logs where event_id=?",String.class,e)).isEqualTo("MANUAL");
        assertThat(db.queryForObject("select operator_name from checkin_logs where event_id=?",String.class,e)).isEqualTo("staff");
        assertThat(dashboard(e).get("checkedIn").asInt()).isEqualTo(1);
      }
      case "UC31-03" -> {
        long e=event(20);check(e,"does-not-exist","MANUAL",400);assertThat(logCount(e)).isZero();registered(e,0);
      }
      case "UC35-01" -> {
        long e=event(20);for(int i=0;i<10;i++){String code=guest(e);if(i<4)check(e,code,"MANUAL",200);}
        JsonNode d=dashboard(e);assertThat(d.get("registered").asInt()).isEqualTo(10);
        assertThat(d.get("checkedIn").asInt()).isEqualTo(4);
        assertThat(d.get("attendanceRate").asDouble()).isEqualTo(40);
        assertThat(d.get("occupancyRate").asDouble()).isEqualTo(20);
      }
      case "UC35-02" -> {
        long e=event(20);String c=guest(e);assertThat(dashboard(e).get("checkedIn").asInt()).isZero();
        check(e,c,"QR",200);
        for(int i=0;i<2;i++)assertThat(dashboard(e).get("checkedIn").asInt()).isEqualTo(1);
      }
      case "UC35-03" -> {
        long e=event(20);
        for(int i=1;i<=19;i++){
          check(e,guest(e),"MANUAL",200);
          if(i==16||i==17||i==19){JsonNode d=dashboard(e);
            assertThat(d.get("occupancyRate").asDouble()).isEqualTo(i*5.0);
            assertThat(d.get("occupancyAlert").asText()).isEqualTo(i==16?"NORMAL":i==17?"YELLOW":"RED");}
        }
      }
      case "UC42-01", "UC42-02", "UC42-03", "UC42-04" -> {
        long e=event(20);String c=register(e,"visitor","0912345678");
        if(!id.equals("UC42-02"))check(e,c,"MANUAL",200);
        if(id.equals("UC42-03"))feedback(c,"visitor",4,201);
        if(id.equals("UC42-04")){feedback(c,"visitor",0,400);feedback(c,"visitor",6,400);}
        else feedback(c,"visitor",4,id.equals("UC42-01")?201:409);
        int total=id.equals("UC42-01")||id.equals("UC42-03")?1:0;
        JsonNode s=getOk("/events/"+e+"/feedback","organizer");assertThat(s.get("count").asInt()).isEqualTo(total);
        if(total==1)assertThat(s.get("averageRating").asDouble()).isEqualTo(4);
        if(id.equals("UC42-01")){
          assertThat(getOk("/tickets/"+c,"visitor").get("feedbackSent").asBoolean()).isTrue();
          assertThat(getOk("/feedback/mine","visitor").size()).isEqualTo(1);
          assertThat(getOk("/tickets/"+c,"visitor").get("status").asText()).isEqualTo("CHECKED_IN");
        }
      }
      case "UC44-01" -> {
        long e=event(20);for(int score:new int[]{5,4,3,4})ratedGuest(e,score);
        JsonNode s=getOk("/events/"+e+"/feedback","organizer");
        assertThat(s.get("count").asInt()).isEqualTo(4);assertThat(s.get("averageRating").asDouble()).isEqualTo(4);
        int[] expected={0,0,1,2,1};for(JsonNode r:s.get("ratings")){
          int n=expected[r.get("rating").asInt()-1];assertThat(r.get("count").asInt()).isEqualTo(n);
          assertThat(r.get("percentage").asDouble()).isEqualTo(n*25.0);}
      }
      case "UC44-02" -> {
        long e=event(20);JsonNode s=getOk("/events/"+e+"/feedback","organizer");
        assertThat(s.get("count").asInt()).isZero();assertThat(s.get("averageRating").asDouble()).isZero();
        assertThat(s.get("trend").isEmpty()).isTrue();assertThat(items(e,"",0,0).get("items").isEmpty()).isTrue();
      }
      case "UC44-03" -> {
        long e=event(30);for(int i=0;i<21;i++)ratedGuest(e,i%5+1);
        JsonNode a=items(e,"",0,0),b=items(e,"",0,1);
        assertThat(a.get("items").size()).isEqualTo(20);assertThat(b.get("items").size()).isEqualTo(1);
        Set<String> names=new HashSet<>();for(JsonNode r:a.get("items"))names.add(r.get("attendeeName").asText());
        assertThat(names).doesNotContain(b.get("items").get(0).get("attendeeName").asText());
        JsonNode filtered=items(e,"review",5,0);assertThat(filtered.get("total").asInt()).isEqualTo(4);
        for(JsonNode r:filtered.get("items"))assertThat(r.get("rating").asInt()).isEqualTo(5);
        assertThat(items(e,"not-present",0,0).get("total").asInt()).isZero();
      }
      case "UC44-04" -> {
        long e=event(30);for(int i=0;i<21;i++)ratedGuest(e,i%5+1);
        items(e,"review",5,0);reports(e,21);
      }
      case "INT-01" -> {
        long e=event(20);String c=register(e,"visitor","0912345678");
        assertThat(httpGet("/tickets/"+c+"/qr","visitor").getResponse().getStatus()).isEqualTo(200);
        check(e,c,"QR",200);feedback(c,"visitor",4,201);registered(e,1);
        assertThat(logCount(e)).isEqualTo(1);assertThat(dashboard(e).get("checkedIn").asInt()).isEqualTo(1);
        assertThat(getOk("/events/"+e+"/feedback","organizer").get("count").asInt()).isEqualTo(1);reports(e,1);
      }
      default -> throw new AssertionError("Unmapped original case: "+id);
    }
  }

  Map<String,Object> input(int capacity){
    var start=TimeUtil.now().plusMinutes(30).withSecond(0).withNano(0);
    return new HashMap<>(Map.of("title","Original 36 QA","onlineUrl","https://example.org/test-room",
        "startsAt",start.toString(),"endsAt",start.plusHours(3).toString(),"capacity",capacity,"status","OPEN"));
  }
  long venue(String owner){Venue v=new Venue();v.setOrganizer(users.findByUsernameIgnoreCase(owner).orElseThrow());v.setName("QA hall");v.setAddress("QA address");v.setCapacity(20);return venues.saveAndFlush(v).getId();}
  long created(Map<String,Object> in)throws Exception{return parse(expectPost("/events",in,"organizer",201)).get("id").asLong();}
  long event(int cap)throws Exception{long e=created(input(cap));approve(e);return e;}
  void approve(long e)throws Exception{
    expectPost("/events/"+e+"/submit",Map.of(),"organizer",200);
    expectPost("/admin/events/"+e+"/review",Map.of("decision","APPROVED"),"admin",200);
    long s=users.findByUsernameIgnoreCase("staff").orElseThrow().getId();
    expectPost("/admin/events/"+e+"/staff/"+s,Map.of(),"admin",200);
  }
  Map<String,Object> registration(String phone){return Map.of("fullName","Guest","phone",phone);}
  String register(long e,String who,String phone)throws Exception{return parse(expectPost("/events/"+e+"/registrations",registration(phone),who,201)).get("code").asText();}
  String guest(long e)throws Exception{
    int n=++nextGuest;String name="qa"+n;accounts.createAttendee(name,"Guest "+n,name+"@example.org","QaGuest@123456");
    return parse(expectPost("/events/"+e+"/registrations",Map.of("fullName","Guest "+n,"phone",String.format("09%08d",n)),name,201)).get("code").asText();
  }
  void ratedGuest(long e,int score)throws Exception{String c=guest(e);check(e,c,"MANUAL",200);feedback(c,"qa"+nextGuest,score,201);}
  String token(String c){return tickets.findByCode(c).orElseThrow().getQrToken();}
  void check(long e,String c,String method,int expected)throws Exception{
    expectPost("/events/"+e+"/check-ins",method.equals("QR")?Map.of("method",method,"qrToken",token(c)):Map.of("method",method,"ticketCode",c),"staff",expected);
  }
  void feedback(String c,String who,int rating,int expected)throws Exception{expectPost("/tickets/"+c+"/feedback",Map.of("rating",rating,"comment","review"),who,expected);}
  JsonNode dashboard(long e)throws Exception{return getOk("/events/"+e+"/dashboard","organizer");}
  void registered(long e,int n)throws Exception{assertThat(dashboard(e).get("registered").asInt()).isEqualTo(n);}
  int logCount(long e){return db.queryForObject("select count(*) from checkin_logs where event_id=?",Integer.class,e);}
  JsonNode items(long e,String query,int rating,int page)throws Exception{return getOk("/events/"+e+"/feedback/items?query="+query+"&rating="+rating+"&page="+page,"organizer");}
  void reports(long e,int count)throws Exception{
    var csv=httpGet("/events/"+e+"/reports/feedback","organizer").getResponse();assertThat(csv.getStatus()).isEqualTo(200);
    String text=csv.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(text).startsWith("\uFEFF");
    assertThat(text.split("\r\n").length).isEqualTo(count+1);
    var x=httpGet("/events/"+e+"/reports/feedback.xlsx","organizer").getResponse();assertThat(x.getStatus()).isEqualTo(200);
    try(var book=new XSSFWorkbook(new ByteArrayInputStream(x.getContentAsByteArray()))){
      assertThat(book.getNumberOfSheets()).isEqualTo(2);assertThat(book.getSheetAt(0).getSheetName()).isEqualTo("Tổng quan");
      assertThat(book.getSheetAt(1).getSheetName()).isEqualTo("Chi tiết");assertThat(book.getSheetAt(1).getLastRowNum()).isEqualTo(count);
      assertThat(book.getSheetAt(0).getRow(3).getCell(1).getNumericCellValue()).isEqualTo(count);
    }
  }
  String role(String who){return who.equals("admin")?"ADMIN":who.startsWith("organizer")?"ORGANIZER":who.startsWith("staff")?"STAFF":"ATTENDEE";}
  MvcResult httpGet(String path,String who)throws Exception{var req=get("/api/v1"+path);if(who!=null)req.with(user(who).roles(role(who)));return mvc.perform(req).andReturn();}
  JsonNode getOk(String path,String who)throws Exception{var r=httpGet(path,who);assertThat(r.getResponse().getStatus()).as(path).isEqualTo(200);return parse(r);}
  MvcResult expectPost(String path,Object data,String who,int expected)throws Exception{
    var r=mvc.perform(post("/api/v1"+path).with(csrf()).with(user(who).roles(role(who))).contentType("application/json").content(json.writeValueAsBytes(data))).andReturn();
    assertThat(r.getResponse().getStatus()).as(path+" "+r.getResponse().getContentAsString()).isEqualTo(expected);return r;
  }
  JsonNode parse(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsByteArray());}
}
