package com.ibs;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class AutosysIncidentTest {
 @Test void deduplication(){
 var desk=new ServiceDeskController();
 var body=Map.of("application","IBS","title","Training batch failure","impact","2","urgency","1","category","Database","requestId","AUTOSYS-"+"a".repeat(64));
 var first=desk.createAutosys(body);var second=desk.createAutosys(body);
 assertEquals(first.get("id"),second.get("id"));assertEquals("P2",first.get("priority"));assertEquals("Database Support",first.get("group"));
 }
 @Test void invalidReference(){assertThrows(ResponseStatusException.class,()->new ServiceDeskController().createAutosys(Map.of("requestId","invalid")));}
 @Test void rejectsUnauthorized(){var c=new AutosysIncidentController(new ServiceDeskController());assertEquals(401,assertThrows(ResponseStatusException.class,()->c.create("",Map.of())).getStatusCode().value());}
}
