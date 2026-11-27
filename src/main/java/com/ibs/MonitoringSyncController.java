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
 private final ServiceDeskController incidents;
 private boolean liveEnabled=false;
 private final Map<String,Instant> delivered=new LinkedHashMap<>();
 private Instant next=Instant.EPOCH;
 public MonitoringSyncController(ObjectMapper json,ServiceDeskController incidents){this.json=json;this.incidents=incidents;}
 @GetMapping("/settings") public synchronized Map<String,Boolean> settings(HttpServletRequest r){authorize(r);return Map.of("liveEnabled",liveEnabled);}
 @PutMapping("/settings") public synchronized Map<String,Boolean> settings(HttpServletRequest r,@RequestBody Map<String,Boolean> body){authorize(r);if(body.get("liveEnabled")==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);liveEnabled=body.get("liveEnabled");return Map.of("liveEnabled",liveEnabled);}
 private void authorize(HttpServletRequest r){if(!SupportPortalController.authorized(r))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 private void deliver(HttpServletRequest r,String app,String rule,String priority,List<String> created,List<String> failures){Instant now=Instant.now();delivered.entrySet().removeIf(e->e.getValue().isBefore(now.minusSeconds(3600)));String dedup=app+":"+rule;if(delivered.size()>=10||delivered.containsKey(dedup))return;String impact=priority.equals("P1")?"1":priority.equals("P2")?"2":"3";String urgency=priority.equals("P4")?"2":"1";try{var t=incidents.create(r,Map.of("application",app,"title","[MONITORING] "+rule,"description","Eligible upstream monitoring alert; verify whether underlying data is simulated before remediation.","impact",impact,"urgency",urgency,"category","Application","requestId",app+"-ALERT-"+rule));delivered.put(dedup,now);created.add(t.get("id").toString());}catch(RuntimeException e){failures.add(dedup);}}
 private JsonNode send(HttpClient client,HttpRequest request)throws Exception{
  var response=client.send(request,HttpResponse.BodyHandlers.ofInputStream());
  try(InputStream in=response.body()){
   if(response.statusCode()<200||response.statusCode()>=300)throw new IOException("Upstream HTTP "+response.statusCode());
   byte[] bytes=in.readNBytes(1048577);if(bytes.length>1048576)throw new IOException("Response limit");
   return bytes.length==0?json.nullNode():json.readTree(bytes);
  }
 }
 private boolean fresh(String timestamp){try{Instant t=Instant.parse(timestamp),now=Instant.now();return !t.isBefore(now.minusSeconds(45))&&!t.isAfter(now.plusSeconds(5));}catch(Exception e){return false;}}
 private HttpRequest.Builder request(String url){return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header("Accept","application/json");}
 private HttpClient client(){return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NEVER).cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ORIGINAL_SERVER)).build();}
 @PostMapping("/preview") public synchronized Map<String,Object> preview(HttpServletRequest r){return collect(r,false);}
 @PostMapping("/sync") public synchronized Map<String,Object> sync(HttpServletRequest r){authorize(r);if(!liveEnabled)throw new ResponseStatusException(HttpStatus.CONFLICT,"Live delivery disabled");return collect(r,true);}
 private Map<String,Object> collect(HttpServletRequest r,boolean deliver){
  if(!SupportPortalController.authorized(r))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  if(Instant.now().isBefore(next))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
  next=Instant.now().plusSeconds(60);
  Map<String,Object> result=new LinkedHashMap<>();List<String> created=new ArrayList<>(),failures=new ArrayList<>();
  String key=System.getenv("MONITOR_IBS_SUPPORT_KEY");
  if(key==null||key.isBlank())result.put("IBS",Map.of("status","NOT_CONFIGURED"));
  else try{
   String url="https://ibs-banking-appserver.onrender.com/api/support/logs?level=ERROR&from="+Instant.now().minusSeconds(300);
   JsonNode rows=send(client(),request(url).header("X-Support-Key",key).GET().build());
   if(!rows.isArray())throw new IOException("Invalid response");
   int http500=0,http503=0,other=0;
   for(JsonNode row:rows){int status=row.path("status").asInt();if(status==500)http500++;else if(status==503)http503++;else if(status>=500&&status<600)other++;}
   List<String> eligible=new ArrayList<>();if(http500>=5)eligible.add("HTTP_500");if(http503>=3)eligible.add("HTTP_503");if(other>=5)eligible.add("OTHER_5XX");
   result.put("IBS",Map.of("status","OK","recentErrorCount",rows.size(),"windowSeconds",300,"eligibleRules",eligible,"http500",http500,"http503",http503,"other5xx",other));
   if(deliver)for(String rule:eligible)deliver(r,"IBS",rule,rule.equals("OTHER_5XX")?"P3":"P2",created,failures);
  }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();result.put("IBS",Map.of("status","UNAVAILABLE"));}
  String user=System.getenv("MONITOR_EIMP_USERNAME"),password=System.getenv("MONITOR_EIMP_PASSWORD");
  if(user==null||user.isBlank()||password==null||password.isBlank())result.put("EIMP",Map.of("status","NOT_CONFIGURED"));
  else{
   HttpClient c=client();String base="https://eimp-insurance-app.onrender.com/api/support";boolean logged=false;
   try{
    send(c,request(base+"/login").header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("username",user,"password",password)))).build());logged=true;
    JsonNode rows=send(c,request(base+"/alerts/config/evaluations").GET().build()).path("evaluations");
    if(!rows.isArray())throw new IOException("Invalid response");
    List<String> eligible=new ArrayList<>();for(JsonNode row:rows)if(row.path("incidentEligible").asBoolean(false)&&fresh(row.path("timestamp").asText()))eligible.add(row.path("id").asText());
    result.put("EIMP",Map.of("status","OK","evaluatedCount",rows.size(),"eligibleRules",eligible));
    if(deliver){JsonNode config=send(c,request(base+"/alerts/config").GET().build());if(config.path("controls").path("enabled").asBoolean(false)&&!config.path("controls").path("maintenance").asBoolean(true)){for(JsonNode rule:config.path("rules")){String id=rule.path("id").asText(),priority=rule.path("priority").asText();if(rule.path("active").asBoolean(false)&&eligible.contains(id)&&Set.of("P1","P2","P3","P4").contains(priority)&&id.matches("[A-Z_0-9]{1,60}"))deliver(r,"EIMP",id,priority,created,failures);}}}

   }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();result.put("EIMP",Map.of("status","UNAVAILABLE"));}
   finally{if(logged)try{send(c,request(base+"/logout").POST(HttpRequest.BodyPublishers.noBody()).build());}catch(Exception ignored){}}
  }
  result.put("createdIncidentIds",created,failures);result.put("mode",deliver?"ON_DEMAND_DELIVERY":"READ_ONLY_PREVIEW");result.put("timestamp",Instant.now().toString());return result;
 }
}
