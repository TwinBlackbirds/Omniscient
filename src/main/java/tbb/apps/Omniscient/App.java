// Scraper Template  Application
// Author: Michael Amyotte (twinblackbirds)
// Date: 6/23/25
// Purpose: Template for Java web scraper applications
// 

package tbb.apps.Omniscient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;


import tbb.db.Driver.Sqlite;
import tbb.utils.Config.ConfigPayload;
import tbb.utils.Config.Configurator;
import tbb.utils.Logger.LogLevel;
import tbb.utils.Logger.Logger;
import tbb.utils.Printer.Printer;
import tbb.utils.Printer.State;

public class App 
{
	// configuration
	private static Logger log = new Logger(LogLevel.ERROR); // min log level
	private static final ConfigPayload config = new Configurator(log).getData();
	
	// consts
	private static final int MAX_RETRIES = 3; // if page fails to load (cd.get())
	private static final int TIMEOUT_SEC = 30; // time to wait for el to be present
	private static final int EXTRA_WAIT_MS = 1000; // extra time spent waiting after el is present
	
	private static final String[] BANNED_URL_FRAGMENTS = {"/wiki/Help:", "/wiki/Special:"};
	
	// db
	private static Sqlite sql = new Sqlite(log, true);
	
	// selenium browser tools
	private static ChromeDriver cd;
	private static JavascriptExecutor js; // to execute JS in browser context
	
	
    public static void main( String[] args )
    {
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
    	ChromeOptions co = new ChromeOptions();
    	if (headless) { co.addArguments("headless"); }
    	
    	// point selenium to correct driver
    	log.Write(LogLevel.DBG, "Creating default ChromeDriverService");
    	ChromeDriverService cds = ChromeDriverService.createDefaultService();
    	
    	
    	// start driver
    	log.Write(LogLevel.INFO, "Starting Chrome browser");
    	cd = new ChromeDriver(cds, co);
    	js = (JavascriptExecutor) cd;
    	
    	// end-user feedback
    	Printer.startBox("Omniscient");
    	
    	// String s = loopUntilInput();
    	try {
    		// only enable while loop once you are confident in the bot's abilities
//    		while (true) {
            	bot();
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
    private static void bot() throws Exception {
    	navigateTo("https://en.wikipedia.org"); // we only want english articles
    	
    	
    }
    
    // clean urls, split them up, and dispatch them to children processes
    private static void dispatch() throws Exception {
    	
    }
    
    // this is the child process which scrapes a wiki 
    // TODO: figure out a plan of action for how to control the chrome driver without too much resource hogging
    // Share one chrome driver between many scrapers and just open new tabs perhaps?
    private static void scraper() throws Exception {
    	
    }
    
    
    private static void navigateTo(String URL) {
    	sendState(State.NAVIGATING);
    	cd.get(URL);
    	waitUntilPageLoaded();
    }
    
    // wait for TIMEOUT_SEC seconds OR until the DOM reports readyState = complete
    private static void waitUntilPageLoaded() {
    	sendState(State.LOADING);
    	String pageName = cd.getTitle();
    	log.Write(LogLevel.INFO, String.format("Waiting for page '%s' to load", pageName));
    	new WebDriverWait(cd, Duration.ofSeconds(TIMEOUT_SEC)).until(
                webDriver -> ((JavascriptExecutor) webDriver)
                    .executeScript("return document.readyState")
                    .equals("complete")
            );
    	log.Write(LogLevel.INFO, "Page loaded");
    	sendState(State.WAITING);
    }
    
    private static List<String> getOnlyEnglishPages(List<String> urls) {
    	List<String> list = new ArrayList<>();
    	for (String url : urls) {
    		if (isUrlEnglish(url)) list.add(url);
		}
    	return list;
    }
    
    private static boolean isUrlWiki(String url) {
    	for (String frag : BANNED_URL_FRAGMENTS) {
    		if (url.contains(frag)) return false;
    	}
    	return true;
    }
    
    private static boolean isUrlEnglish(String url) {
    	return (ensureSchema(url, false).startsWith("en.") ? true : false);
    }
    
    private static void sendState(State state) {
    	String cleanURL = ensureSchema(cd.getCurrentUrl(), false);
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
    
    private static void waitForElementClickable(String selector) {
    	sendState(State.LOADING);
    	new WebDriverWait(cd, Duration.ofSeconds(TIMEOUT_SEC)).until(
		    ExpectedConditions.elementToBeClickable(By.cssSelector(selector))
		);
    	try {
    		Thread.sleep(EXTRA_WAIT_MS);
    	} catch (Exception e) { }
    	sendState(State.WAITING);
	}
    
    private static void waitForElementVisible(String selector) {
    	sendState(State.LOADING);
    	new WebDriverWait(cd, Duration.ofSeconds(TIMEOUT_SEC)).until(
		    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector))
		);
    	try {
    		Thread.sleep(EXTRA_WAIT_MS);
    	} catch (Exception e) { }
    	sendState(State.WAITING);
	}
}

