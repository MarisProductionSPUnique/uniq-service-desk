package com.ibs;
import java.util.*;
import java.time.Instant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/work")
public class WorkController {
private final Map<String,Map<String,String>> records=new LinkedHashMap<>();
private int seq=1000;
private void auth(HttpServletRequest r){if(!SupportPortalController.authorized(r))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);}
private void bad(){throw new ResponseStatusException(HttpStatus.BAD_REQUEST);}
private String get(Map<String,String>b,String k){String v=b.getOrDefault(k,"");if(v==null||v.length()>4000)bad();return v.trim();}
@GetMapping public synchronized List<Map<String,String>> list(HttpServletRequest r){auth(r);return records.values().stream().map(Map::copyOf).toList();}
@PostMapping public synchronized Map<String,String> create(HttpServletRequest r,@RequestBody Map<String,String>b){auth(r);if(records.size()>=500)throw new ResponseStatusException(HttpStatus.CONFLICT);String type=get(b,"type"),app=get(b,"application"),title=get(b,"title");if(!Set.of("PRB","CHG","REQ").contains(type)||!Set.of("IBS","EIMP").contains(app)||title.isBlank())bad();String category=get(b,"category");if(type.equals("CHG")&&!Set.of("Standard","Normal","Emergency").contains(category))bad();Map<String,String> t=new LinkedHashMap<>();String id=type+String.format("%07d",++seq);t.put("id",id);t.put("type",type);t.put("application",app);t.put("title",title);t.put("category",category);t.put("state","New");t.put("created",Instant.now().toString());for(String k:List.of("description","related","assignee","rca","workaround","plan","rollback","validation"))t.put(k,get(b,k));t.put("approval","Pending");t.put("history",Instant.now()+" Created");records.put(id,t);return Map.copyOf(t);}
@PutMapping("/{id}") public synchronized Map<String,String> update(HttpServletRequest r,@PathVariable String id,@RequestBody Map<String,String>b){auth(r);Map<String,String> t=records.get(id);if(t==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);String state=get(b,"state"),approval=get(b,"approval"),type=t.get("type");if(t.get("state").equals("Closed")||!Set.of("New","In Progress","On Hold","Resolved","Closed").contains(state)||!Set.of("Pending","Approved","Rejected").contains(approval))bad();if(!type.equals("PRB")&&Set.of("In Progress","Resolved","Closed").contains(state)&&!approval.equals("Approved"))bad();if(type.equals("PRB")&&Set.of("Resolved","Closed").contains(state)&&get(b,"rca").isBlank())bad();if(type.equals("CHG")&&Set.of("In Progress","Resolved","Closed").contains(state)&&(get(b,"plan").isBlank()||get(b,"rollback").isBlank()))bad();if(Set.of("Resolved","Closed").contains(state)&&get(b,"validation").isBlank())bad();if(state.equals("Closed")&&!t.get("state").equals("Resolved"))bad();String history=t.get("history")+"\n"+Instant.now()+" "+t.get("state")+" -> "+state+" | Approval: "+approval+" | "+get(b,"note");if(history.length()>60000)throw new ResponseStatusException(HttpStatus.CONFLICT);for(String k:List.of("description","related","assignee","rca","workaround","plan","rollback","validation"))t.put(k,get(b,k));t.put("state",state);t.put("approval",approval);t.put("history",history);return Map.copyOf(t);}
}
