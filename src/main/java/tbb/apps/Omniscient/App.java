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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.hibernate.exception.GenericJDBCException;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.sqlite.SQLiteException;

import tbb.db.Schema.Wiki;
import tbb.utils.Logger.LogLevel;
import tbb.utils.Printer.State;

public class App extends AppHelper
{
	
	// TODO: sub-program or feature flag that enables 'verify' mode: check the links in database are valid
    public static void main( String[] args )
    {
    	// end-user feedback
    	// Printer.startBox("Omniscient");
    	
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
    		
    		// alternate TODO: optimize single-threaded spider link collection
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
    
    
    
    private static LocalDateTime dispatcher() throws Exception {
    	
    	log.Write(LogLevel.INFO, "Dispatching to collector processes");
    	ExecutorService es = Executors.newFixedThreadPool(MAX_CHILDREN);
    	
    	List<Future<?>> tasks = new ArrayList<>();
    	// create tasks
        while (!currentLinks.isEmpty()) {
            String[] urls = grabRange(BLOCK_SIZE);
            if (urls == null) {
            	currentLinks.clear();
            	break;
            }
            if (urls.length > 0) {
                tasks.add(es.submit(() -> {
                	WebDriver cd = null;
                	while (cd == null) {
                		try {
                			cd = makeChromeInstance();
                		} catch (Exception e) {
                			log.Write(LogLevel.ERROR, "Failed to start browser! Retrying..");
                			try {Thread.sleep(5000);} catch (Exception ex) {}
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
     	log.Write(LogLevel.INFO, String.format("Scraper received %d urls. There are %d left in currentLinks.", urls.length, currentLinks.size()));
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
    
    
}

