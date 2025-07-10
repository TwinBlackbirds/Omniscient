package tbb.apps.Omniscient;

import tbb.db.Driver.Sqlite;
import tbb.utils.Logger.Logger;

public class VerifyData {
	private static final Logger log = new Logger();
	private static final Sqlite sql = new Sqlite(log);
	
	public static void verify() {
		// walk through the database and report bad data (in both instances and wiki)
		// perform summary of data as well
		sql.countWikis();
	}
}
