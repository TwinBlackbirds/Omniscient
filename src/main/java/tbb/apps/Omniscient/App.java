// Scraper Template  Application
// Author: Michael Amyotte (twinblackbirds)
// Date: 6/23/25
// Purpose: Template for Java web scraper applications
// 

package tbb.apps.Omniscient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.sqlite.SQLiteException;
import org.hibernate.exception.GenericJDBCException;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.openqa.selenium.support.ui.WebDriverWait;

import tbb.db.Driver.Sqlite;
import tbb.db.Schema.Instance;
import tbb.db.Schema.Wiki;
import tbb.utils.Config.ConfigPayload;
import tbb.utils.Config.Configurator;
import tbb.utils.Logger.LogLevel;
import tbb.utils.Logger.Logger;
import tbb.utils.Printer.Printer;
import tbb.utils.Printer.State;

public class App 
{
	// configuration
	private static final ConfigPayload config = new Configurator().getData();
	private static Logger log = new Logger(config.minLogLevel); 
	
	// consts
//	private static final int MAX_RETRIES = config.MAX_RETRIES; // if page fails to load (cd.get())
	private static final int TOTAL_ARTICLES = config.TOTAL_ARTICLES; //500;
	private static final int MAX_WORKERS = config.MAX_WORKERS; //10; // amount of child procs dispatch() is allowed to spawn
	private static final int PARTITION_COUNT = config.PARTITION_COUNT;
	private static final int EXTRA_WAIT_MS = config.EXTRA_WAIT_MS; // extra time spent waiting after el is present
	private static final int TIMEOUT_SEC = config.TIMEOUT_SEC; // time to wait for el to be present
	
	// calculated consts
	private static final int PARTITION_SIZE = (int)Math.ceil(TOTAL_ARTICLES/PARTITION_COUNT);
	private static final int BLOCK_SIZE = (int)Math.ceil(PARTITION_SIZE / MAX_WORKERS);
	
