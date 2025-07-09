package tbb.db.Schema;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "instances")
public class Instance {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	public String id;
	
	// program-wide
	@Column(name = "time_omniscient_started", nullable = false)
	public LocalDateTime timeOmniscientStarted = LocalDateTime.now();
	@Column(name = "time_omniscient_completed", nullable = true)
	public LocalDateTime timeOmniscientCompleted;
	@Column(name = "time_omnisicent_running_ms", nullable = true)
	public long timeOmniscientRunningMs;
	@Column(name = "was_success", nullable = false)
	public boolean wasSuccessful = false;
	
	// link info
	@Column(name = "amount_links_wanted", nullable = false)
	public long linksWanted = 0;
	@Column(name = "amount_links_collected", nullable = false)
	public long linksCollected = 0;
	@Column(name = "amount_links_scraped", nullable = false)
	public long linksScraped = 0;
	
	// extra info
	@Column(name = "partition_size", nullable = false)
	public long partitionSize = 0;
	@Column(name = "block_size", nullable = false)
	public long blockSize = 0;
	@Column(name = "max_children", nullable = false)
	public long maxChildren = 0;
	@Column(name = "extra_wait_in_milliseconds", nullable = false)
	public long extraWaitMs = 0;
	@Column(name = "timeout_in_seconds", nullable = false)
	public long timeoutSec = 0;
	
	// if we are running in eclipse (red square termination messes up instance logging)
	// basically if this is true, we are still debugging the program
	// mainly just a way for me to get rid of bad data later (filter out running_in_eclipse instances)
	// TODO: more sophisticated way to work around this, background process or something?
	@Column(name = "running_in_eclipse", nullable = false)
	public boolean runningInEclipse = false;
	
	// average timers (using average because of partition system)
	@Column(name = "time_spider_running_average_ms", nullable = true)
	public long timeSpiderRunningAvgMs;
	
	@Column(name = "time_dispatcher_running_average_ms", nullable = true)
	public long timeDispatcherRunningAvgMs;
	
	@Column(name = "time_bots_running_average_ms", nullable = true)
	public long timeBotsRunningAvgMs;
	
	@Column(name = "unaccounted_runtime_ms", nullable = true)
	public long unaccountedRuntimeMs;
	
	public Instance() { }
	
	public Instance(
			int totalArticles, 
			int partitionSize, 
			int blockSize, 
			int maxChildren, 
			int extraWaitMs, 
			int timeoutSec
			) {
		this.linksWanted = totalArticles;
		this.partitionSize = partitionSize;
		this.blockSize = blockSize;
		this.maxChildren = maxChildren;
		this.extraWaitMs = extraWaitMs;
		this.timeoutSec = timeoutSec;
	}
}
