package com.ibs;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/support/knowledge-problem")
public class KnowledgeProblemController {
 private final Map<String,Map<String,String>> records=new LinkedHashMap<>();
 private int sequence=1000;
 private final ServiceDeskController incidents;
 public KnowledgeProblemController(ServiceDeskController incidents){this.incidents=incidents;}
 private void auth(HttpServletRequest r){if(!SupportPortalController.authorized(r))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 private String field(Map<String,String> b,String k,int max){String v=b.getOrDefault(k,"");if(v==null||v.length()>max)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,k);return v.trim();}
 @GetMapping public synchronized List<Map<String,String>> list(HttpServletRequest r){auth(r);return records.values().stream().map(Map::copyOf).toList();}
 @PostMapping public synchronized Map<String,String> create(HttpServletRequest r,@RequestBody Map<String,String> b){auth(r);if(records.size()>=200)throw new ResponseStatusException(HttpStatus.CONFLICT,"Training capacity");String type=field(b,"type",20);if(!Set.of("Problem","KEDB","SOP","Runbook","Article").contains(type))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Type");String id=(type.equals("Problem")?"PRB":"KB")+String.format("%07d",++sequence);Map<String,String> record=validate(r,b,type);record.put("id",id);record.put("type",type);record.put("created",Instant.now().toString());records.put(id,record);return Map.copyOf(record);}
 @PutMapping("/{id}") public synchronized Map<String,String> update(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String> b){auth(r);Map<String,String> old=records.get(id);if(old==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);Map<String,String> record=validate(r,b,old.get("type"));record.put("id",id);record.put("type",old.get("type"));record.put("created",old.get("created"));records.put(id,record);return Map.copyOf(record);}
 private Map<String,String> validate(HttpServletRequest r,Map<String,String> b,String type){Map<String,String> m=new LinkedHashMap<>();for(String k:List.of("title","application","state","incident","problem","description","rca","workaround","solution"))m.put(k,field(b,k,Set.of("description","rca","workaround","solution").contains(k)?4000:200));if(m.get("title").isBlank()||!Set.of("IBS","EIMP").contains(m.get("application"))||!(type.equals("Problem")?Set.of("Open","Investigating","Known Error","Resolved"):Set.of("Draft","Published","Retired")).contains(m.get("state")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Required fields or state");if(!m.get("incident").isBlank()&&incidents.list(r).stream().noneMatch(t->m.get("incident").equals(t.get("id"))&&m.get("application").equals(t.get("application"))))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown incident");if(!m.get("problem").isBlank()){Map<String,String> p=records.get(m.get("problem"));if(p==null||!p.get("type").equals("Problem")||!p.get("application").equals(m.get("application"))||type.equals("Problem"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown problem");}if(Set.of("Known Error","Resolved").contains(m.get("state"))&&m.get("rca").isBlank()||m.get("state").equals("Resolved")&&m.get("solution").isBlank()||m.get("state").equals("Published")&&m.get("solution").isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"RCA or solution required");m.put("updated",Instant.now().toString());return m;}
}
