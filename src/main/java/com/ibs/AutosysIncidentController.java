package com.ibs;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@RestController
@RequestMapping("/api/integration/autosys/incidents")
public class AutosysIncidentController {
 private final ServiceDeskController desk;
 public AutosysIncidentController(ServiceDeskController desk){this.desk=desk;}
 @PostMapping public Map<String,Object> create(@RequestHeader(value="X-Autosys-Key",defaultValue="") String key,@RequestBody Map<String,String> b){
 String expected=System.getenv("AUTOSYS_INTEGRATION_KEY");
 if(!"true".equalsIgnoreCase(System.getenv("AUTOSYS_INTEGRATION_ENABLED"))||expected==null||expected.length()<32||!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
 String reason=b.getOrDefault("reason",""),job=b.getOrDefault("job",""),app=b.getOrDefault("application","");
 if(job==null||app==null||reason==null||!job.matches("[A-Za-z][A-Za-z0-9_]{0,79}")||!Set.of("IBS","EIMP").contains(app)||!job.startsWith(app+"_")||!Set.of("FILE_MISSING","INVALID_FORMAT","DB_CONNECTION","SQL_EXECUTION","PERMISSION_DENIED","RECON_MISMATCH","EXECUTION_TIMEOUT","SLA_BREACH","OPERATOR_FAILURE","FILE_TIMEOUT").contains(reason))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
 String category=switch(reason){case "DB_CONNECTION","SQL_EXECUTION"->"Database";case "PERMISSION_DENIED"->"Infrastructure";default->"Batch";};
 String urgency=Set.of("DB_CONNECTION","SQL_EXECUTION","RECON_MISMATCH","EXECUTION_TIMEOUT","FILE_TIMEOUT").contains(reason)?"1":"2";
 return desk.createAutosys(Map.of("application",app,"title","[TRAINING AUTOSYS] "+job+" "+reason,"impact","2","urgency",urgency,"category",category,"ci",job,"caller","UNIQ AutoSys","description","Simulated batch incident. Execution reference: "+b.getOrDefault("execution",""),"requestId",Objects.toString(b.get("requestId"),"")));
 }
}
