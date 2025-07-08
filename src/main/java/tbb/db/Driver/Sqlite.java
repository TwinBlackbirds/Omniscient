// Name: Michael Amyotte
// Date: 6/16/25
// Purpose: SQLite ORM example driver for JScraper template

package tbb.db.Driver;

import tbb.db.Schema.Instance;
// database table objects
import tbb.db.Schema.Wiki;
import tbb.utils.Logger.LogLevel;
import tbb.utils.Logger.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;

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
				
			} catch (FileSystemException fex) {
				log.Write(LogLevel.BYPASS, "Could not delete database! File is in use by another process! Exiting...");
				System.exit(1);
			} catch (IOException e) { 
				log.Write(LogLevel.BYPASS, "Could not delete database! " + e);
			}		
			
		}
		
		log.Write(LogLevel.DBG, "Dialect = " + config.getProperty("hibernate.dialect"));	
		
		
		this.db = config.buildSessionFactory();
	}
	
	public boolean findWiki(String id) {
		try (Session s = db.openSession()){ // try-with-resources
			Wiki o = s.find(Wiki.class, id);
			if (o != null) {
				return true;
			}
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "findWikiByUrl operation failed! " + e);
		}
		return false;
	}
	public int countWikis() {
		try (Session s = db.openSession()){ // try-with-resources
			return s.createNativeQuery("SELECT COUNT(*) from wikis", int.class).getSingleResult();
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "countWikis operation failed! " + e);
		}
		return 0;
	}
	
	public Wiki getLastWiki() {
		try (Session s = db.openSession()){ // try-with-resources
			return s.createQuery("FROM Wiki ORDER BY timeCollected DESC", Wiki.class)
	                .setMaxResults(1)
	                .uniqueResult();
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "getLastWiki operation failed! " + e);
		}
		return null;
	}
	
	public void writeWiki(Wiki w) throws Exception {
		try (Session s = db.openSession()){ // try-with-resources
			s.beginTransaction();
			s.persist(w);
			s.getTransaction().commit();
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "WriteWiki operation failed! " + e);
		}
	}
	public Instance startInstance() {
		Instance i = new Instance();
		writeInstance(i);
		return i;
	}
	public Instance startInstance(
			int totalArticles,
			int partitionSize,
			int blockSize,
			int maxChildren,
			int extraWaitMs,
			int timeoutSec
			) {
		Instance i = new Instance(totalArticles, partitionSize, blockSize, maxChildren, extraWaitMs, timeoutSec);
		writeInstance(i);
		return i;
	}
	protected Instance findInstance(String ID) {
		try (Session s = db.openSession()){ // try-with-resources
			return s.find(Instance.class, ID);
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "findInstance operation failed! " + e);
		}
		return null;
	}
	protected void writeInstance(Instance i) {
		try (Session s = db.openSession()){ // try-with-resources
			s.beginTransaction();
			s.persist(i);
			s.getTransaction().commit();
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "writeInstance operation failed! " + e);
		}
	}
	
	protected void updateInstance(Instance i) {
		try (Session s = db.openSession()){ // try-with-resources
			s.beginTransaction();
			s.merge(i);
			s.getTransaction().commit();
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "updateInstance operation failed! " + e);
		}
	}
	
	public <T> void updateInstanceField(Instance i, String fieldName, T value) {
		for (Field f : i.getClass().getDeclaredFields()) {
			if (f.getName().equals(fieldName)) {
				try {
					Class<?> fieldType = f.getType();
					Class<?> valueType = value.getClass();
					if (fieldType.isPrimitive()) {
						fieldType = getWrapperClass(fieldType);
					}
					if (!fieldType.isAssignableFrom(valueType)) {
						String msg = String.format("Wrong data type for Instance field %s. (Expecting %s, got %s)", 
												   f.getName(), f.getType(), value.getClass());
						log.Write(LogLevel.ERROR, msg);
						continue;
					}
					f.set(i, value);
				} catch (Exception ex) {
					log.Write(LogLevel.ERROR, "Could not update Instance field " + f.getName() + "(using fieldName " + fieldName + ")");
				}
				updateInstance(i);
				return;
			}
		}
		log.Write(LogLevel.ERROR, "Could not find Instance field (using fieldName " + fieldName + ")");
	}
	
	private Class<?> getWrapperClass(Class<?> primitiveType) {
	    if (primitiveType == int.class) return Integer.class;
	    if (primitiveType == long.class) return Long.class;
	    if (primitiveType == boolean.class) return Boolean.class;
	    if (primitiveType == byte.class) return Byte.class;
	    if (primitiveType == char.class) return Character.class;
	    if (primitiveType == float.class) return Float.class;
	    if (primitiveType == double.class) return Double.class;
	    if (primitiveType == short.class) return Short.class;
	    return primitiveType; // If it's not a primitive, just return it as-is
	}
	
	public void endInstance(Instance i) {
		// TODO: ensure all fields are neatly wrapped up
		LocalDateTime completionTimestamp = LocalDateTime.now();
		updateInstanceField(i, "timeOmniscientCompleted", completionTimestamp);
		long omniRunningMs = Duration.between(i.timeOmniscientStarted, completionTimestamp).toMillis();
		updateInstanceField(i, "timeOmniscientRunningMs", omniRunningMs);
		
		updateInstanceField(i, "unaccountedRuntimeMs", omniRunningMs - (i.timeDispatcherRunningAvgMs + i.timeBotsRunningAvgMs + i.timeSpiderRunningAvgMs));
		
		if (i.linksScraped == i.linksWanted) {
			updateInstanceField(i, "wasSuccessful", true);
		}
	}
}
