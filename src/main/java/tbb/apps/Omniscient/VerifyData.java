package tbb.apps.Omniscient;

import tbb.db.Driver.Sqlite;
import tbb.utils.Logger.LogLevel;
import tbb.utils.Logger.Logger;

public class VerifyData {
	private final static Logger log = new Logger(VerifyData.class, LogLevel.INFO);
	private static final Sqlite sql = new Sqlite(log, false);
	
	public static boolean verify() {
		// walk through the database and report bad data (in both instances and wiki)
		// perform summary of data as well
		// start first by loading all into memory, optimize later
		DatabaseCriteria c = new DatabaseCriteria(new Logger(DatabaseCriteria.class, LogLevel.INFO));
		
		// first check: database exists
		try {
			c.setExists(sql.checkDbExists());
		} catch (Exception e) {
			log.Write(LogLevel.ERROR, "Database file does not exist! Database could not be verified.");
			return false;
		}
		
		// second check: database has entries
		int cnt = sql.countWikis();
		c.setCount(cnt);
		log.Write(LogLevel.DBG, "Amount of wikis recognized: " + cnt);
		
		log.close();
		return c.checkCriteria();
	}
}
