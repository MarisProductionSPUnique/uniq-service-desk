package com.ibs;
import java.time.Instant;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/support/inventory")
public class RequestCmdbController {
 private final Map<String,Map<String,String>> requests=new LinkedHashMap<>(), cis=new LinkedHashMap<>();
 private final List<String> catalog=List.of("Application Access","Password Reset","Database Read Access","Batch Rerun","Report Request","Software Installation");
 private int reqSeq=1000,ciSeq=1000;
 private void auth(HttpServletRequest r){if(!SupportPortalController.authorized(r))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
 private void require(boolean ok,String reason){if(!ok)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,reason);}
 private String field(Map<String,String>b,String k,int max){String v=b.getOrDefault(k,"");require(v!=null&&v.length()<=max,k);return v.trim();}
 private Map<String,String> fields(Map<String,String>b,String...keys){Map<String,String> m=new LinkedHashMap<>();for(String k:keys)m.put(k,field(b,k,Set.of("description","note").contains(k)?2000:200));return m;}
 @GetMapping("/catalog") public List<String> catalog(HttpServletRequest r){auth(r);return catalog;}
 @GetMapping("/requests") public synchronized List<Map<String,String>> requests(HttpServletRequest r){auth(r);return requests.values().stream().map(Map::copyOf).toList();}
 @PostMapping("/requests") public synchronized Map<String,String> createRequest(HttpServletRequest r,@RequestBody Map<String,String>b){auth(r);if(requests.size()>=200)throw new ResponseStatusException(HttpStatus.CONFLICT,"Capacity");Map<String,String> m=fields(b,"item","application","caller","description");require(catalog.contains(m.get("item"))&&Set.of("IBS","EIMP").contains(m.get("application"))&&!m.get("caller").isBlank()&&!m.get("description").isBlank(),"Required request fields");m.put("id",String.format("REQ%07d",++reqSeq));m.put("state","Requested");m.put("created",Instant.now().toString());m.put("history","Requested "+Instant.now());requests.put(m.get("id"),m);return Map.copyOf(m);}
 @PutMapping("/requests/{id}") public synchronized Map<String,String> updateRequest(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String>b){auth(r);Map<String,String> old=requests.get(id);if(old==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);String from=old.get("state"),to=field(b,"state",30),note=field(b,"note",2000);Set<String> allowed=switch(from){case "Requested"->Set.of("Approved","Rejected","Cancelled");case "Approved"->Set.of("In Progress","Cancelled");case "In Progress"->Set.of("Fulfilled","Cancelled");case "Fulfilled"->Set.of("Closed");default->Set.of();};require(allowed.contains(to)&&!note.isBlank(),"Transition and note required");Map<String,String> m=new LinkedHashMap<>(old);m.put("state",to);m.put("history",old.get("history")+"\n"+Instant.now()+" "+from+" -> "+to+" | "+note);requests.put(id,m);return Map.copyOf(m);}
 @GetMapping("/cis") public synchronized List<Map<String,String>> cis(HttpServletRequest r){auth(r);return cis.values().stream().map(Map::copyOf).toList();}
 @PostMapping("/cis") public synchronized Map<String,String> createCi(HttpServletRequest r,@RequestBody Map<String,String>b){auth(r);if(cis.size()>=200)throw new ResponseStatusException(HttpStatus.CONFLICT,"Capacity");Map<String,String> m=validateCi(b,"");m.put("id",String.format("CI%07d",++ciSeq));cis.put(m.get("id"),m);return Map.copyOf(m);}
 @PutMapping("/cis/{id}") public synchronized Map<String,String> updateCi(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String>b){auth(r);if(!cis.containsKey(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND);Map<String,String> m=validateCi(b,id);require(m.get("application").equals(cis.get(id).get("application")),"Application cannot change");m.put("id",id);cis.put(id,m);return Map.copyOf(m);}
 private Map<String,String> validateCi(Map<String,String>b,String id){Map<String,String> m=fields(b,"name","application","type","status","environment","owner","dependsOn","description");require(!m.get("name").isBlank()&&Set.of("IBS","EIMP").contains(m.get("application"))&&Set.of("Web Server","Application Server","Database","Application","API","Batch Job").contains(m.get("type"))&&Set.of("Operational","Degraded","Down","Retired").contains(m.get("status"))&&Set.of("DEV","SIT","PROD Training").contains(m.get("environment")),"Invalid CI fields");String dep=m.get("dependsOn");Set<String> visited=new HashSet<>();while(!dep.isBlank()){require(!dep.equals(id)&&visited.add(dep),"Dependency cycle");Map<String,String> parent=cis.get(dep);require(parent!=null&&parent.get("application").equals(m.get("application")),"Unknown dependency");dep=parent.get("dependsOn");}return m;}
}
