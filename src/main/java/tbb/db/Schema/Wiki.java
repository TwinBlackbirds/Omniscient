package tbb.db.Schema;

import jakarta.persistence.*;

@Entity
@Table(name = "wikis")
public class Wiki {
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	public String id;
	
	@Column(name = "url", nullable = false)
	public String url;
	
	@Column(name = "title", nullable = false)
	public String title;
	
	@Column(name = "content", nullable = false)
	public String content;
	
	@Column(name = "info_box_content", nullable = true)
	public String infoBoxContent;
	
	@Column(name = "links", nullable = true)
	public String links;
	
	public Wiki() {}
}
