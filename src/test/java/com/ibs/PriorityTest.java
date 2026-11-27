package com.ibs;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;
class PriorityTest {
 @Test void matrix(){for(int i=1;i<=3;i++)for(int u=1;u<=3;u++)assertEquals("P"+(i+u-1),ServiceDeskController.priority(""+i,""+u));}
 @Test void invalid(){assertThrows(ResponseStatusException.class,()->ServiceDeskController.priority("0","1"));assertThrows(ResponseStatusException.class,()->ServiceDeskController.priority("1","4"));}
}
