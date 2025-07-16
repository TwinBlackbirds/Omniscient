// Name: Michael Amyotte
// Date: 6/13/25
// Purpose: Generic logging utility library

package tbb.utils.Logger;


import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Logger implements AutoCloseable {
	private LogLevel minLevel = LogLevel.INFO;
	
	private String cleanParentName = null;
	private String formattedParentName = null;
	
	
	private ArrayList<String> Stack = new ArrayList<String>();
	
	public <T> Logger(Class<T> parent, LogLevel minLevel) {
		Path fPath = Paths.get("./logs");
		try {
			if (Files.exists(fPath)) FileDeleter.walkTreeAndDeleteAll(fPath);
			Files.createDirectory(fPath);
		} catch (Exception e) { 
			System.out.println("Failed to initialize log folder! Exiting...");
			System.exit(1);
		}
		this.minLevel = minLevel;
		
		String[] pNameSplit = parent.getName().toString().split("\\.");
		this.cleanParentName = parent.getName();
		if (pNameSplit.length > 1) {
			this.cleanParentName = pNameSplit[pNameSplit.length-1]; 
		}

		if (!(cleanParentName == null || 30 <= cleanParentName.length())) {
	        int padding = 12 - cleanParentName.length();
	        if (padding % 2 != 0) padding -= 1; //adjust centering if length of word is odd
	        
	        int paddingStart = padding / 2;
	        int paddingEnd = padding - paddingStart;

	        // bounds check
	        paddingStart = (paddingStart < 0) ? 0 : paddingStart;
	        paddingEnd = (paddingEnd < 0) ? 0 : paddingEnd;
	        
	        this.formattedParentName = " |" + " ".repeat(paddingStart) + cleanParentName + " ".repeat(paddingEnd) + "|";           
        } else {
        	this.formattedParentName = this.cleanParentName;
        }

        
		this.Write(LogLevel.DBG, "Logger parent process name is: " + cleanParentName);
	}

	// generic logging function
	public void Write(LogLevel severity, String msg) {
		String ldt = LocalDateTime.now()
				.format(
						DateTimeFormatter.ofPattern("HH:mm:ss")
				);
		String severityMsg = "";
		switch (severity) {
		case DBG:
			severityMsg = "DEBUG"; break;
		case INFO:
			severityMsg = "INFO"; break;
		case WARN:
			severityMsg = "WARN"; break;
		case ERROR:
			severityMsg = "ERROR"; break;
		default:
			severityMsg = "SYSTEM"; break;
		}
		
		String log = String.format("[ %s ] %s (%s): %s", ldt, formattedParentName, severityMsg, msg);
		
		// printing
		if (severity.compareTo(minLevel) >= 0 || severity == LogLevel.BYPASS) { // only print messages at or above minLevel
			System.out.println(log);
		}
		// log to file
		Stack.add(log);
	}
	
	// dump stack trace to file 
	public void Dump() {
		Path pa = Paths.get("./logs/" + cleanParentName + "_log.txt");
		if (Files.exists(pa)) {
			try {
				Files.delete(pa);	
			} catch (Exception ex) { /* swallow */ };
		}
		StringBuilder s = new StringBuilder();
		for (String el : Stack) {
			s.append(el.toString()).append("\n");
		}
		try {
			Files.writeString(pa, s.toString());
		} catch (Exception ex) { /* swallow */}
	}
		
	// dump stack trace to file (for severe failures)
	public void Dump(Exception e) {
		Path pa = Paths.get("./logs/" + cleanParentName + "_exception_log.txt");
		if (Files.exists(pa)) {
			try {
				Files.delete(pa);	
			} catch (Exception ex) { /* swallow */ };
		}
		StringBuilder s = new StringBuilder();
		for (StackTraceElement el : e.getStackTrace()) {
			s.append(el.toString()).append("\n");
		}
		try {
			Files.writeString(pa, s.toString());
		} catch (Exception ex) { /* swallow */}
	}

	@Override
	public void close() {
		Write(LogLevel.DBG, "Dumping log to file");
		this.Dump();
	}
}
