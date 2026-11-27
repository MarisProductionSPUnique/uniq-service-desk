package com.ibs;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/support/training")
public class TrainingSeedController {
 private final ServiceDeskController incidents;
 public TrainingSeedController(ServiceDeskController incidents){this.incidents=incidents;}
 @PostMapping("/samples") public synchronized Map<String,Object> samples(HttpServletRequest r){
 List<Map<String,Object>> existing=incidents.list(r);
 List<String> ids=new ArrayList<>();
 String[][] data={{"IBS","Customer login unavailable","Application","1","1","HTTP 503 during synthetic login test"},{"IBS","Fund transfer timeout","Application","2","1","Synthetic payment timeout; verify request correlation"},{"IBS","Daily reconciliation file missing","Batch","2","2","Training file not received in inbound directory"},{"EIMP","Claims database connection failure","Database","1","1","Synthetic Oracle connectivity failure"},{"EIMP","Policy quotation slow","Application","2","2","Synthetic response latency exceeds threshold"},{"EIMP","Settlement report delayed","Batch","3","2","Synthetic outbound report not generated"}};
 for(int i=0;i<data.length;i++){String[] d=data[i];String key="TRAINING-SAMPLE-"+(i+1);Optional<Map<String,Object>> prior=existing.stream().filter(t->key.equals(t.get("requestId"))).findFirst();Map<String,Object> t=prior.orElseGet(()->incidents.create(r,Map.of("application",d[0],"title","[TRAINING] "+d[1],"category",d[2],"impact",d[3],"urgency",d[4],"description",d[5],"caller","Training User","requestId",key)));ids.add(t.get("id").toString());}
 return Map.of("incidentIds",ids,"sampleCount",ids.size(),"source","SIMULATED");
 }
}
