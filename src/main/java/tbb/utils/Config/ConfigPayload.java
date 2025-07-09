// Name: Michael Amyotte
// Date: 6/13/25
// Purpose: Configurator 'payload' object to marshal in and out of JSON form

package tbb.utils.Config;

import tbb.utils.Logger.LogLevel;

public class ConfigPayload {
	// your configuration parameters
	public boolean headless = true;
	public boolean DEBUG_MODE = false;
	
	public LogLevel minLogLevel = LogLevel.INFO;
	
	public int MAX_RETRIES = 3;
	public int TOTAL_ARTICLES = 100000;
	public int MAX_CHILDREN = 8;
	public int PARTITION_COUNT = 10;
	public int EXTRA_WAIT_MS = 1000;
	public int TIMEOUT_SEC = 30;
	
	
	public ConfigPayload() { };
}
