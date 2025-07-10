// Name: Michael Amyotte
// Date: 7/10/2025
// Purpose: Sub-class to hold all of the fields for App to improve readability

package tbb.apps.Omniscient;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

import org.openqa.selenium.chrome.ChromeOptions;

import tbb.db.Driver.Sqlite;
import tbb.db.Schema.Instance;
import tbb.utils.Config.ConfigPayload;
import tbb.utils.Config.Configurator;
import tbb.utils.Logger.Logger;

public class AppFields {

	
	// configuration
	protected static final ConfigPayload config = new Configurator().getData();
	protected static Logger log = new Logger(config.minLogLevel); 
	// selenium browser tools
	protected static ChromeOptions co = null;
	
	// consts
	protected static final int MAX_RETRIES = config.MAX_RETRIES; // if page fails to load (cd.get())
	protected static final int TOTAL_ARTICLES = config.TOTAL_ARTICLES; //500;
	protected static final int MAX_CHILDREN = config.MAX_CHILDREN; //10; // amount of child procs dispatch() is allowed to spawn
	protected static final int PARTITION_COUNT = config.PARTITION_COUNT;
	protected static final int EXTRA_WAIT_MS = config.EXTRA_WAIT_MS; // extra time spent waiting after el is present
	protected static final int TIMEOUT_SEC = config.TIMEOUT_SEC; // time to wait for el to be present
	
	// calculated consts
	protected static final int PARTITION_SIZE = (int)Math.ceil(TOTAL_ARTICLES/PARTITION_COUNT);
	protected static final int BLOCK_SIZE = (int)Math.ceil(PARTITION_SIZE / MAX_CHILDREN);
	
	// shared identification pools for duplicate reduction
	protected static ConcurrentLinkedQueue<String> allLinks = new ConcurrentLinkedQueue<String>();
	protected static ConcurrentLinkedQueue<String> currentLinks = new ConcurrentLinkedQueue<String>();
	protected static ConcurrentLinkedQueue<String> visitedLinks = new ConcurrentLinkedQueue<String>();
	protected static ConcurrentLinkedQueue<String> scrapedLinks = new ConcurrentLinkedQueue<String>();
	
	// db
	protected static Sqlite sql = new Sqlite(log, config.DEBUG_MODE); // boolean = debug mode (delete db)
	protected static Instance instance = sql.startInstance(TOTAL_ARTICLES, PARTITION_SIZE, 
														 BLOCK_SIZE, MAX_CHILDREN, 
														 EXTRA_WAIT_MS, TIMEOUT_SEC);
	protected static final int START_COUNT = sql.countWikis(); // get original amount of entries in db to track amount of changes
	
	// url handling
	protected static final String[] BANNED_URL_FRAGMENTS = {
			"/wiki/Help:", 
			"/wiki/Special:",
			"/wiki/Wikipedia:",
			"/wiki/Category:",
			"/wiki/File:",
			"/wiki/Template:",
			"/wiki/Template_talk:",
			"/wiki/Portal:",
			"/wiki/Talk:"
	};
	

	// for data cleaning
	protected static final String[] BANNED_TEXT_CHUNKS = {
			"div.reflist",
			"div.refbegin",
			"#catlinks",
			"div.navbox",
			".metadata.sister-bar",
			".mw-heading + ul",
	};
	protected static final Pattern splitPatt = Pattern.compile("(Notes|References) ?(\\[edit\\])?\n\n");
	
}
