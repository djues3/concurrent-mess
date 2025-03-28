package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.job;

import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command.Command;

public class FileProcessingTask implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(FileProcessingTask.class);

	private final Path path;

	/// If true, treat file as CSV otherwise semicolon separated.
	private final boolean isCsv;

	private final double min;

	private final double max;

	private final char targetChar;

	private final String outputFile;
	private final String jobName;


	// Reference to the job's overall result map.


	public FileProcessingTask(Path path, Command.Scan scanParameters, String jobName) {
		this.path = path;
		this.isCsv = path
				.toString()
				.endsWith(".csv");
		this.min = scanParameters.min();
		this.max = scanParameters.max();
		this.targetChar = scanParameters.letter();
		this.outputFile = scanParameters.outputFilename();
		this.jobName = jobName;
	}

	@Override
	public void run() {
		try {
			var outputPath = Paths.get(outputFile);
			try (var stream = Files.lines(path); var writer = Files.newBufferedWriter(
					outputPath,
					StandardOpenOption.APPEND)) {
				Stream<String> s = isCsv ? stream.skip(1) : stream;

				AtomicInteger count = new AtomicInteger(0);

				s
						.filter(string -> string.charAt(0) == targetChar)
						.map(this::parseLine)
						.filter(l -> l.temperature >= min && l.temperature <= max)
						.forEach(m -> {
							try {
								writer.write(String.format("%s;%.1f%n", m.stationName, m.temperature));
								count.incrementAndGet();
							} catch (IOException e) {
								log.error("{} - Error writing to output file: {}", jobName, e.getMessage());
							}
						});
				log.info("{} - Got {} results for file {}", jobName, count.get(), path);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Measurement parseLine(String line) {
		String[] parts = line.split(isCsv ? "," : ";");
		if (parts.length != 2) {
			throw new IllegalArgumentException("Invalid line format: " + line);
		}
		String stationName = parts[0].trim();
		double temperature = Double.parseDouble(parts[1].trim());
		return new Measurement(stationName, temperature);
	}

	record Measurement(String stationName, double temperature) {}

	record Stats(double sum, int count) {}
}