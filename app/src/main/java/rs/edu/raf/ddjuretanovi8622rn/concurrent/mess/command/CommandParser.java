package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command;

import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class CommandParser {

	private static final Logger log = LoggerFactory.getLogger(CommandParser.class);
	private static final Map<String, String> SCAN_FLAG_TO_PARAM = Map.of(
			"--min",
			"min",
			"-m",
			"min",
			"--max",
			"max",
			"-M",
			"max",
			"--letter",
			"letter",
			"-l",
			"letter",
			"--output",
			"output",
			"-o",
			"output",
			"--job",
			"job",
			"-j",
			"job");

	private CommandParser() {}

	public static Command parseCommand(String line) {
		String[] parts = line.split("\\s+");
		if (parts.length == 0) {
			throw new IllegalArgumentException("Empty command");
		}
		for (int i = 0; i < parts.length; i++) {
			parts[i] = parts[i].trim();
		}
		parts[0] = parts[0].toUpperCase();
		return switch (parts[0]) {
			case "START" -> {
				if (parts.length > 2) {
					throw new IllegalArgumentException("Too many arguments for START command");
				}
				if (parts.length == 1) {
					yield new Command.Start(false);
				}
				if (parts[1].equalsIgnoreCase("-l") || parts[1].equalsIgnoreCase("--load-jobs")) {
					yield new Command.Start(true);
				}
				throw new IllegalArgumentException("Invalid argument for START command: " + parts[1]);
			}
			case "STOP", "SHUTDOWN", "EXIT", "QUIT" -> {
				if (parts.length > 2) {
					throw new IllegalArgumentException("Too many arguments for " + parts[0] + " command");
				}
				if (parts.length == 1) {
					yield new Command.Stop(false);
				}
				if (parts[1].equalsIgnoreCase("-s") || parts[1].equalsIgnoreCase("--save-jobs")) {
					yield new Command.Stop(true);
				}
				throw new IllegalArgumentException("Invalid argument for SHUTDOWN command: " + parts[1]);
			}
			case "SCAN" -> parseScan(parts);
			case "EXPORTMAP" -> new Command.ExportMap();
			case "MAP" -> new Command.Map();
			case "STATUS" -> parseStatus(parts);
			default -> {
				throw new IllegalArgumentException("Unknown command: " + parts[0]);
			}
		};
	}

	private static Command parseStatus(String[] parts) {
		if (parts.length > 3) {
			throw new IllegalArgumentException("Too many arguments for STATUS command, expected 1 or 0");
		}
		if (parts.length == 2) {
			throw new IllegalArgumentException("Invalid argument for STATUS command, expecting odd number but got 2: " + Arrays.toString(
					parts));
		}
		if (parts.length == 3) {
			String jobName = parts[2];
			if (jobName.isEmpty()) {
				throw new IllegalArgumentException("Job name cannot be empty");
			}
			return new Command.Status(jobName);
		} else {
			return new Command.Status(null);
		}
	}

	private static Command.Scan parseScan(String[] parts) {
		var options = new HashMap<String, String>();
		for (int i = 1; i < parts.length; i++) {
			var flag = parts[i];
			if (!flag.startsWith("-")) {
				throw new IllegalArgumentException("Expected a flag, but got: " + flag);
			}
			if (i + 1 >= parts.length) {
				throw new IllegalArgumentException("Missing value for flag: " + flag);
			}
			var value = parts[++i];
			var canonicalFlag = SCAN_FLAG_TO_PARAM.get(flag);
			options.put(canonicalFlag, value);
		}

		if (!options.containsKey("min") || !options.containsKey("max") || !options.containsKey("letter") || !options.containsKey(
				"output") || !options.containsKey("job")) {
			throw new IllegalArgumentException("Missing required options. Options provided: " + options);
		}

		double min, max;
		try {
			min = Double.parseDouble(options.get("min"));
			max = Double.parseDouble(options.get("max"));
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Invalid number format for min or max.");
		}
		if (min > max) {
			throw new IllegalArgumentException("Min value must not exceed max value.");
		}
		String letterStr = options.get("letter");
		if (letterStr.length() != 1) {
			throw new IllegalArgumentException("Letter must be a single character.");
		}
		char letter = letterStr.charAt(0);
		String output = options.get("output");
		String job = options.get("job");

		return new Command.Scan(min, max, letter, output, job);
	}
}
