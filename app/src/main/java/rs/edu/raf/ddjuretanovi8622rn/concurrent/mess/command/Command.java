package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command;

public sealed interface Command extends Message {

	record Start(boolean loadJobs) implements Command {}

	record Stop(boolean saveJobs) implements Command {}

	record Scan(double min, double max, char letter, String outputFilename, String jobName) implements Command {
		public Scan {
			if (outputFilename == null || outputFilename.isBlank()) {
				throw new IllegalArgumentException("Output filename cannot be null or blank");
			}
			if (jobName == null || jobName.isBlank()) {
				throw new IllegalArgumentException("Job name cannot be null or blank");
			}
			if (min > max) {
				throw new IllegalArgumentException("Min cannot be greater than max");
			}
		}
	}

	///  A null jobname is treated as a wildcard, meaning all jobs will be returned.
	record Status(String jobname) implements Command {}

	record Map() implements Command {}

	record ExportMap() implements Command {}
}
