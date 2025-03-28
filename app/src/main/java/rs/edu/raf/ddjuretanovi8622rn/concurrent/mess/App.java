package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess;

// Preview feature -- Module Import Declarations

import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command.Command;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command.CommandParser;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command.PoisonPill;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.config.ConcurrentMessConfig;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.directory.monitoring.DirectoryWatcher;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.directory.monitoring.MapReportService;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.job.JobManager;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.queue.MessageQueue;

public class App {
	private static final Logger log = LoggerFactory.getLogger(App.class);
	private final MessageQueue mq = new MessageQueue();
	private final JobManager jobManager = new JobManager();
	private Path watchDir;
	private DirectoryWatcher watcher;
	private MapReportService mapReportService;

	/// Equivalent to:
	/// ```java
	/// public static void main(String[] args){
	///     var app = new App();
	///     app.main();
	///}
	///```
	//  Preview feature -- Simple Source Files and Instance Main Methods
	public void main() throws Exception {
		log.info("Starting Concurrent Mess...");
		log.info("""
				                 Due to the fact that this app uses multithreaded logging,
				                 it is possible that the "Enter command:" prompt will be followed by some log messages in the same line, as an example:
				                 "Enter command: 2025-03-28 15:43:05.494 INFO  --- [     virtual-26] r.e.r.d.concurrent.mess.App              : Received event: ENTRY_CREATE - File: watch/file.txt"
				                 This won't cause an issue with command parsing.
				         """);
		var toml = ObjectMapperHolder.getTomlMapper();
		try (var is = App.class.getResourceAsStream("/config.toml")) {
			if (is == null) {
				throw new IllegalStateException("Configuration file not found");
			}
			var cfg = toml.readValue(is, ConcurrentMessConfig.class);
			this.watchDir = Path.of(cfg.watchDirectory());
			this.watcher = new DirectoryWatcher(this.watchDir);
			this.mapReportService = new MapReportService(this.watcher);
		} catch (Exception e) {
			log.error("Failed to read configuration file {}", e.getMessage());
			log.error("Exiting...");
			return;
		}
//
//		var t = Thread
//				.ofVirtual()
//				.start(new FileProcessingTask(
//						Path.of("watch/file.txt"),
//						new Command.Scan(0, 100, 'H', "output.txt", "jobs")));
//		t.join();
//
//		if (true) return;
		try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
			log.info("Starting directory monitoring...");
			var monitorTask = exec.submit(() -> this.watcher.start());
			var cliTask = exec.submit(() -> {
				try {
					cliThread();
				} catch (Exception e) {
					log.error("Error in CLI: {}", e.getMessage());
				}
			});
			var consumerTask = exec.submit(() -> {
				try {
					queueConsumer();
				} catch (Exception e) {
					log.error("Error in queue consumer: {}", e.getMessage());
				}
			});
			var reportTask = exec.submit(() -> {
				try {
					this.mapReportService.start();
				} catch (Exception e) {
					log.error("Error in report service: {}", e.getMessage());
				}
			});
			// Block until the CLI thread exits
			cliTask.get();
			// run on the main thread, I hate that this is necessary
			// since everything else is running on the virtual thread executor(s)
			// all the threads are daemon threads which means that the JVM can exit
			var pill = this.mq.take();
			if (pill instanceof PoisonPill(boolean saveJobs) ) {
				if (saveJobs) {
					this.jobManager.quit(true);
				} else {
					this.jobManager.quit(false);
				}
			}
			this.mapReportService.stop();
			consumerTask.cancel(false);
			// consumer task should exit immediately after the CLI task, so an explicit cancel is unnecessary
			monitorTask.cancel(true);
			reportTask.cancel(true);
		} finally {
			log.info("Exiting...");
		}

	}

	private void cliThread() {
		try (var reader = new BufferedReader(new InputStreamReader(System.in))) {
			while (true) {
				System.out.print("Enter command: ");
				String line = reader.readLine();
				if (line == null || line.isEmpty()) {
					continue;
				}
				Command command;
				try {
					command = CommandParser.parseCommand(line);
				} catch (IllegalArgumentException e) {
					log.error("Error parsing command: {}", e.getMessage());
					continue;
				}
				if (command != null) {
					if (command instanceof Command.Stop(var saveJobs)) {
						log.info("Emitting poison pill...");
						this.mq.emit(new PoisonPill(saveJobs));
						break;
					}

					this.mq.emit(command);
				} else {
					log.warn("Invalid command: {}", line);
				}
			}
		} catch (IOException e) {
			log.error("Error reading input: {}", e.getMessage());
		}
	}

	private void queueConsumer() {
		while (true) {
			try {
				var message = this.mq.take();
				if (message instanceof PoisonPill) {
					log.info("Poison pill detected. Exiting consumer thread...");
					break;
				}
				switch (message) {
					case Command command -> {
						switch (command) {
							case Command.ExportMap _ -> this.mapReportService.exportMapToCsv();
							case Command.Map _ -> this.mapReportService.printMap();
							case Command.Scan scan -> jobManager.scan(scan);
							case Command.Start start -> jobManager.init(start.loadJobs(), watchDir);
							case Command.Status status -> jobManager.status(status.jobname());
							case Command.Stop _ -> throw new IllegalStateException(
									"Stop command should never be emitted into the message queue");
						}
					}
					case PoisonPill _ ->
							throw new IllegalStateException("Message queue should never contain a poison pill");
				}

			} catch (InterruptedException e) {
				log.error("Error taking message from queue: {}", e.getMessage());
			}
		}
	}

	public static class ObjectMapperHolder {

		private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

		private static final TomlMapper TOML_MAPPER = new TomlMapper();

		static {
			OBJECT_MAPPER.findAndRegisterModules();
			TOML_MAPPER.findAndRegisterModules();

			TOML_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
		}

		@SuppressWarnings("unused")
		public static ObjectMapper getObjectMapper() {
			return OBJECT_MAPPER;
		}

		public static TomlMapper getTomlMapper() {
			return TOML_MAPPER;
		}
	}
}
