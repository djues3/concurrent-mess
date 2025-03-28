package rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.job;

import module java.base;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.App;
import rs.edu.raf.ddjuretanovi8622rn.concurrent.mess.command.Command;

public final class JobManager {

	private static final Logger log = LoggerFactory.getLogger(JobManager.class);
	private static final Path JOBS_CONFIG_FILE = Path.of("load_config.toml");


	private final ExecutorService executorService;

	private final Map<String, Job> jobs = new ConcurrentHashMap<>();

	private boolean started = false;

	private Path watchDir;

	public JobManager() {
		executorService = Executors.newVirtualThreadPerTaskExecutor();
	}


	public void init(boolean loadJobs, Path watchDir) {
		if (started) {
			throw new IllegalStateException("JobManager is already started");
		}
		started = true;
		this.watchDir = watchDir;
		log.info("JobManager initialized with watch directory: {}", watchDir);


		if (loadJobs) {
			loadSavedJobs();
		}
	}

	public void quit(boolean saveJobs) {
		if (!started) {
			log.warn("JobManager not started, nothing to quit");
			return;
		}

		if (saveJobs) {
			log.info("Saving unexecuted jobs to {}", JOBS_CONFIG_FILE);
			saveUnexecutedJobs();
		}
		log.debug("Shutting down executor service");

		executorService.shutdown();
		try {
			log.info("Waiting for JobManager executor service to terminate");
			if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
				executorService.shutdownNow();
			}
		} catch (InterruptedException e) {
			executorService.shutdownNow();
			Thread
					.currentThread()
					.interrupt();
		}

		started = false;
		log.info("JobManager shut down");
	}

	public void scan(Command.Scan scan) {
		if (!started) {
			log.error("JobManager not started, cannot execute scan");
			return;
		}
		if (jobs.containsKey(scan.jobName())) {
			log.error("Job with name {} already exists", scan.jobName());
			log.debug("The spec doesn't say what to do in this situation, so nothing will happen");
			return;
		}

		log.info("Starting scan job: {}", scan.jobName());

		try {
			try {
				// Truncate output file
				Path path = Path.of(scan.outputFilename());
				Files.deleteIfExists(path);
				Files.createFile(path);
			} catch (IOException e) {
				log.warn("Job {} - Could not delete existing output file: {}", scan.jobName(), e.getMessage());
			}

			List<Path> filesToProcess = getFilesToProcess();

			if (filesToProcess.isEmpty()) {
				log.warn("Job {} - No files to process", scan.jobName());
				return;
			}

			Job job = new Job(scan.jobName(), scan, filesToProcess);
			jobs.put(scan.jobName(), job);

			executorService.submit(() -> {
				try {
					job.start();
					log.info("Job {} - completed", scan.jobName());
				}
				catch (InterruptedException | CancellationException e) {
					log.info("Job {} - interrupted / cancelled", scan.jobName());
				} catch (Exception e) {
					log.error(
							"Job {} - Error in job: {} error: {}",
							scan.jobName(),
							e.getMessage(),
							e
									.getClass()
									.getSimpleName());
					job.cancel();
				}
			});
		} catch (Exception e) {
			log.error("Failed to start scan job: {}", e.getMessage());
		}
	}

	public void status(String jobName) {
		if (!started) {
			log.error("JobManager not started, cannot check status");
			return;
		}
		if (jobName == null || jobName.isEmpty()) {
			log.info("No job name provided, showing status of all jobs");
			jobs.forEach((name, job) -> {
				Job.JobInfo jobInfo = job.getJobInfo();
				log.info(
						"{} is {}",
						name,
						jobInfo
								.state()
								.name()
								.toLowerCase());
			});
			return;
		}

		log.info("Checking status of job: {}", jobName);

		if (!jobs.containsKey(jobName)) {
			log.info("Job {} not found", jobName);
			return;
		}
		Job job = jobs.get(jobName);
		if (job == null) {
			log.info("Job {} not found", jobName);
			return;
		}

		Job.JobInfo jobInfo = job.getJobInfo();
		log.info(
				"{} is {}",
				jobName,
				jobInfo
						.state()
						.name()
						.toLowerCase());

	}

	public boolean isStarted() {
		return started;
	}


	private List<Path> getFilesToProcess() {
		try {
			try (var stream = Files.walk(watchDir, 1)) {
				return stream
						.filter(Files::isRegularFile)
						.filter(p -> {
							String filename = p
									.toString()
									.toLowerCase();
							return filename.endsWith(".txt") || filename.endsWith(".csv");
						})
						.collect(Collectors.toList());
			}
		} catch (IOException e) {
			log.error("Error getting files to process: {}", e.getMessage());
			return List.of();
		}
	}

	private void loadSavedJobs() {
		try {
			if (!Files.exists(JOBS_CONFIG_FILE)) {
				log.info("No saved jobs file found");
				return;
			}

			TomlMapper tomlMapper = App.ObjectMapperHolder.getTomlMapper();
			SavedJobs savedJobs = tomlMapper.readValue(JOBS_CONFIG_FILE.toFile(), SavedJobs.class);

			if (savedJobs.jobs() == null || savedJobs
					.jobs()
					.isEmpty()) {
				log.info("No jobs to load from saved file");
				return;
			}

			for (SavedJob savedJob : savedJobs.jobs()) {
				if ("SCAN".equals(savedJob.jobType())) {
					Command.Scan scanCommand = savedJob
							.scanParams()
							.toScanCommand(savedJob.jobName());
					scan(scanCommand);
					log.info("Loaded saved job: {}", savedJob.jobName());
				}
			}
		} catch (Exception e) {
			log.error("Failed to load saved jobs: {}", e.getMessage());
		}
	}

	private void saveUnexecutedJobs() {
		try {
			List<SavedJob> pendingJobs = jobs
					.values()
					.stream()
					.filter(job -> job
							.getJobInfo()
							.state() == JobState.PENDING || job
							.getJobInfo()
							.state() == JobState.RUNNING)
					.map(job -> SavedJob.fromCommand(job
							                                 .getJobInfo()
							                                 .scanParams()))
					.collect(Collectors.toList());
			if (pendingJobs.isEmpty()) {
				log.info("No unexecuted jobs to save");
				return;
			}

			SavedJobs savedJobs = new SavedJobs(pendingJobs);
			TomlMapper tomlMapper = App.ObjectMapperHolder.getTomlMapper();
			tomlMapper.writeValue(JOBS_CONFIG_FILE.toFile(), savedJobs);

			log.info("Saved {} unexecuted jobs to {}", pendingJobs.size(), JOBS_CONFIG_FILE);
		} catch (Exception e) {
			log.error("Failed to save jobs: {}", e.getMessage());
		}
	}

}


record SavedJob(String jobName, String jobType, SavedScanParameters scanParams) {
	public static SavedJob fromCommand(Command.Scan scan) {
		SavedScanParameters params = new SavedScanParameters(
				scan.min(),
		                                                     scan.max(),
		                                                     scan.letter(),
		                                                     scan.outputFilename());
		return new SavedJob(scan.jobName(), "SCAN", params);
	}
}

record SavedScanParameters(double min, double max, char letter, String outputFilename) {
	public Command.Scan toScanCommand(String jobName) {
		return new Command.Scan(min, max, letter, outputFilename, jobName);
	}
}

record SavedJobs(List<SavedJob> jobs) {}