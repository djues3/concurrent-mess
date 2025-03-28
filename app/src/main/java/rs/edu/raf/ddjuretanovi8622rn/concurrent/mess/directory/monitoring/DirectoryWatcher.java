package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.directory.monitoring;

import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.AggregateData;

public class DirectoryWatcher {
	private static final Logger log = LoggerFactory.getLogger(DirectoryWatcher.class);

	private final ExecutorService executorService;
	private final Path watchDirectory;
	private final AtomicReference<Map<Character, AggregateData>> aggregateMap = new AtomicReference<>(new HashMap<>());
	private final Map<Path, Instant> lastModifiedTimes = new ConcurrentHashMap<>();
	private WatchService watchService;
	private boolean isRunning = false;

	public DirectoryWatcher(Path watchDirectory) {
		this.watchDirectory = watchDirectory;
		this.executorService = Executors.newVirtualThreadPerTaskExecutor();
	}

	public void start() {
		if (isRunning) {
			log.warn("Directory watcher is already running");
			return;
		}

		try {
			Files.createDirectories(watchDirectory);

			// Initial scan of all files to build the aggregate map
			processAllFiles();

			// Start watching for changes
			startWatchService();

			isRunning = true;
			log.info("Directory watcher started for: {}", watchDirectory);
		} catch (IOException e) {
			log.error("Failed to start directory watcher: {}", e.getMessage());
		}
	}

	public Map<Character, AggregateData> getAggregateMap() {
		return new HashMap<>(aggregateMap.get());
	}

	private void startWatchService() {
		executorService.submit(() -> {
			try {
				watchService = FileSystems
						.getDefault().newWatchService();
				watchDirectory.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
				                        StandardWatchEventKinds.ENTRY_MODIFY);

				while (isRunning) {
					WatchKey key;
					try {
						key = watchService.take(); // Wait for events
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}

					for (WatchEvent<?> event : key.pollEvents()) {
						WatchEvent.Kind<?> kind = event.kind();

						if (kind == StandardWatchEventKinds.OVERFLOW) {
							continue;
						}

						@SuppressWarnings("unchecked")
						WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
						Path filename = pathEvent.context();
						Path filePath = watchDirectory.resolve(filename);

						if (!Files.isRegularFile(filePath)) {
							continue;
						}

						String fileExtension = getFileExtension(filePath);
						if (!".txt".equals(fileExtension) && !".csv".equals(fileExtension)) {
							continue;
						}

						Instant lastModified = Files.getLastModifiedTime(filePath).toInstant();
						Instant previousModified = lastModifiedTimes.get(filePath);

						if (previousModified == null || !previousModified.equals(lastModified)) {
							log.info("File changed: {}", filePath);
							lastModifiedTimes.put(filePath, lastModified);

							// Process the changed file
							processFile(filePath);
						}
					}

					boolean valid = key.reset();
					if (!valid) {
						break;
					}
				}
			} catch (IOException e) {
				log.error("Error in watch service: {}", e.getMessage());
			}
		});
	}

	private String getFileExtension(Path path) {
		String name = path.toString();
		int lastIndexOf = name.lastIndexOf(".");
		return lastIndexOf == -1 ? "" : name.substring(lastIndexOf).toLowerCase();
	}

	private void processAllFiles() {
		List<Path> files = getFilesToProcess();
		if (files.isEmpty()) {
			log.info("No files found in watch directory");
			return;
		}

		log.info("Processing {} files for initial aggregate map", files.size());
		Map<Character, AggregateData> newMap = new ConcurrentHashMap<>();
		CountDownLatch latch = new CountDownLatch(files.size());

		for (Path file : files) {
			executorService.submit(() -> {
				try {
					processFileForAggregateMap(file, newMap);
					Instant lastModified = Files.getLastModifiedTime(file).toInstant();
					lastModifiedTimes.put(file, lastModified);
				} catch (IOException e) {
					log.error("Error getting last modified time for {}: {}", file, e.getMessage());
				} finally {
					latch.countDown();
				}
			});
		}

		try {
			latch.await(5, TimeUnit.MINUTES);
			aggregateMap.set(new HashMap<>(newMap));
			log.info("Initial aggregate map created with {} letters", newMap.size());
		} catch (InterruptedException e) {
			log.error("Interrupted while waiting for files to process", e);
			Thread.currentThread().interrupt();
		}
	}

	private void processFile(Path file) {
		Map<Character, AggregateData> currentMap = new HashMap<>(aggregateMap.get());
		processFileForAggregateMap(file, currentMap);
		aggregateMap.set(currentMap);
	}

	private List<Path> getFilesToProcess() {
		try {
			try (Stream<String> stream = Files.walk(watchDirectory, 1)
			                                  .filter(Files::isRegularFile)
			                                  .map(Path::toString)
			                                  .filter(string -> {
				                                  String filename = string.toLowerCase();
				                                  return filename.endsWith(".txt") || filename.endsWith(".csv");
			                                  })) {
				return stream.map(Path::of).collect(Collectors.toList());
			}
		} catch (IOException e) {
			log.error("Error getting files to process: {}", e.getMessage());
			return List.of();
		}
	}

	private void processFileForAggregateMap(Path file, Map<Character, AggregateData> map) {
		boolean isCsv = file.toString().toLowerCase().endsWith(".csv");
		log.info("Processing file for aggregate map: {}", file);

		try (Stream<String> lines = Files.lines(file)) {
			Stream<String> dataLines = isCsv ? lines.skip(1) : lines;

			dataLines.forEach(line -> {
				try {
					String[] parts = line.split(isCsv ? "," : ";");
					if (parts.length != 2) {
						log.warn("Invalid line format: {}", line);
						return;
					}

					String stationName = parts[0].trim();
					if (stationName.isEmpty()) return;

					char firstLetter = Character.toUpperCase(stationName.charAt(0));
					double temperature = Double.parseDouble(parts[1].trim());

					map.compute(firstLetter, (_, v) -> {
						if (v == null) return new AggregateData(1, temperature);
						return new AggregateData(v.count() + 1, v.sum() + temperature);
					});
				} catch (NumberFormatException | IndexOutOfBoundsException _) {
				}
			});
		} catch (IOException e) {
			log.error("Error processing file {}: {}", file, e.getMessage());
		}
	}
}