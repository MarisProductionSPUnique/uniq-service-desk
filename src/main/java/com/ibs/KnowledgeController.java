package com.ibs;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/library")
public class KnowledgeController {
private final Map<String,Map<String,String>> items=new LinkedHashMap<>();
private int seq=1000;
public KnowledgeController(){
for(String app:List.of("IBS","EIMP")){
seed(app,"SOP","Daily application health check","Check web, application and database connectivity; review errors, latency and resource metrics; record evidence and escalate deviations.","");
seed(app,"KEDB","HTTP 503 — service unavailable","Check web and upstream health, correlate request ID and logs. Escalate confirmed service failure; restart only with approved runbook and authorization.","");
seed(app,"RUNBOOK","Batch file missing","Check expected filename, arrival schedule, inbound directory and transfer logs. Confirm upstream delivery before any authorized rerun.","");
seed(app,"CI","Application service","Training application service; owner and actual endpoint require verification.","");
seed(app,"CI","Database service","Oracle dependency in local training architecture; cloud integration not verified.","Application service");
}}
private void seed(String app,String type,String title,String body,String related){String id=type+"-"+(++seq);items.put(id,new LinkedHashMap<>(Map.of("id",id,"application",app,"type",type,"title",title,"body",body,"related",related,"state","Draft","owner","Training team")));}
private void auth(HttpServletRequest r){if(!SupportPortalController.authorized(r))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
private Map<String,String> validate(Map<String,String>b){Map<String,String> t=new LinkedHashMap<>();for(String k:List.of("application","type","title","body","related","state","owner")){String v=b.getOrDefault(k,"");if(v==null||v.length()>12000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);t.put(k,v.trim());}if(!Set.of("IBS","EIMP").contains(t.get("application"))||!Set.of("SOP","KEDB","RUNBOOK","CI").contains(t.get("type"))||t.get("title").isBlank()||!Set.of("Draft","Published","Retired").contains(t.get("state")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);return t;}
@GetMapping public synchronized List<Map<String,String>> list(HttpServletRequest r){auth(r);return items.values().stream().map(Map::copyOf).toList();}
@PostMapping public synchronized Map<String,String> create(HttpServletRequest r,@RequestBody Map<String,String>b){auth(r);if(items.size()>=500)throw new ResponseStatusException(HttpStatus.CONFLICT);Map<String,String> t=validate(b);String id=t.get("type")+"-"+(++seq);t.put("id",id);items.put(id,t);return Map.copyOf(t);}
@PutMapping("/{id}") public synchronized Map<String,String> update(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String>b){auth(r);if(!items.containsKey(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);Map<String,String> t=validate(b);if(!t.get("type").equals(items.get(id).get("type")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);t.put("id",id);items.put(id,t);return Map.copyOf(t);}
}
