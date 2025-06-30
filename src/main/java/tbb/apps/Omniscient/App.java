// Scraper Template  Application
// Author: Michael Amyotte (twinblackbirds)
// Date: 6/23/25
// Purpose: Template for Java web scraper applications
// 

package tbb.apps.Omniscient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import tbb.db.Driver.Sqlite;
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
	private static Logger log = new Logger(LogLevel.INFO); // min log level
	private static final ConfigPayload config = new Configurator(log).getData();
	
	// consts
	private static final int MAX_RETRIES = 3; // if page fails to load (cd.get())
	private static final int TIMEOUT_SEC = 30; // time to wait for el to be present
	private static final int EXTRA_WAIT_MS = 1000; // extra time spent waiting after el is present
	private static final int MAX_CHILDREN = 1; //10; // amount of child procs dispatch() is allowed to spawn
	private static final int ARTICLES_PER_CHILD = 50; //500;
	
	// url handling
	private static final String[] BANNED_URL_FRAGMENTS = {
			"/wiki/Help:", 
			"/wiki/Special:",
			"/wiki/Wikipedia:",
			"/wiki/Category:",
			"/wiki/File:",
			"/wiki/Template:",
			"/wiki/Template_talk:"
	};
	
	private static final String[] BANNED_TEXT_CHUNKS = {
			"div.reflist",
			"div.refbegin",
			"#catlinks",
			"div.navbox",
			".metadata.sister-bar",
			".mw-heading + ul",
	};
	
	private static List<String> allLinks = new ArrayList<String>();
	private static ConcurrentLinkedQueue<String> currentLinks = new ConcurrentLinkedQueue<String>();
	
	// db
	private static Sqlite sql = new Sqlite(log, true);
	
	// selenium browser tools
	private static ChromeOptions co = null;
	private static ChromeDriverService cds = null;
	private static ChromeDriver cd;
	private static JavascriptExecutor js; // to execute JS in browser context
	
	
    public static void main( String[] args )
    {
    	cd = makeChromeInstance();
    	js = (JavascriptExecutor) cd;
    	
    	
    	// end-user feedback
//    	Printer.startBox("Omniscient");
    	// TODO: add how long the program has been running for in box TUI
    	// TODO: also add how many urls have been grabbed and how many articles have been extracted
    	
    	try {
//    		while (true) {
            	bot(ARTICLES_PER_CHILD * MAX_CHILDREN); 
            	dispatch();
            	try {
            		Thread.sleep(5000);
            	} catch (Exception e) {};
//    		}
    	} catch (Exception e) {
    		log.Write(LogLevel.ERROR, "Bot failed! " + e);
    	} finally {
    		log.Write(LogLevel.INFO, "Closing Chrome browser");
            // close browser + all tabs
            cd.quit();
            // dump logs
            log.close();
            System.out.println("Process terminated with return code 0");
        		
    	}
    }
    
    // our 'spider'
    private static void bot(int amtOfLinks) throws Exception {
    	log.Write(LogLevel.INFO, String.format("Starting spider process to collect %d links", amtOfLinks));
    	int startingIdx;
    	String navTo;
    	
    	if (allLinks.size() < 1) {
    		startingIdx = 0;
    		navTo = "https://en.wikipedia.org"; // 'starting article' ability later on
    	} else {
    		startingIdx = allLinks.size()-1;
    		navTo = allLinks.get(startingIdx);
    	}
    	int count = 0;
    	
    	while (allLinks.size() < startingIdx + amtOfLinks) {
    		navigateTo(navTo);
        	List<String> links = getUniqueValidLinks();
        	allLinks.addAll(links);
        	currentLinks.addAll(links);
        	navTo = allLinks.get(startingIdx + count);
        	count++;
    	}
    }
    
    private static void dispatch() throws Exception {
    	
    	log.Write(LogLevel.INFO, "Dispatching to collector processes");
    	ExecutorService es = Executors.newFixedThreadPool(MAX_CHILDREN);
    	
    	sendState(State.COLLECTING);
    	List<Future<?>> tasks = new ArrayList<>();

    	// create tasks
        while (!currentLinks.isEmpty()) {
            String[] urls = grabRange(ARTICLES_PER_CHILD);
            if (urls.length > 0) {
                tasks.add(es.submit(() -> {
                	WebDriver tab = makeChromeInstance();
                	try {
                        scraper(tab, urls);
                    } catch (Exception e) {
                        log.Write(LogLevel.ERROR, "Could not dispatch! " + e);
                    } finally {
                    	tab.quit();
                    }
                }));
            }
        }

        es.shutdown();

        // Wait for all tasks to complete
        for (Future<?> task : tasks) {
            try {
                task.get(); // this blocks until that specific task finishes
            } catch (ExecutionException | InterruptedException e) {
                log.Write(LogLevel.ERROR, "Task failed: " + e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
        
    	sendState(State.WAITING);
    }
    
    // this is the child process which scrapes a wiki 
    // TODO: figure out a plan of action for how to control the chrome driver without too much resource hogging
    // Share one chrome driver between many scrapers and just open new tabs perhaps?
    private static void scraper(WebDriver tab, String[] urls) throws Exception {
    	log.Write(LogLevel.INFO, String.format("Scraper received %d urls. There are %d left in currentLinks.", urls.length, currentLinks.size()));
    	for (String url : urls) {
    		navigateTo(tab, url);
    		
    		sendState(State.COLLECTING);
    		// collect main body of text from page
    		WebElement mainChunk = tab.findElement(By.cssSelector("div.mw-content-ltr"));
    		String mainText = mainChunk.getText();
    		
    		// grab text of stuff we want to separate
    		List<WebElement> refLists = new ArrayList<WebElement>();
    		for (String selector : BANNED_TEXT_CHUNKS) {
    			refLists.addAll(tab.findElements(By.cssSelector(selector)));
    		}
    		
    		// get infobox text separately for data integrity
    		boolean hasInfoBox = (tab.findElements(By.cssSelector("table.infobox")).size() > 0);
    		String infoBoxText = "";
    		if (!hasInfoBox) {
    			log.Write(LogLevel.WARN, "No infobox located on the page " + tab.getCurrentUrl());
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
    		if (mainText.contains("Notes\n\n")) {
    			mainText = mainText.split("Notes\n\n")[0];
    		} else if (mainText.contains("References\n\n")) {
    			mainText = mainText.split("References\n\n")[0];
    		}
    		
    		WebElement titleEl = tab.findElement(By.cssSelector("main h1"));
    		
    		// create our wiki object
    		Wiki w = new Wiki();
    		w.title = titleEl.getText();
    		w.content = mainText;
    		w.infoBoxContent = infoBoxText;
    		w.links = sb.toString();
    		log.Write(LogLevel.INFO, "collected data : " + url); 
    		
    		// send it to database
    		sql.writeWiki(w);
    		log.Write(LogLevel.INFO, "wrote data to database : " + url); 
    		
    		sendState(State.WAITING);
    	}
    }
    
    public static ChromeDriver makeChromeInstance() {
    	if (co == null) {
    		String startingArticle = null;
        	boolean headless = false;
        	if (config != null) {
        		headless = config.headless;
        		startingArticle = config.startingArticle;
        	}
        	
        	log.Write(LogLevel.BYPASS, "Headless mode: " + (headless ? "enabled" : "disabled"));
        	log.Write(LogLevel.BYPASS, "Starting article: " + startingArticle);
        	
        	// set launch options
    		log.Write(LogLevel.DBG, "Setting Chrome launch options");
        	ChromeOptions cOpts = new ChromeOptions();
        	if (headless) { cOpts.addArguments("headless"); }
        	co = cOpts;
    	}
    	if (cds == null) {
    		// point selenium to correct driver
        	log.Write(LogLevel.DBG, "Creating default ChromeDriverService");
        	cds = ChromeDriverService.createDefaultService();
    	}
    	
    	// start driver
    	log.Write(LogLevel.INFO, "Starting Chrome browser instance");
    	return new ChromeDriver(cds, co);
    }
    
    public static String[] grabRange(int amount) {
    	
    	int lastIdx = currentLinks.size() - 1;
    	if (amount >= lastIdx) { // we are at the end
    		String[] range = currentLinks.toArray(new String[0]);
    		currentLinks.clear();
    		return range;
    	}
    	
        ArrayList<String> newList = new ArrayList<String>();
        String pop = currentLinks.poll();
        int count = 0;
    	while (pop != null && count < amount)  {
        	newList.add(pop);
    		pop = currentLinks.poll();
    		count++;
        }   
    	return newList.toArray(new String[0]);
    }
    
    
    private static List<String> getUniqueValidLinks() {
    	return getUniqueValidLinks(cd);
    }
    private static List<String> getUniqueValidLinks(WebDriver driver) {
    	sendState(State.COLLECTING);
    	List<WebElement> links = driver.findElements(By.cssSelector("div.mw-content-ltr a[href*='/wiki']"));
    	ArrayList<String> hrefs = new ArrayList<String>();
    	for (WebElement link : links) {
    		String href = link.getAttribute("href");
    		if (isUrlEnglish(href) && isUrlWiki(href) && !allLinks.contains(href)) {
    			hrefs.add(href);
    		}
    	}
    	sendState(State.WAITING);
    	return hrefs;
    }
    
    private static void navigateTo(String URL) {
    	navigateTo(cd, URL);
    }
    private static void navigateTo(WebDriver driver, String URL) {
    	sendState(State.NAVIGATING);
    	driver.get(URL);
    	waitUntilPageLoaded(driver);
    }
    
    // wait for TIMEOUT_SEC seconds OR until the DOM reports readyState = complete
    private static void waitUntilPageLoaded(WebDriver driver) {
    	sendState(State.LOADING);
    	String pageName = driver.getTitle();
    	log.Write(LogLevel.INFO, String.format("Waiting for page '%s' to load", pageName));
    	new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEC)).until(
                webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState")
                    .equals("complete")
            );
    	log.Write(LogLevel.INFO, "Page loaded");
    	sendState(State.WAITING);
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
    
    private static void sendState(State state) {
    	sendState(cd, state);
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
    
    private static void jsClick(WebElement el) {
    	js.executeScript("arguments[0].click();", el);
    }
    
    private static void scrollPage(int scrolls) {
    	sendState(State.LOADING);
    	for (int i = 0; i < scrolls; i++) {
    		js.executeScript(String.format("window.scrollBy(0, %d);", 1080*i), "");
    		try { Thread.sleep(1000); } catch (InterruptedException e) { }
    	}
    	js.executeScript("window.scrollTo(0, 0);",  "");
    	sendState(State.WAITING);
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
    
    private static void waitForElementClickable(WebDriver driver, String selector) {
    	sendState(State.LOADING);
    	new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEC)).until(
		    ExpectedConditions.elementToBeClickable(By.cssSelector(selector))
		);
    	try {
    		Thread.sleep(EXTRA_WAIT_MS);
    	} catch (Exception e) { }
    	sendState(State.WAITING);
	}
    
    private static void waitForElementVisible(WebDriver driver, String selector) {
    	sendState(State.LOADING);
    	new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEC)).until(
		    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector))
		);
    	try {
    		Thread.sleep(EXTRA_WAIT_MS);
    	} catch (Exception e) { }
    	sendState(State.WAITING);
	}
}

