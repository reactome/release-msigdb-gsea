# MSigDB-GSEA exporter (post-release)

This program will produce a data export of pathways and their gene identifiers in the MSIGDB-GSEA format
(see <a href="https://www.gsea-msigdb.org/gsea/msigdb">https://www.gsea-msigdb.org/gsea/msigdb</a> for details)

The file output will be

* Reactome_GeneSet_XX.txt

where XX is the Reactome Release Version Number.

The file format is a tsv file with the following example content:

```
Gene_Set_Name	Brief_Description	External_Link	NCBI Gene IDs
REACTOME_INTERLEUKIN-6_SIGNALING	Genes involved in Interleukin-6 signaling	https://reactome.org/content/detail/R-HSA-1059683	"3569, 3570, 3572, 3716, 3717, 5781, 6772, 6774, 7297, 867, 9021"
```

## Compiling & Running

1. Compile the jar file: `mvn clean package`

If the compilation was successful, you should see a JAR file in the `target` directory, with a name such as
`msigdb-gsea-exporter-VERSION_NUMBER-jar-with-dependencies.jar`. This is the file you will run with a command like the
following

2. `java -jar target/msigdb-gsea-exporter-1.0-SNAPSHOT-jar-with-dependencies.jar [-c /path/to/config/file]`

The configuration file location is optional and will default to src/main/resources/config.properties.

## Configuration

A configuration file is required and can be placed in the default location of src/main/resources/config.properties
before compilation (see compiling and running section above).

This default file is included in the `.gitignore` list to prevent it from being tracked by Git and accidentally storing
and making public sensitive information such database passwords.

The following are the required properties in the configuration file:

```
neo4jUserName=neo4j
neo4jPassword=[password]
neo4jHostName=localhost
neo4jPort=7687
releaseNumber=93
```