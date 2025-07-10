// Name: Michael Amyotte
// Date: 7/10/2025
// Purpose: Sub-class to inherit AppFields and hold helper functions for App that are not the main 3; (spider, dispatcher, scraper)

package tbb.apps.Omniscient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import tbb.utils.Logger.LogLevel;
import tbb.utils.Printer.Printer;
import tbb.utils.Printer.State;

public class AppHelper extends AppFields {
	

    protected static void waitForElementClickable(WebDriver driver, String selector) {
    	sendState(driver, State.LOADING);
    	new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEC)).until(
		    ExpectedConditions.elementToBeClickable(By.cssSelector(selector))
		);
    	try {
    		Thread.sleep(EXTRA_WAIT_MS);
    	} catch (Exception e) { }
    	sendState(driver, State.WAITING);
	}
    
    protected static void waitForElementVisible(WebDriver driver, String selector) {
    	sendState(driver, State.LOADING);
    	new WebDriverWait(driver, Duration.ofSeconds(TIMEOUT_SEC)).until(
		    ExpectedConditions.visibilityOfElementLocated(By.cssSelector(selector))
		);
    	try {
    		Thread.sleep(EXTRA_WAIT_MS);
    	} catch (Exception e) { }
    	sendState(driver, State.WAITING);
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
    
    protected static List<String> getUniqueValidLinks(WebDriver driver, int amount) {
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
    
    protected static void navigateTo(WebDriver driver, String URL) {
    	sendState(driver, State.NAVIGATING);
    	driver.get(URL);
    	waitUntilPageLoaded(driver);
    	visitedLinks.add(URL);
    }
    
    // wait for TIMEOUT_SEC seconds OR until the DOM reports readyState = complete
    protected static void waitUntilPageLoaded(WebDriver driver) {
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
    
    protected static boolean isUrlWiki(String url) {
    	for (String frag : BANNED_URL_FRAGMENTS) {
    		if (url.contains(frag)) return false;
    	}
    	if (!url.contains("wikipedia.org")) return false;
    	return true;
    }
    
    protected static boolean isUrlEnglish(String url) {
    	return (ensureSchema(url, false).startsWith("en.") ? true : false);
    }
    
    
    protected static long averageDuration(ArrayList<Duration> times) {
    	long sum = 0;
    	// summarize timers
    	for (Duration d : times) {
    		sum += d.toMillis();
    	}
    	return sum / times.size();
    }
    
    protected static void sendState(WebDriver driver, State state) {
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
    
    protected static String ensureSchema(String url, boolean giveSchemaBack) {
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
    

    protected static String superPanicForURL(WebDriver cd) {
    	// we are fucked
    	navigateTo(cd, "https://en.wikipedia.org/wiki/Main_Page");
    	return "#!panic";
    }
    
    protected static String panicForURL(WebDriver cd) {
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
    

    protected static String getPrettyPageTitle(WebDriver cd) {
    	return cd.getTitle().replace("- Wikipedia", "");
    }
}
