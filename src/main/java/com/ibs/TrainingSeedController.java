package com.ibs;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/support/training")
public class TrainingSeedController {
 private final ServiceDeskController incidents;
 private final KnowledgeProblemController knowledge; private final ChangeController changes; private final RequestCmdbController inventory;
 public TrainingSeedController(ServiceDeskController incidents,KnowledgeProblemController knowledge,ChangeController changes,RequestCmdbController inventory){this.incidents=incidents;this.knowledge=knowledge;this.changes=changes;this.inventory=inventory;}
 @PostMapping("/samples") public synchronized Map<String,Object> samples(HttpServletRequest r){
 List<Map<String,Object>> existing=incidents.list(r);
 List<String> ids=new ArrayList<>();
 String[][] data={{"IBS","Customer login unavailable","Application","1","1","HTTP 503 during synthetic login test"},{"IBS","Fund transfer timeout","Application","2","1","Synthetic payment timeout; verify request correlation"},{"IBS","Daily reconciliation file missing","Batch","2","2","Training file not received in inbound directory"},{"EIMP","Claims database connection failure","Database","1","1","Synthetic Oracle connectivity failure"},{"EIMP","Policy quotation slow","Application","2","2","Synthetic response latency exceeds threshold"},{"EIMP","Settlement report delayed","Batch","3","2","Synthetic outbound report not generated"}};
 for(int i=0;i<data.length;i++){String[] d=data[i];String key="TRAINING-SAMPLE-"+(i+1);Optional<Map<String,Object>> prior=existing.stream().filter(t->key.equals(t.get("requestId"))).findFirst();Map<String,Object> t=prior.orElseGet(()->incidents.create(r,Map.of("application",d[0],"title","[TRAINING] "+d[1],"category",d[2],"impact",d[3],"urgency",d[4],"description",d[5],"caller","Training User","requestId",key)));ids.add(t.get("id").toString());}
 return Map.of("incidentIds",ids,"sampleCount",ids.size(),"source","SIMULATED");
 }
 @PostMapping("/module-samples") public synchronized Map<String,Object> moduleSamples(HttpServletRequest r){
 samples(r);List<String> ids=new ArrayList<>();
 for(String app:List.of("IBS","EIMP")){
 String issue=app.equals("IBS")?"Login unavailable":"Claims database unavailable";
 String incident=incidents.list(r).stream().filter(t->app.equals(t.get("application"))&&("[TRAINING] "+(app.equals("IBS")?"Customer login unavailable":"Claims database connection failure")).equals(t.get("title"))).findFirst().orElseThrow().get("id").toString();
 String problem="";
 for(String type:List.of("Problem","KEDB","SOP","Runbook")){
 String title="[TRAINING] "+app+" "+type+" - "+issue;
 Map<String,String> m=new LinkedHashMap<>();m.put("application",app);m.put("type",type);m.put("title",title);m.put("state",type.equals("Problem")?"Known Error":"Published");m.put("incident",incident);m.put("problem",type.equals("Problem")?"":problem);m.put("description","Fictional connectivity incident for support practice.");m.put("rca","Simulated database connectivity failure; not a verified live fault.");m.put("workaround","Escalate to database support and communicate service impact.");m.put("solution","Check health and logs, validate connectivity with approved read-only checks, obtain approval before remediation, retest and document evidence.");
 Map<String,String> row=knowledge.list(r).stream().filter(x->title.equals(x.get("title"))).findFirst().orElseGet(()->knowledge.create(r,m));ids.add(row.get("id"));if(type.equals("Problem"))problem=row.get("id");
 }
 String title="[TRAINING] "+app+" connectivity configuration review";
 ids.add(changes.list(r).stream().filter(x->title.equals(x.get("title"))).findFirst().orElseGet(()->changes.create(r,Map.of("title",title,"application",app,"type","Normal","risk","Medium","impact","Training environment only","implementation","Review approved connectivity configuration","rollback","Restore previous approved configuration","incident",incident))).get("id"));
 String desc="[TRAINING] "+app+" read-only access for support investigation";
 ids.add(inventory.requests(r).stream().filter(x->desc.equals(x.get("description"))).findFirst().orElseGet(()->inventory.createRequest(r,Map.of("item","Database Read Access","application",app,"caller","Training User","description",desc))).get("id"));
 String parent="";
 for(String type:List.of("Database","Application Server","Web Server")){
 String name="[TRAINING] "+app+" "+type;String dep=parent;
 Map<String,String> ci=inventory.cis(r).stream().filter(x->name.equals(x.get("name"))).findFirst().orElseGet(()->inventory.createCi(r,Map.of("name",name,"application",app,"type",type,"status","Operational","environment","PROD Training","owner","Training Support","dependsOn",dep,"description","Fictional CI; not live discovery")));
 parent=ci.get("id");ids.add(parent);
 }
 }
 return Map.of("recordIds",ids,"sampleCount",ids.size(),"source","SIMULATED");
 }
}