	// url handling
	private static final String[] BANNED_URL_FRAGMENTS = {
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
	private static final String[] BANNED_TEXT_CHUNKS = {
			"div.reflist",
			"div.refbegin",
			"#catlinks",
			"div.navbox",
			".metadata.sister-bar",
			".mw-heading + ul",
	};
	private static final Pattern splitPatt = Pattern.compile("(Notes|References) ?(\\[edit\\])?\n\n");
	
	// shared identification pools for duplicate reduction
	private static ConcurrentLinkedQueue<String> allLinks = new ConcurrentLinkedQueue<String>();
	private static ConcurrentLinkedQueue<String> currentLinks = new ConcurrentLinkedQueue<String>();
	private static ConcurrentLinkedQueue<String> visitedLinks = new ConcurrentLinkedQueue<String>();
	private static ConcurrentLinkedQueue<String> scrapedLinks = new ConcurrentLinkedQueue<String>();
	
	// db
	private static Sqlite sql = new Sqlite(log, config.DEBUG_MODE); // boolean = debug mode (delete db)
	private static Instance instance = sql.startInstance(TOTAL_ARTICLES, PARTITION_SIZE, 
														 BLOCK_SIZE, MAX_WORKERS, 
														 EXTRA_WAIT_MS, TIMEOUT_SEC);
	private static final int START_COUNT = sql.countWikis(); // get original amount of entries in db to track amount of changes
	
	// selenium browser tools
	private static ChromeOptions co = null;
	
	
	// TODO: sub-program or feature flag that enables 'verify' mode: check the links in database are valid
    public static void main( String[] args )
    {
    	// end-user feedback
//    	Printer.startBox("Omniscient");
    	
    	// ensure we wrap up shop before closing (CTRL-C)
    	Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    		log.close();
    		// wrap up the instance variables that we must calculate in this scope before passing it off 
    		if (instance.linksCollected == 0) instance.linksCollected = allLinks.size();
    		if (instance.linksScraped == 0) instance.linksScraped = sql.countWikis() - START_COUNT;
    		
		    sql.endInstance(instance);
	        System.out.println("Process terminated with return code 0");	
    	}));
    	
    	// rudimentary way to check if we are running in eclipse
    	boolean isJar = App.class.getResource("App.class").toString().startsWith("jar");
    	if (!isJar) sql.updateInstanceField(instance, "runningInEclipse", true);
    	
    	log.Write(LogLevel.BYPASS, "Environment detected: " + (isJar ? "JAR" : "Eclipse (.class)"));
    	log.Write(LogLevel.BYPASS, "Headless mode: " + (config.headless ? "enabled" : "disabled"));
    	log.Write(LogLevel.INFO, String.format("Configured to get [ %d ] articles in total", TOTAL_ARTICLES));
    	
    	try {
    		handleBots();	
    	}
    	catch (Exception e) {
    		log.Write(LogLevel.ERROR, "Supervisor failed! " + e);
    	} 
    	
    	
    }
    private static void handleBots() {
    	WebDriver cd = null;
    	ArrayList<Duration> spiderTimes = new ArrayList<Duration>();
    	ArrayList<Duration> dispatcherTimes = new ArrayList<Duration>();
    	ArrayList<Duration> botTimes = new ArrayList<Duration>();
    	// do it in 8 chunks
    	for (int i = 0; i < PARTITION_COUNT; i++) {
    		/*
    		 * 
    		 * Spider
    		 * 
    		 */
    		// TODO: multi-threaded spider
    		// they all grab their own links (amount = block_size)
    		// they congregate their lists and remove duplicates
    		// if they have not met the quota, go again
    		
    		// alternative TODO: optimize single-threaded link collection
    		LocalDateTime spiderStart = LocalDateTime.now();
    		try {
	    		cd = makeChromeInstance();
	    		spider(cd, PARTITION_SIZE); 	
	    		log.Write(LogLevel.INFO, "Exiting spider (round " + (i+1) + "/" + PARTITION_COUNT + ")");
	    		cd.quit(); cd = null;
	    	} catch (Exception e) {
	    		log.Write(LogLevel.ERROR, "Spider failed! " + e);
	    	} finally {
	            // close browser + all tabs
	            if (cd != null) {
	            	log.Write(LogLevel.INFO, "Closing Chrome browser"); 
		            cd.quit();
	            }
	    	}
    		LocalDateTime spiderEnd = LocalDateTime.now();
    		spiderTimes.add(Duration.between(spiderStart, spiderEnd));
    		sql.updateInstanceField(instance, "linksCollected", (long) allLinks.size());
    		
    		/*
    		 * 
    		 * Dispatcher
    		 * 
    		 */
	    	try {
	    		Thread.sleep(3000);	
	    	} catch (Exception e) {}
	    	
	    	try {
	    		LocalDateTime dispatcherStart = LocalDateTime.now();
	    		LocalDateTime dispatcherEnd = dispatcher(); // is also 'botStart'
	    		
	    		dispatcherTimes.add(Duration.between(dispatcherStart, dispatcherEnd));
	    		
	    		LocalDateTime botEnd = LocalDateTime.now();
		    	botTimes.add(Duration.between(dispatcherEnd, botEnd));
	    		
	    	} catch (Exception e) {
	    		log.Write(LogLevel.ERROR, "Dispatcher failed! " + e);
	    	}
	    	
    	}
    	
    	sql.updateInstanceField(instance, "timeSpiderRunningAvgMs", averageDuration(spiderTimes));
    	sql.updateInstanceField(instance, "timeDispatcherRunningAvgMs", averageDuration(dispatcherTimes));
    	sql.updateInstanceField(instance, "timeBotsRunningAvgMs", averageDuration(botTimes));
    }

    private static void spider(WebDriver cd, int amtOfLinks) throws Exception {
    	log.Write(LogLevel.INFO, String.format("Starting spider process to collect partition size (%d) links", amtOfLinks));
    	int startingSize = allLinks.size();
    	Wiki recent = sql.getLastWiki();
    	if (recent == null) {
    		navigateTo(cd, "https://en.wikipedia.org/wiki/Main_Page");
    	} else {
    		navigateTo(cd, recent.id);
    	}
    	
    	while (allLinks.size() < startingSize + amtOfLinks) {
    		log.Write(LogLevel.INFO, "Collecting URLs on page : " + getPrettyPageTitle(cd));
        	List<String> links = getUniqueValidLinks(cd, amtOfLinks);
        	
        	if (links.size() == 0) {
        		log.Write(LogLevel.WARN, "No URLs on page : " + getPrettyPageTitle(cd));
        		links.add(panicForURL(cd));
        	}
        	currentLinks.addAll(links);
        	
        	int count = 0;
        	String link = links.getFirst();
        	// find next valid page
        	// db operation is expensive that is why it is not done in getUniqueValidLinks
        	while (visitedLinks.contains(link) || sql.findWiki(link)) { 
        		count++;
        		link = links.get(count);
        	}
        	if (link != "#!panic") navigateTo(cd, link);
    	}
    }
    
    
    private static String getPrettyPageTitle(WebDriver cd) {
    	return cd.getTitle().replace("- Wikipedia", "");
    }
    
    private static LocalDateTime dispatcher() throws Exception {
    	
    	log.Write(LogLevel.INFO, "Dispatching to collector processes");
    	ExecutorService es = Executors.newFixedThreadPool(MAX_WORKERS);
    	
    	List<Future<?>> tasks = new ArrayList<>();
    	// create tasks
        while (!currentLinks.isEmpty()) {
            String[] urls = grabRange(BLOCK_SIZE);
            if (urls == null) {
            	currentLinks.clear();
            	break;
            }
            log.Write(LogLevel.INFO, String.format(
                    "Dispatching scraper task with %d URLs. Remaining in queue: %d", 
                    urls.length, currentLinks.size()));
            if (urls.length > 0) {
                tasks.add(es.submit(() -> {
                	WebDriver cd = null;
                	while (cd == null) {
                		try {
                			cd = makeChromeInstance();
                		} catch (Exception e) {
                			log.Write(LogLevel.ERROR, "Failed to start browser! Retrying..");
                			try {Thread.sleep(3000);} catch (Exception ex) {}
                		}
                		
                	}
                	try {
                        scraper(cd, urls);
                        // updated scraped count field
                		long endCount = sql.countWikis();
                		sql.updateInstanceField(instance, "linksScraped", (long) endCount-START_COUNT);
                    } catch (Exception e) {
                        log.Write(LogLevel.ERROR, "Scraper failed! " + e);
                    } finally {
                    	cd.quit();
                    }
                }));
            }
            Thread.sleep(1000); // stagger tasks
        }
        LocalDateTime dispatchEnd = LocalDateTime.now();
        es.shutdown();

        
        // Wait for all tasks to complete
        for (Future<?> task : tasks) {
            try {
                task.get(); // this blocks until that specific task finishes
            } catch (ExecutionException | InterruptedException e) {
                log.Write(LogLevel.ERROR, "Task failed: " + e.getMessage());
                task.cancel(true);
            } catch (UnreachableBrowserException e) {}
        }
        return dispatchEnd;
    }
    
    private static void scraper(WebDriver tab, String[] urls) throws Exception {
     	for (String url : urls) {
    		navigateTo(tab, url);
    		String currUrl = tab.getCurrentUrl();
    		if (scrapedLinks.contains(currUrl) || sql.findWiki(currUrl)) { // extra layer of duplicate protection
    			log.Write(LogLevel.WARN, "Skipping duplicate entry " + currUrl);
    			continue;
    		}
    		log.Write(LogLevel.INFO, "Collecting data from page : " + getPrettyPageTitle(tab));
    		sendState(tab, State.COLLECTING);
    		// collect main body of text from page
    		WebElement mainChunk = tab.findElement(By.cssSelector("div[class*='mw-content-ltr']"));
    		String mainText = mainChunk.getText();
    		
    		// grab text of stuff we want to separate
    		List<WebElement> refLists = new ArrayList<WebElement>();
    		for (String selector : BANNED_TEXT_CHUNKS) {
    			refLists.addAll(tab.findElements(By.cssSelector(selector)));
    		}
    		
    		// get infobox text separately for data integrity
    		boolean hasInfoBox = (tab.findElements(By.cssSelector("table.infobox")).size() > 0);
    		String infoBoxText = null;
    		if (!hasInfoBox) {
    			log.Write(LogLevel.DBG, "DBG WARN: No infobox located on the page " + tab.getCurrentUrl());
    		} else {
    			infoBoxText = tab.findElement(By.cssSelector("table.infobox")).getText();	
    		}
    		
    		// separate the texts
    		ArrayList<String> refTexts = new ArrayList<String>();
    		for (WebElement ref : refLists) {
    			refTexts.add(ref.getText());
    		}

    		// join refTexts
    		StringBuilder sb = new StringBuilder();
    		for (String text : refTexts) {
    			mainText = mainText.replace(text, "");
    			sb.append(text).append("\n\n\n");
    		}
    		if (hasInfoBox) mainText = mainText.replace(infoBoxText, "");
    		
    		// remove the extra bad-quality data left behind
    		mainText = splitPatt.split(mainText)[0];
    		
    		WebElement titleEl = tab.findElement(By.cssSelector("main h1"));
    		
    		// create our wiki object
    		Wiki w = new Wiki();
    		w.id = tab.getCurrentUrl();
    		w.title = titleEl.getText();
    		w.content = mainText;
    		w.infoBoxContent = infoBoxText;
    		w.links = sb.toString();
    		
    		if (w.content.trim() == "" || w.title.trim() == "") {
    			log.Write(LogLevel.ERROR, "Could not extract data for page " + w.id);
    			sendState(tab, State.WAITING);
    			continue;
    		}
    		log.Write(LogLevel.DBG, "Done collecting data : " + w.title); 
    		
    		// send it to database
    		try {
    			sql.writeWiki(w);
    			
        		log.Write(LogLevel.INFO, "Saved data to database : " + w.title); 
    		} catch (GenericJDBCException ex) {
    			if (ex.getCause() instanceof SQLiteException && ex.getMessage().contains("SQLITE_CONSTRAINT_PRIMARYKEY")) {
    				// swallow duplicate error (just skip it)
    				log.Write(LogLevel.WARN, "Attempted to insert duplicate entry: " + w.id);
    				if (!allLinks.contains(w.id)) allLinks.add(w.id);
    			} else {
    				throw ex;
    			}
    		} finally {
    			scrapedLinks.add(w.id);
    			sendState(tab, State.WAITING);
    		}
		}
    }
    
    public static ChromeDriver makeChromeInstance() {
    	if (co == null) {
        	boolean headless = config.headless;
        	        	
        	// set launch options
    		log.Write(LogLevel.DBG, "Setting Chrome launch options");
        	ChromeOptions cOpts = new ChromeOptions();
        	if (headless) { cOpts.addArguments("headless"); }
        	co = cOpts;
    	}
    	
    	// start driver
    	log.Write(LogLevel.DBG, "Starting Chrome browser instance");
    	return new ChromeDriver(ChromeDriverService.createDefaultService(), co);
    }
    
    public static String[] grabRange(int amount) {
    	// TODO: concatenate array if larger than TOTAL_ARTICLES ?
        ArrayList<String> newList = new ArrayList<String>();
        String pop = currentLinks.poll();
        int count = 0;
    	while (pop != null && count < amount)  {
        	newList.add(pop);
    		pop = currentLinks.poll();
    		count++;
        }   
    	if (newList.isEmpty()) {
    		return null;
    	}
    	return newList.toArray(new String[0]);
    }
    
    private static List<String> getUniqueValidLinks(WebDriver driver, int amount) {
    	sendState(driver, State.COLLECTING);
    	List<WebElement> links = driver.findElements(By.cssSelector("div.mw-content-ltr a[href*='/wiki']"));
    	ArrayList<String> hrefs = new ArrayList<String>();
    	int count = 0;
    	for (WebElement link : links) {
    		String href = link.getAttribute("href");
    		if (isUrlEnglish(href) && isUrlWiki(href) && !allLinks.contains(href) && count < amount) {
    			hrefs.add(href);
    			allLinks.add(href);
    			count++;
    		}
    	}
    	sendState(driver, State.WAITING);
    	return hrefs;
    }
    
    private static void navigateTo(WebDriver driver, String URL) {
    	sendState(driver, State.NAVIGATING);
    	driver.get(URL);
    	waitUntilPageLoaded(driver);
    	visitedLinks.add(URL);
    }
    
    // wait for TIMEOUT_SEC seconds OR until the DOM reports readyState = complete
    private static void waitUntilPageLoaded(WebDriver driver) {
    	sendState(driver, State.LOADING);
    	String pageName = driver.getTitle();
    	log.Write(LogLevel.DBG, String.format("Waiting for page '%s' to load", pageName));
    	new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEC)).until(
                webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState")
                    .equals("complete")
            );
    	log.Write(LogLevel.DBG, "Page loaded");
    	sendState(driver, State.WAITING);
    }
    
    private static boolean isUrlWiki(String url) {
    	for (String frag : BANNED_URL_FRAGMENTS) {
    		if (url.contains(frag)) return false;
    	}
    	if (!url.contains("wikipedia.org")) return false;
    	return true;
    }
    
    private static boolean isUrlEnglish(String url) {
    	return (ensureSchema(url, false).startsWith("en.") ? true : false);
    }
    
    
    private static long averageDuration(ArrayList<Duration> times) {
    	long sum = 0;
    	// summarize timers
    	for (Duration d : times) {
    		sum += d.toMillis();
    	}
    	return sum / times.size();
    }
    
    private static void sendState(WebDriver driver, State state) {
    	String cleanURL = ensureSchema(driver.getCurrentUrl(), false);
    	if (cleanURL.startsWith("data")) { // browser just started
    		Printer.sh.update(state, "N/A");
    		return;
    	}
    	if (cleanURL.startsWith("www.")) {
    		cleanURL = cleanURL.replace("www.", "");
    	}
    	cleanURL = cleanURL.split("/")[0];
    	Printer.sh.update(state, cleanURL);
    }
    
    private static String ensureSchema(String url, boolean giveSchemaBack) {
    	if (url.startsWith("https://")) {
    		if (giveSchemaBack) {
    			return url;
    		}
    		return url.replace("https://", "");
    	} else {
    		if (giveSchemaBack) {
    			return "https://" + url;
    		}
    		return url;
    	}
    }
    

    private static String superPanicForURL(WebDriver cd) {
    	// we are fucked
    	navigateTo(cd, "https://en.wikipedia.org/wiki/Main_Page");
    	return "#!panic";
    }
    
    private static String panicForURL(WebDriver cd) {
    	if (allLinks.peek() == null) {
    		return superPanicForURL(cd);
    	}
    	String[] urls = allLinks.toArray(new String[0]);
    	int count = 0;
    	while (visitedLinks.contains(urls[count])) count++;
    	try {
    		return urls[count];
    	} catch (IndexOutOfBoundsException ex) {
    		return urls[count-1];
    	}
	}
}

