package com.ibs;
import java.sql.*;
import java.util.Properties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class OracleConnectivityProbe implements ApplicationRunner {
 private static final Logger log=LoggerFactory.getLogger(OracleConnectivityProbe.class);
 public void run(ApplicationArguments args) {
  String host=System.getenv("DB_HOST"), port=System.getenv("DB_PORT"), service=System.getenv("DB_SERVICE"), user=System.getenv("DB_USERNAME"), password=System.getenv("DB_PASSWORD");
  if(host==null || port==null || service==null || user==null || password==null || System.getenv("TS_AUTHKEY")==null) { log.info("Oracle diagnostic skipped: configuration incomplete"); return; }
  if(!host.matches("[0-9.]+") || !port.matches("[0-9]+") || !service.matches("[A-Za-z0-9_]+")) { log.warn("Oracle diagnostic skipped: invalid configuration"); return; }
  Thread worker=new Thread(()->{
   Properties p=new Properties();
   p.setProperty("user",user); p.setProperty("password",password);
   p.setProperty("oracle.net.CONNECT_TIMEOUT","10000");
   p.setProperty("oracle.jdbc.ReadTimeout","10000");
   String url="jdbc:oracle:thin:@(DESCRIPTION=(CONNECT_TIMEOUT=10)(TRANSPORT_CONNECT_TIMEOUT=5)(RETRY_COUNT=0)(ADDRESS=(PROTOCOL=TCP)(HOST="+host+")(PORT="+port+")(SOCKS_PROXY=127.0.0.1)(SOCKS_PROXY_PORT=1055))(CONNECT_DATA=(SERVICE_NAME="+service+")))";
   try(Connection c=DriverManager.getConnection(url,p); Statement s=c.createStatement()){
    s.setQueryTimeout(10);
    try(ResultSet r=s.executeQuery("SELECT 1, USER, SYS_CONTEXT('USERENV','CON_NAME') FROM DUAL")) {
     if(r.next()) log.info("Oracle JDBC validation successful: result={}, schema={}, PDB={}",r.getInt(1),r.getString(2),r.getString(3));
    }
    try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME LIKE 'ITSM_%'")) {
     if(r.next()) log.info("Oracle ITSM table count: {}",r.getInt(1));
    }
   } catch(SQLException e){log.warn("Oracle JDBC diagnostic failed: SQLState={}, vendorCode={}",e.getSQLState(),e.getErrorCode());}
   catch(Exception e){log.warn("Oracle JDBC diagnostic failed: {}",e.getClass().getSimpleName());}
  },"oracle-diagnostic");
  worker.setDaemon(true); worker.start();
 }
}
