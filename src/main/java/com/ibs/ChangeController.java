package com.ibs;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/support/changes")
public class ChangeController {
 private final Map<String,Map<String,String>> records=new LinkedHashMap<>();
 private int sequence=1000;
 private final ServiceDeskController incidents;
 public ChangeController(ServiceDeskController incidents){this.incidents=incidents;}
 private void auth(HttpServletRequest r){if(!SupportPortalController.authorized(r))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 private void require(boolean ok,String reason){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
 private String field(Map<String,String> b,String k,int max){String v=b.getOrDefault(k,"");require(v!=null&&v.length()<=max,k);return v.trim();}
 @GetMapping public synchronized List<Map<String,String>> list(HttpServletRequest r){auth(r);return records.values().stream().map(Map::copyOf).toList();}
 @PostMapping public synchronized Map<String,String> create(HttpServletRequest r,@RequestBody Map<String,String> b){auth(r);if(records.size()>=200)throw new ResponseStatusException(HttpStatus.CONFLICT,"Capacity");Map<String,String> m=fields(r,b);m.put("id",String.format("CHG%07d",++sequence));m.put("state","Draft");m.put("created",Instant.now().toString());m.put("history","Draft "+Instant.now());records.put(m.get("id"),m);return Map.copyOf(m);}
 @PutMapping("/{id}") public synchronized Map<String,String> update(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String> b){auth(r);Map<String,String> old=records.get(id);if(old==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);String from=old.get("state"),to=field(b,"state",30);Map<String,String> m=fields(r,b);
 Set<String> allowed=switch(from){case "Draft"->Set.of("Draft","Assessment","Cancelled");case "Assessment"->Set.of("Assessment","Approval","Cancelled");case "Approval"->Set.of("Approval","Scheduled","Rejected");case "Scheduled"->Set.of("Scheduled","Implementation","Cancelled");case "Implementation"->Set.of("Implementation","Review");case "Review"->Set.of("Review","Closed");default->Set.of();};
 require(allowed.contains(to),"Invalid transition");
 if(!Set.of("Draft","Assessment").contains(from)){for(String k:List.of("type","application","title","risk","impact","implementation","rollback","start","end","incident"))require(Objects.equals(old.get(k),m.get(k)),"Approved scope is locked");}
 if(Set.of("Approval","Scheduled","Implementation","Review","Closed").contains(to)){require(!m.get("implementation").isBlank()&&!m.get("rollback").isBlank()&&!m.get("impact").isBlank(),"Plans and impact required");try{require(Instant.parse(m.get("end")).isAfter(Instant.parse(m.get("start"))),"Invalid window");}catch(java.time.format.DateTimeParseException e){require(false,"Use ISO UTC timestamps");}}
 if(from.equals("Approval")&&Set.of("Scheduled","Rejected").contains(to))require(!m.get("approvalNote").isBlank(),"Approval or rejection rationale required");
 if(!Set.of("Draft","Assessment","Approval").contains(from))require(Objects.equals(old.get("approvalNote"),m.get("approvalNote")),"Approval locked");
 if(to.equals("Closed"))require(!m.get("review").isBlank()&&Set.of("Successful","Failed","Rolled Back").contains(m.get("outcome")),"Review and outcome required");
 String history=old.get("history");if(history.length()>12000)throw new ResponseStatusException(HttpStatus.CONFLICT,"History capacity");m.put("history",history+"\n"+Instant.now()+" "+from+" -> "+to);m.put("id",id);m.put("created",old.get("created"));m.put("state",to);records.put(id,m);return Map.copyOf(m);}
 private Map<String,String> fields(HttpServletRequest r,Map<String,String> b){Map<String,String> m=new LinkedHashMap<>();for(String k:List.of("type","application","title","risk","impact","implementation","rollback","start","end","incident","approvalNote","review","outcome"))m.put(k,field(b,k,Set.of("impact","implementation","rollback","approvalNote","review").contains(k)?4000:200));require(Set.of("Standard","Normal","Emergency").contains(m.get("type"))&&Set.of("IBS","EIMP").contains(m.get("application"))&&Set.of("Low","Medium","High").contains(m.get("risk"))&&!m.get("title").isBlank(),"Required classification");require(m.get("incident").isBlank()||incidents.list(r).stream().anyMatch(t->m.get("incident").equals(t.get("id"))&&m.get("application").equals(t.get("application"))),"Unknown incident");return m;}
}
