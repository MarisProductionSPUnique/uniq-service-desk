package com.ibs;
import java.net.URI;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.io.*;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/support/monitoring")
public class MonitoringSyncController {
 private final ObjectMapper json;
 private Instant next=Instant.EPOCH;
 public MonitoringSyncController(ObjectMapper json){this.json=json;}
 private JsonNode send(HttpClient client,HttpRequest request)throws Exception{
  var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
  try(InputStream in=response.body()){
   if(response.statusCode()<200||response.statusCode()>=300)throw new IOException("Upstream HTTP "+response.statusCode());
   byte[] bytes=in.readNBytes(1048577);if(bytes.length>1048576)throw new IOException("Response limit");
   return bytes.length==0?json.nullNode():json.readTree(bytes);
  }
 }
 private HttpRequest.Builder request(String url){return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Accept","application/json");}
 private HttpClient client(){return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();}
 @PostMapping("/preview") public synchronized Map<String,Object> preview(HttpServletRequest r){
  if(!SupportPortalController.authorized(r))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  if(Instant.now().isBefore(next))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
  next=Instant.now().plusSeconds(60);
  Map<String,Object> result=new LinkedHashMap<>();
  String key=System.getenv("MONITOR_IBS_SUPPORT_KEY");
  if(key==null||key.isBlank())result.put("IBS",Map.of("status","NOT_CONFIGURED"));
  else try{
   String url="https://ibs-banking-appserver.onrender.com/api/support/logs?level=ERROR&from="+Instant.now().minusSeconds(300);
   JsonNode rows=send(client(),request(url).header("X-Support-Key",key).GET().build());
   if(!rows.isArray())throw new IOException("Invalid response");
   result.put("IBS",Map.of("status","OK","recentErrorCount",rows.size(),"windowSeconds",300));
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();result.put("IBS",Map.of("status","UNAVAILABLE"));}
  String user=System.getenv("MONITOR_EIMP_USERNAME"),password=System.getenv("MONITOR_EIMP_PASSWORD");
  if(user==null||user.isBlank()||password==null||password.isBlank())result.put("EIMP",Map.of("status","NOT_CONFIGURED"));
  else{
   HttpClient c=client();String base="https://eimp-insurance-app.onrender.com/api/support";boolean logged=false;
   try{
    send(c,request(base+"/login").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("username",user,"password",password)))).build());logged=true;
    JsonNode rows=send(c,request(base+"/alerts/config/evaluations").GET().build()).path("evaluations");
    if(!rows.isArray())throw new IOException("Invalid response");
    List<String> eligible=new ArrayList<>();for(JsonNode row:rows)if(row.path("incidentEligible").asBoolean(false))eligible.add(row.path("id").asText());
    result.put("EIMP",Map.of("status","OK","evaluatedCount",rows.size(),"eligibleRules",eligible));
   }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();result.put("EIMP",Map.of("status","UNAVAILABLE"));}
   finally{if(logged)try{send(c,request(base+"/logout").POST(HttpRequest.BodyPublishers.noBody()).build());}catch(Exception ignored){}}
  }
  result.put("mode","READ_ONLY_PREVIEW");result.put("timestamp",Instant.now().toString());return result;
 }
}
