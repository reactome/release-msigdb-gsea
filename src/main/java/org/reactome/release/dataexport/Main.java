package org.reactome.release.dataexport;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

import org.neo4j.driver.*;

/**
 * Generates post-release MSigDB-GSEA export file.
 * @author jweiser
 */
public class Main {
	@Parameter(names={"--config-file-path", "-c"})
	private String configFilePath = "config.properties";

	private Session session;

	/**
	 * Main method to process configuration file and run the main logic
	 *
	 * @param args Command line arguments for the post-release data files export (currently the only arguments are,
	 * optionally, "--generate-config-file" or "-g" to indicate the configuration file should be (re)created and
	 * "--config-file-path" or "-c"
	 * @throws IOException Thrown if unable to create and/or read the configuration file or write files
	 */
	public static void main(String[] args) throws IOException {
		Main main = new Main();
		JCommander.newBuilder()
			.addObject(main)
			.build()
			.parse(args);

		try {
			main.run();
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1); // Allows caller of the program to see the error exit code
		}
	}

	/**
	 * Runs the program after the command-line arguments have been parsed and stored for use in the main method
	 * @throws IOException Thrown if unable to create and/or read the configuration file, create output directory
	 * or write files
	 */
	private void run() throws IOException {
		Properties props = getProps();
		this.session = getGraphDBDriver(props).session();

		writeFileHeader();
		writeFileContents();
	}

	private void writeFileHeader() throws IOException {
		String fileHeader = String.join("\t", "Gene_Set_Name", "Brief_Description", "External_Link", "NCBI Gene IDs")
			.concat(System.lineSeparator());

		Files.write(
			getOutputFilePath(),
			fileHeader.getBytes(),
			StandardOpenOption.CREATE, StandardOpenOption.APPEND
		);
	}

	private void writeFileContents() {
		getPathwayToNCBIGeneIdentifiers().entrySet()
			.stream()
			.filter(this::hasMinimumNumberOfGeneIdentifiers)
			.sorted(entriesByStableId())
			.forEach(this::report);
	}

	private boolean hasMinimumNumberOfGeneIdentifiers(Map.Entry<Value, Set<String>> entry) {
		final int geneSetSizeMinimum = 10;

		return entry.getValue().size() >= geneSetSizeMinimum;
	}

	private Comparator<Map.Entry<Value, Set<String>>> entriesByStableId() {
		return (entry1, entry2) -> getPathwayStableId(entry1.getKey()).compareTo(getPathwayStableId(entry2.getKey()));
	}

	private void report(Map.Entry<Value, Set<String>> entry) {
        try {
            Files.write(
                getOutputFilePath(),
                getLine(entry).getBytes(),
                StandardOpenOption.CREATE,StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            throw new RuntimeException("Unable to write report line for " + getPathwayDisplayName(entry.getKey()) +
				" (" + getPathwayStableId(entry.getKey()) + ")", e);
        }
    }

	private String getLine(Map.Entry<Value, Set<String>> entry) {
		Value pathway = entry.getKey();
		Set<String> ncbiGeneIdentifiers = entry.getValue();

		return String.join("\t",
			getGeneSetName(pathway),
			getDescription(pathway),
			getExternalLink(pathway),
			getNCBIGeneIdentifiersString(ncbiGeneIdentifiers)
		).concat(System.lineSeparator());
	}

	private String getGeneSetName(Value pathway) {
		return ("REACTOME " + getPathwayDisplayName(pathway)).replaceAll("\\s+", "_").toUpperCase();
	}

	private String getDescription(Value pathway) {
		return "Genes involved in " + getPathwayDisplayName(pathway);
	}

	private String getExternalLink(Value pathway) {
		return "https://reactome.org/content/detail/" + getPathwayStableId(pathway);
	}

	private String getNCBIGeneIdentifiersString(Set<String> ncbiGeneIdentifiers) {
		return "\"" + String.join(", ",ncbiGeneIdentifiers) + "\"";
	}

	private Path getOutputFilePath() {
		return Paths.get("Reactome_GeneSet_" + getReactomeVersion() + ".txt");
	}

	private int getReactomeVersion() {
		return this.session.run("MATCH (dbInfo:DBInfo) RETURN dbInfo.releaseNumber as releaseNumber")
			.next().get("releaseNumber").asInt();
	}

	private String getPathwayStableId(Value pathway) {
		return pathway.get("stId").asString();
	}

	private String getPathwayDisplayName(Value pathway) {
		return pathway.get("displayName").asString();
	}

	private Map<Value, Set<String>> getPathwayToNCBIGeneIdentifiers() {
		Map<Value, Set<Long>> pathwayToRleDbIds = getPathwayToRleDbIds();
		Map<Long, Set<String>> rleDbIdToNCBIGeneIdentifiers = getRleDbIdToNCBIGeneIdentifiers();

		Map<Value, Set<String>> pathwayToNCBIGeneIdentifiers = new HashMap<>();
		for (Value pathway : pathwayToRleDbIds.keySet()) {
			Set<Long> rleDbIds = pathwayToRleDbIds.get(pathway);
			for (long rleDbId : rleDbIds) {
				Set<String> ncbiGeneIdentifiers = rleDbIdToNCBIGeneIdentifiers
					.computeIfAbsent(rleDbId, k -> new HashSet<>());

				pathwayToNCBIGeneIdentifiers.computeIfAbsent(pathway, k -> new TreeSet<>()).addAll(ncbiGeneIdentifiers);
			}
		}
		return pathwayToNCBIGeneIdentifiers;
	}

	private Map<Value, Set<Long>> getPathwayToRleDbIds() {

		Map<Value, Set<Long>> pathwayDbIdToRleDbIds = new HashMap<>();

		Result result = getPathwayDbIdWithRLEDbIds();
		while (result.hasNext()) {
			Record record = result.next();
			Value pathway = record.get("pathway");
			long rleDbId = record.get("rdbId").asLong();

			pathwayDbIdToRleDbIds.computeIfAbsent(pathway, k -> new TreeSet<>()).add(rleDbId);
		}

		return pathwayDbIdToRleDbIds;
	}

	private Result getPathwayDbIdWithRLEDbIds() {
		return this.session.run(
			"MATCH (p:Pathway)-[:hasEvent*]->(rle:ReactionLikeEvent) " +
			"WHERE p.speciesName = \"Homo sapiens\" " +
			"RETURN p as pathway, rle.dbId as rdbId"
		);

	}

	private Map<Long, Set<String>> getRleDbIdToNCBIGeneIdentifiers() {
		Map<Long, Set<String>> rleDbIdToNCBIGeneIdentifiers = new HashMap<>();

		Result result = getRLEDbIdWithNCBIGeneIdentifiers();
		while (result.hasNext()) {
			Record record = result.next();
			long rleDbId = record.get("rdbId").asLong();
			String ncbiGeneId = record.get("ncbiGeneId").asString();

			rleDbIdToNCBIGeneIdentifiers.computeIfAbsent(rleDbId, k -> new TreeSet<>()).add(ncbiGeneId);
		}

		return rleDbIdToNCBIGeneIdentifiers;
	}

	private Result getRLEDbIdWithNCBIGeneIdentifiers() {
		return this.session.run(
			"MATCH (rds:ReferenceDNASequence)<-[:referenceGene]-" +
			"(rgp:ReferenceGeneProduct)<-[:referenceEntity|:referenceSequence|:hasModifiedResidue]-" +
			"(ewas:EntityWithAccessionedSequence)<-[:hasComponent|hasMember|hasCandidate|repeatedUnit|input|output|catalystActivity|physicalEntity*]-(rle:ReactionLikeEvent)\n" +
			"WHERE rle.speciesName = \"Homo sapiens\" AND rds.databaseName = \"NCBI Gene\"\n" +
			"RETURN DISTINCT rle.dbId as rdbId, rds.identifier as ncbiGeneId;"
		);
	}

	private Properties getProps() throws IOException {
		Properties props = new Properties();
		System.out.println("Configuration: " + this.configFilePath);
		props.load(loadConfig(this.configFilePath));
		return props;
	}

	private InputStream loadConfig(String path) throws IOException {
		InputStream in = this.getClass().getClassLoader().getResourceAsStream(path);
		if (in != null) {
			return in;
		}

		return Files.newInputStream(Paths.get(path));
	}

	/**
	 * Parses connections options and returns a Neo4J Driver object for the graph database
	 * @param props Properties object with graph database connection information
	 * @return Driver for the graph database being run by the Neo4J server
	 */
	private static Driver getGraphDBDriver(Properties props) {
		String host = props.getProperty("neo4jHostName","localhost");
		String port = props.getProperty("neo4jPort", Integer.toString(7687));
		String user = props.getProperty("neo4jUserName", "neo4j");
		String password = props.getProperty("neo4jPassword", "root");

		return GraphDatabase.driver("bolt://" + host + ":" + port, AuthTokens.basic(user, password));
	}
}
