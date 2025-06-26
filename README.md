# Omniscient

Omniscient is a web crawler that collects data from Wikipedia and other knowledge bases.


### To use:

1. First, configure the database as necessary within `src/main/resources/hibernate.cfg.xml`.
1. Next, add your models and annotate them with JPA.
1. Add the mappings to the database configuration
1. Write necessary methods for specialized CRUD within the database driver (tbb.db.Driver.Sqlite)