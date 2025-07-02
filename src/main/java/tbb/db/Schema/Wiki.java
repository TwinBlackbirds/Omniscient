package tbb.db.Schema;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "wikis")
public class Wiki {
	@Id
	@Column (name= "id", nullable = false)
	public String id;
	
	@Column(name = "title", nullable = false)
	public String title;
	
	@Column(name = "content", nullable = false)
	public String content;
	
	@Column(name = "info_box_content", nullable = true)
	public String infoBoxContent;
	
	@Column(name = "links", nullable = true)
	public String links;
	
	@Column (name = "time_collected", nullable = false)
	public LocalDateTime timeCollected = LocalDateTime.now();
	
	public Wiki() {}
}
