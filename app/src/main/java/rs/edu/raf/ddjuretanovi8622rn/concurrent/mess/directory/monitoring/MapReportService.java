package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.directory.monitoring;

import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.AggregateData;

public class MapReportService {
	private static final Logger log = LoggerFactory.getLogger(MapReportService.class);
	private static final Path CSV_FILE_PATH = Paths.get("meteorological_data_map.csv");
	private static final Lock exportLock = new ReentrantLock();

	private final DirectoryWatcher directoryWatcher;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private boolean running = false;

	public MapReportService(DirectoryWatcher directoryWatcher) {
		this.directoryWatcher = directoryWatcher;
	}

	public void start() {
		if (running) {
			log.warn("Map report service already running");
			return;
		}

		running = true;
		scheduler.scheduleAtFixedRate(
				() -> {
					log.info("Generating periodic report");
					exportMapToCsv();
				}, 1, 1, TimeUnit.MINUTES);
		log.info("Map report service started");
	}

	public void stop() {
		if (!running) {
			return;
		}

		running = false;
		scheduler.shutdown();
		try {
			if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
				scheduler.shutdownNow();
			}
		} catch (InterruptedException e) {
			scheduler.shutdownNow();
			Thread
					.currentThread()
					.interrupt();
		}
		log.info("Map report service stopped");
	}

	public void printMap() {
		// Crimes against programming
		Map<Character, AggregateData> map = Collections.unmodifiableMap(directoryWatcher.getAggregateMap());
		if (map.isEmpty()) {
			log.info("Map is not yet available");
			return;
		}
		record FullData(char letter, long count, double sum) {
			@Override
			public String toString() {
				return String.format("%c: %d, %.1f", letter, count, sum);
			}
		}

		var data = map
				.entrySet()
				.stream()
				.map(entry -> new FullData(
						entry.getKey(),
						entry
								.getValue()
								.count(),
						entry
								.getValue()
								.sum()))
				.toList();
		for (int i = 0; i < data.size(); i += 2) {
			if (i + 1 < data.size()) {
				log.info("{}\t|\t{}", data.get(i), data.get(i + 1));
			} else {
				log.info("{}", data.get(i));
			}
		}


	}

	public void exportMapToCsv() {
		exportLock.lock();
		try {
			Map<Character, AggregateData> map = Collections.unmodifiableMap(directoryWatcher.getAggregateMap());
			if (map.isEmpty()) {
				log.warn("Map is not yet available");
				return;
			}

			record FullData(char letter, long count, double sum) {
				@Override
				public String toString() {
					// CSV row format: Letter,Station count,Sum
					return String.format("%c,%d,%.1f", letter, count, sum);
				}
			}

			// Build a List of FullData objects from the map entries.
			var data = map
					.entrySet()
					.stream()
					.map(entry -> new FullData(
							entry.getKey(),
							entry
									.getValue()
									.count(),
							entry
									.getValue()
									.sum()))
					.toList();

			try (BufferedWriter writer = Files.newBufferedWriter(
					CSV_FILE_PATH,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING)) {
				writer.write("Letter,Station count,Sum");
				writer.newLine();

				for (FullData fd : data) {
					writer.write(fd.toString());
					writer.newLine();
				}
				log.info("Map exported to {}", CSV_FILE_PATH);
			} catch (IOException e) {
				log.error("Error exporting map to CSV: {}", e.getMessage());
			}
		} finally {
			exportLock.unlock();
		}
	}

}