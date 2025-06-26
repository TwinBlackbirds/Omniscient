// Name: Michael Amyotte
// Date: 6/16/25
// Purpose: SQLite ORM example driver for JScraper template

package tbb.db.Driver;

// database table objects
import tbb.db.Schema.Channel;
import tbb.utils.Logger.LogLevel;
import tbb.utils.Logger.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;



public class Sqlite {
	private Logger log;
	private SessionFactory db; // do not expose to users, instead write methods such as the writeChannel one below
	
	public Sqlite(Logger log) {
		this(log, false);
	}
	
	public Sqlite(Logger log, boolean deleteDB) {
		this.log = log;
		
		Configuration config = new Configuration()
				   .configure(); // use hibernate.cfg.xml
		
		// debug feature
		if (deleteDB) {
			try {
				log.Write(LogLevel.BYPASS, "DEBUG ALERT: Deleting database");
				Files.deleteIfExists(Paths.get("./database.sqlite"));
				
			} catch (IOException e) { 
				log.Write(LogLevel.BYPASS, "Could not delete database! " + e);
			}		
			
		}
		
		log.Write(LogLevel.DBG, "Dialect = " + config.getProperty("hibernate.dialect"));	
		
		
		this.db = config.buildSessionFactory();
	}
	
	// example write method
	public void writeChannel(Channel c) throws Exception {
		try (Session s = db.openSession()){ // try-with-resources
			s.beginTransaction();
			s.persist(c);
			s.getTransaction().commit();
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "WriteChannel operation failed! " + e);
		}
	}
}
