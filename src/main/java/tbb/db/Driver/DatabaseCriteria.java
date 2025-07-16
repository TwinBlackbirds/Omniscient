package tbb.db.Driver;

import tbb.utils.Logger.LogLevel;
import tbb.utils.Logger.Logger;

public class DatabaseCriteria {
	private boolean exists = false;
	private int cnt = 0;
	private static Logger log = null;
	// add more fields as necessary

	public DatabaseCriteria(Logger _log) {
		log = _log;
	}

	public boolean checkCriteria() {
		if (log == null) {
			System.out.println("ERROR: Logger not initialized properly!");
			return false;
		}
		if (!exists) {
			log.Write(LogLevel.WARN, "Database verification failed: database file does not exist!");
			return false;
		}
		
		if (cnt == 0) {
			log.Write(LogLevel.WARN, "Database verification failed: amount of wikis is zero!");
			return false;
		}
		
		log.Write(LogLevel.INFO, "Database verified successfully!");
		log.close();
		return true;
	}
	public void setCount(int cnt) {
		this.cnt = cnt;
	}
	public void setExists(boolean exists) {
		this.exists = exists;
	}
}

